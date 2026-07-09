package net.minecraft.client.yiz.tool.damage;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 伤害数值提供接口
 * 开发者实现此接口来提供伤害计算的三个关键数值。
 * 所有方法都有默认实现（返回 0），开发者可按需 Override。
 */
public interface DamageValueProvider {

    /**
     * 目标最大生命值百分比伤害。
     *
     * @param target 目标实体
     * @return 百分比值（0.0 - 1.0），默认 0
     */
    default double getTargetMaxHealthPercentage(Entity target) {
        return 0.0;
    }

    /**
     * 固定数值修正。
     *
     * @param entity 攻击实体
     * @param target 目标实体
     * @return 固定数值（可正可负），默认 0
     */
    default double getFixedValue(Entity entity, Entity target) {
        return 0.0;
    }

    /**
     * 最终数值百分比提升。
     *
     * @param entity 攻击实体
     * @param target 目标实体
     * @return 百分比值（-1.0 到 +∞），默认 0
     */
    default double getFinalPercentageMultiplier(Entity entity, Entity target) {
        return 0.0;
    }
}
