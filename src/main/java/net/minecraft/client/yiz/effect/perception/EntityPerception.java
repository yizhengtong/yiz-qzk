package net.minecraft.client.yiz.effect.perception;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.world.entity.LivingEntity;

/**
 * 实体绑定感知 — 天赋 (Talent)
 * 效果直接绑定到实体，通过解锁系统管理。
 */
public class EntityPerception implements PerceptionMode {

    @Override
    public boolean check(LivingEntity entity, EffectContext context) {
        if (context == null || context.effect() == null) {
            return false;
        }
        return UnlockManager.isUnlocked(entity, context.effect().getId());
    }

    @Override
    public String getTypeName() {
        return "天赋 (Talent)";
    }

    @Override
    public PerceptionType getPerceptionType() {
        return PerceptionType.ENTITY;
    }
}
