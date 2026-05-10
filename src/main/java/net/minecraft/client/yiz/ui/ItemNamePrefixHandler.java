package net.minecraft.client.yiz.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 物品名称前缀处理器
 * 当物品带有词缀或随影效果时，在物品名称最前方添加类型标识。
 */
public final class ItemNamePrefixHandler {

    private ItemNamePrefixHandler() {}

    /**
     * 获取物品名称前缀。
     *
     * @param stack 物品
     * @return 前缀字符串，无效果时返回空字符串
     */
    public static String getNamePrefix(ItemStack stack) {
        var effects = net.minecraft.client.yiz.core.data.EffectNBTHandler.getItemEffects(stack);
        if (effects.isEmpty()) return "";

        boolean hasAffix = false;
        boolean hasShadow = false;

        for (var effect : effects) {
            for (var mode : effect.getPerceptionModes()) {
                if (mode instanceof net.minecraft.client.yiz.effect.perception.ItemPerception) {
                    hasAffix = true;
                } else if (mode instanceof net.minecraft.client.yiz.effect.perception.ContainerPerception) {
                    hasShadow = true;
                }
            }
        }

        if (hasAffix && hasShadow) {
            return "[词缀/随影] ";
        } else if (hasAffix) {
            return "[词缀] ";
        } else if (hasShadow) {
            return "[随影] ";
        }

        return "";
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
