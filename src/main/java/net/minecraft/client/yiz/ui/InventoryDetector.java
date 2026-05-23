package net.minecraft.client.yiz.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

/**
 * 背包界面检测器
 * 检测当前打开的界面类型。
 */
public final class InventoryDetector {

    private InventoryDetector() {}

    /**
     * 检测当前界面是否为生存背包界面。
     */
    public static boolean isSurvivalInventory(Screen screen) {
        return screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
    }

    /**
     * 检测玩家是否在生存模式且打开了背包。
     */
    public static boolean shouldShowTalentUI(Screen screen, LocalPlayer player) {
        if (!isSurvivalInventory(screen)) return false;
        if (player == null) return false;
        // 创造模式不显示
        return !player.isCreative() && !player.isSpectator();
    }

    /**
     * 检测是否已打开任何容器界面（非背包）。
     */
    public static boolean isContainerOpen(Screen screen) {
        return screen != null
            && !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
            && screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
    }

    /**
     * 检测是否打开了创造模式物品栏。
     */
    public static boolean isCreativeInventory(Screen screen) {
        return screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
    }
}
