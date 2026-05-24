package net.minecraft.client.yiz.tool.damage;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.tool.helper.EffectContextHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 直接攻击执行器
 * 处理真实伤害和穿甲标签的强制执行，确保不被拦截。
 */
public final class DirectAttackExecutor {

    private DirectAttackExecutor() {}

    /**
     * 执行强制攻击。
     * 根据标签选择执行方式，绕过普通 hurt() 流程。
     *
     * @param attacker 攻击者
     * @param target   目标
     * @param context  攻击上下文
     * @param damage   伤害结果
     */
    public static void executeForcedAttack(
        LivingEntity attacker, Entity target, AttackContext context, DamageResult damage
    ) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        boolean isTrueDamage = damage.hasTag(DamageTag.TRUE_DAMAGE);
        boolean isArmorPiercing = damage.hasTag(DamageTag.ARMOR_PIERCING);

        if (isTrueDamage) {
            // 真实伤害：直接扣除 Health，无视无敌帧、护甲、闪避
            float currentHealth = livingTarget.getHealth();
            livingTarget.setHealth(Math.max(0, currentHealth - (float) damage.finalDamage()));

            // 触发受伤动画
            livingTarget.level().broadcastEntityEvent(livingTarget, (byte) 2);

            // 检查死亡
            if (livingTarget.getHealth() <= 0) {
                livingTarget.die(EffectContextHelper.getAttackDamageSource(attacker));
            }

        } else if (isArmorPiercing) {
            // 穿甲伤害：强制命中，跳过护甲减免
            DamageSource source = attacker.damageSources().magic();

            // 如果有破无敌帧标签，临时清除无敌帧
            if (damage.hasTag(DamageTag.PIERCE_INVULNERABILITY)) {
                int savedInvulnerableTime = livingTarget.invulnerableTime;
                try {
                    livingTarget.invulnerableTime = 0;
                    livingTarget.hurt(source, (float) damage.finalDamage());
                } finally {
                    livingTarget.invulnerableTime = savedInvulnerableTime;
                }
            } else {
                livingTarget.hurt(source, (float) damage.finalDamage());
            }
        }

        // 击退等后续效果
        context.applyPostAttackEffects();
    }

    /**
     * 使用 EffectContext 执行强制攻击。
     */
    public static void executeForcedAttackFromContext(EffectContext context, DamageResult damage) {
        if (!(context.target() instanceof LivingEntity livingTarget) || context.entity() == null) return;

        boolean isTrueDamage = damage.hasTag(DamageTag.TRUE_DAMAGE);
        boolean isArmorPiercing = damage.hasTag(DamageTag.ARMOR_PIERCING);

        if (isTrueDamage) {
            float currentHealth = livingTarget.getHealth();
            livingTarget.setHealth(Math.max(0, currentHealth - (float) damage.finalDamage()));
            livingTarget.hurtMarked = true;
            livingTarget.level().broadcastEntityEvent(livingTarget, (byte) 2);

            if (livingTarget.getHealth() <= 0) {
                livingTarget.die(EffectContextHelper.getAttackDamageSource(context.entity()));
            }
        } else if (isArmorPiercing) {
            DamageSource source = context.entity().damageSources().magic();
            if (damage.hasTag(DamageTag.PIERCE_INVULNERABILITY)) {
                int saved = livingTarget.invulnerableTime;
                try {
                    livingTarget.invulnerableTime = 0;
                    livingTarget.hurt(source, (float) damage.finalDamage());
                } finally {
                    livingTarget.invulnerableTime = saved;
                }
            } else {
                livingTarget.hurt(source, (float) damage.finalDamage());
            }
        } else {
            DamageSource source = context.entity().damageSources().magic();
            if (damage.hasTag(DamageTag.PIERCE_INVULNERABILITY)) {
                int saved = livingTarget.invulnerableTime;
                livingTarget.invulnerableTime = 0;
                livingTarget.hurt(source, (float) damage.finalDamage());
                livingTarget.invulnerableTime = saved;
            } else {
                livingTarget.hurt(source, (float) damage.finalDamage());
            }
        }
    }
}
