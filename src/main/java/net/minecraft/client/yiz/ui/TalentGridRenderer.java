package net.minecraft.client.yiz.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 天赋网格渲染器
 * 渲染天赋列表，使用原版物品面板纹理。
 */
public class TalentGridRenderer {

    private static final ResourceLocation INVENTORY_TEXTURE =
        ResourceLocation.parse("textures/gui/container/inventory.png");

    private int scrollOffset = 0;
    private int maxScroll = 0;

    /**
     * 绘制背景面板。
     */
    public void renderBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blit(INVENTORY_TEXTURE, x, y, 0, 0, 0, width, height, 256, 256);
    }

    /**
     * 绘制单个天赋卡片。
     */
    public void renderTalentCard(GuiGraphics graphics, Font font, AbstractEffect talent, int x, int y) {
        int color = EffectTooltipRenderer.getRarityColor(talent.getRarity());

        // 天赋名称（带稀有度颜色）
        String name = String.format("%s·%s Lv.%d",
            talent.getRarity().getChineseName(),
            talent.getDisplayName(),
            talent.getLevel()
        );
        graphics.drawString(font, name, x + 4, y + 4, color);

        // 父类信息
        graphics.drawString(font,
            Component.literal(talent.getParentType().getChineseName()),
            x + 4, y + 20, 0x888888);

        // 生效条件
        graphics.drawString(font,
            Component.literal("生效：" + talent.getActivationCondition().getConditionName()),
            x + 4, y + 34, 0x888888);

        // 分隔线
        graphics.fill(x, y + 48, x + 172, y + 49, 0x33FFFFFF);
    }

    /**
     * 绘制滚动条。
     */
    public void renderScrollbar(GuiGraphics graphics, int x, int y, int panelHeight, int contentHeight) {
        if (contentHeight <= panelHeight) return;

        int scrollbarHeight = panelHeight * panelHeight / contentHeight;
        int scrollbarY = y + (panelHeight - scrollbarHeight) * scrollOffset / (contentHeight - panelHeight);

        // 滚动条背景
        graphics.fill(x + 175, y, x + 179, y + panelHeight, 0x33FFFFFF);
        // 滚动条滑块
        graphics.fill(x + 175, scrollbarY, x + 179, scrollbarY + scrollbarHeight, 0x88FFFFFF);
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = offset;
    }

    public void setMaxScroll(int maxScroll) {
        this.maxScroll = maxScroll;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getMaxScroll() {
        return maxScroll;
    }
}
