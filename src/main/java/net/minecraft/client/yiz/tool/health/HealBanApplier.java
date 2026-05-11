package net.minecraft.client.yiz.tool.health;

/**
 * 禁疗实体接口
 * 将禁疗比率应用到治疗量上。
 *
 * <p>纯计算：输入原始治疗量和禁疗比率，输出实际生效的治疗量。</p>
 */
public final class HealBanApplier {

    private HealBanApplier() {}

    /**
     * 应用禁疗到治疗量。
     *
     * @param healAmount 原始治疗量
     * @param banFactor  禁疗比率（0.0 ~ 1.0）
     * @return 禁疗后的实际治疗量
     */
    public static double apply(double healAmount, double banFactor) {
        if (banFactor <= 0) return healAmount;
        if (banFactor >= 1.0) return 0.0;
        return healAmount * (1.0 - banFactor);
    }
}
