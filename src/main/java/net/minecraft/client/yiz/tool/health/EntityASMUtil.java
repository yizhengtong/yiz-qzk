package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.bridge.HealthDataBridge;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

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

    // ==================== 死亡触发器开关 ====================

    /**
     * 当 DELTA 伤害导致有效血量 ≤ 0 时，是否通过反射调用
     * {@link LivingEntity#die(DamageSource)} 触发原版死亡事件。
     * 默认开启。关闭后回退到旧行为（DELTA 扣血但不触发 die()）。
     */
    private static volatile boolean deathTriggerEnabled = true;

    public static boolean isDeathTriggerEnabled() {
        return deathTriggerEnabled;
    }

    public static void setDeathTriggerEnabled(boolean enabled) {
        deathTriggerEnabled = enabled;
    }

    // ==================== 伤害效果开关（粒子 + 音效） ====================

    /** 伤害时是否触发受伤动画、红心闪烁和音效，默认关闭 */
    private static volatile boolean damageEffectsEnabled = false;

    public static boolean isDamageEffectsEnabled() {
        return damageEffectsEnabled;
    }

    /**
     * 设置伤害效果开关。开启后，Delta 系统造成的伤害会触发：
     * <ul>
     *   <li>目标实体受伤动画（红心闪烁）</li>
     *   <li>通用受伤音效 ({@link SoundEvents#GENERIC_HURT})</li>
     * </ul>
     */
    public static void setDamageEffectsEnabled(boolean enabled) {
        damageEffectsEnabled = enabled;
    }

    // ==================== die() 方法句柄（反射缓存） ====================

    private static volatile MethodHandle dieMethodHandle;
    private static volatile boolean dieMethodLookupFailed;

    private static MethodHandle getDieMethodHandle() {
        if (dieMethodHandle != null || dieMethodLookupFailed) return dieMethodHandle;
        try {
            // 使用 setAccessible(true) 反射 + unreflect 获取 protected die() 的 MethodHandle，
            // 而非 MethodHandles.lookup().findVirtual()（公共 Lookup 无法访问 protected 方法）。
            java.lang.reflect.Method dieMethod = LivingEntity.class.getDeclaredMethod("die", DamageSource.class);
            dieMethod.setAccessible(true);
            dieMethodHandle = MethodHandles.lookup().unreflect(dieMethod);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            dieMethodLookupFailed = true;
        }
        return dieMethodHandle;
    }

    /**
     * 当 DELTA 伤害导致实体有效血量 ≤ 0 时，通过反射调用 die()。
     */
    private static void triggerDeathIfDead(LivingEntity entity) {
        if (!deathTriggerEnabled) return;
        // 不依赖 getHealth() 注入（部分实体的 override 在部分路径不合并 delta，判断不可靠）：
        // 直接用 delta 判断「有效血量 = maxHealth + delta ≤ 0」即死亡（delta 是 SynchedEntityData，写入可靠）
        float delta = getHealthDelta(entity);
        if (delta >= 0) return;
        if (entity.getMaxHealth() + delta > 0.0F) return;

        MethodHandle mh = getDieMethodHandle();
        if (mh == null) return;

        try {
            mh.invoke(entity, entity.damageSources().generic());
        } catch (Throwable ignored) {
            // die() 调用失败时静默降级（不掉落但也不崩服）
        }
    }

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
     * 以覆盖其他模组的自定义血量系统。
     * </p>
     */
    public static void addDelta(LivingEntity entity, float amount) {
        if (entity.level().isClientSide()) return;

        // 0. 视觉反馈：受伤动画 + 音效（仅伤害时触发，受开关控制）
        if (amount < 0 && damageEffectsEnabled) {
            entity.hurtTime = 10;
            entity.hurtDuration = 10;
            entity.level().broadcastEntityEvent(entity, (byte) 2);
            entity.playSound(SoundEvents.GENERIC_HURT, 1.0f, 1.0f);
        }

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
        //    捕获其他模组的自定义血量（DataParameter 中存巨量 HP 的实体等）。
        //    独立 try-catch：某通道异常不得中断后续（尤其第 4 步死亡触发）
        try {
            for (EntityDataAccessor<Float> channel : HealthChannelScanner.getFloatChannels(entity)) {
                // 跳过 delta 通道（FE_GET_HEALTH_DATA）：由第 1 层 delta 系统处理，避免 max(0,..) 清负 delta
                if (channel.id() == DirectHealthFallback.DELTA_ACCESSOR_ID) continue;
                float value = entity.getEntityData().get(channel);
                float newValue = Math.max(0, value + amount);
                entity.getEntityData().set(channel, newValue);
            }
        } catch (Throwable ignored) {}

        // 3. 最终保底：反射直接修改 DataItem[] 内部值（内部已 try-catch）
        DirectHealthFallback.damageAll(entity, amount);

        // 更新禁疗跟踪基线
        try { VitalitySeveranceHandler.updateBaseline(entity); } catch (Throwable ignored) {}

        // 4. 死亡触发：若有效血量 ≤ 0，通过反射调用 die() 触发原版死亡事件
        //    解决部分模组 Boss 重写 hurt() 返回 false 导致无掉落物的问题
        triggerDeathIfDead(entity);
    }

    /**
     * 攻方「最初梦幻」通用消费：攻击者带 {@code FIRST_DREAM} → 对目标扣真实血量（绕过目标 hurt 免疫）。
     *
     * <p>通用攻击方钩子（Player.attack / Mob.doHurtTarget / 辖界者 hit / 其它模组攻击入口）统一调用——
     * <b>不依赖目标 hurt</b>（自研血量实体在无敌/免疫期间 hurt 返回 false、不走 super.hurt，
     * onHurtPre 不触发，这里直接从攻击方扣）。</p>
     * <ul>
     *   <li>自研血量实体（EntityHealthLocator 定位到真实血量字段）→ 直接改字段 + 永久禁疗（阻止目标回血弹回）</li>
     *   <li>原版/未定位 → Delta 通道（全局不衰减，持久）</li>
     * </ul>
     */
    public static void applyDreamDamage(LivingEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) return;
        if (attacker.level().isClientSide()) return;
        var inst = attacker.getAttribute(net.minecraft.client.yiz.attribute.YizAttributes.FIRST_DREAM);
        if (inst == null || inst.getValue() <= 0) return;
        float dream = (float) inst.getValue();
        if (net.minecraft.client.yiz.tool.health.EntityHealthLocator.applyPersistentDamage(target, dream)) {
            net.minecraft.client.yiz.tool.health.VitalitySeveranceConfig.set(target, 100.0f, 0); // 永久禁疗，堵死目标回血
        } else {
            modifyHealth(target, -dream); // 原版/未定位：Delta（不衰减，持久）
        }
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
            var ban = VitalitySeveranceConfig.get(entity);
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
            VitalitySeveranceHandler.updateBaseline(entity);
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
    public static float applyVitalitySeverance(LivingEntity entity, float healAmount) {
        if (healAmount <= 0) return healAmount;

        float result = healAmount;

        var config = VitalitySeveranceConfig.get(entity);
        if (config != null) {
            result = config.apply(result);
        }

        float tempBan = VitalitySeveranceHandler.getBanFactor(entity);
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
    public static boolean consumeVitalitySeveranceFlag() {
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
        //
        // 设计意图：当 delta 负值超过 maxHealth 时（如 delta=-50, maxHealth=20），
        // Math.min(health, maxHealth + delta) 可能返回负值。这是有意为之——
        // 极端负 delta 代表"强制致死"语义（如 /kill 等效操作），
        // 负值会被下游 setHealth() 的 Agent 层保护态 clamp 到 ≥1，
        // 死亡由 die()/remove() 拦截链统一处理，不依赖 getHealth() 返回值。
        float delta = getHealthDelta(living);
        if (delta != 0) {
            float ret = Math.min(health, living.getMaxHealth() + delta);
            // 【调试】弹回定位：getHealth 注入返回值（节流）
            if (living.tickCount % 40 == 0 && !living.level().isClientSide()) {
                net.minecraft.client.yiz.tizMod.LOGGER.info("[GetHealth] {} h={} maxHp={} delta={} ret={}",
                    living.getClass().getSimpleName(), health, living.getMaxHealth(), delta, ret);
            }
            return ret;
        }
        if (living.tickCount % 40 == 0 && !living.level().isClientSide()) {
            net.minecraft.client.yiz.tizMod.LOGGER.info("[GetHealth0] {} h={} delta=0",
                living.getClass().getSimpleName(), health);
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
     *   <li>VitalitySeverance 上限：禁疗激活时，有效血量不得超过 {@code maxHealth}</li>
     * </ol>
     * 这确保即使部分模组实体在 DataParameter 中存储了巨量 HP，
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

        // 2. VitalitySeverance 上限：禁疗激活时，有效血量不得超过 maxHealth
        //    防止部分模组实体在 DataParameter 中存储巨量 HP 导致打不死
        if (VitalitySeveranceConfig.get(entity) != null) {
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

    // ==================== 攻击/时间 计数器（供法球系统读取）====================

    private static final java.util.Map<java.util.UUID, Integer> ATTACK_COUNTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> TICK_COUNTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static int incrementAttackCount(LivingEntity entity) {
        return ATTACK_COUNTS.merge(entity.getUUID(), 1, Integer::sum);
    }
    public static int getAttackCount(LivingEntity entity) {
        return ATTACK_COUNTS.getOrDefault(entity.getUUID(), 0);
    }
    public static int incrementTickCount(LivingEntity entity) {
        return TICK_COUNTS.merge(entity.getUUID(), 1, Integer::sum);
    }
    public static int getTickCount(LivingEntity entity) {
        return TICK_COUNTS.getOrDefault(entity.getUUID(), 0);
    }

    // ==================== 复活剩余次数（每条命独立）====================

    private static final java.util.Map<java.util.UUID, Integer> UNDYING_CHARGES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 获取剩余复活次数（未初始化则返回 -1）。 */
    public static int getUndyingCharges(java.util.UUID uuid) {
        return UNDYING_CHARGES.getOrDefault(uuid, -1);
    }

    /** 消耗一次复活，返回剩余次数。 */
    public static int consumeUndyingCharge(java.util.UUID uuid) {
        return UNDYING_CHARGES.merge(uuid, -1, (old, v) -> Math.max(0, old - 1));
    }

    /** 重置复活次数到上限（玩家复活/登录时调用）。 */
    public static void resetUndyingCharges(java.util.UUID uuid, int max) {
        UNDYING_CHARGES.put(uuid, Math.max(0, max));
    }

    // ==================== 保护态 ====================

    /** ThreadLocal：allow 本次 setHealth to pass through protection (e.g., /kill) */
    private static final ThreadLocal<Boolean> BYPASS_PROTECTION = ThreadLocal.withInitial(() -> false);

    /** ThreadLocal：hurt 入口存下攻击者的护甲穿透(百分比)，供目标 getArmorValue() 注入扣减 */
    private static final ThreadLocal<Float> ARMOR_PEN_PCT = ThreadLocal.withInitial(() -> 0f);
    /** ThreadLocal：hurt 入口存下攻击者的护甲穿透(固定值) */
    private static final ThreadLocal<Float> ARMOR_PEN_FLAT = ThreadLocal.withInitial(() -> 0f);

    public static void setArmorPenetration(float pct, float flat) {
        ARMOR_PEN_PCT.set(pct);
        ARMOR_PEN_FLAT.set(flat);
    }
    public static float peekArmorPenPct() { return ARMOR_PEN_PCT.get(); }
    public static float peekArmorPenFlat() { return ARMOR_PEN_FLAT.get(); }
    public static void clearArmorPenetration() {
        ARMOR_PEN_PCT.set(0f);
        ARMOR_PEN_FLAT.set(0f);
    }

    /**
     * 临时放行保护态——调用方在触发能杀死实体的操作前设置，
     * 调用后必须清除。用于 /kill 等必须让血量归零的场景。
     */
    public static void beginBypassProtection() {
        BYPASS_PROTECTION.set(true);
    }

    public static void endBypassProtection() {
        BYPASS_PROTECTION.remove();
    }

    /**
     * 由 ASM Agent 注入的 {@code setHealth(float)} 钩子调用。
     * 确保生命值 never NaN，通常 never &lt;1（保护态），
     * 但可通过 {@link #beginBypassProtection()} 临时放行（如 /kill）。
     */
    @SuppressWarnings("unused")
    public static float clampProtectedHealth(float health) {
        if (Float.isNaN(health)) return 1.0F;
        if (BYPASS_PROTECTION.get()) return health; // 放行：允许 0 或负值
        if (health < 1.0F) return 1.0F;
        return health;
    }
}
