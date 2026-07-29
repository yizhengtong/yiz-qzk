package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.yiz.CombinedContainerMenu;

/**
 * 组合容器 GUI：左半=箱子（176px），右半=工作台（176px），总宽=352px。
 */
public class CombinedContainerScreen extends AbstractContainerScreen<CombinedContainerMenu> {

    private static final ResourceLocation CRAFTING_TABLE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final ResourceLocation CHEST_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public CombinedContainerScreen(CombinedContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 352;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        int x = this.leftPos;
        int y = this.topPos;

        // 左半：箱子背景
        g.blit(CHEST_TEXTURE, x, y, 0, 0, 176, 166);

        // 右半：工作台背景
        g.blit(CRAFTING_TABLE_TEXTURE, x + 176, y, 0, 0, 176, 166);
    }
}
