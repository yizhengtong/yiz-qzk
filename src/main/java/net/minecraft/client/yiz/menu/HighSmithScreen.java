package net.minecraft.client.yiz.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.yiz.tizMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 高阶锻造台 GUI 屏幕。
 *
 * <p>使用积木纹理组合渲染：边框 + 背景填充 + 锻造特征 + 槽位 + 流程指示 + 分隔条 + 玩家背包。</p>
 */
public class HighSmithScreen extends AbstractContainerScreen<HighSmithMenu> {

    // ── 所有纹理引用（1.21.1 ResourceLocation 必须包含 .png） ──

    private static final ResourceLocation TEX_FILL      = tex("fill_white.png");
    private static final ResourceLocation TEX_CORNER_TL = tex("border_corner_tl.png");
    private static final ResourceLocation TEX_CORNER_TR = tex("border_corner_tr.png");
    private static final ResourceLocation TEX_CORNER_BL = tex("border_corner_bl.png");
    private static final ResourceLocation TEX_CORNER_BR = tex("border_corner_br.png");
    private static final ResourceLocation TEX_EDGE_TOP  = tex("border_edge_top.png");
    private static final ResourceLocation TEX_EDGE_BOT  = tex("border_edge_bottom.png");
    private static final ResourceLocation TEX_EDGE_LFT  = tex("border_edge_left.png");
    private static final ResourceLocation TEX_EDGE_RGT  = tex("border_edge_right.png");
    private static final ResourceLocation TEX_SLOT      = tex("slot_default.png");
    private static final ResourceLocation TEX_SPLIT     = tex("split_bar.png");
    private static final ResourceLocation TEX_SLOTS     = tex("player_slots_9x4.png");
    private static final ResourceLocation TEX_FEATURE   = tex("smithing_table_feature.png");
    private static final ResourceLocation TEX_PLUS      = tex("process_plus.png");
    private static final ResourceLocation TEX_ARROW     = tex("result_arrow.png");

    private static final int BORDER = 5;
    private static final int SLOT   = 18;
    private static final int COLS   = 9;

    // 布局常量
    private static final int CONTENT_WIDTH  = COLS * SLOT;              // 162
    private static final int GUI_WIDTH      = BORDER * 2 + CONTENT_WIDTH; // 172

    private static final int FEATURE_Y      = 10;
    private static final int SLOT_ROW_Y     = 45;
    private static final int SPLIT_Y        = 78;
    private static final int PLAYER_Y       = 92;
    private static final int CONTENT_HEIGHT = PLAYER_Y + 76 - BORDER;   // 163
    private static final int GUI_HEIGHT     = BORDER * 2 + CONTENT_HEIGHT;

    public HighSmithScreen(HighSmithMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = GUI_HEIGHT - 94;
        this.titleLabelX = BORDER + 40;
        this.titleLabelY = 8;
        tizMod.LOGGER.info("HighSmithScreen 创建完毕，slots={}", menu.slots.size());
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/container/" + name);
    }

    // ════════════════════════════════════════════════════════════════
    //  渲染入口
    // ════════════════════════════════════════════════════════════════

    // 调试：记录所有鼠标点击
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        Slot slot = null;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (isHovering(s.x, s.y, 16, 16, mx, my)) { slot = s; break; }
        }
        if (slot != null) {
            tizMod.LOGGER.info("点击 槽位 idx={} x={} y={} 物品={}", slot.index, slot.x, slot.y,
                slot.hasItem() ? slot.getItem().getHoverName().getString() : "空");
        } else {
            tizMod.LOGGER.info("点击 空白 mx={} my={} leftPos={} topPos={}", mx, my, leftPos, topPos);
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 1) 背景填充 — 使用 fill() 代替逐像素平铺（手册 §5、MC原版颜色 0xC6C6C6）
        g.fill(x + BORDER, y + BORDER, x + BORDER + CONTENT_WIDTH, y + BORDER + CONTENT_HEIGHT, 0xFFC6C6C6);

        // 2) 边框
        renderBorder(g, x, y, CONTENT_WIDTH, CONTENT_HEIGHT);

        // 3) 锻造特征图标
        int featureX = x + BORDER + 4;
        g.blit(TEX_FEATURE, featureX, y + FEATURE_Y, 0, 0, 30, 31, 30, 31);

        // 4) 4 个槽位 — 绘制在 (slot.x-1, slot.y-1) 对齐 isHovering 命中盒（手册 §2）
        int[] slotCols = {0, 2, 4, 7};
        for (int col : slotCols) {
            g.blit(TEX_SLOT, x + BORDER + col * SLOT - 1, y + SLOT_ROW_Y - 1, 0, 0, 18, 18, 18, 18);
        }

        // 5) 流程指示 — 在槽位同排的间隙中居中插入（而不是下方）：
        //    槽纹理[4-22) [40-58) [76-94) [130-148) (leftPos 相对)
        //    gap0 [22-40) 中31 → +13/2=6 → x=25   = BORDER+20
        //    gap1 [58-76) 中67 → +13/2=6 → x=61   = BORDER+56
        //    gap2 [94-130) 中112 → +22/2=11 → x=101 = BORDER+96
        g.blit(TEX_PLUS,   x + BORDER + 20, y + SLOT_ROW_Y, 0, 0, 13, 13, 13, 13);
        g.blit(TEX_PLUS,   x + BORDER + 56, y + SLOT_ROW_Y, 0, 0, 13, 13, 13, 13);
        g.blit(TEX_ARROW,  x + BORDER + 96, y + SLOT_ROW_Y + 1, 0, 0, 22, 15, 22, 15);

        // 6) 分隔条（水平平铺，1px × 14px 源，拉伸到 162px 宽）
        for (int sx = 0; sx < CONTENT_WIDTH; sx++) {
            g.blit(TEX_SPLIT, x + BORDER + sx, y + SPLIT_Y, 0, 0, 1, 14, 1, 14);
        }

        // 7) 玩家背包大块 — 绘制在 (leftPos+4, topPos+91)，与槽位命中盒对齐（手册 §2）
        g.blit(TEX_SLOTS, x + BORDER - 1, y + PLAYER_Y - 1, 0, 0, 162, 76, 162, 76);
    }

    // ════════════════════════════════════════════════════════════════
    //  辅助渲染
    // ════════════════════════════════════════════════════════════════

    private void renderBorder(GuiGraphics g, int bx, int by, int cw, int ch) {
        // 四角
        g.blit(TEX_CORNER_TL, bx, by, 0, 0, 5, 5, 5, 5);
        g.blit(TEX_CORNER_TR, bx + cw + 5, by, 0, 0, 5, 5, 5, 5);
        g.blit(TEX_CORNER_BL, bx, by + ch + 5, 0, 0, 5, 5, 5, 5);
        g.blit(TEX_CORNER_BR, bx + cw + 5, by + ch + 5, 0, 0, 5, 5, 5, 5);

        // 上下边框（1×5 源纹理水平平铺到 cw 宽）
        for (int ex = 0; ex < cw; ex++) {
            g.blit(TEX_EDGE_TOP, bx + 5 + ex, by, 0, 0, 1, 5, 1, 5);
            g.blit(TEX_EDGE_BOT, bx + 5 + ex, by + ch + 5, 0, 0, 1, 5, 1, 5);
        }
        // 左右边框（5×1 源纹理垂直平铺到 ch 高）
        for (int ey = 0; ey < ch; ey++) {
            g.blit(TEX_EDGE_LFT, bx, by + 5 + ey, 0, 0, 5, 1, 5, 1);
            g.blit(TEX_EDGE_RGT, bx + cw + 5, by + 5 + ey, 0, 0, 5, 1, 5, 1);
        }
    }

}
