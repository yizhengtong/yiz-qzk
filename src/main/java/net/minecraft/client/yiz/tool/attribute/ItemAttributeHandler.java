package net.minecraft.client.yiz.tool.attribute;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.function.Consumer;

/**
 * 物品属性读写工具。
 *
 * <p>支持 7 种属性的 set/add/get。原版属性通过 {@link DataComponents#ATTRIBUTE_MODIFIERS} 读写，
 * 自定义属性通过 {@link DataComponents#CUSTOM_DATA} NBT 读写。</p>
 */
public final class ItemAttributeHandler {

    private static final String STATS_KEY = "yizmodqzk:item_stats";
    private static final String AMPLIFICATION_KEY = "damage_amplification";
    private static final String REDUCTION_KEY = "damage_reduction";
    private static final String SWEEP_DECAY_KEY = "sweep_decay";

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
    //  4b. 横扫衰减开关 (NBT via CustomData)
    // ══════════════════════════════════════════════════════════════

    public static boolean isSweepDecayEnabled(ItemStack stack) {
        CompoundTag tag = getCustomData(stack);
        CompoundTag stats = tag.getCompound(STATS_KEY);
        return !stats.contains(SWEEP_DECAY_KEY) || stats.getBoolean(SWEEP_DECAY_KEY);
    }

    public static void setSweepDecay(ItemStack stack, boolean enabled) {
        updateCustomData(stack, tag -> {
            CompoundTag stats = tag.getCompound(STATS_KEY);
            stats.putBoolean(SWEEP_DECAY_KEY, enabled);
            tag.put(STATS_KEY, stats);
        });
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
        setMaxDurability(stack, stack.getMaxDamage() + delta);
    }

    // ══════════════════════════════════════════════════════════════
    //  6. %伤害增幅 (NBT via CustomData)
    // ══════════════════════════════════════════════════════════════

    public static double getDamageAmplification(ItemStack stack) {
        CompoundTag tag = getCustomData(stack);
        CompoundTag stats = tag.getCompound(STATS_KEY);
        return stats.getDouble(AMPLIFICATION_KEY);
    }

    public static void setDamageAmplification(ItemStack stack, double percent) {
        updateCustomData(stack, tag -> {
            CompoundTag stats = tag.getCompound(STATS_KEY);
            stats.putDouble(AMPLIFICATION_KEY, percent);
            tag.put(STATS_KEY, stats);
        });
    }

    public static void addDamageAmplification(ItemStack stack, double delta) {
        setDamageAmplification(stack, getDamageAmplification(stack) + delta);
    }

    // ══════════════════════════════════════════════════════════════
    //  7. %伤害减免 (NBT via CustomData)
    // ══════════════════════════════════════════════════════════════

    public static double getDamageReduction(ItemStack stack) {
        CompoundTag tag = getCustomData(stack);
        CompoundTag stats = tag.getCompound(STATS_KEY);
        return stats.getDouble(REDUCTION_KEY);
    }

    public static void setDamageReduction(ItemStack stack, double percent) {
        updateCustomData(stack, tag -> {
            CompoundTag stats = tag.getCompound(STATS_KEY);
            stats.putDouble(REDUCTION_KEY, percent);
            tag.put(STATS_KEY, stats);
        });
    }

    public static void addDamageReduction(ItemStack stack, double delta) {
        setDamageReduction(stack, getDamageReduction(stack) + delta);
    }

    // ══════════════════════════════════════════════════════════════
    //  批量查询
    // ══════════════════════════════════════════════════════════════

    public static double getTotalDamageAmplification(net.minecraft.world.entity.LivingEntity entity) {
        double total = 0;
        total += getDamageAmplification(entity.getMainHandItem());
        total += getDamageAmplification(entity.getOffhandItem());
        return total;
    }

    public static double getTotalDamageReduction(net.minecraft.world.entity.LivingEntity entity) {
        double total = 0;
        total += getDamageReduction(entity.getMainHandItem());
        total += getDamageReduction(entity.getOffhandItem());
        return total;
    }

    // ══════════════════════════════════════════════════════════════
    //  CustomData helpers (replaces removed getOrCreateTag)
    // ══════════════════════════════════════════════════════════════

    private static CompoundTag getCustomData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void updateCustomData(ItemStack stack, Consumer<CompoundTag> mutator) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> {
            CompoundTag tag = data.copyTag();
            mutator.accept(tag);
            return CustomData.of(tag);
        });
    }

    // ══════════════════════════════════════════════════════════════
    //  Vanilla AttributeModifier helpers
    // ══════════════════════════════════════════════════════════════

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
}
