package net.minecraft.client.yiz.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 面板纹理拼装工具。
 *
 * <p>封装 YizMod QZK GUI 积木块的绘制逻辑：</p>
 * <ol>
 *   <li>四角（5×5 固定尺寸，不拉伸）</li>
 *   <li>四边（1×5 / 5×1，沿轴拉伸/平铺）</li>
 *   <li>背景填充（1×1，双向拉伸）</li>
 *   <li>槽位背景（18×18）</li>
 * </ol>
 *
 * <p>所有纹理来自 {@code assets/yizmodqzk/textures/gui/container/}，
 * 遵循 Minecraft GUI 纹理系统的 -1px 偏移规则。</p>
 */
public final class PanelRenderHelper {

    // ── 纹理资源定位 ──

    private static final ResourceLocation TEX_FILL = tex("fill_white");
    private static final ResourceLocation TEX_SLOT = tex("slot_default");
    private static final ResourceLocation TEX_TL   = tex("border_corner_tl");
    private static final ResourceLocation TEX_TR   = tex("border_corner_tr");
    private static final ResourceLocation TEX_BL   = tex("border_corner_bl");
    private static final ResourceLocation TEX_BR   = tex("border_corner_br");
    private static final ResourceLocation TEX_TOP    = tex("border_edge_top");
    private static final ResourceLocation TEX_BOTTOM = tex("border_edge_bottom");
    private static final ResourceLocation TEX_LEFT   = tex("border_edge_left");
    private static final ResourceLocation TEX_RIGHT  = tex("border_edge_right");

    /** 边框纹理尺寸（px） */
    public static final int BORDER = 5;
    /** 槽位纹理尺寸（px） */
    public static final int SLOT   = 18;

    private PanelRenderHelper() {}

    /**
     * 绘制完整面板边框。
     *
     * <p>绘制顺序（从底到顶）：
     * 纯色填充 → 四边 → 四角。后续在上一层绘制槽位背景和物品。</p>
     *
     * @param g GuiGraphics
     * @param x 面板左上角 X（屏幕坐标）
     * @param y 面板左上角 Y（屏幕坐标）
     * @param w 面板总宽度
     * @param h 面板总高度
     */
    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h) {
        // 1. 背景填充 — fill_white 1×1 双向拉伸
        int innerW = w - BORDER * 2;
        int innerH = h - BORDER * 2;
        if (innerW > 0 && innerH > 0) {
            g.blit(TEX_FILL, x + BORDER, y + BORDER, 0, 0, innerW, innerH, 1, 1);
        }

        // 2. 四边 — 沿轴方向拉伸
        // 顶边
        g.blit(TEX_TOP, x + BORDER, y, 0, 0, innerW, BORDER, 1, BORDER);
        // 底边
        g.blit(TEX_BOTTOM, x + BORDER, y + h - BORDER, 0, 0, innerW, BORDER, 1, BORDER);
        // 左边
        g.blit(TEX_LEFT, x, y + BORDER, 0, 0, BORDER, innerH, BORDER, 1);
        // 右边
        g.blit(TEX_RIGHT, x + w - BORDER, y + BORDER, 0, 0, BORDER, innerH, BORDER, 1);

        // 3. 四角 — 固定 5×5，不拉伸
        g.blit(TEX_TL, x, y, 0, 0, BORDER, BORDER, BORDER, BORDER);
        g.blit(TEX_TR, x + w - BORDER, y, 0, 0, BORDER, BORDER, BORDER, BORDER);
        g.blit(TEX_BL, x, y + h - BORDER, 0, 0, BORDER, BORDER, BORDER, BORDER);
        g.blit(TEX_BR, x + w - BORDER, y + h - BORDER, 0, 0, BORDER, BORDER, BORDER, BORDER);
    }

    /**
     * 绘制单个槽位背景纹理。
     *
     * <p><b>重要：</b>调用方必须已经对 tx/ty 应用 -1 偏移。
     * 即 {@code tx = leftPos + slotX - 1, ty = topPos + slotY - 1}。</p>
     *
     * @param g  GuiGraphics
     * @param tx 槽位左上角 X（已含 -1 偏移）
     * @param ty 槽位左上角 Y（已含 -1 偏移）
     */
    public static void drawSlot(GuiGraphics g, int tx, int ty) {
        g.blit(TEX_SLOT, tx, ty, 0, 0, SLOT, SLOT, SLOT, SLOT);
    }

    /**
     * 构建带命名空间和路径的纹理 ResourceLocation。
     */
    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(
            "yizmodqzk", "textures/gui/container/" + name + ".png"
        );
    }
}
