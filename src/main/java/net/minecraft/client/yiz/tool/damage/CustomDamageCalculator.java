package net.minecraft.client.yiz.tool.damage;

import net.minecraft.client.yiz.tool.helper.EffectContextHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 自定义伤害计算器
 * 根据伤害标签执行不同的伤害应用逻辑。
 */
public final class CustomDamageCalculator {

    private CustomDamageCalculator() {}

    /**
     * 应用伤害（自动根据标签选择应用方式）。
     *
     * @param entity 攻击者
     * @param target 目标实体
     * @param damage 伤害结果
     */
    public static void applyDamage(Entity entity, Entity target, DamageResult damage) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        boolean isTrueDamage = damage.hasTag(DamageTag.TRUE_DAMAGE);
        boolean isArmorPiercing = damage.hasTag(DamageTag.ARMOR_PIERCING);
        boolean pierceInvulnerability = damage.hasTag(DamageTag.PIERCE_INVULNERABILITY);

        if (isTrueDamage) {
            applyTrueDamage(entity, livingTarget, damage);
        } else if (isArmorPiercing && pierceInvulnerability) {
            applyArmorPiercingAndPierceInvulnerability(entity, livingTarget, damage);
        } else if (isArmorPiercing) {
            applyArmorPiercingDamage(entity, livingTarget, damage);
        } else if (pierceInvulnerability) {
            applyPierceInvulnerabilityDamage(entity, livingTarget, damage);
        } else {
            applyNormalDamage(entity, livingTarget, damage);
        }
    }

    /**
     * 真实伤害应用：直接修改 Health，穿透无敌帧。
     */
    private static void applyTrueDamage(Entity entity, LivingEntity target, DamageResult damage) {
        float currentHealth = target.getHealth();
        float damageAmount = (float) damage.finalDamage();
        if (Float.isNaN(currentHealth)) currentHealth = 20F;
        if (Float.isNaN(damageAmount)) return;
        float newHealth = Math.max(0, currentHealth - damageAmount);
        target.setHealth(newHealth);

        // 触发受伤动画
        target.hurtMarked = true;
        if (target.level() != null) {
            target.level().broadcastEntityEvent(target, (byte) 2);
        }

        // 检查死亡
        if (newHealth <= 0 && entity != null) {
            target.die(EffectContextHelper.getAttackDamageSource(entity));
        }
    }

    /**
     * 破甲伤害应用：跳过护甲减伤，仍受无敌帧限制。
     */
    private static void applyArmorPiercingDamage(Entity entity, LivingEntity target, DamageResult damage) {
        // 检查无敌帧
        if (target.invulnerableTime > 0) return;

        DamageSource source = entity != null
            ? entity.damageSources().magic()
            : target.damageSources().generic();

        target.hurt(source, (float) damage.finalDamage());
    }

    /**
     * 破甲+破无敌帧伤害应用。
     */
    private static void applyArmorPiercingAndPierceInvulnerability(
        Entity entity, LivingEntity target, DamageResult damage
    ) {
        int savedInvulnerableTime = target.invulnerableTime;

        try {
            target.invulnerableTime = 0;
            DamageSource source = entity != null
                ? entity.damageSources().magic()
                : target.damageSources().generic();
            target.hurt(source, (float) damage.finalDamage());
        } finally {
            target.invulnerableTime = savedInvulnerableTime;
        }
    }

    /**
     * 仅破无敌帧伤害应用：经过护甲减伤，无视无敌帧。
     */
    private static void applyPierceInvulnerabilityDamage(
        Entity entity, LivingEntity target, DamageResult damage
    ) {
        int savedInvulnerableTime = target.invulnerableTime;

        try {
            target.invulnerableTime = 0;
            DamageSource source = entity != null
                ? EffectContextHelper.getAttackDamageSource(entity)
                : target.damageSources().generic();
            target.hurt(source, (float) damage.finalDamage());
        } finally {
            target.invulnerableTime = savedInvulnerableTime;
        }
    }

    /**
     * 默认伤害应用：经过护甲减伤，受无敌帧限制。
     */
    private static void applyNormalDamage(Entity entity, LivingEntity target, DamageResult damage) {
        DamageSource source = entity != null
            ? EffectContextHelper.getAttackDamageSource(entity)
            : target.damageSources().generic();

        target.hurt(source, (float) damage.finalDamage());
    }

    /**
     * 应用击退效果。
     */
    public static void applyKnockback(Entity entity, LivingEntity target, double knockbackStrength) {
        if (entity == null || knockbackStrength <= 0) return;

        Vec3 direction = target.position()
            .subtract(entity.position())
            .normalize();

        // 零向量 normalize 会产生 NaN，极近距离时跳过击退
        if (Double.isNaN(direction.x)) return;

        target.push(
            direction.x * knockbackStrength,
            0.4,
            direction.z * knockbackStrength
        );
    }
}
