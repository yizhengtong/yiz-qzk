package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.handler.SkillChargeManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 技能信息 HUD — 大槽 + 当前选中小槽的详情。
 * <p>第1排：图标+名称 | 第2排：蓝耗 | 第3排：冷却/状态</p>
 */
public class SkillInfoHud extends HudElement {

    private static final int ICON_SZ = 20;
    private static final int LINE_H = 10;
    private static final int COL_W = 120;

    public SkillInfoHud() {
        super("skill_info", 200, 430, 1.0f);
    }

    @Override public int getLogicalWidth()  { return COL_W; }
    @Override public int getLogicalHeight() { return 8 * LINE_H + ICON_SZ; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(mc.player.getUUID());
        if (data == null) return;

        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;

        int y = 0;
        // ── 大槽 ──
        ItemStack big = data.bigLoad().getItem(0);
        if (!big.isEmpty()) {
            String key = getKeyName(net.minecraft.client.yiz.handler.SkillKeyMappings.BIG);
            y = renderSlotInfo(g, mc, big, 0, gameTime, key + "键·" + big.getHoverName().getString(), y);
            y += 2;
        }

        // ── 选中小槽 ──
        int sel = SkillHud.selectedSmall;
        ItemStack small = data.skillLoad().getItem(sel);
        if (!small.isEmpty()) {
            String key = getKeyName(net.minecraft.client.yiz.handler.SkillKeyMappings.SKILL);
            renderSlotInfo(g, mc, small, sel + 1, gameTime, key + "键·" + small.getHoverName().getString(), y);
        }
    }

    private int renderSlotInfo(GuiGraphics g, Minecraft mc, ItemStack item, int slot,
                                long gameTime, String label, int y) {
        // 名称从 label 读取，图标用 item
        // Row 1: icon + label
        g.pose().pushPose();
        float scale = ICON_SZ / 16f;
        g.pose().translate(ICON_SZ / 2f, y + ICON_SZ / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(item, -8, -8);
        g.pose().popPose();
        g.drawString(mc.font, label + " " + item.getHoverName().getString(),
            ICON_SZ + 4, y + (ICON_SZ - mc.font.lineHeight) / 2, 0xFF55FFFF);
        y += ICON_SZ + 2;

        // Row 2: 蓝耗
        float manaCost = readAttr(item, YizAttributes.MANA_COST);
        float manaPerSec = readAttr(item, YizAttributes.MANA_COST_PER_SEC);
        String costText;
        if (manaPerSec > 0) {
            costText = "§7蓝耗: §9" + manaPerSec + " /s";
        } else if (manaCost > 0) {
            costText = "§7蓝耗: §9" + (int) manaCost;
        } else {
            costText = "§7蓝耗: §8无";
        }
        g.drawString(mc.font, costText, 4, y, 0xFFFFFFFF);
        y += LINE_H;

        // Row 3: 冷却/状态
        String statusText;
        if (manaPerSec > 0) {
            boolean active = net.minecraft.client.yiz.handler.ToggleSkillState.isActive(mc.player);
            statusText = active ? "§7状态: §9开启中" : "§7状态: §8关闭";
        } else {
            int charges = SkillChargeManager.getCharges(mc.player, slot);
            int max = SkillChargeManager.maxChargesOf(item, slot, mc.player);
            long rechargeEnd = SkillChargeManager.getRechargeEnd(mc.player, slot);
            boolean recharging = rechargeEnd > gameTime && charges < max;
            if (recharging) {
                int remain = (int) Math.max(0, rechargeEnd - gameTime);
                statusText = "§7冷却: §9" + String.format("%.1f", remain / 20f) + "s";
            } else if (charges > 0) {
                statusText = "§7可用: §9" + charges + (max > 1 ? "/" + max : "");
            } else {
                statusText = "§7可用: §90";
            }
        }
        g.drawString(mc.font, statusText, 4, y, 0xFFFFFFFF);
        y += LINE_H;

        // Row 4: 伤害（实时计算，同物品面板）
        double sp = YizAttributes.getEffectiveSpellPower(mc.player);
        float base = readAttr(item, YizAttributes.DAMAGE_BASE);
        float coeff = readAttr(item, YizAttributes.DAMAGE_SPELL_COEFF);
        int dmg = (int)(base + sp * coeff / 100.0);
        if (dmg > 0) {
            g.drawString(mc.font, "§7伤害: §9" + dmg, 4, y, 0xFFFFFFFF);
        }
        y += LINE_H;

        return y;
    }

    private static String getKeyName(net.minecraft.client.KeyMapping key) {
        if (key.isUnbound()) return "?";
        return key.getTranslatedKeyMessage().getString();
    }

    private static float readAttr(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        var mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double val = 0;
        for (var e : mods.modifiers()) {
            if (e.attribute().is(attr)) val += e.modifier().amount();
        }
        return (float) val;
    }
}
