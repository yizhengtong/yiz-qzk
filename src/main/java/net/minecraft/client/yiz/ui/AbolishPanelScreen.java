package net.minecraft.client.yiz.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.core.AbolitionStateManager;
import net.minecraft.client.yiz.core.StartupAbolishConfig;
import net.minecraft.client.yiz.tool.abolish.ItemAbolitionHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/**
 * CTRL+B 打开的物品废除面板。
 *
 * <p>上方模组标签页 → 点击切换 → 下方显示该模组所有物品（带图标）→ 点击切换废除</p>
 * <p>右侧：护甲废除开关</p>
 */
public class AbolishPanelScreen extends Screen {

    private static final int MARGIN = 10;
    private static final int TAB_HEIGHT = 22;
    private static final int TAB_PAD = 6;
    private static final int RIGHT_W = 140;
    private static final int ITEM_SIZE = 20;  // 物品图标大小
    private static final int ITEM_GAP = 4;    // 图标间距
    private static final int ITEM_STEP = ITEM_SIZE + ITEM_GAP;
    private static final int ROW_HEIGHT = ITEM_SIZE + 6;
    private static final int BOTTOM_BAR = 22;
    private static final int SEARCH_H = 16;

    // 状态
    private EditBox searchBox;
    private String selectedMod = "";    // "" = 全部
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // 数据
    private List<String> modTabs;               // 排好序的模组 ID 列表
    private Map<String, List<ItemEntry>> modItems;
    private List<ItemEntry> currentItems;       // 当前选中的模组的物品列表
    private int contentLeft, contentTop, contentW, contentH;
    private int rightLeft;

    // 护甲按钮
    private Button armorBtn;

    public AbolishPanelScreen() {
        super(Component.literal("物品废除"));
    }

    // ══════════════════════════════════════════════════════════
    //  数据
    // ══════════════════════════════════════════════════════════

    private record ItemEntry(ResourceLocation id, Item item, String displayName) {}

    private void buildData() {
        modItems = new LinkedHashMap<>();
        String search = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == null) continue;
            String modId = id.getNamespace();
            String displayName = item.getDescription().getString();

            if (!search.isEmpty() && !displayName.toLowerCase().contains(search)
                    && !id.getPath().contains(search)) continue;

            modItems.computeIfAbsent(modId, k -> new ArrayList<>())
                    .add(new ItemEntry(id, item, displayName));
        }

        // 模组排序
        modTabs = modItems.keySet().stream()
                .sorted((a, b) -> {
                    if (a.equals("yizmodqzk")) return -1;
                    if (b.equals("yizmodqzk")) return 1;
                    if (a.equals("minecraft")) return 1;
                    if (b.equals("minecraft")) return -1;
                    return a.compareTo(b);
                })
                .collect(Collectors.toList());

        // 物品排序
        for (var entry : modItems.entrySet()) {
            entry.getValue().sort(Comparator.comparing(e -> e.id().getPath()));
        }

        // 如果当前选中的模组不存在了，切回全部
        if (!selectedMod.isEmpty() && !modTabs.contains(selectedMod)) {
            selectedMod = "";
        }

        updateCurrentItems();
    }

    private void updateCurrentItems() {
        if (selectedMod.isEmpty() || !modItems.containsKey(selectedMod)) {
            currentItems = modItems.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        } else {
            currentItems = modItems.get(selectedMod);
        }
        updateScroll();
    }

    private void updateScroll() {
        int cols = Math.max(1, (contentW - 8) / ITEM_STEP);
        int rows = (currentItems.size() + cols - 1) / cols;
        int totalH = rows * ROW_HEIGHT;
        maxScroll = Math.max(0, totalH - contentH + 4);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    // ══════════════════════════════════════════════════════════
    //  Init
    // ══════════════════════════════════════════════════════════

    @Override
    protected void init() {
        super.init();
        contentLeft = MARGIN;
        contentTop = MARGIN + TAB_HEIGHT + SEARCH_H + 6;
        contentW = width - MARGIN * 2 - RIGHT_W - 6;
        contentH = height - contentTop - BOTTOM_BAR - MARGIN;
        rightLeft = contentLeft + contentW + 6;

        // 搜索
        searchBox = new EditBox(font, contentLeft + 2, MARGIN + TAB_HEIGHT + 2,
                contentW - 4, SEARCH_H, Component.literal("搜索"));
        searchBox.setResponder(s -> { buildData(); });
        addRenderableWidget(searchBox);

        // 护甲按钮
        updateArmorBtn();
        addRenderableWidget(armorBtn);

        buildData();
    }

    private void updateArmorBtn() {
        if (armorBtn != null) removeWidget(armorBtn);
        boolean on = AbolitionStateManager.isArmorAbolished();
        armorBtn = Button.builder(
                Component.literal(on ? "§c▓ 护甲已废除" : "§a护甲正常"),
                btn -> {
                    AbolitionStateManager.toggleArmorAbolished();
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        if (AbolitionStateManager.isArmorAbolished())
                            net.minecraft.client.yiz.tool.abolish.InventoryDefenseAbolisher.abolishPlayerDefense(mc.player);
                        else
                            net.minecraft.client.yiz.tool.abolish.InventoryDefenseAbolisher.restorePlayerDefense(mc.player);
                    }
                    updateArmorBtn();
                })
                .bounds(rightLeft + 8, MARGIN + 30, RIGHT_W - 16, 20)
                .build();
    }

    // ══════════════════════════════════════════════════════════
    //  背景（禁用模糊）
    // ══════════════════════════════════════════════════════════

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 不调 super.renderBackground，直接画黑色半透明背景代替模糊
        graphics.fill(0, 0, width, height, 0x88000000);
    }

    // ══════════════════════════════════════════════════════════
    //  渲染
    // ══════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);

        // ── 主面板背景 ──
        g.fill(MARGIN, MARGIN, width - MARGIN, height - MARGIN, 0xCC111111);

        // ── 模组标签页 ──
        int tabY = MARGIN;
        int tabX = MARGIN + 2;
        for (String mod : modTabs) {
            String label = mod.equals("yizmodqzk") ? "§e" + mod : (mod.equals("minecraft") ? "§7原版" : "§f" + mod);
            int w = font.width(mod) + TAB_PAD * 2;
            boolean sel = mod.equals(selectedMod) || (selectedMod.isEmpty() && mod.equals(modTabs.get(0)));
            // 但初始 selectedMod="" 时默认全选，不在标签页中
            boolean active = mod.equals(selectedMod);
            int bg = active ? 0xFF444444 : 0xFF222222;
            g.fill(tabX, tabY, tabX + w, tabY + TAB_HEIGHT, bg);
            g.drawString(font, label, tabX + TAB_PAD, tabY + (TAB_HEIGHT - font.lineHeight) / 2 + 1, 0xCCCCCC);
            tabX += w + 2;
        }

        // ── 搜索框（已在 EditBox 中渲染） ──

        // ── 物品格子 ──
        int cx = contentLeft + 4;
        int cy = contentTop - scrollOffset;
        int cols = Math.max(1, (contentW - 8) / ITEM_STEP);
        int idx = 0;

        g.enableScissor(contentLeft, contentTop, contentLeft + contentW, contentTop + contentH);

        for (ItemEntry entry : currentItems) {
            int col = idx % cols;
            int row = idx / cols;
            int ix = cx + col * ITEM_STEP;
            int iy = cy + row * ROW_HEIGHT;

            if (iy + ITEM_SIZE < contentTop) { idx++; continue; }
            if (iy > contentTop + contentH) break;

            boolean abolished = AbolitionStateManager.isItemAbolished(entry.id());
            boolean startupAbolished = StartupAbolishConfig.isAbolished(entry.id().toString());
            boolean hover = mx >= ix && mx <= ix + ITEM_SIZE && my >= iy && my <= iy + ITEM_SIZE;

            // 背景 — 双重废除用混合色
            int slotBg;
            if (abolished && startupAbolished) slotBg = 0x44FF44FF; // 品红色：两套都已废除
            else if (abolished) slotBg = 0x44FF4444;                 // 红色：运行时废除
            else if (startupAbolished) slotBg = 0x444444FF;           // 蓝色：启动黑名单
            else if (hover) slotBg = 0x44FFFFFF;
            else slotBg = 0x33000000;
            g.fill(ix, iy, ix + ITEM_SIZE, iy + ITEM_SIZE, slotBg);

            // 物品图标（渲染在格子中间）
            var stack = new ItemStack(entry.item());
            g.renderFakeItem(stack, ix + 2, iy + 2);

            // 废除标记（右上角红点 = 运行时废除）
            if (abolished) {
                g.fill(ix + ITEM_SIZE - 5, iy, ix + ITEM_SIZE, iy + 5, 0xFFFF0000);
            }
            // 启动黑名单标记（左下角蓝点 = 下次重启生效）
            if (startupAbolished) {
                g.fill(ix, iy + ITEM_SIZE - 5, ix + 5, iy + ITEM_SIZE, 0xFF4488FF);
            }

            idx++;
        }

        g.disableScissor();

        // ── 右侧：护甲废除 ──
        g.drawString(font, "§l护甲废除", rightLeft + 8, MARGIN + 8, 0xFFFFFF);
        g.drawString(font, "§7启用后穿戴的护甲", rightLeft + 8, MARGIN + 55, 0x888888);
        g.drawString(font, "§7不提供任何防御", rightLeft + 8, MARGIN + 66, 0x888888);

        // ── 底栏 ──
        int botY = height - MARGIN - BOTTOM_BAR;
        g.fill(MARGIN, botY, width - MARGIN, botY + BOTTOM_BAR, 0xCC222222);
        int cnt = AbolitionStateManager.getAbolishedItems().size();
        int scnt = StartupAbolishConfig.size();
        String armorS = AbolitionStateManager.isArmorAbolished() ? "§c护甲已废除" : "§a护甲正常";
        String startupS = scnt > 0 ? "  |  §b启动黑名单: " + scnt + " §7(重启生效)" : "";
        g.drawString(font, "§7运行时废除 " + cnt + " 个物品  |  " + armorS + startupS,
                MARGIN + 6, botY + 5, 0xAAAAAA);

        // ── 按钮 ──
        super.render(g, mx, my, pt);
    }

    // ══════════════════════════════════════════════════════════
    //  鼠标
    // ══════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 右键 = 切换启动黑名单
        if (btn == GLFW.GLFW_MOUSE_BUTTON_2) {
            return rightClickItem(mx, my);
        }
        if (btn != GLFW.GLFW_MOUSE_BUTTON_1) return super.mouseClicked(mx, my, btn);

        // 模组标签页点击
        int tabX = MARGIN + 2;
        int tabY = MARGIN;
        for (String mod : modTabs) {
            String label = mod.equals("yizmodqzk") ? "§e" + mod : (mod.equals("minecraft") ? "§7原版" : "§f" + mod);
            int w = font.width(mod) + TAB_PAD * 2;
            if (mx >= tabX && mx <= tabX + w && my >= tabY && my <= tabY + TAB_HEIGHT) {
                selectedMod = selectedMod.equals(mod) ? "" : mod;  // 再次点击取消选择
                updateCurrentItems();
                return true;
            }
            tabX += w + 2;
        }

        // 物品格子点击
        int cx = contentLeft + 4;
        int cy = contentTop - scrollOffset;
        int cols = Math.max(1, (contentW - 8) / ITEM_STEP);
        int idx = 0;

        for (ItemEntry entry : currentItems) {
            int col = idx % cols;
            int row = idx / cols;
            int ix = cx + col * ITEM_STEP;
            int iy = cy + row * ROW_HEIGHT;
            if (mx >= ix && mx <= ix + ITEM_SIZE && my >= iy && my <= iy + ITEM_SIZE) {
                toggleItem(entry);
                return true;
            }
            idx++;
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void toggleItem(ItemEntry entry) {
        var mc = Minecraft.getInstance();
        if (AbolitionStateManager.isItemAbolished(entry.id())) {
            ItemAbolitionHelper.restoreItemById(entry.id());
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("§a已恢复: " + entry.displayName()), true);
        } else {
            ItemAbolitionHelper.abolishItemById(entry.id());
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("§c已废除: " + entry.displayName()), true);
        }
    }

    /** 右键 — 切换启动黑名单（写文件，重启生效） */
    private boolean rightClickItem(double mx, double my) {
        int cx = contentLeft + 4;
        int cy = contentTop - scrollOffset;
        int cols = Math.max(1, (contentW - 8) / ITEM_STEP);
        int idx = 0;

        for (ItemEntry entry : currentItems) {
            int col = idx % cols;
            int row = idx / cols;
            int ix = cx + col * ITEM_STEP;
            int iy = cy + row * ROW_HEIGHT;
            if (mx >= ix && mx <= ix + ITEM_SIZE && my >= iy && my <= iy + ITEM_SIZE) {
                toggleStartupAbolish(entry);
                return true;
            }
            idx++;
        }
        return false;
    }

    private void toggleStartupAbolish(ItemEntry entry) {
        var mc = Minecraft.getInstance();
        String idStr = entry.id().toString();
        if (StartupAbolishConfig.isAbolished(idStr)) {
            StartupAbolishConfig.remove(idStr);
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("§7已从启动黑名单移除: §f" + entry.displayName()), true);
        } else {
            StartupAbolishConfig.add(idStr);
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("§b已加入启动黑名单: §f" + entry.displayName()
                        + "  §7(重启生效)"), true);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  滚轮
    // ══════════════════════════════════════════════════════════

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) {
            scrollOffset = Math.clamp(scrollOffset - (int)(sy * 30), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ══════════════════════════════════════════════════════════
    //  键盘
    // ══════════════════════════════════════════════════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
