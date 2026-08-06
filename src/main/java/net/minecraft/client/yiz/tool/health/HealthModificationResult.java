package net.minecraft.client.yiz.tool.health;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 健康值修改结果
 * 记录修改的详细信息，用于调试和统计。
 *
 * @param success             是否成功
 * @param totalModification   总修改量
 * @param currentHealth       修改后的当前健康值
 * @param details             详细修改记录
 * @param deltaAdjustment     Delta 偏移量（仅在 DELTA 模式下有效）
 * @param bypassFlags         绕过标记位
 * @param cancelReason        取消原因（如果取消）
 * @param failureReason       失败原因（如果 success=false 且非取消）
 */
public record HealthModificationResult(
    boolean success,
    double totalModification,
    float currentHealth,
    List<ModificationDetail> details,
    float deltaAdjustment,
    Set<BypassFlag> bypassFlags,
    String cancelReason,
    String failureReason
) {
    /**
     * 创建成功结果（无 delta，无绕过）。
     */
    public static HealthModificationResult success(
        double totalModification, float currentHealth, List<ModificationDetail> details
    ) {
        return new HealthModificationResult(
            true, totalModification, currentHealth, details,
            0F, EnumSet.noneOf(BypassFlag.class), "", ""
        );
    }

    /**
     * 创建成功结果（带 delta 调整）。
     */
    public static HealthModificationResult successWithDelta(
        double totalModification, float currentHealth, List<ModificationDetail> details,
        float deltaAdjustment
    ) {
        return new HealthModificationResult(
            true, totalModification, currentHealth, details,
            deltaAdjustment, EnumSet.noneOf(BypassFlag.class), "", ""
        );
    }

    /**
     * 创建成功结果（带绕过标记）。
     */
    public static HealthModificationResult successWithBypass(
        double totalModification, float currentHealth, List<ModificationDetail> details,
        Set<BypassFlag> bypassFlags
    ) {
        return new HealthModificationResult(
            true, totalModification, currentHealth, details,
            0F, bypassFlags, "", ""
        );
    }

    /**
     * 创建取消结果。
     */
    public static HealthModificationResult canceled(String reason) {
        return new HealthModificationResult(
            false, 0, 0, List.of(), 0F, EnumSet.noneOf(BypassFlag.class), reason, ""
        );
    }

    /**
     * 创建失败结果（非取消，例如被锁血机制拦截）。
     */
    public static HealthModificationResult failed(String reason) {
        return new HealthModificationResult(
            false, 0, 0, List.of(), 0F, EnumSet.noneOf(BypassFlag.class), "", reason
        );
    }

    // ==================== 快捷检查 ====================

    public boolean isCanceled() {
        return !success && !cancelReason.isEmpty();
    }

    public boolean isFailed() {
        return !success && !failureReason.isEmpty();
    }

    public boolean hasBypass(BypassFlag flag) {
        return bypassFlags.contains(flag);
    }

    public boolean isDeltaModification() {
        return deltaAdjustment != 0F;
    }

    // ==================== 内部枚举 ====================

    /**
     * 绕过标记枚举。
     * 决定修改如何绕过原版健康值系统的各个检查点。
     */
    public enum BypassFlag {
        /** 绕过 BAN_HEALING 治疗削减 */
        BYPASS_BAN_HEALING,
        /** 绕过护甲减伤 */
        BYPASS_ARMOR,
        /** 绕过无敌帧 */
        BYPASS_INVULNERABILITY,
        /** 使用 VarHandle 直接写底层 DataParameter（绕过 setHealth()） */
        USE_VAR_HANDLE,
        /** 强制触发死亡判定（即使血量 > 0 也视为致死） */
        FORCE_DEATH,
        /** 跳过死亡判定（即使血量 ≤ 0 也不触发 die()） */
        SUPPRESS_DEATH
    }

    // ==================== 内部记录 ====================

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
