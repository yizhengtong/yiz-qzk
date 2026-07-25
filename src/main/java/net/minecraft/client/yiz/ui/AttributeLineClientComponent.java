package net.minecraft.client.yiz.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.yiz.tool.icon.AttributeIconRegistry;
import net.minecraft.client.yiz.tool.icon.IconBlitHelper;

/**
 * 属性行客户端渲染组件：blit 属性图标 + drawString 文字，同一行。
 *
 * <p>行高由 {@link #getHeight()} 决定（= 图标尺寸 + 上下留白），引擎据此分配空间，
 * 图标多大都不会溢出到相邻行 —— 解决字体方案"图标作字符超高导致上下重叠"的问题。
 * 文字紧跟图标右侧，垂直居中；无图标的属性文字从行首开始（不留图标位）。</p>
 */
public class AttributeLineClientComponent implements ClientTooltipComponent {

    private static final int ICON = 16;   // 图标尺寸（blit 目标像素）
    private static final int PAD_Y = 1;   // 上下留白
    private static final int GAP = 4;     // 图标与文字间距

    private final String attrId;
    private final String name;
    private final String value;
    private final int color;

    public AttributeLineClientComponent(AttributeLineTooltipComponent data) {
        this.attrId = data.attrId();
        this.name = data.name();
        this.value = data.value();
        this.color = data.color();
    }

    @Override
    public int getHeight() {
        return ICON + PAD_Y * 2;  // 18：图标 16 + 上下各 1，相邻行留 2px 间隙不重叠
    }

    @Override
    public int getWidth(Font font) {
        return ICON + GAP + font.width(name + "：" + value);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics g) {
        int iconY = y + PAD_Y;
        int textY = y + PAD_Y + (ICON - font.lineHeight) / 2;  // 文字垂直居中于图标
        int textX;
        var icon = AttributeIconRegistry.get(attrId);
        if (icon != null) {
            IconBlitHelper.blit(g, icon, x, iconY, ICON);
            textX = x + ICON + GAP;
        } else {
            textX = x;  // 无图标：文字从行首开始
        }
        // 属性名（白）+ 值（蓝，与原 onItemTooltip 的 BLUE 观感一致）
        g.drawString(font, name + "：", textX, textY, 0xFFFFFFFF);
        g.drawString(font, value, textX + font.width(name + "："), textY, 0xFF5555FF);
    }
}
