package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 伤害减免注册表（Agent 级写前钩子）
 * <p>
 * 由 ASM Agent 在 {@code LivingEntity.setHealth(float)} 入口处调用。
 * 在所有 health 修改路径的最终写入点进行拦截，无论从哪个入口来的修改都生效。
 * 下游模组注册自己的 {@link HealthModifier}，在最终写入前进行百分比/固定/任何形式的修改。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 注册 8% 每层减免
 * DamageReductionRegistry.register((entity, oldHealth, newHealth) -> {
 *     if (entity instanceof Player player) {
 *         int level = StarDataHelper.getStarLevel(player);
 *         float reduction = level * 0.08f;
 *         float damage = oldHealth - newHealth;
 *         float reducedDamage = damage * (1.0f - reduction);
 *         return oldHealth - reducedDamage;
 *     }
 *     return newHealth;
 * });
 * }</pre>
 */
// 大白话: 减伤方法
public final class DamageReductionRegistry {

    private static final List<HealthModifier> MODIFIERS = new CopyOnWriteArrayList<>();

    /** ThreadLocal 标志：Agent 层已处理减免，Mixin 层跳过 */
    private static final ThreadLocal<Boolean> REDUCTION_APPLIED = ThreadLocal.withInitial(() -> false);

    // ══════════════════════════════════════════════════════════
    //  背包废除全局开关
    // ══════════════════════════════════════════════════════════
    /** 全局背包废除开关：开启后所有 {@link HealthModifier} 被跳过 */
    private static volatile boolean ABOLISHED = false;

    /**
     * 设置背包废除状态。
     * @param abolished true 时所有注册的 HealthModifier 被跳过，不产生防御效果
     */
    public static void setAbolished(boolean abolished) {
        ABOLISHED = abolished;
    }

    /**
     * 查询背包废除状态。
     */
    public static boolean isAbolished() {
        return ABOLISHED;
    }

    private DamageReductionRegistry() {}

    /**
     * 由 ASM Agent 调用：标记本请求的减免已由 Agent 层处理。
     * Mixin 层检测到此标志时应跳过，避免重复减免。
     */
    public static void markReductionApplied() {
        REDUCTION_APPLIED.set(true);
    }

    /**
     * Mixin 层调用：检查 Agent 层是否已处理减免。
     * @return true = Agent 已处理，Mixin 跳过
     */
    public static boolean consumeReductionApplied() {
        boolean applied = REDUCTION_APPLIED.get();
        REDUCTION_APPLIED.remove();
        return applied;
    }

    /**
     * 健康值修改器，在 setHealth() 最终写入前执行
     */
    @FunctionalInterface
    public interface HealthModifier {
        /**
         * @param entity    被修改的实体
         * @param oldHealth 当前健康值（getHealth() 已走 Delta 系统的值）
         * @param newHealth 即将写入的健康值
         * @return 修改后的健康值（最终写入的内容）
         */
        float modify(LivingEntity entity, float oldHealth, float newHealth);
    }

    /**
     * 注册一个健康值修改器
     */
    public static void register(HealthModifier modifier) {
        MODIFIERS.add(modifier);
    }

    /**
     * 由 ASM Agent 在 setHealth(float) 入口调用
     */
    public static float applyBeforeSetHealth(LivingEntity entity, float newHealth) {
        // 全局废除 → 跳过所有 modifier
        if (ABOLISHED) return newHealth;
        if (MODIFIERS.isEmpty()) return newHealth;

        float oldHealth = entity.getHealth();

        // 要致死时放行（/kill 等），不拦截
        if (newHealth <= 0) return newHealth;

        // 只对扣血方向生效（治疗/不变时不干涉）
        if (newHealth >= oldHealth) return newHealth;

        float result = newHealth;
        for (HealthModifier modifier : MODIFIERS) {
            result = modifier.modify(entity, oldHealth, result);
        }
        return Math.max(0, result);
    }
}
