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
import java.util.List;

/**
 * 属性显示管理器
 * 管理物品属性的检测和格式化显示。
 */
public final class ItemAttributeDisplay {

    private static final Holder<Attribute> ENTITY_INTERACTION_RANGE =
            BuiltInRegistries.ATTRIBUTE.getHolder(
                    ResourceLocation.withDefaultNamespace("entity_interaction_range")).orElse(null);
    private static final Holder<Attribute> SWEEPING_DAMAGE_RATIO =
            BuiltInRegistries.ATTRIBUTE.getHolder(
                    ResourceLocation.fromNamespaceAndPath("neoforge", "sweeping_damage_ratio")).orElse(null);

    private ItemAttributeDisplay() {}

    /**
     * 检测物品是否具备特定属性。
     */
    public static boolean hasAttribute(ItemStack stack, Holder<Attribute> attribute) {
        if (attribute == null) return false;
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return false;

        return modifiers.modifiers().stream()
            .anyMatch(m -> m.attribute() != null && m.attribute().is(attribute));
    }

    /**
     * 获取物品的所有可用属性（原版 + 自定义）。
     */
    public static List<AttributeInfo> getAvailableAttributes(ItemStack stack) {
        List<AttributeInfo> attributes = new ArrayList<>();

        // 原版属性
        checkAttribute(stack, Attributes.ATTACK_DAMAGE, "攻击力", attributes);
        checkAttribute(stack, Attributes.ARMOR, "护甲值", attributes);
        checkAttribute(stack, Attributes.ATTACK_SPEED, "攻击速度", attributes);
        checkAttribute(stack, Attributes.ARMOR_TOUGHNESS, "护甲韧性", attributes);
        checkAttribute(stack, ENTITY_INTERACTION_RANGE, "交互距离", attributes);
        checkAttribute(stack, SWEEPING_DAMAGE_RATIO, "横扫范围", attributes);

        // 耐久值
        if (stack.isDamageableItem()) {
            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getDamageValue();
            String durability = (maxDamage - currentDamage) + "/" + maxDamage;
            attributes.add(new AttributeInfo("耐久值", durability, 0x888888));
        }

        // 自定义 mod 属性（不从原版 modifier 读取）
        addCustomStats(stack, attributes);

        return attributes;
    }

    /**
     * 追加 mod 专属自定义属性。
     */
    private static void addCustomStats(ItemStack stack, List<AttributeInfo> list) {
        double amp = net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.getDamageAmplification(stack);
        if (amp != 0) {
            String text = (amp > 0 ? "+" : "") + String.format("%.0f", amp * 100) + "%";
            list.add(new AttributeInfo("%伤害增幅", text, amp > 0 ? 0xFF55FF55 : 0xFFFF5555));
        }

        double red = net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.getDamageReduction(stack);
        if (red != 0) {
            String text = (red > 0 ? "+" : "") + String.format("%.0f", red * 100) + "%";
            list.add(new AttributeInfo("%伤害减免", text, red > 0 ? 0xFF55FF55 : 0xFFFF5555));
        }
    }

    private static void checkAttribute(ItemStack stack, Holder<Attribute> attr, String name, List<AttributeInfo> list) {
        if (hasAttribute(stack, attr)) {
            double value = getAttributeValue(stack, attr);
            list.add(new AttributeInfo(name, formatValue(attr, value), getValueColor(value)));
        }
    }

    /**
     * 获取属性值（总和）。
     */
    public static double getAttributeValue(ItemStack stack, Holder<Attribute> attribute) {
        if (attribute == null) return 0;
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0;

        double total = 0;
        for (var modifier : modifiers.modifiers()) {
            if (modifier.attribute() != null && modifier.attribute().is(attribute)) {
                total += modifier.modifier().amount();
            }
        }
        return total;
    }

    /**
     * 格式化属性显示文本。
     */
    public static String formatValue(Holder<Attribute> attribute, double value) {
        if (attribute == Attributes.ATTACK_SPEED) {
            return String.format("%.2f", value);
        }
        if (attribute == SWEEPING_DAMAGE_RATIO) {
            return String.format("%.0f", value * 100) + "%";
        }
        if (attribute == ENTITY_INTERACTION_RANGE) {
            return String.format("%.1f", value);
        }
        return String.format("%.1f", value);
    }

    /**
     * 获取属性值颜色（正数绿色，负数红色）。
     */
    private static int getValueColor(double value) {
        if (value > 0) return 0xFF55FF55;
        if (value < 0) return 0xFFFF5555;
        return 0xFFFFFFFF;
    }

    /**
     * 创建属性显示组件。
     */
    public static Component createAttributeComponent(String name, String value, int color) {
        MutableComponent component = Component.literal("  " + name + "：");
        component.append(Component.literal(value).withStyle(
            color == 0xFF55FF55 ? ChatFormatting.GREEN :
            color == 0xFFFF5555 ? ChatFormatting.RED :
            ChatFormatting.WHITE
        ));
        return component;
    }

    public record AttributeInfo(String name, String value, int color) {}
}
