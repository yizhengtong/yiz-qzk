package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 直接健康值修改执行器
 * 提供快捷方法直接修改实体健康值，统一委派给 {@link HealthModificationManager}。
 *
 * <p>不走原版 hurt() 流程，因此不会被"伤害免疫"、"闪避"等原版机制影响。</p>
 */
public final class DirectHealthModExecutor {

    private DirectHealthModExecutor() {}

    /**
     * 执行直接健康值修改（通过完整的事件-聚合-应用流程）。
     */
    public static void executeDirectHealthMod(
        LivingEntity attacker, Entity target, EffectContext context
    ) {
        if (!(target instanceof LivingEntity livingTarget)) return;
        HealthModificationManager.executeModification(livingTarget, context);
    }

    /**
     * 直接设置生命值（等效于 OVERRIDE 模式）。
     */
    public static void setHealthDirectly(LivingEntity target, double newHealth) {
        float clamped = (float) Math.max(0, Math.min(target.getMaxHealth(), newHealth));
        target.setHealth(clamped);

        if (clamped <= 0) {
            target.die(target.damageSources().generic());
        }
    }

    /**
     * 直接增加生命值（等效于 ADDITIVE 模式正数）。
     */
    public static void addHealthDirectly(LivingEntity target, double amount) {
        if (amount > 0) {
            float newHealth = Math.min(target.getMaxHealth(), target.getHealth() + (float) amount);
            target.setHealth(newHealth);
        }
    }

    /**
     * 直接减少生命值（等效于 ADDITIVE 模式负数）。
     */
    public static void removeHealthDirectly(LivingEntity target, double amount) {
        if (amount > 0) {
            float newHealth = Math.max(0, target.getHealth() - (float) amount);
            target.setHealth(newHealth);

            if (newHealth <= 0) {
                target.die(target.damageSources().generic());
            }
        }
    }
}
