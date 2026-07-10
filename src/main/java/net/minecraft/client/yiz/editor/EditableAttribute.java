package net.minecraft.client.yiz.editor;

import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * "可设属性"数据源：属性编辑台列表遍历此集合显示。
 *
 * <p>每个属性记录：id、显示名、setter/getter/playerReader、单位后缀。
 * 显示时自动附上单位（% / 点 / 格 / tick / 次）。</p>
 *
 * <h3>单位语义约定</h3>
 * <ul>
 *   <li>{@code %} — 百分比，1=1%（如暴击率、吸血率、减伤率）</li>
 *   <li>{@code %%} — 百分比×100（如 generic_damage，1=+100%，显示时 ×100）</li>
 *   <li>{@code 点} — 固定点数（如格挡、耐久）</li>
 *   <li>{@code 格} — 方块距离（如溅射半径、会心范围）</li>
 *   <li>{@code tick} — 游戏刻（如渴攻充能时间）</li>
 *   <li>{@code 次} — 次数（如复活、反击连击数）</li>
 *   <li>{@code ""} — 无单位（原版属性/布尔型属性）</li>
 * </ul>
 */
public record EditableAttribute(
    String id,
    String displayName,
    boolean unimplemented,
    String unit,
    BiConsumer<ItemStack, Double> setter,
    Function<ItemStack, Double> getter,
    Function<Player, Double> playerReader
) {

    // ═══════════════════════════════════════════════════════════
    //  内置列表
    // ═══════════════════════════════════════════════════════════

    private static final List<EditableAttribute> BUILTIN = List.of(
        // ── 原版 ────────────────────────────────────────────
        vanilla("generic.attack_damage",    "攻击力",     false, ""),
        vanilla("generic.attack_speed",     "攻击速度",   false, ""),
        vanilla("generic.attack_knockback", "攻击击退",   false, ""),
        vanilla("generic.armor",            "护甲值",     false, ""),
        vanilla("generic.armor_toughness",  "盔甲韧性",   false, ""),
        vanilla("generic.max_health",       "最大生命",   false, ""),
        vanilla("generic.knockback_resistance", "击退抗性", false, ""),
        vanilla("generic.luck",             "幸运",       false, ""),
        vanilla("generic.movement_speed",   "移动速度",   false, ""),

        // ── 特殊路径 ──────────────────────────────────────
        attr("max_durability", "耐久值", false, "点",
            (s, v) -> ItemAttributeHandler.addMaxDurability(s, v.intValue()),
            s -> (double) ItemAttributeHandler.getMaxDurability(s),
            p -> 0.0),
        attr("interaction_range", "交互距离", false, "格",
            (s, v) -> ItemAttributeHandler.addInteractionRange(s, v),
            ItemAttributeHandler::getInteractionRange,
            p -> playerAttr(p, ResourceLocation.withDefaultNamespace("entity_interaction_range"))),
        attr("sweep_ratio", "横扫比例", false, "%",
            (s, v) -> ItemAttributeHandler.addSweepRatio(s, v),
            ItemAttributeHandler::getSweepRatio,
            p -> playerAttr(p, ResourceLocation.fromNamespaceAndPath("neoforge", "sweeping_damage_ratio"))),

        // ── 库 22 个 (百分比类 1=1%) ─────────────────────────
        yiz("crit_rate",            "暴击率",       false, "%"),
        yiz("crit_damage",          "暴击伤害",     false, "%"),
        yiz("life_steal",           "吸血",         false, "%"),
        yiz("splash_radius",        "溅射半径",     false, "格"),
        yiz("splash_damage",        "溅射伤害",     false, "%"),
        yiz("splash_falloff",       "溅射衰减",     false, "%"),
        yiz("huixin",               "会心",         false, "格"),
        yiz("kegong",               "渴攻",         false, "tick"),

        // 库 — 点数/次数类
        yiz("armor",                "防御力",       false, "点"),
        yiz("damage_block",         "+伤害格挡",    false, "点"),
        // generic_damage: 用户输入 10 → 存 0.1 → 实际 +10%（和其他 % 属性统一 1=1% 约定）
        new EditableAttribute("generic_damage", "+全伤害", false, "%",
            (s, v) -> setAttr(s, ResourceLocation.fromNamespaceAndPath("yizmodqzk", "generic_damage"), "generic_damage", v / 100.0),
            s -> sumAttr(s, ResourceLocation.fromNamespaceAndPath("yizmodqzk", "generic_damage")) * 100.0,
            p -> playerAttr(p, ResourceLocation.fromNamespaceAndPath("yizmodqzk", "generic_damage")) * 100.0),
        yiz("damage_reduction",     "伤害减免",     false, "%"),
        yiz("counter_rate",         "反击率",       false, "%"),
        yiz("counter_value",        "反击值",       false, "%"),
        yiz("counter_count",        "反击数",       false, "次"),
        yiz("undying",              "不死",         false, "次"),

        // ── 迁移自 EffectTag（28 个新原生属性）──────────────
        yiz("move_speed",           "移动速度",     false, ""),
        yiz("max_run_speed",        "最大奔跑速度", false, ""),
        yiz("jump_strength",        "跳跃力度",     false, ""),
        yiz("air_speed",            "空中移速",     false, ""),
        yiz("jump_count",           "跳跃次数",     false, "次"),
        yiz("jump_height",          "跳跃高度",     false, "格"),
        yiz("fall_safe",            "跌落保护",     false, "格"),
        yiz("fall_reduce",          "跌落减免",     false, "格"),
        yiz("dodge_chance",         "闪避几率",     false, "%"),
        yiz("invincibility_mult",   "无敌帧倍率",   false, "tick"),
        yiz("lava_immune_time",     "熔岩免疫时间", true,  "tick"),
        yiz("lava_damage_reduction","熔岩减伤",     true,  "%"),
        yiz("life_regen_rate",      "生命恢复(定点)",false,"点/tick"),
        yiz("life_regen_pct",       "生命恢复(%)",  false, "%"),
        yiz("melee_damage",         "近战伤害",     true,  ""),
        yiz("ranged_damage",        "远程伤害",     true,  ""),
        yiz("magic_damage",         "魔法伤害",     true,  ""),
        yiz("summon_damage",        "召唤伤害",     true,  ""),
        yiz("armor_penetration",        "护甲穿透%",  true,  "%"),
        yiz("armor_penetration_flat",   "护甲穿透固定", true, "点"),
        yiz("attack_range",         "攻击距离",     true,  "格"),
        yiz("jump_speed",           "步高",         true,  "格"),
        yiz("max_minions",          "最大仆从数",   true,  "次"),
        yiz("max_sentries",         "最大哨兵数",   true,  "次"),
        yiz("water_breath_time",    "水下呼吸时间", true,  "秒"),

        // 库 — 触发器（次数） / 布尔型
        yiz("on_hurt",              "受伤触发",     true,  "次"),
        yiz("projectile_reflection","投射物反弹",   false, "格"),
        yiz("no_collision",         "无碰撞",       false, ""),
        yiz("knockback_immunity",   "击退免疫",     false, ""),
        yiz("projectile_immunity",  "投射物免疫",   false, "")
    );

    // ═══════════════════════════════════════════════════════════
    //  扩展注册
    // ═══════════════════════════════════════════════════════════

    private static final List<EditableAttribute> EXTRA = new ArrayList<>();

    public static void registerExtra(EditableAttribute attr) { EXTRA.add(attr); }

    public static List<EditableAttribute> getAll() {
        if (EXTRA.isEmpty()) return BUILTIN;
        List<EditableAttribute> merged = new ArrayList<>(BUILTIN.size() + EXTRA.size());
        merged.addAll(BUILTIN);
        merged.addAll(EXTRA);
        return Collections.unmodifiableList(merged);
    }

    // ═══════════════════════════════════════════════════════════
    //  工厂方法
    // ═══════════════════════════════════════════════════════════

    private static EditableAttribute vanilla(String id, String name, boolean u, String unit) {
        ResourceLocation loc = ResourceLocation.withDefaultNamespace(id);
        return new EditableAttribute(id, name, u, unit,
            (s, v) -> setAttr(s, loc, id, v), s -> sumAttr(s, loc), p -> playerAttr(p, loc));
    }

    private static EditableAttribute yiz(String attrId, String name, boolean u, String unit) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("yizmodqzk", attrId);
        return new EditableAttribute(attrId, name, u, unit,
            (s, v) -> setAttr(s, loc, attrId, v), s -> sumAttr(s, loc), p -> playerAttr(p, loc));
    }

    private static EditableAttribute attr(String id, String name, boolean u, String unit,
            BiConsumer<ItemStack, Double> setter, Function<ItemStack, Double> getter,
            Function<Player, Double> playerReader) {
        return new EditableAttribute(id, name, u, unit, setter, getter, playerReader);
    }

    // ═══════════════════════════════════════════════════════════
    //  读写
    // ═══════════════════════════════════════════════════════════

    private static void setAttr(ItemStack stack, ResourceLocation attrLoc, String idKey, double value) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(attrLoc).orElse(null);
        if (holder == null) return;
        ResourceLocation modId = ResourceLocation.fromNamespaceAndPath("yizmodqzk", "attr_" + idKey.replace('.', '_'));
        ItemAttributeModifiers old = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (var entry : old.modifiers()) {
            if (!entry.attribute().is(holder)) builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        builder.add(holder, new AttributeModifier(modId, value, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    private static double sumAttr(ItemStack stack, ResourceLocation loc) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(loc).orElse(null);
        if (holder == null) return 0;
        return ItemAttributeHandler.sumVanillaModifierPublic(stack, holder);
    }

    private static double playerAttr(Player player, ResourceLocation loc) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(loc).orElse(null);
        if (holder == null) return 0;
        var inst = player.getAttribute(holder);
        return inst != null ? inst.getValue() : 0;
    }

    // ═══════════════════════════════════════════════════════════
    //  显示
    // ═══════════════════════════════════════════════════════════

    public String listLabel(double currentValue) {
        String suffix = unimplemented ? "（待实现）" : "";
        if (Math.abs(currentValue) < 0.0001) return displayName + suffix;
        return displayName + " " + formatWithUnit(currentValue) + suffix;
    }

    public String hudLabel(double playerValue) {
        if (Math.abs(playerValue) < 0.0001) return null;
        return displayName + " " + formatWithUnit(playerValue);
    }

    private String formatWithUnit(double v) {
        return switch (unit) {
            case "%"  -> formatValue(v) + "%";
            case "点" -> formatValue(v) + "点";
            case "格" -> formatValue(v) + "格";
            case "tick" -> formatValue(v) + " tick";
            case "次" -> formatValue(v) + "次";
            default -> formatValue(v);
        };
    }

    private static String formatValue(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }
}
