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
        //    直接写 DataParameter 绕过其 getHealth() 覆盖，作用于真实存储值
        for (EntityDataAccessor<Float> channel : HealthChannelScanner.getFloatChannels(entity)) {
            float value = entity.getEntityData().get(channel);
            float newValue = Math.max(0, value + amount);
            entity.getEntityData().set(channel, newValue);
        }
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

    // ==================== 统计 ====================

    /**
     * 获取当前跟踪的实体数量（用于调试）。
     * DataParameter 模式下没有全局存储，返回 -1。
     */
    public static int getTrackedEntityCount() {
        return -1;
    }
}
