package net.minecraft.client.yiz.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.api.SkillHudData;
import net.minecraft.client.yiz.handler.SkillKeyMappings;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 技能装载 HUD — 带按键提示。
 */
public class SkillHud extends HudElement {

    private static final ResourceLocation TEX_BIG =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/skill_frame_big.png");
    private static final ResourceLocation TEX_SMALL =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/skill_frame_small.png");
    private static final ResourceLocation TEX_HIGHLIGHT =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/skill_highlight.png");
    private static final ResourceLocation TEX_KEY =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/key_hint.png");

    private static final int BIG_W = 64, BIG_H = 64;
    private static final int SMALL_W = 44, SMALL_H = 44;
    private static final int BIG_ICON_X = 9, BIG_ICON_Y = 9, BIG_ICON_SZ = 46;
    private static final int SMALL_ICON_X = 6, SMALL_ICON_Y = 6, SMALL_ICON_SZ = 32;
    private static final int HIGHLIGHT_X = 7, HIGHLIGHT_Y = 7, HIGHLIGHT_SZ = 32;

    // 按键提示
    private static final int KEY_W = 16, KEY_H = 16;
    private static final int KEY_TEXT_X = 5, KEY_TEXT_Y = 3;
    private static final int KEY_GAP = 4;

    private static final int SMALL_GAP = 4;
    private static final int BIG_TO_SMALL_GAP = 8;

    // 总尺寸：左侧按键 + 大槽 + 间距 + 3小槽 + 小槽上方按键高度
    private static final int MAIN_Y = KEY_H + KEY_GAP; // 主行起始Y（留给小槽上方的键位）
    private static final int BIG_X = KEY_W + KEY_GAP;
    private static final int SMALL_X = BIG_X + BIG_W + BIG_TO_SMALL_GAP;
    private static final int TOTAL_W = SMALL_X + SMALL_W * 3 + SMALL_GAP * 2;
    private static final int TOTAL_H = MAIN_Y + Math.max(BIG_H, SMALL_H);

    private static final int COOLDOWN_OVERLAY = 0xAA444444;
    private static final int COOLDOWN_TEXT    = 0xFFFFFFFF;

    public static int selectedSmall = 0;

    public SkillHud() {
        super("skill_bar", 200, 200, 0.8f);
    }

    @Override public int getLogicalWidth()  { return TOTAL_W; }
    @Override public int getLogicalHeight() { return TOTAL_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        SkillHudData.Slots slots;
        if (editMode) {
            slots = new SkillHudData.Slots(
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(Items.BOW),
                new ItemStack(Items.IRON_SHOVEL),
                1, 1, 1, 1, 0L, 0L, 0L, 0L);
        } else {
            slots = SkillHudData.read();
        }
        if (slots == null || slots.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;

        int bigY = MAIN_Y + (TOTAL_H - MAIN_Y - BIG_H) / 2;

        // 大槽按键提示（左侧）
        renderKeyHint(g, SkillKeyMappings.BIG, 0, bigY + BIG_H / 2 - KEY_H / 2);
        // 大槽（含充能/冷却）
        renderBigSlot(g, slots.big(), BIG_X, bigY, gameTime,
            slots.charges(0), slots.isRecharging(0, gameTime),
            slots.rechargeRemaining(0, gameTime));

        // 小槽
        int smallY = MAIN_Y + (TOTAL_H - MAIN_Y - SMALL_H) / 2;
        ItemStack[] smallItems = { slots.s0(), slots.s1(), slots.s2() };
        for (int i = 0; i < 3; i++) {
            int sx = SMALL_X + i * (SMALL_W + SMALL_GAP);
            // 选中槽按键提示（上方）
            if (i == selectedSmall) {
                renderKeyHint(g, SkillKeyMappings.SKILL, sx + SMALL_W / 2 - KEY_W / 2, 0);
            }
            renderSmallSlot(g, smallItems[i], sx, smallY, i, gameTime,
                slots.charges(i + 1), slots.isRecharging(i + 1, gameTime),
                slots.rechargeRemaining(i + 1, gameTime));
        }
    }

    private void renderKeyHint(GuiGraphics g, net.minecraft.client.KeyMapping key, int x, int y) {
        if (key.isUnbound()) return;
        g.blit(TEX_KEY, x, y, 0, 0, KEY_W, KEY_H, KEY_W, KEY_H);
        String name = key.getTranslatedKeyMessage().getString();
        g.drawString(Minecraft.getInstance().font, name,
            x + KEY_TEXT_X, y + KEY_TEXT_Y, 0xFFFFFFFF);
    }

    private void renderBigSlot(GuiGraphics g, ItemStack item, int x, int y,
                                long gameTime, int charges,
                                boolean recharging, int remainTicks) {
        g.blit(TEX_BIG, x, y, 0, 0, BIG_W, BIG_H, BIG_W, BIG_H);
        if (item.isEmpty()) return;
        float scale = BIG_ICON_SZ / 16f;
        int cx = x + BIG_ICON_X + BIG_ICON_SZ / 2;
        int cy = y + BIG_ICON_Y + BIG_ICON_SZ / 2;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(item, -8, -8);
        g.pose().popPose();

        // 冷却中灰显+倒计时
        if (recharging) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + BIG_ICON_X, y + BIG_ICON_Y,
                   x + BIG_ICON_X + BIG_ICON_SZ, y + BIG_ICON_Y + BIG_ICON_SZ,
                   COOLDOWN_OVERLAY);
            int sec = (int) Math.min(9999L, (remainTicks + 19L) / 20L);
            g.drawCenteredString(Minecraft.getInstance().font, String.valueOf(sec),
                x + BIG_ICON_X + BIG_ICON_SZ / 2,
                y + BIG_ICON_Y + BIG_ICON_SZ / 2 - 4,
                COOLDOWN_TEXT);
            g.pose().popPose();
        }

        // 充能数（>1时显示x/max）
        if (charges > 0) {
            var player = Minecraft.getInstance().player;
            int max = net.minecraft.client.yiz.handler.SkillChargeManager.maxChargesOf(item, 0, player);
            String label = max > 1 ? charges + "/" + max : "";
            if (!label.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                g.drawString(Minecraft.getInstance().font, label,
                    x + BIG_W - Minecraft.getInstance().font.width(label) - 4, y + BIG_H - 12, COOLDOWN_TEXT);
                g.pose().popPose();
            }
        }
    }

    private void renderSmallSlot(GuiGraphics g, ItemStack item, int x, int y,
                                  int index, long gameTime,
                                  int charges, boolean recharging, int remainTicks) {
        boolean selected = index == selectedSmall;
        ResourceLocation bg = selected ? TEX_HIGHLIGHT : TEX_SMALL;
        int iconX = selected ? HIGHLIGHT_X : SMALL_ICON_X;
        int iconY = selected ? HIGHLIGHT_Y : SMALL_ICON_Y;
        int iconSz = selected ? HIGHLIGHT_SZ : SMALL_ICON_SZ;

        g.blit(bg, x, y, 0, 0, SMALL_W, SMALL_H, SMALL_W, SMALL_H);
        if (item.isEmpty()) return;

        float scale = iconSz / 16f;
        int cx = x + iconX + iconSz / 2;
        int cy = y + iconY + iconSz / 2;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(item, -8, -8);
        g.pose().popPose();

        if (recharging) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + SMALL_ICON_X, y + SMALL_ICON_Y,
                   x + SMALL_ICON_X + SMALL_ICON_SZ, y + SMALL_ICON_Y + SMALL_ICON_SZ,
                   COOLDOWN_OVERLAY);
            int sec = (int) Math.min(9999L, (remainTicks + 19L) / 20L);
            g.drawCenteredString(Minecraft.getInstance().font, String.valueOf(sec),
                x + SMALL_ICON_X + SMALL_ICON_SZ / 2,
                y + SMALL_ICON_Y + SMALL_ICON_SZ / 2 - 4,
                COOLDOWN_TEXT);
            g.pose().popPose();
        }

        if (charges > 0) {
            var player = Minecraft.getInstance().player;
            int slotIdx = index + 1;
            ItemStack slotItem = ItemStack.EMPTY;
            if (player != null) {
                var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
                if (data != null) slotItem = data.skillLoad().getItem(index);
            }
            int max = net.minecraft.client.yiz.handler.SkillChargeManager.maxChargesOf(slotItem, slotIdx, player);
            String label = max > 1 ? charges + "/" + max : String.valueOf(charges);
            g.pose().pushPose();
            g.pose().translate(0, 0, 250);
            g.drawString(Minecraft.getInstance().font, label,
                x + SMALL_W - Minecraft.getInstance().font.width(label) - 2, y + SMALL_H - 12, COOLDOWN_TEXT);
            g.pose().popPose();
        }
    }
}
