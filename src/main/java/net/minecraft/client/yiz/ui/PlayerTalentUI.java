package net.minecraft.client.yiz.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.perception.EntityPerception;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 玩家实体天赋 UI 主类
 * 在生存背包界面右侧显示已解锁天赋信息。
 *
 * 快捷键 CTRL + SHIFT 切换开关。
 */
public class PlayerTalentUI {

    private static final int PANEL_WIDTH = 180;
    private static final int HEADER_HEIGHT = 16;
    private static final int CARD_HEIGHT = 50;
    private static final int CARD_MARGIN = 4;

    private PlayerTalentUI() {}

    /**
     * 检查是否应该显示天赋 UI。
     */
    public static boolean shouldShow(Minecraft mc) {
        if (!UIConfig.isPlayerTalentUIEnabled()) return false;
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return false;
        if (mc.player == null) return false;
        return hasUnlockedTalents(mc.player);
    }

    /**
     * 检查玩家是否有已解锁天赋。
     */
    private static boolean hasUnlockedTalents(LocalPlayer player) {
        return !getPlayerTalents(player).isEmpty();
    }

    /**
     * 获取玩家所有已解锁的天赋。
     */
    public static List<AbstractEffect> getPlayerTalents(LocalPlayer player) {
        List<AbstractEffect> talents = new ArrayList<>();

        for (AbstractEffect effect : ModRegistries.getAllEffects()) {
            // 筛选实体绑定感知（天赋）
            boolean isEntityTalent = effect.getPerceptionModes().stream()
                .anyMatch(mode -> mode instanceof EntityPerception);

            if (!isEntityTalent) continue;

            // 检查是否已解锁
            if (UnlockManager.isUnlocked(player, effect.getId())) {
                talents.add(effect);
            }
        }

        // 排序：稀有度降序 → 等级降序
        talents.sort((a, b) -> {
            int rarityCompare = Integer.compare(a.getRarity().ordinal(), b.getRarity().ordinal());
            if (rarityCompare != 0) return rarityCompare;
            return Integer.compare(b.getLevel(), a.getLevel());
        });

        return talents;
    }

    /**
     * 渲染天赋 UI。
     */
    public static void renderTalentUI(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<AbstractEffect> talents = getPlayerTalents(mc.player);
        if (talents.isEmpty()) return;

        Font font = mc.font;

        // 计算位置（背包界面右侧）
        int guiLeft = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
        int guiTop = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
        int panelX = guiLeft + 176 + 8;
        int panelY = guiTop + 8;

        int panelHeight = Math.min(
            HEADER_HEIGHT + talents.size() * (CARD_HEIGHT + CARD_MARGIN),
            240
        );

        // 绘制背景
        renderPanelBackground(graphics, panelX, panelY, PANEL_WIDTH, panelHeight);

        // 绘制标题
        graphics.drawString(font, Component.literal("已解锁天赋"),
            panelX + 6, panelY + 4, 0xFFFFFF);

        // 绘制天赋列表
        int currentY = panelY + HEADER_HEIGHT;
        for (AbstractEffect talent : talents) {
            renderTalentCard(graphics, font, talent, panelX + 4, currentY);
            currentY += CARD_HEIGHT + CARD_MARGIN;
        }
    }

    /**
     * 绘制面板背景。
     */
    private static void renderPanelBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        // 使用原版背包纹理
        var texture = ResourceLocation.parse("textures/gui/container/inventory.png");
        graphics.blit(texture, x, y, 0, 0, 0, width, height, 256, 256);
    }

    /**
     * 绘制单个天赋卡片。
     */
    private static void renderTalentCard(GuiGraphics graphics, Font font, AbstractEffect talent, int x, int y) {
        int color = EffectTooltipRenderer.getRarityColor(talent.getRarity());
        String name = String.format("%s·%s Lv.%d",
            talent.getRarity().getChineseName(),
            talent.getDisplayName(),
            talent.getLevel()
        );

        // 名称
        graphics.drawString(font, name, x, y, color);
        // 父类
        graphics.drawString(font,
            "  └─ " + talent.getParentType().getChineseName() + " · " + talent.getParentType().getRecommendedUse(),
            x, y + 12, 0x888888);
        // 生效条件
        graphics.drawString(font,
            "  └─ 生效：" + talent.getActivationCondition().getConditionName(),
            x, y + 24, 0x888888);
        // 感知类型
        graphics.drawString(font,
            "  └─ " + talent.getPerceptionTypeName(),
            x, y + 36, 0x888888);
    }
}
