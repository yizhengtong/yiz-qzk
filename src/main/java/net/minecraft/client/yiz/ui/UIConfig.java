package net.minecraft.client.yiz.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * UI 配置
 * 管理 UI 的快捷键和显示设置。
 */
public final class UIConfig {

    // 快捷键
    private static final KeyMapping TOGGLE_ITEM_UI_KEY = new KeyMapping(
        "key.yizmodqzk.toggle_item_ui",
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.yizmodqzk"
    );

    private static final KeyMapping TOGGLE_TALENT_UI_KEY = new KeyMapping(
        "key.yizmodqzk.toggle_talent_ui",
        GLFW.GLFW_KEY_LEFT_SHIFT,
        "key.categories.yizmodqzk"
    );

    // UI 开关状态
    private static boolean customItemUIEnabled = false;
    private static boolean playerTalentUIEnabled = false;

    // 显示设置
    private static int talentPanelWidth = 200;
    private static int talentPanelHeight = 166;
    private static int talentCardHeight = 60;
    private static int uiBackgroundColor = 0xCC000000;
    private static int uiBorderColor = 0xFF888888;
    private static int lineHeight = 12;

    private UIConfig() {}

    // ==================== 快捷检测 ====================

    /**
     * 检测是否应该切换物品 UI（CTRL + ALT）。
     */
    public static boolean checkItemUIToggle() {
        return TOGGLE_ITEM_UI_KEY.isDown()
            && Screen.hasControlDown();
    }

    /**
     * 检测是否应该切换天赋 UI（CTRL + SHIFT）。
     */
    public static boolean checkTalentUIToggle() {
        return TOGGLE_TALENT_UI_KEY.isDown()
            && Screen.hasControlDown();
    }

    // ==================== 物品 UI ====================

    public static boolean isCustomItemUIEnabled() {
        return customItemUIEnabled;
    }

    public static void toggleItemUI() {
        customItemUIEnabled = !customItemUIEnabled;
    }

    public static void setItemUIEnabled(boolean enabled) {
        customItemUIEnabled = enabled;
    }

    // ==================== 天赋 UI ====================

    public static boolean isPlayerTalentUIEnabled() {
        return playerTalentUIEnabled;
    }

    public static void toggleTalentUI() {
        playerTalentUIEnabled = !playerTalentUIEnabled;
    }

    public static void setTalentUIEnabled(boolean enabled) {
        playerTalentUIEnabled = enabled;
    }

    // ==================== 面板尺寸 ====================

    public static int getTalentPanelWidth() {
        return talentPanelWidth;
    }

    public static int getTalentPanelHeight() {
        return talentPanelHeight;
    }

    public static int getTalentCardHeight() {
        return talentCardHeight;
    }

    // ==================== 颜色 ====================

    public static int getUiBackgroundColor() {
        return uiBackgroundColor;
    }

    public static int getUiBorderColor() {
        return uiBorderColor;
    }

    public static int getLineHeight() {
        return lineHeight;
    }

    // ==================== 快捷键注册 ====================

    public static KeyMapping getToggleItemUIKey() {
        return TOGGLE_ITEM_UI_KEY;
    }

    public static KeyMapping getToggleTalentUIKey() {
        return TOGGLE_TALENT_UI_KEY;
    }

    // 引用 Screen 以避免编译依赖
    private static final class Screen {
        static boolean hasControlDown() {
            return net.minecraft.client.gui.screens.Screen.hasControlDown();
        }
    }
}
