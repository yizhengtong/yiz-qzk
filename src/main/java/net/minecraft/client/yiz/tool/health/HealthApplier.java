package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Set;

/**
 * 健康值实体接口
 * 将聚合后的数值结果应用到目标实体。
 * 不参与数值计算，只负责对实体的最终写入。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>将聚合结果写入 {@link LivingEntity#setHealth(float)}</li>
 *   <li>处理 DELTA / OVERRIDE 等特殊模式</li>
 *   <li>执行禁疗检查（调用 {@link HealBanValueCalculator}）</li>
 *   <li>处理 bypass / 死亡判定</li>
 * </ul>
 */
public final class HealthApplier {

    private HealthApplier() {}

    /**
     * 将聚合结果应用到实体。
     *
     * @param entity       目标实体
     * @param aggregated   聚合结果
     * @param bypassFlags  绕过标记
     * @return 应用结果
     */
    public static HealthModificationResult apply(
        LivingEntity entity,
        MultiModifierAggregator.AggregatedResult aggregated,
        Set<HealthModificationResult.BypassFlag> bypassFlags
    ) {
        // ========== 1. DELTA 模式 ==========
        if (aggregated.deltaTotal() != 0) {
            EntityASMUtil.addDelta(entity, aggregated.deltaTotal());
            return HealthModificationResult.successWithDelta(
                aggregated.deltaTotal(), entity.getHealth(),
                toModificationDetails(aggregated), aggregated.deltaTotal()
            );
        }

        // ========== 2. OVERRIDE 模式 ==========
        if (aggregated.hasOverride()) {
            float clamped = (float) Math.max(0, aggregated.overrideValue());
            entity.setHealth(clamped);
            return HealthModificationResult.successWithBypass(
                aggregated.overrideValue(), entity.getHealth(),
                toModificationDetails(aggregated), bypassFlags
            );
        }

        // ========== 3. 标准模式：加法 + 乘法 ==========
        float currentHealth = entity.getHealth();
        float maxHealth = entity.getMaxHealth();

        double computed = (currentHealth + aggregated.additiveTotal())
                        * aggregated.multiplicativeTotal();

        // ========== 4. 禁疗检查 ==========
        if (computed > currentHealth) {
            double banFactor = HealBanValueCalculator.calculate(entity);
            if (banFactor > 0) {
                double healingAmount = computed - currentHealth;
                double reducedHealing = HealBanApplier.apply(healingAmount, banFactor);
                computed = currentHealth + reducedHealing;
            }
        }

        // ========== 5. 边界裁剪 ==========
        float finalHealth;
        if (bypassFlags.contains(HealthModificationResult.BypassFlag.SUPPRESS_DEATH)) {
            finalHealth = (float) Math.max(0.5, Math.min(maxHealth, computed));
        } else {
            finalHealth = (float) Math.max(0, Math.min(maxHealth, computed));
        }

        // ========== 6. 写入实体 ==========
        entity.setHealth(finalHealth);

        // ========== 7. 死亡判定 ==========
        if (!bypassFlags.contains(HealthModificationResult.BypassFlag.SUPPRESS_DEATH)
            && entity.getHealth() <= 0) {
            entity.die(entity.damageSources().generic());
        }

        return HealthModificationResult.success(
            computed, entity.getHealth(),
            toModificationDetails(aggregated)
        );
    }

    // ==================== 辅助方法 ====================

    private static List<HealthModificationResult.ModificationDetail> toModificationDetails(
        MultiModifierAggregator.AggregatedResult aggregated
    ) {
        return aggregated.details().stream()
            .map(d -> new HealthModificationResult.ModificationDetail(
                d.modifierId(), d.type(), d.amount()))
            .toList();
    }
}
