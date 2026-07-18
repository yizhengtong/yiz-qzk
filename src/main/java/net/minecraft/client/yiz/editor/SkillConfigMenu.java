package net.minecraft.client.yiz.editor;

import com.google.gson.JsonObject;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 技能配置界面 Menu（v2: 430×195 新布局）。
 *
 * <pre>
 * slot  0:    B 技能升级槽(30×30) — 放入技能物品展示可强化效果
 * slot  1-27: D 玩家背包 9×3     — (156,115)~(317,168) 标准18×18
 * slot 28-36: D 玩家快捷栏 9×1   — (156,173)~(317,190) 标准18×18
 * slot 37:    F 大装载槽(36×36)  — 最强技能 (332,81)~(367,116)
 * slot 38-40: F 技能装载槽 ×3    — (368,81)~(421,98) 标准18×18
 * slot 41-43: F 被动装载槽 ×3    — (368,99)~(421,116) 标准18×18
 * slot 44-63: G 技能库 5×4=20    — (332,122)~(421,191) 标准18×18
 * </pre>
 *
 * <p>A 预览槽 / B 加强槽×6(14×14) / C +/- / E 信息栏 均为纯 UI，不在此 Menu 中。</p>
 */
public class SkillConfigMenu extends AbstractContainerMenu {

    private final Container skillUpgrade;
    private final Container bigLoad;
    private final Container skillLoad;
    private final Container passiveLoad;
    private final Container skillLibrary;

    /** 打开界面时各装载槽的物品快照（服务端），用于 removed() 对比检测卸载并清理 transient 效果。 */
    private final ItemStack[] loadSnapshot;

    // ── 客户端构造 ────────────────────────────────────────────
    public SkillConfigMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv,
            new SimpleContainer(1), new SimpleContainer(1),
            new SimpleContainer(3), new SimpleContainer(3),
            new SimpleContainer(20));
    }

    // ── 服务端构造 ────────────────────────────────────────────
    public SkillConfigMenu(int containerId, Inventory playerInv,
                           Container skillUpgrade, Container bigLoad,
                           Container skillLoad, Container passiveLoad,
                           Container skillLibrary) {
        super(SkillConfigRegistries.SKILL_CONFIG_MENU.get(), containerId);
        this.skillUpgrade = skillUpgrade;
        this.bigLoad = bigLoad;
        this.skillLoad = skillLoad;
        this.passiveLoad = passiveLoad;
        this.skillLibrary = skillLibrary;

        // B: 技能升级槽 18×18，左上 (182,36)
        this.addSlot(new Slot(skillUpgrade, 0, 182, 36));

        // D: 玩家背包 9×3，起始 (156,115)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                    156 + col * 18, 115 + row * 18));

        // D: 玩家快捷栏 9×1，起始 (156,173)
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col, 156 + col * 18, 173));

        // F: 大装载槽 18×18（仅 ISkillItem）
        this.addSlot(new FilteredSlot(bigLoad, 0, 341, 91,
            net.minecraft.client.yiz.api.ISkillItem.class));

        // F: 技能装载槽 ×3（仅 ISkillItem）
        for (int i = 0; i < 3; i++)
            this.addSlot(new FilteredSlot(skillLoad, i, 368 + i * 18, 81,
                net.minecraft.client.yiz.api.ISkillItem.class));

        // F: 被动装载槽 ×3（仅 IPassiveItem）
        for (int i = 0; i < 3; i++)
            this.addSlot(new FilteredSlot(passiveLoad, i, 368 + i * 18, 99,
                net.minecraft.client.yiz.api.IPassiveItem.class));

        // G: 技能库 5×4=20（无限制）
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 5; col++)
                this.addSlot(new Slot(skillLibrary, row * 5 + col,
                    332 + col * 18, 122 + row * 18));

        // 快照装载槽初始物品（服务端），供 removed() 检测哪些技能/被动被卸载。
        // 布局：[0]=bigLoad, [1..3]=skillLoad, [4..6]=passiveLoad
        this.loadSnapshot = new ItemStack[7];
        this.loadSnapshot[0] = bigLoad.getItem(0).copy();
        for (int i = 0; i < 3; i++) this.loadSnapshot[1 + i] = skillLoad.getItem(i).copy();
        for (int i = 0; i < 3; i++) this.loadSnapshot[4 + i] = passiveLoad.getItem(i).copy();
    }

    // ── Shift 点击 ────────────────────────────────────────────

    private static final int SLOT_B        = 0;
    private static final int INV_START     = 1;   // 背包+快捷栏
    private static final int INV_END       = 36;
    private static final int SLOT_BIG_LOAD = 37;
    private static final int LOAD_START    = 38;  // 技能+被动装载
    private static final int LOAD_END      = 43;
    private static final int LIB_START     = 44;  // 技能库
    private static final int LIB_END       = 63;
    private static final int TOTAL_SLOTS   = 64;

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack src = slot.getItem();
        ItemStack copy = src.copy();

        // 非玩家背包 → 玩家背包
        if (index == SLOT_B || index >= SLOT_BIG_LOAD) {
            if (!this.moveItemStackTo(src, INV_START, INV_END + 1, true))
                return ItemStack.EMPTY;
        }
        // 玩家背包 → 优先 B 升级槽，然后大装载槽，然后装载槽，然后技能库
        else {
            if (!this.moveItemStackTo(src, SLOT_B, SLOT_B + 1, false)
                && !this.moveItemStackTo(src, SLOT_BIG_LOAD, SLOT_BIG_LOAD + 1, false)
                && !this.moveItemStackTo(src, LOAD_START, LOAD_END + 1, false)
                && !this.moveItemStackTo(src, LIB_START, LIB_END + 1, false))
                return ItemStack.EMPTY;
        }

        if (src.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, src);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return true; }

    /** 关闭时：检测被卸载的技能/被动并清理其 transient 效果，保存容器 + 同步装载槽。 */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            cleanupUnequippedSources(player);
            SkillConfigStorage.Data data = SkillConfigStorage.get(player.getUUID());
            if (data != null) {
                SkillConfigStorage.saveToPlayerData(player, data);
            }
            syncLoadSlots(player);
        }
    }

    /**
     * 对比打开时的装载槽快照与当前槽位，对被换走（卸载）的技能/被动，
     * 通过 EffectRegistry 清理其产生的全部 transient 效果。
     * <p>布局：loadSnapshot[0]=bigLoad, [1..3]=skillLoad, [4..6]=passiveLoad。
     * 被动卸载时额外清它对外提供的每个标签来源。</p>
     */
    private void cleanupUnequippedSources(Player player) {
        net.minecraft.world.entity.player.Player p = (net.minecraft.world.entity.player.Player) player;
        // 当前装载槽物品
        ItemStack[] now = new ItemStack[7];
        now[0] = bigLoad.getItem(0);
        for (int i = 0; i < 3; i++) now[1 + i] = skillLoad.getItem(i);
        for (int i = 0; i < 3; i++) now[4 + i] = passiveLoad.getItem(i);

        for (int i = 0; i < 7; i++) {
            ItemStack before = loadSnapshot[i];
            ItemStack current = now[i];
            if (before.isEmpty()) continue;
            // 物品类型变了（被换走/拿走）→ 清理旧物品的来源
            if (!ItemStack.isSameItem(before, current)) {
                String regName = net.minecraft.client.yiz.api.EffectSources.regNameOf(before);
                if (regName.isEmpty()) continue;
                boolean isPassive = i >= 4;
                String sourceKey = (isPassive ? "passive:" : "skill:") + regName;
                net.minecraft.client.yiz.api.EffectRegistry.clearSource(p.getUUID(), sourceKey);
                // 被动卸载：额外清它提供的每个标签来源（强化槽里激活的这些标签效果）
                if (isPassive && before.getItem() instanceof net.minecraft.client.yiz.api.IPassiveItem pi) {
                    for (String tagKey : pi.getProvidedTags(before)) {
                        net.minecraft.client.yiz.api.EffectRegistry.clearSource(p.getUUID(), "tag:" + tagKey);
                    }
                }
            }
        }
    }

    private static final String LOAD_SLOTS_KEY = "yizmodqzk:load_slots";

    private void syncLoadSlots(Player player) {
        net.minecraft.core.RegistryAccess registry = player.registryAccess();
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("big", serializeItem(bigLoad.getItem(0), registry));
        json.addProperty("s0", serializeItem(skillLoad.getItem(0), registry));
        json.addProperty("s1", serializeItem(skillLoad.getItem(1), registry));
        json.addProperty("s2", serializeItem(skillLoad.getItem(2), registry));
        PlayerDataAPI.set(player, LOAD_SLOTS_KEY, json.toString());
    }

    private static String serializeItem(ItemStack stack, net.minecraft.core.RegistryAccess registry) {
        if (stack.isEmpty()) return "";
        return stack.save(registry).toString();
    }

    public static SkillConfigMenu createClientMenu(int containerId, Inventory playerInv) {
        return new SkillConfigMenu(containerId, playerInv);
    }
}
