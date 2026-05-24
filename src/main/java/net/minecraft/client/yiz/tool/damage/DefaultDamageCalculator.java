package net.minecraft.client.yiz.tool.damage;

import net.minecraft.client.yiz.attribute.AttributeModifier;
import net.minecraft.client.yiz.attribute.ModifierStack;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.tool.helper.EffectContextHelper;
import net.minecraft.world.damagesource.DamageSource;
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
     * @param context    效果上下文
     * @param baseDamage 基础伤害值
     * @return 伤害结果
     */
    public static DamageResult calculateDamage(EffectContext context, double baseDamage) {
        // 1. 收集修正器
        List<AttributeModifier> additive = new ArrayList<>();
        List<AttributeModifier> multiplicative = new ArrayList<>();
        List<AttributeModifier> independent = new ArrayList<>();

        // 1a. 攻击者物品的 %伤害增幅
        if (context.entity() instanceof LivingEntity attacker) {
            double amp = ItemAttributeHandler.getTotalDamageAmplification(attacker);
            if (amp != 0) {
                multiplicative.add(new AttributeModifier("item_damage_amp", amp, AttributeModifier.ModifierType.MULTIPLICATIVE));
            }
        }

        // 1b. 目标物品的 %伤害减免
        if (context.target() instanceof LivingEntity target) {
            double red = ItemAttributeHandler.getTotalDamageReduction(target);
            if (red != 0) {
                multiplicative.add(new AttributeModifier("item_damage_red", -red, AttributeModifier.ModifierType.MULTIPLICATIVE));
            }
        }

        // 2. 使用多乘区计算
        double finalDamage = ModifierStack.calculate(baseDamage, additive, multiplicative, independent);

        // 3. 应用目标护甲减伤
        if (context.target() instanceof LivingEntity livingTarget) {
            double armor = livingTarget.getArmorValue();
            finalDamage = applyArmorReduction(finalDamage, armor);
        }

        // 4. 构建结果
        return new DamageResult(
            finalDamage,
            baseDamage,
            context.effect() != null ? context.effect().getId() : null
        );
    }

    /**
     * 应用伤害到目标。
     */
    public static void applyDamage(EffectContext context, DamageResult damage) {
        if (!(context.target() instanceof LivingEntity livingTarget)) return;

        DamageSource source = createDamageSource(context);
        livingTarget.hurt(source, (float) damage.finalDamage());

        // 触发击退
        if (damage.knockback() > 0) {
            applyKnockback(context, livingTarget, damage.knockback());
        }
    }

    /**
     * 快捷方法：计算并应用伤害。
     */
    public static void calculateAndApply(EffectContext context, double baseDamage) {
        DamageResult result = calculateDamage(context, baseDamage);
        applyDamage(context, result);
    }

    /**
     * 快捷方法：计算并应用伤害（带击退）。
     */
    public static void calculateAndApplyWithKnockback(
        EffectContext context, double baseDamage, double knockbackStrength
    ) {
        DamageResult result = calculateDamage(context, baseDamage).withKnockback(knockbackStrength);
        applyDamage(context, result);
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
    private static DamageSource createDamageSource(EffectContext context) {
        if (context.entity() == null) {
            return context.target().damageSources().generic();
        }

        // 根据效果父类类型选择伤害源
        if (context.effect() != null) {
            return switch (context.effect().getParentType()) {
                case ECHO -> EffectContextHelper.getAttackDamageSource(context.entity());
                case MANIFESTATION -> context.entity().damageSources().magic();
                case ORIGIN -> context.entity().damageSources().explosion(null);
                default -> context.entity().damageSources().generic();
            };
        }

        return EffectContextHelper.getAttackDamageSource(context.entity());
    }

    /**
     * 应用击退效果。
     */
    private static void applyKnockback(EffectContext context, LivingEntity target, double knockback) {
        Vec3 direction = target.position()
            .subtract(context.entity().position())
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
