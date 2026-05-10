package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 直接健康值修改执行器
 * 处理健康值修改标签，直接调用健康值修改管理器，不走伤害流程。
 */
public final class DirectHealthModExecutor {

    private DirectHealthModExecutor() {}

    /**
     * 执行直接健康值修改。
     *
     * @param attacker 攻击者
     * @param target   目标
     * @param context  攻击上下文（用作元数据）
     */
    public static void executeDirectHealthMod(
        LivingEntity attacker, Entity target, EffectContext context
    ) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        // 构建效果上下文
        EffectContext effContext = EffectContext.create(attacker, target);

        // 直接调用健康值修改管理器
        // 不走 hurt 流程，不会被"伤害免疫"、"闪避"等机制影响
        HealthModificationResult result = HealthModificationManager.executeModification(
            livingTarget, effContext
        );

        // 处理结果（可选）
        if (result.success()) {
            // 可在此播放特效
        }
    }

    /**
     * 简单的直接加/扣血方法。
     */
    public static void setHealthDirectly(LivingEntity target, double newHealth) {
        float clamped = (float) Math.max(0, Math.min(target.getMaxHealth(), newHealth));
        target.setHealth(clamped);

        if (clamped <= 0) {
            target.die(target.damageSources().generic());
        }
    }

    /**
     * 直接增加生命值。
     */
    public static void addHealthDirectly(LivingEntity target, double amount) {
        if (amount > 0) {
            float newHealth = Math.min(target.getMaxHealth(), target.getHealth() + (float) amount);
            target.setHealth(newHealth);
        }
    }

    /**
     * 直接减少生命值。
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
