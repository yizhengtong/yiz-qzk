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

    private static final KeyMapping TOGGLE_PANEL_FIX_KEY = new KeyMapping(
        "key.yizmodqzk.toggle_panel_fix",
        GLFW.GLFW_KEY_C,
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

    // 摄像机跟随面板（HandheldPanelRenderer）
    private static float handheldPanelDistance = 1.5f;
    private static float handheldPanelWidth = 1.6f;
    private static float handheldPanelHeight = 0.9f;

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

    /**
     * 检测 C 键按下（用于 Ctrl+C 切换面板固定/跟随）。
     */
    public static boolean isPanelFixKey(int keyCode, int action) {
        return action == GLFW.GLFW_PRESS
            && keyCode == TOGGLE_PANEL_FIX_KEY.getKey().getValue();
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

    // ==================== 摄像机跟随面板 ====================

    /** 面板与相机的距离（方块单位） */
    public static float getHandheldPanelDistance() {
        return handheldPanelDistance;
    }

    public static void setHandheldPanelDistance(float distance) {
        handheldPanelDistance = distance;
    }

    /** 面板宽度（方块单位） */
    public static float getHandheldPanelWidth() {
        return handheldPanelWidth;
    }

    public static void setHandheldPanelWidth(float width) {
        handheldPanelWidth = width;
    }

    /** 面板高度（方块单位） */
    public static float getHandheldPanelHeight() {
        return handheldPanelHeight;
    }

    public static void setHandheldPanelHeight(float height) {
        handheldPanelHeight = height;
    }

    // ==================== 快捷键获取 ====================

    public static KeyMapping getToggleItemUIKey() {
        return TOGGLE_ITEM_UI_KEY;
    }

    public static KeyMapping getToggleTalentUIKey() {
        return TOGGLE_TALENT_UI_KEY;
    }

    public static KeyMapping getTogglePanelFixKey() {
        return TOGGLE_PANEL_FIX_KEY;
    }

}
