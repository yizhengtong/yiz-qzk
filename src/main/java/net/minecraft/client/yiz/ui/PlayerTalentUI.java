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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 玩家实体天赋 UI 主类
 * 在生存背包界面左侧显示已解锁天赋信息。
 *
 * 快捷键 CTRL + SHIFT 切换开关。
 */
public class PlayerTalentUI {

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
     * 渲染天赋 UI（使用 renderTooltip 统一为原版紫色边框风格）。
     */
    public static void renderTalentUI(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<AbstractEffect> talents = getPlayerTalents(mc.player);
        if (talents.isEmpty()) return;

        // 构建工具提示行
        List<Component> lines = new ArrayList<>();

        // 标题
        lines.add(Component.literal("§6§l已解锁天赋"));
        lines.add(Component.literal(""));

        // 天赋列表
        for (AbstractEffect talent : talents) {
            int color = EffectTooltipRenderer.getRarityColor(talent.getRarity());
            String hexColor = color == 0xFFFF5555 ? "§c" :
                             color == 0xFFFFAA00 ? "§6" :
                             color == 0xFFAA00AA ? "§d" :
                             color == 0xFF5555FF ? "§9" : "§f";

            String name = String.format("%s%s §fLv.%d",
                hexColor, talent.getDisplayName(), talent.getLevel());
            lines.add(Component.literal(name));

            lines.add(Component.literal(String.format("  §7└─ %s · %s",
                talent.getParentType().getChineseName(),
                talent.getParentType().getRecommendedUse())));

            lines.add(Component.literal(String.format("  §7└─ 生效：%s",
                talent.getActivationCondition().getConditionName())));

            lines.add(Component.literal(String.format("  §7└─ %s",
                talent.getPerceptionTypeName())));

            lines.add(Component.literal(""));
        }

        // 计算位置（背包界面左侧），renderTooltip 默认在 mouseX+12, mouseY-12 渲染
        int guiLeft = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
        int guiTop = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
        int panelX = guiLeft - 12 - 8;
        int panelY = guiTop + 8 + 12;

        Font font = mc.font;
        graphics.renderTooltip(font, lines, Optional.empty(), panelX, panelY);
    }
}
