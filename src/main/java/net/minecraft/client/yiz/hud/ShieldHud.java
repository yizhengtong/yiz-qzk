package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.tool.health.ShieldTracker;
import net.minecraft.resources.ResourceLocation;

/**
 * 护盾值 HUD — 原版护甲条风格：满格 + 半格 + 空格（共 20 格）。
 *
 * <p>护盾值 V（可小数）：</p>
 * <ul>
 *   <li>V ≤ 0：不显示</li>
 *   <li>0 &lt; V ≤ 20：满格(floor V) + 半格(小数≥0.5) + 空格，拼成 20 格条</li>
 *   <li>V &gt; 20：1 个满图标 + "x{数值}"</li>
 * </ul>
 * <p>半格用「满图标左半 + 空图标右半」拼接（无需单独半格图）。</p>
 */
public class ShieldHud extends HudElement {

    private static final ResourceLocation TEX_FULL =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/shield_full.png");
    private static final ResourceLocation TEX_EMPTY =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/shield_empty.png");

    private static final int ICON = 10;        // 每格目标尺寸
    private static final int SRC = 20;         // 源图尺寸（20×20）
    private static final int MAX_ICONS = 10;   // 图标总数（仿原版护甲 10 图标）
    private static final int VALUE_PER_ICON = 2;  // 每图标 2 值：满=2 半=1 空=0
    private static final int TOTAL_W = MAX_ICONS * ICON + 30;  // +30 给 x数值
    private static final int TOTAL_H = ICON;

    public ShieldHud() {
        super("shield_bar", 486, 456, 0.8f);
    }

    @Override public int getLogicalWidth()  { return TOTAL_W; }
    @Override public int getLogicalHeight() { return TOTAL_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        float shield = editMode ? 15.5f : (mc.player == null ? 0 : ShieldTracker.get(mc.player));
        if (shield <= 0) return;
        renderBar(g, mc, shield, TEX_FULL, TEX_EMPTY);
    }

    /** 通用：满+半+空 20 格条（>20 显示 1 图标 + x数值）。texFull/texEmpty 由子类/调用方提供。 */
    static void renderBar(GuiGraphics g, Minecraft mc, double value,
                          ResourceLocation texFull, ResourceLocation texEmpty) {
        if (value > MAX_ICONS * VALUE_PER_ICON) {
            blitIcon(g, texFull, 0, 0);
            g.drawString(mc.font, "x" + (int) value, ICON + 2,
                (ICON - mc.font.lineHeight) / 2, 0xFFFFFFFF);
            return;
        }
        int full = (int) (value / VALUE_PER_ICON);              // 满 = floor(V/2)
        boolean half = (value - full * VALUE_PER_ICON) >= 1.0;  // 余≥1 → 半格
        int empty = MAX_ICONS - full - (half ? 1 : 0);
        int x = 0;
        for (int i = 0; i < full; i++) { blitIcon(g, texFull, x, 0); x += ICON; }
        if (half) { blitHalf(g, texFull, texEmpty, x, 0); x += ICON; }
        for (int i = 0; i < empty; i++) { blitIcon(g, texEmpty, x, 0); x += ICON; }
    }

    private static void blitIcon(GuiGraphics g, ResourceLocation tex, int x, int y) {
        g.blit(tex, x, y, ICON, ICON, 0, 0, SRC, SRC, SRC, SRC);
    }

    private static void blitHalf(GuiGraphics g, ResourceLocation texFull, ResourceLocation texEmpty, int x, int y) {
        int half = ICON / 2;
        g.blit(texFull, x, y, half, ICON, 0, 0, SRC / 2, SRC, SRC, SRC);
        g.blit(texEmpty, x + half, y, half, ICON, SRC / 2, 0, SRC / 2, SRC, SRC, SRC);
    }
}
