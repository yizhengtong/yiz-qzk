package net.minecraft.client.yiz.effect.activation;

import net.minecraft.client.yiz.effect.EffectContext;

/**
 * 无条件生效（常驻效果）
 * shouldActivate 始终返回 true。
 */
public class PassiveCondition implements ActivationCondition {

    @Override
    public boolean shouldActivate(EffectContext context) {
        return true;
    }

    @Override
    public String getConditionName() {
        return "常驻生效";
    }
}
