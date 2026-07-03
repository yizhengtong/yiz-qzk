package net.minecraft.client.yiz.tool.attribute;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import net.minecraft.client.yiz.attribute.YizAttributes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 物品 + 实体 属性读写工具。
 *
 * <p>支持 7 种属性的 set/add/get。原版属性通过 {@link DataComponents#ATTRIBUTE_MODIFIERS} 读写，
 * 自定义属性通过 {@link DataComponents#CUSTOM_DATA} NBT 读写。</p>
 *
 * <p>实体级方法直接操作 {@link LivingEntity#getAttribute(Holder)}，
 * 自定义属性（伤害增幅/减免）通过内存 Map 存储，{@link #getTotalDamageAmplification(LivingEntity)}
 * 和 {@link #getTotalDamageReduction(LivingEntity)} 自动合并物品 + 实体两边的值。</p>
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
    //  批量查询（物品 + 实体合并）
    // ══════════════════════════════════════════════════════════════

    /** 合并物品 + 实体级的伤害增幅总值 */
    public static double getTotalDamageAmplification(LivingEntity entity) {
        double total = 0;
        total += getDamageAmplification(entity.getMainHandItem());
        total += getDamageAmplification(entity.getOffhandItem());
        total += entityAmplification.getOrDefault(entity.getUUID(), 0.0);
        return total;
    }

    /** 合并物品 + 实体级的伤害减免总值 */
    public static double getTotalDamageReduction(LivingEntity entity) {
        // 背包防御已废除 → 所有物品减免归零
        if (DEFENSE_ABOLISHED_PLAYERS.contains(entity.getUUID())) {
            return 0.0;
        }
        double total = 0;
        total += getDamageReduction(entity.getMainHandItem());
        total += getDamageReduction(entity.getOffhandItem());
        total += entityReduction.getOrDefault(entity.getUUID(), 0.0);
        return total;
    }

    // ══════════════════════════════════════════════════════════════
    //  实体级属性（直接挂载到 LivingEntity，不需要物品）
    // ══════════════════════════════════════════════════════════════

    /** 实体伤害增幅内存存储（非物品、非 NBT，纯服务端计算用） */
    private static final Map<UUID, Double> entityAmplification = new ConcurrentHashMap<>();
    /** 实体伤害减免内存存储 */
    private static final Map<UUID, Double> entityReduction = new ConcurrentHashMap<>();

    // ══════════════════════════════════════════════════════════════
    //  背包废除系统
    // ══════════════════════════════════════════════════════════════

    /** 标记为"背包防御已废除"的玩家 UUID 集合 */
    private static final java.util.Set<UUID> DEFENSE_ABOLISHED_PLAYERS = ConcurrentHashMap.newKeySet();

    /**
     * 设置/取消玩家的背包防御废除状态。
     * <p>
     * 废除后该玩家身上所有物品的 % 伤害减免（damage_reduction）
     * 在 {@link #getTotalDamageReduction} 中会被忽略。
     * </p>
     *
     * @param playerUuid 玩家 UUID
     * @param abolished  true = 废除防御，false = 恢复
     */
    public static void setDefenseAbolished(UUID playerUuid, boolean abolished) {
        if (abolished) {
            DEFENSE_ABOLISHED_PLAYERS.add(playerUuid);
        } else {
            DEFENSE_ABOLISHED_PLAYERS.remove(playerUuid);
        }
    }

    /**
     * 查询玩家的背包防御是否已被废除。
     */
    public static boolean isDefenseAbolished(UUID playerUuid) {
        return DEFENSE_ABOLISHED_PLAYERS.contains(playerUuid);
    }

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

    /** 实体伤害增幅（存入内存，{@link #getTotalDamageAmplification} 自动合并） */
    public static void setEntityDamageAmplification(LivingEntity entity, double value) {
        if (value == 0) entityAmplification.remove(entity.getUUID());
        else entityAmplification.put(entity.getUUID(), value);
    }

    public static double getEntityDamageAmplification(LivingEntity entity) {
        return entityAmplification.getOrDefault(entity.getUUID(), 0.0);
    }

    /** 实体伤害减免（存入内存，{@link #getTotalDamageReduction} 自动合并） */
    public static void setEntityDamageReduction(LivingEntity entity, double value) {
        if (value == 0) entityReduction.remove(entity.getUUID());
        else entityReduction.put(entity.getUUID(), value);
    }

    public static double getEntityDamageReduction(LivingEntity entity) {
        return entityReduction.getOrDefault(entity.getUUID(), 0.0);
    }

    /** 清除实体上所有 yizmodqzk 修饰器（死亡/退出时调用） */
    public static void clearEntityAttributes(LivingEntity entity) {
        entityAmplification.remove(entity.getUUID());
        entityReduction.remove(entity.getUUID());
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
}
