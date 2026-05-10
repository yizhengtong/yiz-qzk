package net.minecraft.client.yiz.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.core.data.EffectNBTHandler;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 物品信息 UI 主类
 * 完全替代原版指针悬浮物品提示，通过自定义绘制显示新的物品信息。
 *
 * 快捷键 CTRL + ALT 切换开关。
 */
public final class ItemInfoUI {

    private ItemInfoUI() {}

    /**
     * 检查是否应该显示自定义 UI。
     */
    public static boolean shouldShow() {
        return UIConfig.isCustomItemUIEnabled();
    }

    /**
     * 渲染自定义物品信息（使用 GuiGraphics.renderTooltip 确保在最上层）。
     */
    public static void renderItemInfo(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (!shouldShow() || stack.isEmpty()) return;

        List<Component> lines = buildInfoLines(stack);

        if (!lines.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            // 使用 renderTooltip 渲染，其 RenderType 在所有 GUI 元素最上层
            graphics.renderTooltip(font, lines, Optional.empty(), x, y);
        }
    }

    /**
     * 构建物品信息行。
     */
    private static List<Component> buildInfoLines(ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        // 1. 物品名称
        lines.add(stack.getHoverName());

        // 2. 属性信息
        var attributes = ItemAttributeDisplay.getAvailableAttributes(stack);
        for (var attr : attributes) {
            MutableComponent line = Component.literal("  ");
            line.append(Component.literal(attr.name() + "："));
            String color = attr.color() == 0xFF55FF55 ? "§a" :
                          attr.color() == 0xFFFF5555 ? "§c" : "§f";
            line.append(Component.literal(color + attr.value()));
            lines.add(line);
        }

        // 3. 效果信息
        List<AbstractEffect> effects = EffectNBTHandler.getItemEffects(stack);
        if (!effects.isEmpty()) {
            for (AbstractEffect effect : effects) {
                String effectLine = String.format(" §7[§f%s§7] §f%s §7(Lv.%d)",
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
