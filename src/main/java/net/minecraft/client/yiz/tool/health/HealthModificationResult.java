package net.minecraft.client.yiz.tool.health;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 健康值修改结果
 * 记录修改的详细信息，用于调试和统计。
 *
 * @param success             是否成功
 * @param totalModification   总修改量
 * @param currentHealth       当前健康值
 * @param details             详细修改记录
 * @param cancelReason        取消原因（如果取消）
 */
public record HealthModificationResult(
    boolean success,
    double totalModification,
    float currentHealth,
    List<ModificationDetail> details,
    String cancelReason
) {
    /**
     * 创建成功结果。
     */
    public static HealthModificationResult success(
        double totalModification, float currentHealth, List<ModificationDetail> details
    ) {
        return new HealthModificationResult(true, totalModification, currentHealth, details, "");
    }

    /**
     * 创建取消结果。
     */
    public static HealthModificationResult canceled(String reason) {
        return new HealthModificationResult(false, 0, 0, List.of(), reason);
    }

    /**
     * 修改详细信息。
     */
    public record ModificationDetail(
        ResourceLocation modifierId,
        HealthModifier.ModifierType type,
        double amount
    ) {
        /**
         * 获取本地化描述。
         */
        public Component getLocalizedDescription() {
            String sign = amount > 0 ? "+" : "";
            return Component.translatable(
                "health_modification.yizmodqzk.detail",
                modifierId.toString(),
                type.getDisplayName(),
                sign + amount
            );
        }
    }
}
