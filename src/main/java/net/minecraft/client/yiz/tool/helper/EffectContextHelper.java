package net.minecraft.client.yiz.tool.helper;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 伤害源辅助工具（效果框架移除后精简版）。
 */
public final class EffectContextHelper {

    private EffectContextHelper() {}

    /**
     * 获取实体的攻击伤害源。
     */
    public static DamageSource getAttackDamageSource(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return entity.damageSources().mobAttack(living);
        }
        return entity.damageSources().generic();
    }
}