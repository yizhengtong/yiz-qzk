package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 健康值数值接口
 * 纯数值计算，不修改实体。
 * 收集所有 HealthModifier 并聚合，返回统一的计算结果。
 *
 * <p>所有以本模组为前置的模组应通过此接口获取最终的数值，
 * 而非自行处理修正器逻辑。</p>
 */
public final class HealthValueCalculator {

    private HealthValueCalculator() {}

    /**
     * 执行数值计算。
     * <ol>
     *   <li>发布 {@link HealthModificationEvent} 收集所有修正器</li>
     *   <li>按优先级排序</li>
     *   <li>按模式聚合（ADDITIVE / MULTIPLICATIVE / OVERRIDE / DELTA）</li>
     *   <li>返回计算结果</li>
     * </ol>
     *
     * @param target  被修改健康值的实体
     * @return 计算结果
     */
    public static CalculationResult calculate(LivingEntity target) {
        // 1. 创建并发布事件
        HealthModificationEvent event = new HealthModificationEvent(target);
        NeoForge.EVENT_BUS.post(event);

        // 2. 检查取消
        if (event.isCanceled()) {
            return CalculationResult.canceled(event.getCancelReason());
        }

        // 3. 收集修正器
        List<HealthModifier> modifiers = new ArrayList<>(event.getModifiers());

        // 4. 按优先级排序（高优先级在前）
        modifiers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // 5. 聚合计算
        MultiModifierAggregator.AggregatedResult aggregated =
            MultiModifierAggregator.aggregate(modifiers, target);

        // 6. 收集 bypass 标记
        Set<HealthModificationResult.BypassFlag> bypassFlags = collectBypassFlags(modifiers);

        return new CalculationResult(false, "", aggregated, modifiers, bypassFlags);
    }

    /**
     * 计算结果记录。
     */
    public record CalculationResult(
        boolean canceled,
        String cancelReason,
        MultiModifierAggregator.AggregatedResult aggregated,
        List<HealthModifier> modifiers,
        Set<HealthModificationResult.BypassFlag> bypassFlags
    ) {
        public static CalculationResult canceled(String reason) {
            return new CalculationResult(
                true, reason, null, List.of(),
                EnumSet.noneOf(HealthModificationResult.BypassFlag.class)
            );
        }

        public boolean isCanceled() {
            return canceled;
        }
    }

    /**
     * 从修正器列表中收集绕过标记。
     */
    private static Set<HealthModificationResult.BypassFlag> collectBypassFlags(
        List<HealthModifier> modifiers
    ) {
        return EnumSet.noneOf(HealthModificationResult.BypassFlag.class);
    }
}
