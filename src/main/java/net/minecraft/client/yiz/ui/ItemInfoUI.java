package net.minecraft.client.yiz.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.core.data.EffectNBTHandler;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 物品信息 UI 主类
 * 完全替代原版指针悬浮物品提示，通过自定义绘制显示新的物品信息。
 *
 * 快捷键 CTRL + ALT 切换开关。
 */
public class ItemInfoUI {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 12;

    private ItemInfoUI() {}

    /**
     * 检查是否应该显示自定义 UI。
     */
    public static boolean shouldShow() {
        return UIConfig.isCustomItemUIEnabled();
    }

    /**
     * 渲染自定义物品信息。
     */
    public static void renderItemInfo(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        if (!shouldShow() || stack.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // 位置计算
        int x = mouseX + 8;
        int y = mouseY - 12;

        // 获取显示内容
        List<Component> lines = buildInfoLines(stack, font);

        // 计算背景尺寸
        int maxWidth = 0;
        for (Component line : lines) {
            int width = font.width(line);
            if (width > maxWidth) maxWidth = width;
        }
        int height = lines.size() * LINE_HEIGHT + PADDING * 2;
        int bgWidth = maxWidth + PADDING * 2;

        // 绘制背景
        graphics.fill(x, y, x + bgWidth, y + height, 0xCC000000);
        graphics.fill(x, y, x + bgWidth, y + 1, 0xFF888888);
        graphics.fill(x, y + height - 1, x + bgWidth, y + height, 0xFF888888);
        graphics.fill(x, y, x + 1, y + height, 0xFF888888);
        graphics.fill(x + bgWidth - 1, y, x + bgWidth, y + height, 0xFF888888);

        // 绘制文本
        int textY = y + PADDING;
        for (Component line : lines) {
            graphics.drawString(font, line, x + PADDING, textY, 0xFFFFFFFF);
            textY += LINE_HEIGHT;
        }
    }

    /**
     * 构建物品信息行。
     */
    private static List<Component> buildInfoLines(ItemStack stack, Font font) {
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>();

        // 1. 物品名称
        lines.add(stack.getHoverName());

        // 2. 属性信息
        var attributes = ItemAttributeDisplay.getAvailableAttributes(stack);
        for (var attr : attributes) {
            lines.add(ItemAttributeDisplay.createAttributeComponent(attr.name(), attr.value(), attr.color()));
        }

        // 3. 效果信息
        List<AbstractEffect> effects = EffectNBTHandler.getItemEffects(stack);
        if (!effects.isEmpty()) {
            lines.add(Component.literal(""));
            for (AbstractEffect effect : effects) {
                String effectLine = String.format(" [%s] %s (Lv.%d)",
                    effect.getParentType().getChineseName(),
                    effect.getDisplayName(),
                    effect.getLevel()
                );
                lines.add(Component.literal(effectLine));
            }
        }

        return lines;
    }
}
