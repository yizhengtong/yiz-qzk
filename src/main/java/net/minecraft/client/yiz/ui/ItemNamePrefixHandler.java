package net.minecraft.client.yiz.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 物品名称前缀处理器（效果框架已移除，前缀功能暂不可用）。
 */
public final class ItemNamePrefixHandler {

    private ItemNamePrefixHandler() {}

    /** 效果系统已移除，始终返回空字符串。 */
    public static String getNamePrefix(ItemStack stack) {
        return "";
    }

    /** 效果系统已移除，直接返回原始名称。 */
    public static Component getPrefixedName(ItemStack stack, Component originalName) {
        return originalName;
    }
}