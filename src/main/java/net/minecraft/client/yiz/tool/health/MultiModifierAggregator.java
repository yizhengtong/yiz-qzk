package net.minecraft.client.yiz.tool.health;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 多修正器聚合计算器。
 * 替代简单的加法汇总，支持：
 * 1. 按优先级排序
 * 2. 按修正模式分类（加法 / 乘法 / 覆盖 / Delta）
 * 3. 边界裁剪
 * 4. 对齐 ModifierStack 的多乘区模式
 */
public final class MultiModifierAggregator {

    private MultiModifierAggregator() {}

    /**
     * 单条详细记录。
     */
    public record Detail(
        ResourceLocation modifierId,
        HealthModifier.ModifierType type,
        HealthModifier.ModificationMode mode,
        double amount
    ) {}

    /**
     * 聚合计算结果。
     */
    public record AggregatedResult(
        double additiveTotal,
        double multiplicativeTotal,
        boolean hasOverride,
        double overrideValue,
        float deltaTotal,
        List<Detail> details
    ) {}

    /**
     * 聚合所有修正器，按模式分类计算。
     */
    public static AggregatedResult aggregate(
        List<HealthModifier> modifiers,
        LivingEntity entity
    ) {
        double additiveSum = 0;
        double multiplicativeProduct = 1.0;
        boolean hasOverride = false;
        double overrideVal = Double.NaN;
        float deltaSum = 0;
        List<Detail> details = new ArrayList<>();

        for (HealthModifier modifier : modifiers) {
            double amount = modifier.getModificationAmount(entity);
            HealthModifier.ModificationMode mode = modifier.getMode();

            details.add(new Detail(
                modifier.getId(),
                modifier.getType(),
                mode,
                amount
            ));

            switch (mode) {
                case ADDITIVE -> additiveSum += amount;
                case MULTIPLICATIVE -> multiplicativeProduct *= (1.0 + amount);
                case OVERRIDE -> {
                    hasOverride = true;
                    overrideVal = amount;
                }
                case DELTA -> deltaSum += (float) amount;
            }
        }

        return new AggregatedResult(
            additiveSum,
            multiplicativeProduct,
            hasOverride,
            overrideVal,
            deltaSum,
            details
        );
    }

    /**
     * 从聚合结果计算最终值。
     *
     * @param result     聚合结果
     * @param baseHealth 当前健康值
     * @return 最终健康值（或 delta 偏移量）
     */
    public static double computeFinal(AggregatedResult result, float baseHealth) {
        if (result.hasOverride) {
            return result.overrideValue;
        }
        return Math.max(0, (baseHealth + result.additiveTotal) * result.multiplicativeTotal);
    }
}
