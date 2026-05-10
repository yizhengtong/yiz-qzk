package net.minecraft.client.yiz.effect.activation;

import net.minecraft.client.yiz.effect.EffectContext;

import java.util.Objects;

/**
 * 生效条件接口
 * 定义效果是否应该触发的判断契约。
 */
@FunctionalInterface
public interface ActivationCondition {

    /**
     * 判断效果是否应该生效。
     *
     * @param context 效果上下文
     * @return true = 生效，false = 不生效
     */
    boolean shouldActivate(EffectContext context);

    /**
     * 组合多个条件（AND 逻辑）。
     */
    default ActivationCondition and(ActivationCondition other) {
        Objects.requireNonNull(other);
        return (ctx) -> this.shouldActivate(ctx) && other.shouldActivate(ctx);
    }

    /**
     * 组合多个条件（OR 逻辑）。
     */
    default ActivationCondition or(ActivationCondition other) {
        Objects.requireNonNull(other);
        return (ctx) -> this.shouldActivate(ctx) || other.shouldActivate(ctx);
    }

    /**
     * 取反条件。
     */
    default ActivationCondition negate() {
        return (ctx) -> !this.shouldActivate(ctx);
    }

    /**
     * 获取生效条件类型名称。
     */
    default String getConditionName() {
        return "自定义条件";
    }
}
