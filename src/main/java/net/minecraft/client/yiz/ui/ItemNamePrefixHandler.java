package net.minecraft.client.yiz.ui;

import net.minecraft.client.yiz.effect.perception.PerceptionMode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 物品名称前缀处理器。
 *
 * <p>从物品所有效果的感知方式中收集 {@link PerceptionMode#getTypeName()}，
 * 拼为 {@code [类型A/类型B]} 前缀。内置感知返回的默认名称为"词缀 (Affix)"、
 * "随影 (Shadow)"等，下游模组通过 {@code CustomPerception} 可提供自定义名称。</p>
 */
public final class ItemNamePrefixHandler {

    private ItemNamePrefixHandler() {}

    /**
     * 获取物品名称前缀，由效果感知方式动态决定。
     */
    public static String getNamePrefix(ItemStack stack) {
        var effects = net.minecraft.client.yiz.core.data.EffectNBTHandler.getItemEffects(stack);
        if (effects.isEmpty()) return "";

        Set<String> names = new LinkedHashSet<>();
        for (var effect : effects) {
            for (var mode : effect.getPerceptionModes()) {
                String name = mode.getTypeName();
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }

        if (names.isEmpty()) return "";
        return "[" + String.join("/", names) + "] ";
    }

    /**
     * 获取带前缀的物品名称组件。
     */
    public static Component getPrefixedName(ItemStack stack, Component originalName) {
        String prefix = getNamePrefix(stack);
        if (prefix.isEmpty()) return originalName;

        return Component.literal(prefix)
            .withStyle(net.minecraft.ChatFormatting.YELLOW)
            .append(originalName);
    }
}
