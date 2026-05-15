package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.bridge.HealthDataBridge;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;

/**
 * 实体 ASM 工具
 * 管理与健康值 Delta 系统的交互。
 *
 * <p>Delta 存储已迁移到 {@code LivingEntity} 的 {@code SynchedEntityData}
 * 中定义的 {@code EntityDataAccessor<Float>}（参见 LivingEntityMixin），
 * 通过 {@link HealthDataBridge} 接口访问。
 *
 * <p>此类的 {@code specialGetHealth/specialIsAlive/specialIsDeadOrDying}
 * 方法供 {@code LivingHealthTransformer}（ASM ClassFileTransformer）
 * 在类加载时通过字节码注入到所有 LivingEntity 子类的对应方法中。
 */
public final class EntityASMUtil {

    private EntityASMUtil() {}

    // ==================== Delta 管理 ====================

    /**
     * 获取实体的健康值偏移量（delta）。
     * 有效血量上限 = getMaxHealth() + delta
     * 有效当前血量 = min(getHealth(), getMaxHealth() + delta)
     */
    public static float getHealthDelta(LivingEntity entity) {
        if (entity instanceof HealthDataBridge bridge) {
            return bridge.yizmodqzk$getHealthDelta();
        }
        return 0F;
    }

    /**
     * 设置健康值偏移量。
     * 注意：仅允许设置 ≤0 的值，且仅在服务端生效。
     */
    public static void setHealthDelta(LivingEntity entity, float value) {
        if (value > 0) return;
        if (entity.level().isClientSide()) return;
        if (entity instanceof HealthDataBridge bridge) {
            bridge.yizmodqzk$setHealthDelta(value);
        }
    }

    /**
     * 累加健康值偏移量（负值降低血量上限，正值恢复血量上限）。
     * 自动裁剪：累计结果若 > 0 则归零（delta 不允许为正）。
     * <p>
     * 同时会对目标实体上<b>所有</b> Float 类型的 DataParameter 施加等量伤害，
     * 以覆盖其他模组的自定义血量系统（如泰坦生物的 TITAN_HEALTH）。
     * </p>
     */
    public static void addDelta(LivingEntity entity, float amount) {
        if (entity.level().isClientSide()) return;

        // 1. 主系统：delta 偏移（对 vanilla/ASM 实体生效）
        float current = getHealthDelta(entity);
        float newDelta = current + amount;
        if (newDelta > 0) {
            newDelta = 0;
        }
        if (entity instanceof HealthDataBridge bridge) {
            bridge.yizmodqzk$setHealthDelta(newDelta);
        }

        // 2. 通用打击：直接修改该实体上所有 Float DataParameter 通道
        //    捕获其他模组的自定义血量（EntityTitan 的 TITAN_HEALTH 等）
        for (EntityDataAccessor<Float> channel : HealthChannelScanner.getFloatChannels(entity)) {
            float value = entity.getEntityData().get(channel);
            float newValue = Math.max(0, value + amount);
            entity.getEntityData().set(channel, newValue);
        }

        // 3. 最终保底：反射直接修改 DataItem[] 内部值
        //    确保上述两步未覆盖的 Float 数据通道也能被打到
        DirectHealthFallback.damageAll(entity, amount);

        // 更新禁疗跟踪基线，使 tick 级强制基于最新血量判断
        HealBanHandler.updateBaseline(entity);
    }

    /**
     * 正向或负向修改健康值（治疗或伤害）。
     * <p>
     * delta < 0 时走完整三層系统加伤害（同 {@link #addDelta}）。<br>
     * delta > 0 时直接修改所有 Float 数据通道（治疗，不经过 delta 系统）。
     * </p>
     * 此方法绕过实体自定义 {@code hurt()}，可供外部模组的技能系统直接调用。
     */
    public static void modifyHealth(LivingEntity entity, float delta) {
        if (entity.level().isClientSide()) return;

        if (delta < 0) {
            // 伤害：走完整三層系统
            addDelta(entity, delta);
        } else if (delta > 0) {
            // 治疗：先应用禁疗配置（百分比 + 固定值）
            var ban = HealBanConfig.get(entity);
            if (ban != null) {
                delta = ban.apply(delta);
                if (delta <= 0) return; // 完全被禁疗
            }

            // 再直接修改所有 Float 数据通道
            for (EntityDataAccessor<Float> channel : HealthChannelScanner.getFloatChannels(entity)) {
                float value = entity.getEntityData().get(channel);
                entity.getEntityData().set(channel, value + delta);
            }
            DirectHealthFallback.healAll(entity, delta);

            // 更新禁疗跟踪基线，防止 tick 级强制将本次治疗也拦截
            HealBanHandler.updateBaseline(entity);
        }
    }

    // ==================== ASM 级别禁疗注入 ====================

    private static final ThreadLocal<Boolean> HEAL_BAN_APPLIED_BY_ASM =
        ThreadLocal.withInitial(() -> false);

    /**
     * 由 ASM 在 {@code heal()} 方法 HEAD 处注入。
     * 在 NeoForge 事件钩子和原版逻辑执行之前，对治疗量应用禁疗配置。
     * 对所有 LivingEntity 子类生效，即使其 {@code heal()} 被子类重写。
     */
    public static float applyHealBan(LivingEntity entity, float healAmount) {
        if (healAmount <= 0) return healAmount;

        float result = healAmount;

        var config = HealBanConfig.get(entity);
        if (config != null) {
            result = config.apply(result);
        }

        float tempBan = HealBanHandler.getBanFactor(entity);
        if (tempBan > 0) {
            result *= (1.0f - tempBan);
        }

        if (result < healAmount) {
            HEAL_BAN_APPLIED_BY_ASM.set(true);
        }
        return Math.max(0, result);
    }

    /**
     * 消费 ASM 禁疗标记。
     * 供 Mixin 和事件处理器调用，避免在 heal() → setHealth() 链中重复禁疗。
     */
    public static boolean consumeHealBanFlag() {
        boolean v = HEAL_BAN_APPLIED_BY_ASM.get();
        HEAL_BAN_APPLIED_BY_ASM.set(false);
        return v;
    }

    /**
     * 移除实体的 delta 记录（归零）。
     */
    public static void removeDelta(LivingEntity entity) {
        if (entity instanceof HealthDataBridge bridge) {
            bridge.yizmodqzk$setHealthDelta(0F);
        }
    }

    /**
     * 清除所有 delta 记录（服务器重启或世界卸载时调用）。
     * DataParameter 模式不需要全局清除，保留用于兼容。
     */
    public static void clearAll() {
        // DataParameter 模式不需要全局清除
    }

    // ==================== 健康值特殊计算（供 ASM 注入调用） ====================

    /**
     * 特殊 getHealth：被 ASM 注入到所有 getHealth() 调用中。
     *
     * <p>决策流程：
     * <ol>
     *   <li>isDead 标志 → 返回 0.0F</li>
     *   <li>Player 无敌 → 返回 max(1, maxHealth)</li>
     *   <li>正常 → min(health, maxHealth + delta)</li>
     * </ol>
     *
     * @param health 原始 getHealth() 返回值
     * @param entityObj 目标实体对象
     * @return 经过修正后的健康值
     */
    public static float specialGetHealth(float health, Object entityObj) {
        if (!(entityObj instanceof LivingEntity living)) return health;

        // 1. isDead 标志强制死亡
        // TODO: 当 LivingEntityExpandedContext 实现后检查 isDead 标志
        // if (living instanceof LivingEntityExpandedContext ctx && ctx.uom$livingECData().isDead)
        //     return 0.0F;

        // 2. delta 截断
        // 注：玩家无敌检查由 PlayerMixin 在 Mixin 层处理。
        // 当 ASM Agent 加载时，PlayerMixin 的 @Inject 仍生效，
        // 因此无需在此处重复检查。
        float delta = getHealthDelta(living);
        if (delta != 0) {
            return Math.min(health, living.getMaxHealth() + delta);
        }

        return health;
    }

    /**
     * 特殊 isAlive：被 ASM 注入到所有 isAlive() 调用中。
     */
    public static boolean specialIsAlive(boolean original, Object entityObj) {
        if (!(entityObj instanceof LivingEntity living)) return original;
        // TODO: isDead 标志覆写
        float delta = getHealthDelta(living);
        if (delta != 0) {
            return living.getHealth() > 0;
        }
        return original;
    }

    /**
     * 特殊 isDeadOrDying：被 ASM 注入到所有 isDeadOrDying() 调用中。
     */
    public static boolean specialIsDeadOrDying(boolean original, Object entityObj) {
        if (!(entityObj instanceof LivingEntity living)) return original;
        // TODO: isDead 标志覆写
        float delta = getHealthDelta(living);
        if (delta != 0) {
            return living.getHealth() <= 0;
        }
        return original;
    }

    // ==================== CoreMod 级别健康值修正 ====================

    /**
     * CoreMod 级别 {@code getHealth()} 返回值修正。
     * <p>
     * 由 {@code META-INF/yizmodqzk_healban.js} 通过 ASM 在 {@code LivingEntity.getHealth()}
     * 的每个 {@code FRETURN} 之前注入。
     * </p>
     * <p>
     * 作为保底方案，在 Mixin 和 ASM Agent 均未生效时提供最终防线：
     * <ol>
     *   <li>Delta 截断：修正后的血量不超过 {@code maxHealth + delta}</li>
     *   <li>HealBan 上限：禁疗激活时，有效血量不得超过 {@code maxHealth}</li>
     * </ol>
     * 这确保即使泰坦类实体在 DataParameter 中存储了巨量 HP，
     * {@code getHealth()} 返回的是封顶后的有效值，死亡检测可以正常触发。
     * </p>
     *
     * @param originalHealth 原始 getHealth() 返回值
     * @param entity         目标实体
     * @return 经过封顶修正后的健康值
     */
    public static float specialCoreModGetHealth(float originalHealth, LivingEntity entity) {
        // 1. Delta 截断（与 Mixin 的 getHealth 修改保持一致）
        float delta = getHealthDelta(entity);
        float afterDelta = (delta != 0)
            ? Math.min(originalHealth, entity.getMaxHealth() + delta)
            : originalHealth;

        // 2. HealBan 上限：禁疗激活时，有效血量不得超过 maxHealth
        //    防止泰坦类实体在 DataParameter 中存储巨量 HP 导致打不死
        if (HealBanConfig.get(entity) != null) {
            return Math.min(afterDelta, entity.getMaxHealth());
        }

        return afterDelta;
    }

    // ==================== 统计 ====================

    /**
     * 获取当前跟踪的实体数量（用于调试）。
     * DataParameter 模式下没有全局存储，返回 -1。
     */
    public static int getTrackedEntityCount() {
        return -1;
    }

    // ==================== Agent 状态（供指令查询） ====================

    /** Agent premain/agentmain 是否成功执行 */
    public static volatile boolean agentActive = false;

    /** Agent Transformer 是否至少转换过一个类 */
    public static volatile boolean agentTransformed = false;

    /** 由 Agent 回调 */
    @SuppressWarnings("unused")
    public static void markAgentActive() { agentActive = true; }

    /** 由 Transformer 回调 */
    @SuppressWarnings("unused")
    public static void markAgentTransformed() { agentTransformed = true; }

    // ==================== 保护态生命值纠正 ====================

    /**
     * 由 ASM Agent 注入的 {@code setHealth(float)} 钩子调用。
     * 确保生命值 never &lt;1, never NaN。
     */
    @SuppressWarnings("unused")
    public static float clampProtectedHealth(float health) {
        if (Float.isNaN(health) || health < 1.0F) return 1.0F;
        return health;
    }
}
