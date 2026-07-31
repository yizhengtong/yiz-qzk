package net.minecraft.client.yiz.tool.attribute;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import net.minecraft.client.yiz.attribute.YizAttributes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品 + 实体 属性读写工具。
 *
 * <p>原版属性（攻击力/攻击速度/交互距离/横扫/耐久）通过 {@link DataComponents#ATTRIBUTE_MODIFIERS}
 * 读写；库模组自定义原生属性（generic_damage / damage_block / counter_* 等）通过
 * {@link YizAttributes} 的 Holder 写入物品修饰符。</p>
 *
 * <p>实体级方法直接操作 {@link LivingEntity#getAttribute(Holder)}。</p>
 *
 * <p>历史遗留的 item_stats NBT 路径（damage_amplification / damage_reduction / sweep_decay）
 * 已在 1.21.1 重构中迁移至原生属性系统并删除，不再支持。</p>
 */
public final class ItemAttributeHandler {

    private ItemAttributeHandler() {}

    // ══════════════════════════════════════════════════════════════
    //  1. 攻击力
    // ══════════════════════════════════════════════════════════════

    public static double getAttackDamage(ItemStack stack) {
        return sumVanillaModifier(stack, Attributes.ATTACK_DAMAGE);
    }

    public static void setAttackDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, Attributes.ATTACK_DAMAGE, "attack_damage", value);
    }

    public static void addAttackDamage(ItemStack stack, double delta) {
        setAttackDamage(stack, getAttackDamage(stack) + delta);
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 攻击速度
    // ══════════════════════════════════════════════════════════════

    public static double getAttackSpeed(ItemStack stack) {
        return sumVanillaModifier(stack, Attributes.ATTACK_SPEED);
    }

    public static void setAttackSpeed(ItemStack stack, double value) {
        setVanillaModifier(stack, Attributes.ATTACK_SPEED, "attack_speed", value);
    }

    public static void addAttackSpeed(ItemStack stack, double delta) {
        setAttackSpeed(stack, getAttackSpeed(stack) + delta);
    }

    // 通过 Registry 查找属性（可选，不存在时为 null）
    private static final Holder<Attribute> ENTITY_INTERACTION_RANGE =
            BuiltInRegistries.ATTRIBUTE.getHolder(
                    ResourceLocation.withDefaultNamespace("entity_interaction_range")).orElse(null);
    private static final Holder<Attribute> SWEEPING_DAMAGE_RATIO =
            BuiltInRegistries.ATTRIBUTE.getHolder(
                    ResourceLocation.fromNamespaceAndPath("neoforge", "sweeping_damage_ratio")).orElse(null);

    // ══════════════════════════════════════════════════════════════
    //  3. 交互距离
    // ══════════════════════════════════════════════════════════════

    public static double getInteractionRange(ItemStack stack) {
        return ENTITY_INTERACTION_RANGE != null ? sumVanillaModifier(stack, ENTITY_INTERACTION_RANGE) : 0;
    }

    public static void setInteractionRange(ItemStack stack, double value) {
        if (ENTITY_INTERACTION_RANGE == null) return;
        setVanillaModifier(stack, ENTITY_INTERACTION_RANGE, "entity_interaction_range", value);
    }

    public static void addInteractionRange(ItemStack stack, double delta) {
        setInteractionRange(stack, getInteractionRange(stack) + delta);
    }

    // ══════════════════════════════════════════════════════════════
    //  4. 横扫伤害比例
    // ══════════════════════════════════════════════════════════════

    public static double getSweepRatio(ItemStack stack) {
        return SWEEPING_DAMAGE_RATIO != null ? sumVanillaModifier(stack, SWEEPING_DAMAGE_RATIO) : 0;
    }

    public static void setSweepRatio(ItemStack stack, double value) {
        if (SWEEPING_DAMAGE_RATIO == null) return;
        setVanillaModifier(stack, SWEEPING_DAMAGE_RATIO, "sweeping_damage_ratio", value);
    }

    public static void addSweepRatio(ItemStack stack, double delta) {
        setSweepRatio(stack, getSweepRatio(stack) + delta);
    }

    // ══════════════════════════════════════════════════════════════
    //  5. 耐久值
    // ══════════════════════════════════════════════════════════════

    public static int getMaxDurability(ItemStack stack) {
        return stack.getMaxDamage();
    }

    public static void setMaxDurability(ItemStack stack, int value) {
        stack.set(DataComponents.MAX_DAMAGE, Math.max(1, value));
        if (stack.getDamageValue() > value) {
            stack.setDamageValue(value);
        }
    }

    public static void addMaxDurability(ItemStack stack, int delta) {
        int current = stack.getMaxDamage();
        // 防护整数溢出：Math.addExact 在溢出时抛 ArithmeticException
        int newValue;
        try {
            newValue = Math.addExact(current, delta);
        } catch (ArithmeticException e) {
            newValue = delta > 0 ? Integer.MAX_VALUE : 1;
        }
        setMaxDurability(stack, Math.max(1, newValue));
    }

    // ══════════════════════════════════════════════════════════════
    //  实体级属性（直接挂载到 LivingEntity，不需要物品）
    // ══════════════════════════════════════════════════════════════

    /**
     * 通用：给实体挂载/更新原版属性修饰器。
     * @param value 修饰器数值；传 0 则移除
     */
    public static void setEntityAttribute(LivingEntity entity, Holder<Attribute> attribute,
                                          String idKey, double value, AttributeModifier.Operation op) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "entity_" + idKey);
        var inst = entity.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        if (value != 0.0 && op != null) {
            inst.addPermanentModifier(new AttributeModifier(id, value, op));
        }
    }

    /** 实体攻击力 [ADD_VALUE] */
    public static void setEntityAttackDamage(LivingEntity entity, double value) {
        setEntityAttribute(entity, Attributes.ATTACK_DAMAGE, "attack_damage",
                value, AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * 实体攻速 [ADD_MULTIPLIED_TOTAL]。
     * @param factor 攻速倍率，例如 0.01 表示 1% 攻速（-99% 减速）
     */
    public static void setEntityAttackSpeed(LivingEntity entity, double factor) {
        setEntityAttribute(entity, Attributes.ATTACK_SPEED, "attack_speed",
                factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** 实体生命上限 [ADD_VALUE] */
    public static void setEntityMaxHealth(LivingEntity entity, double bonus) {
        setEntityAttribute(entity, Attributes.MAX_HEALTH, "max_health",
                bonus, AttributeModifier.Operation.ADD_VALUE);
    }

    /** 清除实体上所有 yizmodqzk 修饰器（死亡/退出时调用） */
    public static void clearEntityAttributes(LivingEntity entity) {
        removeModifierById(entity, Attributes.ATTACK_DAMAGE, "attack_damage");
        removeModifierById(entity, Attributes.ATTACK_SPEED, "attack_speed");
        removeModifierById(entity, Attributes.MAX_HEALTH, "max_health");
    }

    private static void removeModifierById(LivingEntity entity, Holder<Attribute> attr, String idKey) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "entity_" + idKey);
        var inst = entity.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    // ══════════════════════════════════════════════════════════════
    //  Vanilla AttributeModifier helpers
    // ══════════════════════════════════════════════════════════════

    /** 公开：读取物品上指定属性的修饰符总值。 */
    public static double sumVanillaModifierPublic(ItemStack stack, Holder<Attribute> attribute) {
        return sumVanillaModifier(stack, attribute);
    }

    private static double sumVanillaModifier(ItemStack stack, Holder<Attribute> attribute) {
        if (attribute == null) return 0;
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        double total = 0;
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute() != null && entry.attribute().is(attribute)) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    private static void setVanillaModifier(ItemStack stack, Holder<Attribute> attribute,
                                           String idKey, double value) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("yizmodqzk", idKey);

        ItemAttributeModifiers oldMods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();

        for (var entry : oldMods.modifiers()) {
            if (!entry.attribute().is(attribute)) {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        }

        builder.add(attribute,
                new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.ANY);

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    // ═══════════════════════════════════════════════════════════
    //  自定义属性快捷方法
    // ═══════════════════════════════════════════════════════════

    /** 给 ItemStack 添加暴击率修饰符（值域 0~100）。 */
    public static void addCritRate(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.CRIT_RATE,
            "item_crit_rate", value);
    }

    /** 给 ItemStack 添加暴伤修饰符（增量百分比 0~N）。 */
    public static void addCritDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.CRIT_DAMAGE,
            "item_crit_damage", value);
    }

    /** 给 ItemStack 添加吸血修饰符（值域 0~100，百分比）。 */
    public static void addLifeSteal(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.LIFE_STEAL,
            "item_life_steal", value);
    }

    /** 给 ItemStack 添加伤害范围半径修饰符（值域 0~64，格）。 */
    public static void addSplashRadius(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SPLASH_RADIUS,
            "item_splash_radius", value);
    }

    /** 给 ItemStack 添加伤害范围百分比修饰符（值域 0~100）。 */
    public static void addSplashDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SPLASH_DAMAGE,
            "item_splash_damage", value);
    }

    /** 给 ItemStack 添加伤害范围衰减修饰符（值域 0~100）。 */
    public static void addSplashFalloff(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SPLASH_FALLOFF,
            "item_splash_falloff", value);
    }

    /** 给 ItemStack 添加会心属性（值域 ≥0，格）。 */
    public static void addHuixin(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.HUIXIN,
            "item_huixin", value);
    }

    /** 给 ItemStack 添加渴攻属性（值域 ≥0，tick）。 */
    public static void addKegong(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.KEGONG,
            "item_kegong", value);
    }

    /** 给 ItemStack 添加格挡属性（值域 ≥0，点）。 */
    public static void addDamageBlock(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.DAMAGE_BLOCK,
            "item_damage_block", value);
    }

    /** 给 ItemStack 添加减伤率属性（值域 0~100，1 = 1% 最终减伤）。 */
    public static void addDamageReduction(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.DAMAGE_REDUCTION,
            "item_damage_reduction", value);
    }

    /** 给 ItemStack 添加全伤害属性（值域 ≥0，1.0 = +100%）。 */
    public static void addGenericDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.GENERIC_DAMAGE,
            "item_generic_damage", value);
    }

    /** 给 ItemStack 添加近战伤害（10格内生效）。 */
    public static void addMeleeDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.MELEE_DAMAGE,
            "item_melee_damage", value);
    }

    /** 给 ItemStack 添加远程伤害（10格外生效）。 */
    public static void addRangedDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.RANGED_DAMAGE,
            "item_ranged_damage", value);
    }

    /** 给 ItemStack 添加护甲穿透百分比（值域 0~100）。 */
    public static void addArmorPenetration(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.ARMOR_PENETRATION,
            "item_armor_penetration", value);
    }

    /** 给 ItemStack 添加护甲穿透固定值（值域 ≥0）。 */
    public static void addArmorPenetrationFlat(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.ARMOR_PENETRATION_FLAT,
            "item_armor_penetration_flat", value);
    }

    // ── 环境防护 ──

    public static void addStepHeight(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.JUMP_SPEED, "item_step_height", value);
    }
    public static void addMaxMinions(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.MAX_MINIONS, "item_max_minions", value);
    }
    public static void addMaxSentries(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.MAX_SENTRIES, "item_max_sentries", value);
    }
    public static void addFlightTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.FLIGHT_TIME, "item_flight_time", value);
    }
    public static void addLavaImmuneTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.LAVA_IMMUNE_TIME, "item_lava_immune_time", value);
    }
    public static void addLavaImmuneTimeFlat(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.LAVA_IMMUNE_TIME_FLAT, "item_lava_immune_time_flat", value);
    }
    public static void addLavaDamageReduction(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.LAVA_DAMAGE_REDUCTION, "item_lava_damage_reduction", value);
    }
    public static void addLavaDamageReductionFlat(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.LAVA_DAMAGE_REDUCTION_FLAT, "item_lava_damage_reduction_flat", value);
    }
    public static void addWaterBreathTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.WATER_BREATH_TIME, "item_water_breath_time", value);
    }
    public static void addWaterBreathTimeFlat(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.WATER_BREATH_TIME_FLAT, "item_water_breath_time_flat", value);
    }

    /** 给 ItemStack 添加受击触发器（值域 ≥0）。 */
    public static void addOnHurt(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.ON_HURT, "item_on_hurt", value);
    }

    /** 给 ItemStack 添加反击率（值域 [0, 100]）。 */
    public static void addCounterRate(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.COUNTER_RATE, "item_counter_rate", value);
    }

    /** 给 ItemStack 添加反击值（值域 ≥0，1 = 1%）。 */
    public static void addCounterValue(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.COUNTER_VALUE, "item_counter_value", value);
    }

    // addCounterCount 已移除：counter_count 属性已删除，固定为每次 1 次。

    /** 给 ItemStack 添加复活次数（值域 ≥0）。 */
    public static void addUndying(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.UNDYING, "item_undying", value);
    }

    /** 给 ItemStack 添加投射物反弹半径（值域 ≥0，格）。 */
    public static void addProjectileReflection(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.PROJECTILE_REFLECTION, "item_projectile_reflection", value);
    }

    /** 给 ItemStack 添加穿过实体（值域 ≥0，1 = 穿过）。 */
    public static void addNoCollision(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.NO_COLLISION, "item_no_collision", value);
    }

    /** 给 ItemStack 添加击退免疫（值域 ≥0，1 = 免疫）。 */
    public static void addKnockbackImmunity(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.KNOCKBACK_IMMUNITY, "item_knockback_immunity", value);
    }

    /** 给 ItemStack 添加投射物免疫（值域 ≥0，1 = 免疫）。 */
    public static void addProjectileImmunity(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.PROJECTILE_IMMUNITY, "item_projectile_immunity", value);
    }

    // ═══════════════════════════════════════════════════════════
    //  状态效果属性 — 攻方
    // ═══════════════════════════════════════════════════════════

    public static void addStunAttack(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.STUN_ATTACK, "item_stun_attack", value);
    }
    public static void addSlowAttack(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SLOW_ATTACK, "item_slow_attack", value);
    }
    public static void addFreezeAttack(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.FREEZE_ATTACK, "item_freeze_attack", value);
    }
    public static void addShockAttack(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SHOCK_ATTACK, "item_shock_attack", value);
    }
    public static void addKnockbackAttack(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.KNOCKBACK_ATTACK, "item_knockback_attack", value);
    }

    // ═══════════════════════════════════════════════════════════
    //  状态效果属性 — 防方
    // ═══════════════════════════════════════════════════════════

    public static void addStunDefense(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.STUN_DEFENSE, "item_stun_defense", value);
    }
    public static void addSlowDefense(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SLOW_DEFENSE, "item_slow_defense", value);
    }
    public static void addFreezeDefense(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.FREEZE_DEFENSE, "item_freeze_defense", value);
    }
    public static void addShockDefense(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SHOCK_DEFENSE, "item_shock_defense", value);
    }
    public static void addKnockbackDefense(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.KNOCKBACK_DEFENSE, "item_knockback_defense", value);
    }

    // ═══════════════════════════════════════════════════════════
    //  状态效果共享属性 — 时间
    // ═══════════════════════════════════════════════════════════

    public static void addStunTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.STUN_TIME, "item_stun_time", value);
    }
    public static void addSlowTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SLOW_TIME, "item_slow_time", value);
    }
    public static void addFreezeTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.FREEZE_TIME, "item_freeze_time", value);
    }
    public static void addShockTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SHOCK_TIME, "item_shock_time", value);
    }
    public static void addShockRange(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SHOCK_RANGE, "item_shock_range", value);
    }
    public static void addKnockbackTime(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.KNOCKBACK_TIME, "item_knockback_time", value);
    }

    // ═══════════════════════════════════════════════════════════
    //  状态效果共享属性 — 伤害
    // ═══════════════════════════════════════════════════════════

    public static void addStunDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.STUN_DAMAGE, "item_stun_damage", value);
    }
    public static void addSlowDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SLOW_DAMAGE, "item_slow_damage", value);
    }
    public static void addFreezeDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.FREEZE_DAMAGE, "item_freeze_damage", value);
    }
    public static void addShockDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.SHOCK_DAMAGE, "item_shock_damage", value);
    }
    public static void addKnockbackDamage(ItemStack stack, double value) {
        setVanillaModifier(stack, YizAttributes.KNOCKBACK_DAMAGE, "item_knockback_damage", value);
    }
}
