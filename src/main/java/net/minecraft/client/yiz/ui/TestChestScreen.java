package net.minecraft.client.yiz.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.yiz.menu.TestChestMenu;

/**
 * 测试容器屏幕 — 用积木块纹理拼凑完整容器界面，支持物品交互
 *
 * 窗口布局 (172×154)：
 *
 *   ┌─ TL ── top_edge(平铺) ── TR ─┐  ← 5px
 *   │  ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░  │  ← 18px  箱子槽区
 *   │  ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░  │  ← 18px  (slot_default × 27)
 *   │  ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░  │  ← 18px
 *   ├────── split_bar ──────────────┤  ← 14px
 *   │       player_slots_9x4        │  ← 76px  玩家背包
 *   └─ BL ── bottom_edge ── BR ────┘  ← 5px
 */
public class TestChestScreen extends AbstractContainerScreen<TestChestMenu> {

    // 积木块纹理
    private static final ResourceLocation TEX_SLOTS =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/player_slots_9x4.png");
    private static final ResourceLocation TEX_SLOT =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/slot_default.png");
    private static final ResourceLocation TEX_TL =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_corner_tl.png");
    private static final ResourceLocation TEX_TR =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_corner_tr.png");
    private static final ResourceLocation TEX_BL =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_corner_bl.png");
    private static final ResourceLocation TEX_BR =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_corner_br.png");
    private static final ResourceLocation TEX_TOP =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_edge_top.png");
    private static final ResourceLocation TEX_BOTTOM =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_edge_bottom.png");
    private static final ResourceLocation TEX_LEFT =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_edge_left.png");
    private static final ResourceLocation TEX_RIGHT =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/border_edge_right.png");
    private static final ResourceLocation TEX_FILL =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/fill_white.png");
    private static final ResourceLocation TEX_SPLIT =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/test/split_bar.png");

    private static final int BORDER = 5;
    private static final int SLOT = 18;
    private static final int COLS = 9;
    private static final int CONTENT_W = COLS * SLOT;      // 162
    private static final int TOTAL_W = BORDER * 2 + CONTENT_W; // 172
    private static final int CHEST_ROWS = 3;
    private static final int CHEST_H = CHEST_ROWS * SLOT;  // 54
    private static final int SPLIT_H = 14;
    private static final int PLAYER_H = 76;
    private static final int TOTAL_H = BORDER + CHEST_H + SPLIT_H + PLAYER_H + BORDER; // 154

    public TestChestScreen(TestChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = TOTAL_W;
        this.imageHeight = TOTAL_H;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelY = -1000; // 隐藏默认标题（由纹理边框遮盖）
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 第1层：背景填充 — 仅填充内容区内部，不铺到四角/四边位置
        // 这样角落的透明像素不会透出灰色
        drawTiled(graphics, TEX_FILL,
            x + BORDER, y + BORDER,
            CONTENT_W, TOTAL_H - BORDER * 2,
            1, 1);

        // 第2层：边框
        // 顶边
        drawTiled(graphics, TEX_TOP,    x + BORDER, y,               CONTENT_W, BORDER, 1, 5);
        // 底边
        drawTiled(graphics, TEX_BOTTOM, x + BORDER, y + TOTAL_H - BORDER, CONTENT_W, BORDER, 1, 5);
        // 左边
        drawTiled(graphics, TEX_LEFT,   x,          y + BORDER,      BORDER, TOTAL_H - BORDER * 2, 5, 1);
        // 右边
        drawTiled(graphics, TEX_RIGHT,  x + TOTAL_W - BORDER, y + BORDER, BORDER, TOTAL_H - BORDER * 2, 5, 1);
        // 四角
        drawTex(graphics, TEX_TL, x, y, 5, 5);
        drawTex(graphics, TEX_TR, x + TOTAL_W - BORDER, y, 5, 5);
        drawTex(graphics, TEX_BL, x, y + TOTAL_H - BORDER, 5, 5);
        drawTex(graphics, TEX_BR, x + TOTAL_W - BORDER, y + TOTAL_H - BORDER, 5, 5);

        // 第3层：箱子槽位 (3×9) — 用 slot_default 铺出来
        // 偏移 -1px 对齐 isHovering 命中区（isHovering 从 slot.x-1 开始判断）
        int chestY = y + BORDER;
        for (int row = 0; row < CHEST_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                drawTex(graphics, TEX_SLOT,
                    x + BORDER + col * SLOT - 1,
                    chestY + row * SLOT - 1,
                    18, 18);
            }
        }

        // 第4层：分隔带
        int splitY = chestY + CHEST_H;
        drawTiled(graphics, TEX_SPLIT, x + BORDER, splitY, CONTENT_W, SPLIT_H, 1, 14);

        // 第5层：玩家背包预置大块（偏移 -1px 对齐命中区）
        int playerY = splitY + SPLIT_H;
        drawTex(graphics, TEX_SLOTS, x + BORDER - 1, playerY - 1, 162, 76);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // AbstractContainerScreen 的 render() 会调用 renderBg + 渲染槽位和物品
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    // ==================== 渲染辅助 ====================

    private void drawTex(GuiGraphics g, ResourceLocation tex, int x, int y, int texW, int texH) {
        g.blit(tex, x, y, 0.0f, 0.0f, texW, texH, texW, texH);
    }

    private void drawTiled(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH) {
        if (w <= 0 || h <= 0) return;
        g.blit(tex, x, y, 0.0f, 0.0f, w, h, texW, texH);
    }
}
