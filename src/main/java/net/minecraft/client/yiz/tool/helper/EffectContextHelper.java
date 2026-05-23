package net.minecraft.client.yiz.tool.helper;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 上下文辅助工具
 * 提供 EffectContext 的快捷创建方法。
 */
public final class EffectContextHelper {

    private EffectContextHelper() {}

    /**
     * 从攻击事件创建上下文。
     */
    public static EffectContext fromAttack(LivingEntity attacker, Entity target) {
        return EffectContext.createAttackContext(attacker, target);
    }

    /**
     * 从物品使用事件创建上下文。
     */
    public static EffectContext fromItemUse(LivingEntity user, Entity target) {
        return EffectContext.create(user, target);
    }

    /**
     * 从实体 tick 创建上下文。
     */
    public static EffectContext fromEntityTick(LivingEntity entity) {
        return EffectContext.create(entity, null);
    }

    /**
     * 检查上下文中是否有指定类型的元数据。
     */
    public static boolean hasMetadata(EffectContext context, String key) {
        return context.metadata() != null && context.metadata().containsKey(key);
    }

    /**
     * 从上下文获取元数据值。
     */
    @SuppressWarnings("unchecked")
    public static <T> T getMetadata(EffectContext context, String key, T defaultValue) {
        if (context.metadata() != null && context.metadata().containsKey(key)) {
            return (T) context.metadata().get(key);
        }
        return defaultValue;
    }

    /**
     * 添加上下文元数据。
     */
    public static EffectContext withMetadata(EffectContext context, String key, Object value) {
        return context.withMetadata(key, value);
    }

    /**
     * 获取攻击伤害源（自动选择 playerAttack 或 mobAttack）。
     */
    public static DamageSource getAttackDamageSource(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            return attacker.damageSources().playerAttack(player);
        }
        return attacker.damageSources().mobAttack(attacker);
    }
}
