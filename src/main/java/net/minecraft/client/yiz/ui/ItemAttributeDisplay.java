package net.minecraft.client.yiz.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 属性显示管理器 — 管理物品属性的检测和格式化显示。
 *
 * <p>每个属性的显示规则（名称、单位、缩放）由此类统一管理，
 * 保证 tooltip / ItemInfoUI / 属性编辑台 三处的格式一致。</p>
 */
public final class ItemAttributeDisplay {

    private ItemAttributeDisplay() {}

    // ═══════════════════════════════════════════════════════════
    //  属性显示元数据
    // ═══════════════════════════════════════════════════════════

    /** 属性显示规则：名称、单位、显示缩放 */
    private record DisplayRule(String name, String unit, double displayScale) {}

    /** 已知属性的显示规则（按 Holder 查找）。 */
    private static final Map<Holder<Attribute>, DisplayRule> RULES = new LinkedHashMap<>();

    /** 原版属性 Holder 缓存 */
    private static final Holder<Attribute> ENTITY_INTERACTION_RANGE =
        BuiltInRegistries.ATTRIBUTE.getHolder(
            ResourceLocation.withDefaultNamespace("entity_interaction_range")).orElse(null);
    private static final Holder<Attribute> SWEEPING_DAMAGE_RATIO =
        BuiltInRegistries.ATTRIBUTE.getHolder(
            ResourceLocation.fromNamespaceAndPath("neoforge", "sweeping_damage_ratio")).orElse(null);

    static {
        // ── 原版 ────────────────────────────────────────────
        rule(Attributes.ATTACK_DAMAGE,             "攻击力",     "",  1.0);
        rule(Attributes.ATTACK_SPEED,              "攻击速度",   "",  1.0);
        rule(Attributes.ATTACK_KNOCKBACK,          "攻击击退",   "",  1.0);
        rule(Attributes.ARMOR,                     "护甲值",     "",  1.0);
        rule(Attributes.ARMOR_TOUGHNESS,           "盔甲韧性",   "",  1.0);
        rule(Attributes.MAX_HEALTH,                "最大生命",   "",  1.0);
        rule(Attributes.KNOCKBACK_RESISTANCE,      "击退抗性",   "",  1.0);
        rule(Attributes.LUCK,                      "幸运",       "",  1.0);
        rule(Attributes.MOVEMENT_SPEED,            "移动速度",   "",  1.0);
        rule(ENTITY_INTERACTION_RANGE,             "交互距离",   "格", 1.0);
        rule(SWEEPING_DAMAGE_RATIO,                "横扫比例",   "%",  100.0);

        // ── 库 yizmodqzk 属性 ──────────────────────────────
        yiz("crit_rate",            "暴击率",       "%",   1.0);
        yiz("crit_damage",          "暴击伤害",     "%",   1.0);
        yiz("life_steal",           "吸血",         "%",   1.0);
        yiz("splash_radius",        "溅射半径",     "格",   1.0);
        yiz("splash_damage",        "溅射伤害",     "%",   1.0);
        yiz("splash_falloff",       "溅射衰减",     "%",   1.0);
        yiz("huixin",               "会心",         "格",   1.0);
        yiz("kegong",               "渴攻",         "tick", 1.0);
        yiz("armor",                "防御力",       "点",   1.0);
        yiz("damage_block",         "伤害格挡",     "点",   1.0);
        yiz("generic_damage",       "全伤害",       "%",   100.0);
        yiz("damage_reduction",     "伤害减免",     "%",   1.0);
        yiz("counter_rate",         "反击率",       "%",   1.0);
        yiz("counter_value",        "反击值",       "%",   1.0);
        yiz("counter_count",        "反击数",       "次",   1.0);
        yiz("undying",              "不死",         "次",   1.0);
        yiz("projectile_reflection","投射物反弹",   "格",   1.0);
        yiz("no_collision",         "无碰撞",       "",    1.0);
        yiz("knockback_immunity",   "击退免疫",     "",    1.0);
        yiz("projectile_immunity",  "投射物免疫",   "",    1.0);
        yiz("on_hurt",              "受伤触发",     "次",   1.0);
        // 迁自 EffectTag
        yiz("move_speed",           "移动速度",     "",    1.0);
        yiz("max_run_speed",        "最大奔跑速度", "",    1.0);
        yiz("jump_strength",        "跳跃力度",     "",    1.0);
        yiz("air_speed",            "空中移速",     "",    1.0);
        yiz("jump_count",           "跳跃次数",     "次",  1.0);
        yiz("jump_height",          "跳跃高度",     "格",  1.0);
        yiz("fall_safe",            "跌落保护",     "格",  1.0);
        yiz("fall_reduce",          "跌落减免",     "格",  1.0);
        yiz("dodge_chance",         "闪避几率",     "%",   1.0);
        yiz("invincibility_mult",   "无敌帧倍率",   "tick",1.0);
        yiz("lava_immune_time",     "熔岩免疫时间(%)", "%",  1.0);
        yiz("lava_immune_time_flat","熔岩免疫时间(固定)","tick",1.0);
        yiz("lava_damage_reduction","熔岩减伤",     "%",   1.0);
        yiz("lava_damage_reduction_flat","熔岩减伤(固定)","点", 1.0);
        yiz("life_regen_rate",      "生命恢复(定点)","点/tick",1.0);
        yiz("life_regen_pct",       "生命恢复(%)",  "%",   1.0);
        yiz("melee_damage",         "近战伤害",     "",    1.0);
        yiz("ranged_damage",        "远程伤害",     "",    1.0);
        yiz("magic_damage",         "魔法伤害",     "",    1.0);
        yiz("summon_damage",        "召唤伤害",     "",    1.0);
        yiz("armor_penetration",        "护甲穿透(%)",  "%",  1.0);
        yiz("armor_penetration_flat",   "护甲穿透(固定)","点", 1.0);
        yiz("attack_range",         "攻击距离",     "格",  1.0);
        yiz("flight_time",          "飞行时间",     "tick",1.0);
        yiz("jump_speed",           "步高",         "格",  1.0);
        yiz("max_minions",          "最大仆从数",   "个",  1.0);
        yiz("max_sentries",         "最大哨兵数",   "个",  1.0);
        yiz("water_breath_time",        "水下呼吸时间(%)","%",   1.0);
        yiz("water_breath_time_flat",   "水下呼吸时间(固定)","tick",1.0);
        yiz("arrow_damage",         "箭矢伤害",     "",    1.0);
        yiz("arrow_speed",          "箭矢速度",     "",    1.0);
        yiz("arrow_save_chance",    "箭矢节省几率", "%",   1.0);
    }

    private static void rule(Holder<Attribute> attr, String name, String unit, double scale) {
        if (attr != null) RULES.put(attr, new DisplayRule(name, unit, scale));
    }

    private static void yiz(String id, String name, String unit, double scale) {
        var h = BuiltInRegistries.ATTRIBUTE.getHolder(
            ResourceLocation.fromNamespaceAndPath("yizmodqzk", id)).orElse(null);
        rule(h, name, unit, scale);
    }

    // ═══════════════════════════════════════════════════════════
    //  检测
    // ═══════════════════════════════════════════════════════════

    /** 扫描物品上所有已知属性。 */
    public static List<AttributeInfo> getAvailableAttributes(ItemStack stack) {
        List<AttributeInfo> attributes = new ArrayList<>();
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return attributes;

        for (var mod : modifiers.modifiers()) {
            if (mod.attribute() == null) continue;
            DisplayRule rule = RULES.get(mod.attribute());
            if (rule == null) continue;

            double total = sumModifierValue(modifiers, mod.attribute());
            if (Math.abs(total) < 0.0001) continue;

            String value = formatWithUnit(total, rule);
            int color = total > 0 ? 0xFF55FF55 : total < 0 ? 0xFFFF5555 : 0xFFFFFFFF;
            attributes.add(new AttributeInfo(rule.name, value, color));
        }

        // 耐久值
        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getDamageValue();
            String durability = (maxDamage - currentDamage) + "/" + maxDamage;
            attributes.add(new AttributeInfo("耐久值", durability, 0x888888));
        }

        return attributes;
    }

    /** 累加某个 attribute 上所有 modifier 的值。 */
    private static double sumModifierValue(ItemAttributeModifiers modifiers, Holder<Attribute> attr) {
        double total = 0;
        for (var mod : modifiers.modifiers()) {
            if (mod.attribute() != null && mod.attribute().is(attr)) {
                total += mod.modifier().amount();
            }
        }
        return total;
    }

    // ═══════════════════════════════════════════════════════════
    //  格式化
    // ═══════════════════════════════════════════════════════════

    /** 按显示规则格式化属性值。 */
    private static String formatWithUnit(double raw, DisplayRule rule) {
        double display = raw * rule.displayScale;
        String num = formatNum(display);
        return switch (rule.unit) {
            case "%"   -> num + "%";
            case "点"  -> num + "点";
            case "格"  -> num + "格";
            case "tick" -> num + " tick";
            case "次"  -> num + "次";
            default    -> num;
        };
    }

    private static String formatNum(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        // 攻速保留两位小数
        return String.format("%.1f", v);
    }

    // ═══════════════════════════════════════════════════════════
    //  Component 构建
    // ═══════════════════════════════════════════════════════════

    public static Component createAttributeComponent(String name, String value, int color) {
        ChatFormatting fmt = color == 0xFF55FF55 ? ChatFormatting.GREEN
            : color == 0xFFFF5555 ? ChatFormatting.RED : ChatFormatting.WHITE;
        MutableComponent component = Component.literal("  " + name + "：");
        component.append(Component.literal(value).withStyle(fmt));
        return component;
    }

    public record AttributeInfo(String name, String value, int color) {}
}
