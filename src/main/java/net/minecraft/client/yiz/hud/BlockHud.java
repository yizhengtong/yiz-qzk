package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.tool.health.ShieldTracker;
import net.minecraft.resources.ResourceLocation;

/**
 * 格挡值 HUD — 原版护甲条风格（逻辑同 {@link ShieldHud}）。
 *
 * <p>格挡值 V（DAMAGE_BLOCK 属性，可小数）：</p>
 * <ul>
 *   <li>V ≤ 0：不显示</li>
 *   <li>0 &lt; V ≤ 20：满格(floor V) + 半格(小数≥0.5) + 空格，拼成 20 格条</li>
 *   <li>V &gt; 20：1 个满图标 + "x{数值}"</li>
 * </ul>
 */
public class BlockHud extends HudElement {

    private static final ResourceLocation TEX_FULL =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/block_full.png");
    private static final ResourceLocation TEX_EMPTY =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/block_empty.png");

    private static final int ICON = 10;
    private static final int MAX_ICONS = 10;
    private static final int TOTAL_W = MAX_ICONS * ICON + 30;
    private static final int TOTAL_H = ICON;

    public BlockHud() {
        super("block_bar", 487, 468, 0.8f);
    }

    @Override public int getLogicalWidth()  { return TOTAL_W; }
    @Override public int getLogicalHeight() { return TOTAL_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        double block;
        if (editMode) {
            block = 9;
        } else {
            if (mc.player == null) return;
            var inst = mc.player.getAttribute(YizAttributes.DAMAGE_BLOCK);
            block = inst != null ? inst.getValue() : 0;
        }
        if (block <= 0) return;
        // 自动抬升：护盾无值时上移 icon+间隔（12px），填补护盾空位
        boolean lift = !editMode && ShieldTracker.get(mc.player) <= 0;
        if (lift) g.pose().pushPose();
        if (lift) g.pose().translate(0.0f, -(ICON + 2), 0.0f);
        ShieldHud.renderBar(g, mc, block, TEX_FULL, TEX_EMPTY);
        if (lift) g.pose().popPose();
    }
}
