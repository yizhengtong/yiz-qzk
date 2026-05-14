package net.minecraft.client.yiz.ui;

import net.minecraft.client.KeyMapping;
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

    // ── Demo 容器快捷键（单键无修饰符） ──
    private static final KeyMapping CHEST_75_KEY = new KeyMapping(
        "key.yizmodqzk.chest_75", GLFW.GLFW_KEY_C, "key.categories.yizmodqzk");
    private static final KeyMapping CHEST_115_KEY = new KeyMapping(
        "key.yizmodqzk.chest_115", GLFW.GLFW_KEY_V, "key.categories.yizmodqzk");
    private static final KeyMapping CHEST_130_KEY = new KeyMapping(
        "key.yizmodqzk.chest_130", GLFW.GLFW_KEY_B, "key.categories.yizmodqzk");
    private static final KeyMapping CHEST_200_KEY = new KeyMapping(
        "key.yizmodqzk.chest_200", GLFW.GLFW_KEY_N, "key.categories.yizmodqzk");

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
     * 检测 ALT 键按下（用于 Ctrl+Alt 组合）。
     */
    public static boolean isItemUIKey(int keyCode, int action) {
        return action == GLFW.GLFW_PRESS
            && keyCode == TOGGLE_ITEM_UI_KEY.getKey().getValue();
    }

    /**
     * 检测 SHIFT 键按下（用于 Ctrl+Shift 组合）。
     */
    public static boolean isTalentUIKey(int keyCode, int action) {
        return action == GLFW.GLFW_PRESS
            && keyCode == TOGGLE_TALENT_UI_KEY.getKey().getValue();
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

    // ==================== 快捷键获取 ====================

    public static KeyMapping getToggleItemUIKey() {
        return TOGGLE_ITEM_UI_KEY;
    }

    public static KeyMapping getToggleTalentUIKey() {
        return TOGGLE_TALENT_UI_KEY;
    }

    // ==================== 容器快捷键获取 ====================

    public static KeyMapping getChest75Key() { return CHEST_75_KEY; }
    public static KeyMapping getChest115Key() { return CHEST_115_KEY; }
    public static KeyMapping getChest130Key() { return CHEST_130_KEY; }
    public static KeyMapping getChest200Key() { return CHEST_200_KEY; }

}
