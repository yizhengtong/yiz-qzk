package net.minecraft.client.yiz.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SkillConfigScreen extends AbstractContainerScreen<SkillConfigMenu> {

    private static final ResourceLocation GUI_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/skill_config.png");
    public static final int GUI_WIDTH = 630, GUI_HEIGHT = 300;
    private static final int XP_COST_PER_LEVEL = 100;

    private static final int UPGRADE_SLOT_X = 262, UPGRADE_SLOT_Y = 84;
    private static final int[][] ENHANCE_SLOTS = {{302,67},{338,67},{378,67},{302,105},{338,105},{378,105}};
    private static final int ENHANCE_SIZE = 14;
    private static final int PREVIEW_X=51,PREVIEW_Y=45,PREVIEW_W=124,PREVIEW_H=178;
    private static final int INFO_X=450,INFO_Y=85;
    private static final int CLOSE_X1=576,CLOSE_Y1=48,CLOSE_X2=589,CLOSE_Y2=61;

    private int selectedEnhance = -1;
    private int previewScroll = 0;
    private boolean upgradeSlotSelected;
    private int[] enhanceLevels = new int[6];
    private List<EnhanceEntry> enhanceEntries = List.of();
    private String skillRegName = "";

    public SkillConfigScreen(SkillConfigMenu m, Inventory inv, Component t) {
        super(m, inv, t); this.imageWidth = GUI_WIDTH; this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = this.width / 2 - 316;
    }

    private String getSkillRegName() {
        ItemStack item = getUpgradeItem();
        if (item.isEmpty()) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
    }

    /** 每次渲染从升级槽物品 NBT 读取加强等级（每物品实例独立）。 */
    private void loadEnhanceLevels() {
        ItemStack item = getUpgradeItem();
        enhanceLevels = SkillConfigStorage.getEnhanceLevels(item);
        skillRegName = getSkillRegName();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        g.blit(GUI_TEXTURE, leftPos - 1, topPos - 1, 0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight);
        loadEnhanceLevels();
        refreshEnhanceEntries();
        renderEnhanceSlots(g, mx, my);
        renderPreview(g);
        renderInfoPanel(g);
    }

    private void refreshEnhanceEntries() {
        ItemStack item = getUpgradeItem();
        var mc = Minecraft.getInstance();
        if (item.isEmpty() || !(item.getItem() instanceof net.minecraft.client.yiz.api.IEnhanceable e)) {
            enhanceEntries = List.of();
        } else {
            enhanceEntries = e.getEnhanceEntries(item, mc.player);
        }
    }

    // ── 加强槽渲染 ──────────────────────────────────────────

    private void renderEnhanceSlots(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < ENHANCE_SLOTS.length; i++) {
            int x = leftPos + ENHANCE_SLOTS[i][0], y = topPos + ENHANCE_SLOTS[i][1];
            boolean h = mx >= x && mx < x + ENHANCE_SIZE && my >= y && my < y + ENHANCE_SIZE;
            boolean sel = selectedEnhance == i;
            boolean isTag = i < enhanceEntries.size() && enhanceEntries.get(i) instanceof EnhanceEntry.Tag;
            int bg = sel ? 0x44FFFFFF : (h ? 0x33FFFFFF : 0x18FFFFFF);

            // 标签已激活 → 金色背景
            if (isTag && enhanceLevels[i] > 0) bg = sel ? 0x66FFAA00 : 0x44FFAA00;

            g.fill(x, y, x + ENHANCE_SIZE, y + ENHANCE_SIZE, bg);

            if (i < enhanceEntries.size()) {
                var entry = enhanceEntries.get(i);
                if (entry instanceof EnhanceEntry.Tag tag) {
                    // 渲染强化物品图标（-1 像素微调居中）
                    ItemStack icon = SkillEnhanceConfig.getItemFor(tag.key());
                    if (!icon.isEmpty()) {
                        g.renderItem(icon, x - 1, y - 1);
                    }
                    // 已激活标记
                    if (enhanceLevels[i] > 0) {
                        g.fill(x, y, x + ENHANCE_SIZE, y + ENHANCE_SIZE, 0x44FFAA00);
                        g.drawString(font, "✓", x + ENHANCE_SIZE - 6, y + 1, 0xFF55FF55);
                    }
                } else {
                    // 属性型：显示缩写 + 等级
                    String name = entry.displayName();
                    if (name.length() > 2) name = name.substring(0, 2);
                    g.drawString(font, name, x + 1, y + 1, 0xCCFFFFFF);
                    if (enhanceLevels[i] > 0) {
                        String lv = String.valueOf(enhanceLevels[i]);
                        g.drawString(font, lv, x + ENHANCE_SIZE - font.width(lv) - 1, y + ENHANCE_SIZE - 10, 0xFF55FF55);
                    }
                }
            }
        }
    }

    // ── 预览区（4 排格式，白+蓝配色）────────────────────────

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_BLUE  = 0xFF5555FF;
    private static final int COLOR_GRAY  = 0xFF888888;

    private void renderPreview(GuiGraphics g) {
        int x = leftPos + PREVIEW_X, y = topPos + PREVIEW_Y;
        g.fill(x, y, x + PREVIEW_W, y + PREVIEW_H, 0x33000000);

        ItemStack upgradeItem = getUpgradeItem();
        if (upgradeItem.isEmpty()) {
            g.drawString(font, "放入技能物品", x + 4, y + 4, COLOR_GRAY);
            return;
        }

        // 开启裁剪，内容不会超出面板边界
        g.enableScissor(x, y, x + PREVIEW_W, y + PREVIEW_H);

        int cy = y + 4 - previewScroll;
        int lineH = 11;
        int maxTextW = PREVIEW_W - 4;

        // ── 第1排：技能或被动名称 ──
        String itemName = upgradeItem.getHoverName().getString();
        g.drawString(font, font.plainSubstrByWidth(itemName, maxTextW), x + 2, cy, COLOR_WHITE);
        cy += lineH + 2;

        // ── 第2排：技能效果（font.split 标准换行）──
        var mc = Minecraft.getInstance();
        List<net.minecraft.network.chat.Component> tooltip = upgradeItem.getTooltipLines(
            net.minecraft.world.item.Item.TooltipContext.of(mc.level),
            mc.player,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        for (int i = 1; i < tooltip.size(); i++) {
            String text = tooltip.get(i).getString();
            if (text.isBlank()) continue;
            if (text.startsWith("在装备时") || text.startsWith("已觉醒") || text.startsWith(" ")) continue;
            var wrapped = font.split(net.minecraft.network.chat.Component.literal(text), maxTextW);
            for (var line : wrapped) {
                if (cy > y + PREVIEW_H - lineH) break;
                g.drawString(font, line, x + 2, cy, COLOR_WHITE);
                cy += lineH;
            }
        }
        cy += 2;

        // ── 第3排：已装载强化名称 ──
        boolean hasActive = false;
        for (int i = 0; i < enhanceEntries.size() && i < enhanceLevels.length; i++) {
            if (enhanceLevels[i] > 0) { hasActive = true; break; }
        }
        if (!enhanceEntries.isEmpty() && hasActive) {
            g.drawString(font, "已装载:", x + 2, cy, COLOR_GRAY);
            cy += lineH;
            for (int i = 0; i < enhanceEntries.size() && i < enhanceLevels.length; i++) {
                if (enhanceLevels[i] > 0) {
                    if (cy > y + PREVIEW_H - lineH) break;
                    g.drawString(font, font.plainSubstrByWidth(enhanceEntries.get(i).displayName(), maxTextW - 4), x + 4, cy, COLOR_BLUE);
                    cy += lineH;
                }
            }
            cy += 1;
        }

        // ── 分割线 ──
        cy += 2;
        g.fill(x + 2, cy, x + PREVIEW_W - 2, cy + 1, 0x44FFFFFF);
        cy += 4;
        g.drawString(font, "预览", x + 2, cy, COLOR_GRAY);
        cy += lineH + 1;

        // ── 第4排：当前选中强化效果预览 ──
        if (selectedEnhance >= 0 && selectedEnhance < enhanceEntries.size()) {
            var entry = enhanceEntries.get(selectedEnhance);
            if (entry instanceof EnhanceEntry.Tag tag) {
                g.drawString(font, font.plainSubstrByWidth(tag.displayName(), maxTextW), x + 2, cy, COLOR_BLUE);
                cy += lineH;
                var descWrapped = font.split(net.minecraft.network.chat.Component.literal(tag.description()), maxTextW);
                for (var line : descWrapped) {
                    if (cy > y + PREVIEW_H - lineH) break;
                    g.drawString(font, line, x + 2, cy, COLOR_WHITE);
                    cy += lineH;
                }
            } else {
                int lv = (selectedEnhance < enhanceLevels.length) ? enhanceLevels[selectedEnhance] : 0;
                g.drawString(font, font.plainSubstrByWidth(entry.displayName() + " Lv." + lv, maxTextW), x + 2, cy, COLOR_BLUE);
            }
        }

        g.disableScissor();
    }

    // ── 信息面板 ────────────────────────────────────────────

    private void renderInfoPanel(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int totalXp = C2SSkillEnhancePayload.getTotalXp(mc.player);
        int maxUpgrades = totalXp / XP_COST_PER_LEVEL;
        g.drawString(font, "经验: " + totalXp + " (" + maxUpgrades + "次)",
            leftPos + INFO_X + 4, topPos + INFO_Y + 8, 0xFF55FF55);
    }

    // ── 鼠标交互 ────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 关闭按钮
        if (isClose(mx, my)) { this.onClose(); return true; }

        // 加强槽：左键加点/激活，右键选中预览，Shift+左键取消
        for (int i = 0; i < ENHANCE_SLOTS.length; i++) {
            if (isEnh(mx, my, i)) {
                if (i >= enhanceEntries.size()) return true;
                var entry = enhanceEntries.get(i);
                boolean shift = hasShiftDown();

                if (btn == 0) {
                    if (shift) {
                        // Shift+左键：取消强化
                        if (entry instanceof EnhanceEntry.Tag) deactivateTag(i);
                        else resetEnhance(i);
                    } else {
                        // 左键：加点强化
                        if (entry instanceof EnhanceEntry.Tag) activateTag(i);
                        else onPlus(i);
                    }
                } else if (btn == 1) {
                    // 右键：选中预览
                    selectedEnhance = i;
                    upgradeSlotSelected = false;
                }
                return true;
            }
        }
        if (btn == 0 && isUpgrade(mx, my)) { upgradeSlotSelected = true; selectedEnhance = -1; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // 仅在鼠标位于预览面板区域内时滚动
        int px = leftPos + PREVIEW_X, py = topPos + PREVIEW_Y;
        if (mx >= px && mx < px + PREVIEW_W && my >= py && my < py + PREVIEW_H) {
            previewScroll = Math.max(0, previewScroll - (int) Math.signum(scrollY) * 10);
            return true;
        }
        return false;
    }

    // ── 标签激活/停用 ─────────────────────────────────────────

    private void activateTag(int i) {
        if (enhanceLevels[i] > 0) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int totalXp = C2SSkillEnhancePayload.getTotalXp(mc.player);
        if (totalXp < XP_COST_PER_LEVEL) return;
        enhanceLevels[i] = 1;
        C2SSkillEnhancePayload.send(skillRegName, i, 1);
    }

    private void deactivateTag(int i) {
        if (enhanceLevels[i] <= 0) return;
        enhanceLevels[i] = 0;
        C2SSkillEnhancePayload.send(skillRegName, i, 0);
    }

    // ── 属性强化 / 重置 ────────────────────────────────────

    private void onPlus(int i) {
        if (i < 0 || i >= enhanceEntries.size()) return;
        if (!(enhanceEntries.get(i) instanceof EnhanceEntry.Attribute)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int totalXp = C2SSkillEnhancePayload.getTotalXp(mc.player);
        if (totalXp < XP_COST_PER_LEVEL) return;
        enhanceLevels[i]++;
        C2SSkillEnhancePayload.send(skillRegName, i, enhanceLevels[i]);
    }

    private void resetEnhance(int i) {
        if (i < 0 || i >= enhanceEntries.size()) return;
        if (enhanceLevels[i] <= 0) return;
        enhanceLevels[i] = 0;
        C2SSkillEnhancePayload.send(skillRegName, i, 0);
    }

    // ── 命中检测 ────────────────────────────────────────────

    private boolean isUpgrade(double mx, double my) {
        int x = leftPos + UPGRADE_SLOT_X, y = topPos + UPGRADE_SLOT_Y;
        return mx >= x && mx < x + 18 && my >= y && my < y + 18;
    }
    private boolean isEnh(double mx, double my, int i) {
        int x = leftPos + ENHANCE_SLOTS[i][0], y = topPos + ENHANCE_SLOTS[i][1];
        return mx >= x && mx < x + ENHANCE_SIZE && my >= y && my < y + ENHANCE_SIZE;
    }
    private boolean isClose(double mx, double my) {
        return mx >= leftPos + CLOSE_X1 && mx <= leftPos + CLOSE_X2
            && my >= topPos + CLOSE_Y1 && my <= topPos + CLOSE_Y2;
    }

    // ── 工具方法 ─────────────────────────────────────────────

    private ItemStack getUpgradeItem() {
        return this.menu.slots.get(0).getItem();
    }

    // ── 渲染 ─────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
        // 加强槽 hover tooltip
        for (int i = 0; i < ENHANCE_SLOTS.length; i++) {
            if (isEnh(mx, my, i) && i < enhanceEntries.size()) {
                var entry = enhanceEntries.get(i);
                if (entry instanceof EnhanceEntry.Tag tag) {
                    String status = enhanceLevels[i] > 0 ? " [已激活]" : " [未激活]";
                    String tip = tag.displayName() + status + "\n" + tag.description()
                        + "\n左键激活/停用 | 右键预览 | Shift+左键取消";
                    g.renderTooltip(font, Component.literal(tip), mx, my);
                } else {
                    String tip = entry.displayName() + " Lv." + enhanceLevels[i]
                        + "\n左键+1 | 右键预览 | Shift+左键归零";
                    g.renderTooltip(font, Component.literal(tip), mx, my);
                }
                break;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}
}
