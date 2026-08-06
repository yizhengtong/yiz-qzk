package net.minecraft.client.yiz.ui;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * UI 配置
 * 管理 UI 的快捷键和显示设置。
 */
public final class UIConfig {

    // 快捷键
    private static final KeyMapping TOGGLE_ABOLISH_PANEL_KEY = new KeyMapping(
        "key.yizmodqzk.toggle_abolish_panel",
        GLFW.GLFW_KEY_F7,
        "key.categories.yizmodqzk"
    );

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
     * 检测物品废除面板开关键（默认 B）按下。
     */
    public static boolean isAbolishPanelKey(int keyCode, int action) {
        return action == GLFW.GLFW_PRESS
            && keyCode == TOGGLE_ABOLISH_PANEL_KEY.getKey().getValue();
    }

    // ==================== 物品 UI（始终启用）====================

    public static boolean isCustomItemUIEnabled() {
        return true;
    }

    // ==================== 天赋 UI（已废弃）====================

    public static boolean isPlayerTalentUIEnabled() {
        return false;
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

    public static KeyMapping getToggleAbolishPanelKey() {
        return TOGGLE_ABOLISH_PANEL_KEY;
    }

}
