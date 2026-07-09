package net.minecraft.client.yiz.tool.damage;

import net.minecraft.client.yiz.attribute.AttributeModifier;
import net.minecraft.client.yiz.attribute.ModifierStack;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.tool.helper.EffectContextHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认伤害计算器
 * 提供标准的伤害计算和应用逻辑，集成多乘区属性计算系统。
 */
public final class DefaultDamageCalculator {

    private DefaultDamageCalculator() {}

    /**
     * 计算最终伤害（使用多乘区系统）。
     *
     * @param attacker   攻击者
     * @param target     目标
     * @param baseDamage 基础伤害值
     * @return 伤害结果
     */
    public static DamageResult calculateDamage(Entity attacker, Entity target, double baseDamage) {
        // 1. 收集修正器
        List<AttributeModifier> additive = new ArrayList<>();
        List<AttributeModifier> multiplicative = new ArrayList<>();
        List<AttributeModifier> independent = new ArrayList<>();

        // 1a. %伤害增幅 — 已迁移至 NeoForge 属性 generic_damage，由 modifyHurtAmount 消费

        // 1b. %伤害减免 — 已迁移至饰品 AccessoryFlags 路径

        // 2. 使用多乘区计算
        double finalDamage = ModifierStack.calculate(baseDamage, additive, multiplicative, independent);

        // 3. 应用目标护甲减伤
        if (target instanceof LivingEntity livingTarget) {
            double armor = livingTarget.getArmorValue();
            finalDamage = applyArmorReduction(finalDamage, armor);
        }

        // 4. 构建结果
        return new DamageResult(
            finalDamage,
            baseDamage,
            null
        );
    }

    /**
     * 应用伤害到目标。
     */
    public static void applyDamage(Entity attacker, Entity target, DamageResult damage) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        DamageSource source = createDamageSource(attacker, target);
        livingTarget.hurt(source, (float) damage.finalDamage());

        // 触发击退
        if (damage.knockback() > 0) {
            applyKnockback(attacker, livingTarget, damage.knockback());
        }
    }

    /**
     * 快捷方法：计算并应用伤害。
     */
    public static void calculateAndApply(Entity attacker, Entity target, double baseDamage) {
        DamageResult result = calculateDamage(attacker, target, baseDamage);
        applyDamage(attacker, target, result);
    }

    /**
     * 快捷方法：计算并应用伤害（带击退）。
     */
    public static void calculateAndApplyWithKnockback(
        Entity attacker, Entity target, double baseDamage, double knockbackStrength
    ) {
        DamageResult result = calculateDamage(attacker, target, baseDamage).withKnockback(knockbackStrength);
        applyDamage(attacker, target, result);
    }

    /**
     * 护甲减伤公式（Minecraft 原版近似）。
     * 最高减免 80%。
     */
    private static double applyArmorReduction(double damage, double armor) {
        double reduction = Math.min(armor / 5.0, 0.8);
        return damage * (1.0 - reduction);
    }

    /**
     * 护甲韧性修正公式。
     */
    private static double applyArmorToughness(double damage, double armor, double toughness) {
        double reduction = armor / (armor + toughness + 4.0);
        return damage * (1.0 - reduction);
    }

    /**
     * 创建伤害源。
     */
    private static DamageSource createDamageSource(Entity attacker, Entity target) {
        if (attacker == null) {
            return target.damageSources().generic();
        }
        return EffectContextHelper.getAttackDamageSource(attacker);
    }

    /**
     * 应用击退效果。
     */
    private static void applyKnockback(Entity attacker, LivingEntity target, double knockback) {
        Vec3 direction = target.position()
            .subtract(attacker.position())
            .normalize();

        // 零向量 normalize 会产生 NaN，极近距离时跳过击退
        if (Double.isNaN(direction.x)) return;

        target.push(
            direction.x * knockback,
            0.4,
            direction.z * knockback
        );
    }
}
