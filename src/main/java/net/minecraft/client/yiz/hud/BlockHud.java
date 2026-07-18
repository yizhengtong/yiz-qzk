package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.resources.ResourceLocation;

/**
 * 格挡值 HUD — 图标水平排列，实时跟随 DAMAGE_BLOCK 属性值。
 * <p>格挡≤20：显示对应数量图标。格挡>20：显示20个图标+数字。</p>
 */
public class BlockHud extends HudElement {

    private static final ResourceLocation TEX_L =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/block_left.png");
    private static final ResourceLocation TEX_R =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/block_right.png");

    private static final int ICON_W = 10, ICON_H = 20;
    private static final int PAIR_GAP = 2;
    private static final int MAX_ICONS = 20;

    private static final int TOTAL_W = MAX_ICONS * ICON_W + (MAX_ICONS / 2 - 1) * PAIR_GAP + 30;
    private static final int TOTAL_H = ICON_H;

    public BlockHud() {
        super("block_bar", 200, 370, 1.0f);
    }

    @Override public int getLogicalWidth()  { return TOTAL_W; }
    @Override public int getLogicalHeight() { return TOTAL_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        int block;
        if (editMode) {
            block = 25;
        } else {
            if (mc.player == null) return;
            var inst = mc.player.getAttribute(YizAttributes.DAMAGE_BLOCK);
            block = inst != null ? (int) inst.getValue() : 0;
        }
        if (block <= 0) return;

        int show = Math.min(block, MAX_ICONS);
        for (int i = 0; i < show; i++) {
            int pairIdx = i / 2;
            int withinPair = i % 2;
            int px = pairIdx * (ICON_W * 2 + PAIR_GAP) + withinPair * ICON_W;
            g.blit(withinPair == 0 ? TEX_L : TEX_R,
                px, 0, 0, 0, ICON_W, ICON_H, ICON_W, ICON_H);
        }
        if (block > MAX_ICONS) {
            String label = "+" + (block - MAX_ICONS);
            int nx = MAX_ICONS * ICON_W + (MAX_ICONS / 2 - 1) * PAIR_GAP + 2;
            g.drawString(Minecraft.getInstance().font, label, nx, 1, 0xFFFFFFFF);
        }
    }
}
