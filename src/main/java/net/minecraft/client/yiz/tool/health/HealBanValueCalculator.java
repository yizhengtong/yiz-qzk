package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.attribute.ModAttributes;
import net.minecraft.world.entity.LivingEntity;

/**
 * 禁疗数值接口
 * 计算目标实体的禁疗比率。
 *
 * <p>禁疗比率基于目标的 {@link ModAttributes#ATTACHMENT 眷恋} 属性：
 * <ul>
 *   <li>每 1 点眷恋 = 10% 禁疗</li>
 *   <li>10 点眷恋 = 100% 禁疗（完全无法被治疗）</li>
 * </ul>
 */
public final class HealBanValueCalculator {

    private HealBanValueCalculator() {}

    /**
     * 计算目标实体的禁疗比率。
     *
     * @param target 接收治疗的目标实体
     * @return 禁疗比率（0.0 = 不禁疗，1.0 = 完全禁疗）
     */
    public static double calculate(LivingEntity target) {
        var attrInstance = target.getAttribute(ModAttributes.ATTACHMENT);
        if (attrInstance == null) return 0.0;
        double attachment = attrInstance.getValue();
        if (attachment <= 0) return 0.0;
        return Math.min(1.0, attachment / 10.0);
    }
}
