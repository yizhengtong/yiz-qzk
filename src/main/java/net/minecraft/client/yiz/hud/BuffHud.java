package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * 装备 buff 叠层 HUD（通用框架版）。
 *
 * <p>遍历 {@link BuffHudRegistry} 中所有激活的 buff 条目，每个条目渲染一行：
 * 源图标（左）+ "x{层数}" 数字。多行纵向排列，互不干扰。</p>
 *
 * <p>典型条目：鬼索 Guinsoo（攻击叠 CDR 层，无上限，5 秒衰减）。
 * 未来更多叠层装备各自注册 BuffHudEntry，此处不改。</p>
 *
 * <p>逻辑高度预留 MAX_ROWS 行（编辑器固定尺寸），实际按激活条目数渲染。</p>
 */
public class BuffHud extends HudElement {

    private static final int ICON_SZ = 20;
    private static final int ROW_H = 24;          // 图标 20 + 行间隔
    private static final int TEXT_GAP = 4;
    private static final int TEXT_W = 30;         // "x层数" 预留宽
    private static final int MAX_ROWS = 3;        // 预留行数（编辑器固定尺寸）
    private static final int TOTAL_W = ICON_SZ + TEXT_GAP + TEXT_W;
    private static final int TOTAL_H = MAX_ROWS * ROW_H;

    public BuffHud() {
        super("buff_bar", 0, 458, 1.0f);
    }

    @Override public int getLogicalWidth() { return TOTAL_W; }
    @Override public int getLogicalHeight() { return TOTAL_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        if (editMode) {
            // 编辑器：画一行占位让用户能拖动
            drawRow(g, 0, ItemStack.EMPTY, 3);
            return;
        }
        var displays = BuffHudRegistry.collect(mc.player, false);
        if (displays.isEmpty()) return;
        int row = 0;
        for (BuffHudEntry.Display d : displays) {
            if (row >= MAX_ROWS) break; // 超过预留行数截断
            drawRow(g, row * ROW_H, d.icon(), d.stacks());
            row++;
        }
    }

    /** 渲染单行：图标（20px）+ "x{层数}" 文字。 */
    private void drawRow(GuiGraphics g, int y, ItemStack icon, int stacks) {
        // ── 源图标 ──
        if (!icon.isEmpty()) {
            float scale = ICON_SZ / 16f;
            g.pose().pushPose();
            g.pose().translate(ICON_SZ / 2f, y + ICON_SZ / 2f, 0);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(icon, -8, -8);
            g.pose().popPose();
        }
        // ── x层数 文字 ──
        var font = Minecraft.getInstance().font;
        g.drawString(font, "x" + stacks, ICON_SZ + TEXT_GAP,
            y + (ICON_SZ - font.lineHeight) / 2, 0xFFFFFFFF);
    }
}
