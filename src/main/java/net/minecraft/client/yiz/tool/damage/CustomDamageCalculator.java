package net.minecraft.client.yiz.tool.damage;

import net.minecraft.client.yiz.effect.EffectContext;
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
     * @param context 效果上下文
     * @param damage  伤害结果
     */
    public static void applyDamage(EffectContext context, DamageResult damage) {
        if (!(context.target() instanceof LivingEntity livingTarget)) return;

        boolean isTrueDamage = damage.hasTag(DamageTag.TRUE_DAMAGE);
        boolean isArmorPiercing = damage.hasTag(DamageTag.ARMOR_PIERCING);
        boolean pierceInvulnerability = damage.hasTag(DamageTag.PIERCE_INVULNERABILITY);

        if (isTrueDamage) {
            applyTrueDamage(context, livingTarget, damage);
        } else if (isArmorPiercing && pierceInvulnerability) {
            applyArmorPiercingAndPierceInvulnerability(context, livingTarget, damage);
        } else if (isArmorPiercing) {
            applyArmorPiercingDamage(context, livingTarget, damage);
        } else if (pierceInvulnerability) {
            applyPierceInvulnerabilityDamage(context, livingTarget, damage);
        } else {
            applyNormalDamage(context, livingTarget, damage);
        }
    }

    /**
     * 真实伤害应用：直接修改 Health，穿透无敌帧。
     */
    private static void applyTrueDamage(EffectContext context, LivingEntity target, DamageResult damage) {
        float currentHealth = target.getHealth();
        float newHealth = Math.max(0, currentHealth - (float) damage.finalDamage());
        target.setHealth(newHealth);

        // 触发受伤动画
        target.hurtMarked = true;
        if (target.level() != null) {
            target.level().broadcastEntityEvent(target, (byte) 2);
        }

        // 检查死亡
        if (newHealth <= 0 && context.entity() != null) {
            target.die(EffectContextHelper.getAttackDamageSource(context.entity()));
        }
    }

    /**
     * 破甲伤害应用：跳过护甲减伤，仍受无敌帧限制。
     */
    private static void applyArmorPiercingDamage(EffectContext context, LivingEntity target, DamageResult damage) {
        // 检查无敌帧
        if (target.invulnerableTime > 0) return;

        DamageSource source = context.entity() != null
            ? context.entity().damageSources().magic()
            : target.damageSources().generic();

        target.hurt(source, (float) damage.finalDamage());
    }

    /**
     * 破甲+破无敌帧伤害应用。
     */
    private static void applyArmorPiercingAndPierceInvulnerability(
        EffectContext context, LivingEntity target, DamageResult damage
    ) {
        int savedInvulnerableTime = target.invulnerableTime;

        try {
            target.invulnerableTime = 0;
            DamageSource source = context.entity() != null
                ? context.entity().damageSources().magic()
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
        EffectContext context, LivingEntity target, DamageResult damage
    ) {
        int savedInvulnerableTime = target.invulnerableTime;

        try {
            target.invulnerableTime = 0;
            DamageSource source = context.entity() != null
                ? EffectContextHelper.getAttackDamageSource(context.entity())
                : target.damageSources().generic();
            target.hurt(source, (float) damage.finalDamage());
        } finally {
            target.invulnerableTime = savedInvulnerableTime;
        }
    }

    /**
     * 默认伤害应用：经过护甲减伤，受无敌帧限制。
     */
    private static void applyNormalDamage(EffectContext context, LivingEntity target, DamageResult damage) {
        DamageSource source = context.entity() != null
            ? EffectContextHelper.getAttackDamageSource(context.entity())
            : target.damageSources().generic();

        target.hurt(source, (float) damage.finalDamage());
    }

    /**
     * 应用击退效果。
     */
    public static void applyKnockback(EffectContext context, LivingEntity target, double knockbackStrength) {
        if (context.entity() == null || knockbackStrength <= 0) return;

        Vec3 direction = target.position()
            .subtract(context.entity().position())
            .normalize();

        target.push(
            direction.x * knockbackStrength,
            0.4,
            direction.z * knockbackStrength
        );
    }
}
