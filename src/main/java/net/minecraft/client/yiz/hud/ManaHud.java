package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.tool.health.ManaTracker;
import net.minecraft.resources.ResourceLocation;

/**
 * 蓝条 HUD — 空蓝底图 + 蓝条按百分比从左裁切覆盖。
 */
public class ManaHud extends HudElement {

    private static final ResourceLocation TEX_FULL =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/mana_full.png");
    private static final ResourceLocation TEX_EMPTY =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/mana_empty.png");

    private static final int BAR_W = 90, BAR_H = 10;
    private static final int TEXT_COLOR = 0xFF55FFFF; // 淡蓝

    public ManaHud() {
        super("mana_bar", 200, 400, 1.0f);
    }

    @Override public int getLogicalWidth()  { return BAR_W; }
    @Override public int getLogicalHeight() { return BAR_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        float mana, max;
        if (editMode) {
            mana = 100; max = 200;
        } else {
            if (mc.player == null) return;
            mana = ManaTracker.get(mc.player);
            max = ManaTracker.getMax(mc.player);
        }
        if (max <= 0) return;

        // 空蓝底图（满宽）
        g.blit(TEX_EMPTY, 0, 0, 0, 0, BAR_W, BAR_H, BAR_W, BAR_H);

        // 蓝条覆盖（按百分比从左裁切）
        float pct = Math.max(0f, Math.min(1f, mana / max));
        int fillW = Math.max(0, Math.round(BAR_W * pct));
        if (fillW > 0) {
            g.blit(TEX_FULL, 0, 0, 0, 0, fillW, BAR_H, BAR_W, BAR_H);
        }

        // 居中数字：当前值/最大值，淡蓝色，缩小
        String text = ((int) mana) + "/" + ((int) max);
        float scale = 0.7f;
        int textW = (int)(mc.font.width(text) * scale);
        int textH = (int)(mc.font.lineHeight * scale);
        g.pose().pushPose();
        g.pose().translate((BAR_W - textW) / 2f, (BAR_H - textH) / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(mc.font, text, 0, 0, TEXT_COLOR);
        g.pose().popPose();
    }
}
