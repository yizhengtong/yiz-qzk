package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.LivingEntity;

/**
 * 健康值修改管理器
 * 数值计算与实体应用的薄层编排。
 *
 * <p>完整流程：</p>
 * <ol>
 *   <li>{@link HealthValueCalculator#calculate(LivingEntity, EffectContext)} — 数值计算</li>
 *   <li>{@link HealthApplier#apply(LivingEntity, MultiModifierAggregator.AggregatedResult, java.util.Set)} — 实体应用</li>
 * </ol>
 */
public final class HealthModificationManager {

    private HealthModificationManager() {}

    /**
     * 执行完整的健康值修改流程：数值计算 → 实体应用。
     */
    public static HealthModificationResult executeModification(
        LivingEntity entity, EffectContext context
    ) {
        // 1. 数值阶段
        var result = HealthValueCalculator.calculate(entity, context);

        if (result.isCanceled()) {
            return HealthModificationResult.canceled(result.cancelReason());
        }

        // 2. 实体阶段
        return HealthApplier.apply(entity, result.aggregated(), result.bypassFlags());
    }

    /**
     * 快捷方法：触发生命值修改。
     */
    public static HealthModificationResult triggerModification(
        LivingEntity entity, EffectContext context
    ) {
        return executeModification(entity, context);
    }
}
