package net.minecraft.client.yiz.api;

/**
 * 伤害应用结果
 *
 * @param applied  实际应用的伤害值（正数）
 * @param delta    应用后目标的剩余 delta 偏移量
 * @param canceled 是否被取消
 * @param reason   取消原因（canceled 为 true 时有效）
 */
public record DamageResult(
    float applied,
    float delta,
    boolean canceled,
    String reason
) {
    public static DamageResult success(float applied, float delta) {
        return new DamageResult(applied, delta, false, "");
    }

    public static DamageResult canceled(String reason) {
        return new DamageResult(0, 0, true, reason);
    }
}
