package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.tool.health.ShieldTracker;
import net.minecraft.resources.ResourceLocation;

/**
 * 护盾值 HUD — 左右半图成对排列，如原版护甲半颗UI。
 * <p>护盾≤20：显示对应数量图标。护盾>20：显示20个图标+数字。</p>
 */
public class ShieldHud extends HudElement {

    private static final ResourceLocation TEX_L =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/shield_left.png");
    private static final ResourceLocation TEX_R =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/shield_right.png");

    private static final int ICON_W = 10, ICON_H = 20;
    private static final int PAIR_GAP = 2;
    private static final int MAX_ICONS = 20;

    private static final int TOTAL_W = MAX_ICONS * ICON_W + (MAX_ICONS / 2 - 1) * PAIR_GAP + 30;
    private static final int TOTAL_H = ICON_H;

    public ShieldHud() {
        super("shield_bar", 200, 340, 1.0f);
    }

    @Override public int getLogicalWidth()  { return TOTAL_W; }
    @Override public int getLogicalHeight() { return TOTAL_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        int shield;
        if (editMode) {
            shield = 25;
        } else {
            if (mc.player == null) return;
            shield = (int) ShieldTracker.get(mc.player);
        }
        if (shield <= 0) return;

        int show = Math.min(shield, MAX_ICONS);
        for (int i = 0; i < show; i++) {
            int pairIdx = i / 2;
            int withinPair = i % 2;
            int px = pairIdx * (ICON_W * 2 + PAIR_GAP) + withinPair * ICON_W;
            g.blit(withinPair == 0 ? TEX_L : TEX_R,
                px, 0, 0, 0, ICON_W, ICON_H, ICON_W, ICON_H);
        }
        if (shield > MAX_ICONS) {
            String label = "+" + (shield - MAX_ICONS);
            int nx = MAX_ICONS * ICON_W + (MAX_ICONS / 2 - 1) * PAIR_GAP + 2;
            g.drawString(Minecraft.getInstance().font, label, nx, 1, 0xFFFFFFFF);
        }
    }
}
