package net.minecraft.client.yiz.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 玩家天赋面板 — 窗口化实现
 *
 * 像 Windows 窗口一样支持：
 * - 拖拽标题栏移动
 * - 拖拽四角调整大小
 * - 最小/最大尺寸限制
 * - 默认定位在背包界面左侧
 *
 * 鼠标输入通过 ScreenEvent.Render.Post 帧轮询 GLFW 实现，
 * 不依赖单独的 ScreenEvent 鼠标事件，保证兼容性。
 */
public class PlayerTalentUI {

    // ==================== 常量 ====================

    private static final int TITLE_BAR_HEIGHT = 14;
    private static final int HANDLE_SIZE = 8;       // 缩放手柄视觉长度
    private static final int HANDLE_THICK = 4;      // 缩放手柄线宽（加粗）
    private static final int CONTENT_PAD = 5;       // 内容区内边距
    private static final int MIN_WIDTH = 140;
    private static final int MIN_HEIGHT = 120;
    private static final int HANDLE_HIT = 7;        // 手柄点击判定半径（px）

    // ==================== 窗口几何 ====================

    private static int winX = -1;          // -1 = 未初始化
    private static int winY = -1;
    private static int winWidth = 200;
    private static int winHeight = 250;

    // ==================== 交互状态 ====================

    private static boolean dragging = false;
    private static int dragOffX, dragOffY;
    private static boolean resizing = false;
    private static ResizeCorner activeCorner;
    private static boolean wasPressed = false;   // 上一帧左键状态（边沿检测用）

    public enum ResizeCorner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private PlayerTalentUI() {}

    // ==================== 可见性 ====================

    public static boolean shouldShow(Minecraft mc) {
        if (!UIConfig.isPlayerTalentUIEnabled()) return false;
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return false;
        if (mc.player == null) return false;
        return true;
    }

    // ==================== 渲染 + 输入入口 ====================

    /**
     * 渲染面板。
     * 在 ScreenEvent.Render.Post 中每帧调用。
     */
    public static void renderTalentUI(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<AbstractEffect> talents = getPlayerTalents(mc.player);

        // 首次渲染时初始化默认位置（背包界面左侧）
        if (winX == -1 || winY == -1) {
            int guiLeft = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            winX = Math.max(2, guiLeft - winWidth - 5);
            winY = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
        }

        // 约束到屏幕内
        clampToScreen(mc);

        // 轮询鼠标输入（帧同步，不依赖 ScreenEvent 鼠标事件）
        pollMouse(mc, mouseX, mouseY);

        // 绘制窗口
        drawWindow(graphics, mc, talents, mouseX, mouseY);
    }

    /**
     * 每帧轮询鼠标状态，处理拖拽/缩放。
     */
    private static void pollMouse(Minecraft mc, int mouseX, int mouseY) {
        long window = mc.getWindow().getWindow();
        boolean pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

        if (pressed && !wasPressed) {
            // 按下边沿 → 尝试开始拖拽或缩放
            handleMousePress(mouseX, mouseY);
        } else if (pressed && wasPressed && (dragging || resizing)) {
            // 按住中 → 持续拖拽或缩放
            handleMouseDrag(mouseX, mouseY);
        } else if (!pressed && wasPressed) {
            // 松开 → 结束
            dragging = false;
            resizing = false;
            activeCorner = null;
        }

        wasPressed = pressed;
    }

    // ==================== 窗口绘制 ====================

    private static void drawWindow(GuiGraphics graphics, Minecraft mc,
                                   List<AbstractEffect> talents, int mouseX, int mouseY) {
        Font font = mc.font;
        int x = winX, y = winY, w = winWidth, h = winHeight;

        // ── 原版 Tooltip 背景（与物品信息 UI 风格一致）──
        TooltipRenderUtil.renderTooltipBackground(
            graphics, x + 3, y + 3, w - 6, h - 6, 0
        );

        // 内容区起点（Tooltip 内框以内）
        int cx = x + 3 + CONTENT_PAD;
        int cy = y + 3 + CONTENT_PAD;
        int cw = w - 6 - CONTENT_PAD * 2;

        // ── 标题栏 ──
        int titleBottom = cy + TITLE_BAR_HEIGHT;
        String title = talents.isEmpty() ? "§7已解锁天赋" : "§6§l已解锁天赋";
        // 标题文字
        graphics.drawString(font, title,
            cx, cy + (TITLE_BAR_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF);
        // 标题分隔线
        graphics.fill(cx, titleBottom, cx + cw, titleBottom + 1, 0x44FFFFFF);

        // ── 天赋列表 ──
        int lineY = titleBottom + CONTENT_PAD;
        int contentMaxY = y + h - 6 - CONTENT_PAD - HANDLE_SIZE;

        if (talents.isEmpty()) {
            // 无天赋时显示提示
            String hint = "§8当前没有已解锁的天赋";
            int hintX = cx + (cw - font.width(hint)) / 2;
            int hintY = titleBottom + CONTENT_PAD + 10;
            graphics.drawString(font, hint, hintX, hintY, 0xA0A0A0);
        } else for (AbstractEffect talent : talents) {
            // 计算整条天赋的完整高度（标准行 + 详情行 + 间距）
            List<String> details = talent.getTalentDetailLines(mc.player);
            int needed = font.lineHeight * 4                  // 4 行标准信息
                       + font.lineHeight * details.size()     // 详情行
                       + 1 + 2;                                // 标准行后间距 + 天赋间间距
            if (lineY + needed > contentMaxY) break;

            int color = EffectTooltipRenderer.getRarityColor(talent.getRarity());
            String hex = color == 0xFFFF5555 ? "§c" : color == 0xFFFFAA00 ? "§6"
                       : color == 0xFFAA00AA ? "§d" : color == 0xFF5555FF ? "§9" : "§f";

            graphics.drawString(font, hex + talent.getDisplayName() + " §fLv." + talent.getLevel(),
                cx, lineY, 0xFFFFFFFF);
            lineY += font.lineHeight;

            graphics.drawString(font, "  §8└─ " + talent.getParentType().getChineseName(),
                cx, lineY, 0xA0A0A0);
            lineY += font.lineHeight;

            graphics.drawString(font, "  §8└─ 生效：" + talent.getActivationCondition().getConditionName(),
                cx, lineY, 0xA0A0A0);
            lineY += font.lineHeight;

            graphics.drawString(font, "  §8└─ " + talent.getPerceptionTypeName(),
                cx, lineY, 0xA0A0A0);
            lineY += font.lineHeight + 1;

            // 渲染额外详情行
            for (String detailLine : details) {
                graphics.drawString(font, "  " + detailLine, cx, lineY, 0xFFFFFFFF);
                lineY += font.lineHeight;
            }
            lineY += 2;
        }

        // ── 四角缩放手柄（加粗 L 形，视觉上明确提示可拖拽）──
        drawResizeHandles(graphics);
    }

    /**
     * 绘制四角加粗 L 形缩放手柄。
     * 4px 粗线，亮白色，让玩家一眼看出这四个点可以拖拽。
     */
    private static void drawResizeHandles(GuiGraphics graphics) {
        int s = HANDLE_SIZE;
        int t = HANDLE_THICK;
        int x = winX, y = winY, w = winWidth, h = winHeight;
        int col = 0xCCFFFFFF;

        // 左上
        graphics.fill(x, y, x + s, y + t, col);
        graphics.fill(x, y, x + t, y + s, col);

        // 右上
        graphics.fill(x + w - s, y, x + w, y + t, col);
        graphics.fill(x + w - t, y, x + w, y + s, col);

        // 左下
        graphics.fill(x, y + h - t, x + s, y + h, col);
        graphics.fill(x, y + h - s, x + t, y + h, col);

        // 右下
        graphics.fill(x + w - s, y + h - t, x + w, y + h, col);
        graphics.fill(x + w - t, y + h - s, x + w, y + h, col);
    }

    // ==================== 鼠标事件 ====================

    private static boolean handleMousePress(int mx, int my) {
        if (!contains(mx, my)) return false;

        // 优先：四角缩放
        ResizeCorner corner = cornerAt(mx, my);
        if (corner != null) {
            resizing = true;
            activeCorner = corner;
            dragOffX = mx;
            dragOffY = my;
            return true;
        }

        // 次之：标题栏拖拽
        if (onTitleBar(mx, my)) {
            dragging = true;
            dragOffX = mx - winX;
            dragOffY = my - winY;
            return true;
        }

        return false;
    }

    private static void handleMouseDrag(int mx, int my) {
        if (dragging) {
            Minecraft mc = Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            winX = Math.clamp(mx - dragOffX, 0, sw - MIN_WIDTH);
            winY = Math.clamp(my - dragOffY, 0, sh - MIN_HEIGHT);
        } else if (resizing && activeCorner != null) {
            doResize(mx, my);
        }
    }

    // ==================== 几何辅助 ====================

    private static boolean contains(int mx, int my) {
        return mx >= winX && mx <= winX + winWidth
            && my >= winY && my <= winY + winHeight;
    }

    private static boolean onTitleBar(int mx, int my) {
        return mx >= winX && mx <= winX + winWidth
            && my >= winY && my <= winY + TITLE_BAR_HEIGHT;
    }

    private static ResizeCorner cornerAt(int mx, int my) {
        boolean left   = Math.abs(mx - winX) <= HANDLE_HIT;
        boolean right  = Math.abs(mx - (winX + winWidth)) <= HANDLE_HIT;
        boolean top    = Math.abs(my - winY) <= HANDLE_HIT;
        boolean bottom = Math.abs(my - (winY + winHeight)) <= HANDLE_HIT;

        if (left  && top)    return ResizeCorner.TOP_LEFT;
        if (right && top)    return ResizeCorner.TOP_RIGHT;
        if (left  && bottom) return ResizeCorner.BOTTOM_LEFT;
        if (right && bottom) return ResizeCorner.BOTTOM_RIGHT;
        return null;
    }

    private static void doResize(int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int margin = 8;

        switch (activeCorner) {
            case BOTTOM_RIGHT -> {
                winWidth  = Math.clamp(mx - winX, MIN_WIDTH, sw - margin);
                winHeight = Math.clamp(my - winY, MIN_HEIGHT, sh - margin);
            }
            case BOTTOM_LEFT -> {
                int right = winX + winWidth;
                winX      = Math.clamp(mx, margin, right - MIN_WIDTH);
                winWidth  = right - winX;
                winHeight = Math.clamp(my - winY, MIN_HEIGHT, sh - margin);
            }
            case TOP_RIGHT -> {
                int bottom = winY + winHeight;
                winWidth   = Math.clamp(mx - winX, MIN_WIDTH, sw - margin);
                winY       = Math.clamp(my, margin, bottom - MIN_HEIGHT);
                winHeight  = bottom - winY;
            }
            case TOP_LEFT -> {
                int right  = winX + winWidth;
                int bottom = winY + winHeight;
                winX       = Math.clamp(mx, margin, right - MIN_WIDTH);
                winY       = Math.clamp(my, margin, bottom - MIN_HEIGHT);
                winWidth   = right - winX;
                winHeight  = bottom - winY;
            }
        }
    }

    private static void clampToScreen(Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        winX = Math.clamp(winX, 0, Math.max(0, sw - MIN_WIDTH));
        winY = Math.clamp(winY, 0, Math.max(0, sh - MIN_HEIGHT));
        winWidth  = Math.clamp(winWidth,  MIN_WIDTH,  Math.max(MIN_WIDTH,  sw - 8));
        winHeight = Math.clamp(winHeight, MIN_HEIGHT, Math.max(MIN_HEIGHT, sh - 8));
    }

    // ==================== 数据 ====================

    /**
     * 获取实体所有已解锁的天赋。
     * 每帧都会重新构建列表，保证动态数据（如星光层数）实时反映在面板上。
     */
    public static List<AbstractEffect> getPlayerTalents(LivingEntity entity) {
        List<AbstractEffect> list = new ArrayList<>();
        for (AbstractEffect effect : ModRegistries.getEntityPerceptionEffects()) {
            if (UnlockManager.isUnlocked(entity, effect.getId())) {
                list.add(effect);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 重载：接受 LocalPlayer 参数。
     */
    public static List<AbstractEffect> getPlayerTalents(LocalPlayer player) {
        return getPlayerTalents((LivingEntity) player);
    }
}
