package net.minecraft.client.yiz.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.core.data.EffectNBTHandler;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 效果文本渲染器
 * 渲染物品的词缀/随影效果详情。
 */
public final class EffectTooltipRenderer {

    private static final int LINE_HEIGHT = 12;
    private static final int INDENT = 8;

    private EffectTooltipRenderer() {}

    // 稀有度颜色映射
    public static final int MYTHIC_COLOR = 0xFFFF5555;
    public static final int LEGENDARY_COLOR = 0xFFFFAA00;
    public static final int EPIC_COLOR = 0xFFAA00AA;
    public static final int RARE_COLOR = 0xFF5555FF;
    public static final int COMMON_COLOR = 0xFFFFFFFF;

    /**
     * 根据稀有度获取颜色。
     */
    public static int getRarityColor(Rarity rarity) {
        if (rarity == null) return COMMON_COLOR;
        return switch (rarity) {
            case MYTHIC -> MYTHIC_COLOR;
            case LEGENDARY -> LEGENDARY_COLOR;
            case EPIC -> EPIC_COLOR;
            case RARE -> RARE_COLOR;
            case COMMON -> COMMON_COLOR;
        };
    }

    /**
     * 获取物品的所有效果并排序。
     * 排序规则：稀有度降序 → 等级降序。
     */
    public static List<AbstractEffect> getSortedEffects(ItemStack stack) {
        List<AbstractEffect> effects = new ArrayList<>(EffectNBTHandler.getItemEffects(stack));

        effects.sort((a, b) -> {
            // 第一优先级：稀有度降序
            int rarityCompare = Integer.compare(
                a.getRarity().ordinal(),
                b.getRarity().ordinal()
            );
            if (rarityCompare != 0) return rarityCompare;
            // 第二优先级：等级降序
            return Integer.compare(b.getLevel(), a.getLevel());
        });

        return effects;
    }

    /**
     * 渲染单个效果文本。
     */
    public static void renderEffect(GuiGraphics graphics, Font font, AbstractEffect effect, int x, int y) {
        if (effect == null) return;

        int color = getRarityColor(effect.getRarity());
        String name = String.format("%s %s (Lv.%d)",
            effect.getPerceptionTypeName(),
            effect.getDisplayName(),
            effect.getLevel()
        );

        // 效果名称
        graphics.drawString(font, name, x, y, color);
        int currentY = y + LINE_HEIGHT;

        // 父类信息
        String parentInfo = "└─ " + effect.getParentType().getChineseName();
        graphics.drawString(font, parentInfo, x + INDENT, currentY, 0x888888);
        currentY += LINE_HEIGHT;

        // 稀有度
        String rarityInfo = "└─ " + effect.getRarity().getChineseName();
        graphics.drawString(font, rarityInfo, x + INDENT, currentY, color);
    }

    /**
     * 渲染效果列表。
     */
    public static void renderEffects(GuiGraphics graphics, Font font, List<AbstractEffect> effects, int x, int y) {
        int currentY = y;
        for (AbstractEffect effect : effects) {
            renderEffect(graphics, font, effect, x, currentY);
            currentY += LINE_HEIGHT * 3; // 每个效果占 3 行
        }
    }
}
