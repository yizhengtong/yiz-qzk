package net.minecraft.client.yiz.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 通用变列容器屏幕 — 使用 5 层纹理系统渲染动态尺寸的容器 GUI。
 *
 * <p>纹理层叠顺序：fill → border → slot_default（容器槽位）→ split_bar → player_slots_9x4。
 * 所有槽位背景纹理从 {@code (slot.x - 1, slot.y - 1)} 开始绘制，对齐 {@code isHovering} 命中盒。
 */
public class DynamicChestScreen extends AbstractContainerScreen<DynamicChestMenu> {

    private static final ResourceLocation TEX_FILL = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/fill_white.png");
    private static final ResourceLocation TEX_CORNER_TL = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_corner_tl.png");
    private static final ResourceLocation TEX_CORNER_TR = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_corner_tr.png");
    private static final ResourceLocation TEX_CORNER_BL = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_corner_bl.png");
    private static final ResourceLocation TEX_CORNER_BR = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_corner_br.png");
    private static final ResourceLocation TEX_EDGE_TOP = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_edge_top.png");
    private static final ResourceLocation TEX_EDGE_BOTTOM = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_edge_bottom.png");
    private static final ResourceLocation TEX_EDGE_LEFT = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_edge_left.png");
    private static final ResourceLocation TEX_EDGE_RIGHT = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/border_edge_right.png");
    private static final ResourceLocation TEX_SLOT = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/slot_default.png");
    private static final ResourceLocation TEX_SPLIT = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/split_bar.png");
    private static final ResourceLocation TEX_PLAYER_SLOTS = ResourceLocation.fromNamespaceAndPath(
        "yizmodqzk", "textures/gui/container/player_slots_9x4.png");

    private final int cols;
    private final int rows;
    private final int invOffset;

    public DynamicChestScreen(DynamicChestMenu menu, Inventory playerInv, Component component) {
        super(menu, playerInv, component);
        this.cols = menu.getCols();
        this.rows = menu.getRows();
        this.invOffset = cols > 9 ? (cols - 9) * 18 / 2 : 0;
        this.imageWidth = DynamicChestMenu.BORDER * 2 + cols * 18;
        this.imageHeight = DynamicChestMenu.BORDER + rows * 18 + 14 + 76 + DynamicChestMenu.BORDER;
        this.titleLabelX = 8;
        this.inventoryLabelX = DynamicChestMenu.BORDER + invOffset;
        this.inventoryLabelY = DynamicChestMenu.BORDER + rows * 18 + 14 - 12;
    }

    /**
     * 检查此屏幕的槽位数量是否匹配，用于按键切换判断。
     */
    public boolean hasSlotCount(int slots) {
        return menu.getContainerSlotCount() == slots;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int b = DynamicChestMenu.BORDER;
        int contentW = cols * 18;
        int containerH = rows * 18;
        int playerH = 76;
        int interiorH = containerH + 14 + playerH;
        // ═══ Layer 1: fill_white 背景填充 ═══
        graphics.blit(TEX_FILL, x + b, y + b, contentW, interiorH, 0, 0, 1, 1, 1, 1);

        // ═══ Layer 2: 边框（四角 + 四边） ═══
        int rightX = x + b + contentW;
        int botY = y + b + interiorH;

        // 四角
        graphics.blit(TEX_CORNER_TL, x, y, 5, 5, 0, 0, 5, 5, 5, 5);
        graphics.blit(TEX_CORNER_TR, rightX, y, 5, 5, 0, 0, 5, 5, 5, 5);
        graphics.blit(TEX_CORNER_BL, x, botY, 5, 5, 0, 0, 5, 5, 5, 5);
        graphics.blit(TEX_CORNER_BR, rightX, botY, 5, 5, 0, 0, 5, 5, 5, 5);

        // 四边（平铺）
        graphics.blit(TEX_EDGE_TOP, x + 5, y, contentW, 5, 0, 0, 1, 5, 1, 5);
        graphics.blit(TEX_EDGE_BOTTOM, x + 5, botY, contentW, 5, 0, 0, 1, 5, 1, 5);
        graphics.blit(TEX_EDGE_LEFT, x, y + 5, 5, interiorH, 0, 0, 5, 1, 5, 1);
        graphics.blit(TEX_EDGE_RIGHT, rightX, y + 5, 5, interiorH, 0, 0, 5, 1, 5, 1);

        // ═══ Layer 3: 容器槽位背景 ═══
        for (Slot slot : menu.slots) {
            if (slot.container == menu.getContainer()) {
                graphics.blit(TEX_SLOT, x + slot.x - 1, y + slot.y - 1, 18, 18, 0, 0, 18, 18, 18, 18);
            }
        }

        // ═══ Layer 4: 分隔条（水平平铺） ═══
        graphics.blit(TEX_SPLIT, x + b, y + b + containerH, contentW, 14, 0, 0, 1, 14, 1, 14);

        // ═══ Layer 5: 玩家背包槽位群（居中） ═══
        graphics.blit(TEX_PLAYER_SLOTS, x + b + invOffset - 1, y + b + containerH + 14 - 1, 162, playerH, 0, 0, 162, playerH, 162, playerH);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title.getString(), (float) this.titleLabelX, (float) this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle.getString(), (float) this.inventoryLabelX, (float) this.inventoryLabelY, 0x404040, false);
    }
}
