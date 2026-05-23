package net.minecraft.client.yiz.effect.activation;

import net.minecraft.client.yiz.effect.EffectContext;

/**
 * 实体攻击时生效条件
 * 默认总是返回 true，由第三方模组 Override 实现自定义判断。
 */
public class EntityAttackCondition implements ActivationCondition {

    @Override
    public boolean shouldActivate(EffectContext context) {
        return true;
    }

    @Override
    public String getConditionName() {
        return "实体攻击时生效";
    }
}
