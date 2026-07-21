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
    private final Container equipment;
    private final net.minecraft.world.ContainerListener equipListener;

    /** 打开界面时各装载槽的物品快照（服务端），用于 removed() 对比检测卸载并清理 transient 效果。 */
    private final ItemStack[] loadSnapshot;

    // ── 客户端构造 ────────────────────────────────────────────
    public SkillConfigMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv,
            new SimpleContainer(1), new SimpleContainer(1),
            new SimpleContainer(3), new SimpleContainer(3),
            new SimpleContainer(20), new SimpleContainer(6));
    }

    // ── 服务端构造 ────────────────────────────────────────────
    public SkillConfigMenu(int containerId, Inventory playerInv,
                           Container skillUpgrade, Container bigLoad,
                           Container skillLoad, Container passiveLoad,
                           Container skillLibrary, Container equipment) {
        super(SkillConfigRegistries.SKILL_CONFIG_MENU.get(), containerId);
        this.skillUpgrade = skillUpgrade;
        this.bigLoad = bigLoad;
        this.skillLoad = skillLoad;
        this.passiveLoad = passiveLoad;
        this.skillLibrary = skillLibrary;
        this.equipment = equipment;
        // 装备槽实时监听：物品变动立刻刷新属性 + 重编译机制上下文
        this.equipListener = c -> {
            applyEquipmentAttributes(playerInv.player);
            net.minecraft.client.yiz.handler.SpecialGearRouter.compile(playerInv.player, equipment);
        };
        ((net.minecraft.world.SimpleContainer) equipment).addListener(equipListener);

        // B: 技能升级槽 18×18，左上 (262,84)
        this.addSlot(new Slot(skillUpgrade, 0, 262, 84));

        // D: 玩家背包 9×3，起始 (236,163)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                    236 + col * 18, 163 + row * 18));

        // D: 玩家快捷栏 9×1，起始 (236,221)
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col, 236 + col * 18, 221));

        // F: 大装载槽 18×18（仅 ISkillItem）
        this.addSlot(new FilteredSlot(bigLoad, 0, 455, 138,
            net.minecraft.client.yiz.api.ISkillItem.class));

        // F: 技能装载槽 ×3（仅 ISkillItem）
        for (int i = 0; i < 3; i++)
            this.addSlot(new FilteredSlot(skillLoad, i, 482 + i * 18, 129,
                net.minecraft.client.yiz.api.ISkillItem.class));

        // F: 被动装载槽 ×3（仅 IPassiveItem）
        for (int i = 0; i < 3; i++)
            this.addSlot(new FilteredSlot(passiveLoad, i, 482 + i * 18, 147,
                net.minecraft.client.yiz.api.IPassiveItem.class));

        // G: 技能库 5×4=20（无限制）
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 5; col++)
                this.addSlot(new Slot(skillLibrary, row * 5 + col,
                    446 + col * 18, 170 + row * 18));

        // H: 装备槽 ×6 — 默认仅 IEquipmentItem 可放入，ALLOW_ANY_ITEM=true 时全放开
        for (int i = 0; i < 6; i++) {
            final int slotIdx = i;
            this.addSlot(new Slot(equipment, i, 543, 83 + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (net.minecraft.client.yiz.api.IEquipmentItem.ALLOW_ANY_ITEM) return true;
                    return stack.getItem() instanceof net.minecraft.client.yiz.api.IEquipmentItem;
                }
            });
        }

        // 快照装载槽初始物品（服务端），供 removed() 检测哪些技能/被动被卸载。
        // 布局：[0]=bigLoad, [1..3]=skillLoad, [4..6]=passiveLoad, [7..12]=equipment
        this.loadSnapshot = new ItemStack[13];
        this.loadSnapshot[0] = bigLoad.getItem(0).copy();
        for (int i = 0; i < 3; i++) this.loadSnapshot[1 + i] = skillLoad.getItem(i).copy();
        for (int i = 0; i < 3; i++) this.loadSnapshot[4 + i] = passiveLoad.getItem(i).copy();
        for (int i = 0; i < 6; i++) this.loadSnapshot[7 + i] = equipment.getItem(i).copy();
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
    private static final int EQUIP_START   = 64;  // 装备槽
    private static final int EQUIP_END     = 69;
    private static final int TOTAL_SLOTS   = 70;

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
        // 玩家背包 → 优先 B 升级槽，然后大装载槽，然后装载槽，然后技能库，然后装备
        else {
            if (!this.moveItemStackTo(src, SLOT_B, SLOT_B + 1, false)
                && !this.moveItemStackTo(src, SLOT_BIG_LOAD, SLOT_BIG_LOAD + 1, false)
                && !this.moveItemStackTo(src, LOAD_START, LOAD_END + 1, false)
                && !this.moveItemStackTo(src, LIB_START, LIB_END + 1, false)
                && !this.moveItemStackTo(src, EQUIP_START, EQUIP_END + 1, false))
                return ItemStack.EMPTY;
        }

        if (src.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, src);
        return copy;
    }

    @Override public boolean stillValid(Player player) { return true; }

    /** 关闭时：检测被卸载的技能/被动并清理其 transient 效果，保存容器 + 同步装载槽 + 应用装备属性。 */
    @Override
    public void removed(Player player) {
        super.removed(player);
        ((net.minecraft.world.SimpleContainer) equipment).removeListener(equipListener);
        if (!player.level().isClientSide()) {
            cleanupUnequippedSources(player);
            applyEquipmentAttributes(player);
            SkillConfigStorage.Data data = SkillConfigStorage.get(player.getUUID());
            if (data != null) {
                SkillConfigStorage.saveToPlayerData(player, data);
            }
            syncLoadSlots(player);
        }
    }

    // ── 装备属性同步 ─────────────────────────────────────────

    /** 静态入口：登录时调用，从已加载的 Storage Data 中应用装备属性到玩家。 */
    public static void applyEquipmentFromStorage(Player player, SkillConfigStorage.Data data) {
        var inst = new SkillConfigMenu(0, player.getInventory(),
            data.skillUpgrade(), data.bigLoad(),
            data.skillLoad(), data.passiveLoad(),
            data.skillLibrary(), data.equipment());
        inst.applyEquipmentAttributes(player);
    }

    /** 为每个装备槽+属性生成唯一 ResourceLocation */
    private static net.minecraft.resources.ResourceLocation equipModId(int slot, net.minecraft.resources.ResourceLocation attrId) {
        return net.minecraft.resources.ResourceLocation.parse(
            "yizmodqzk:equip_" + slot + "/" + attrId.getNamespace() + "/" + attrId.getPath().replace('/', '_'));
    }

    /** 应用装备属性 → 先清全部旧修饰符，再逐物品添加。 */
    private void applyEquipmentAttributes(Player player) {
        // 收集当前装备槽涉及的所有属性 key
        var toClean = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipment.getItem(i);
            if (stack.isEmpty()) continue;
            for (var entry : stack.getAttributeModifiers().modifiers())
                toClean.add(net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value()));
        }
        // 1) 清除旧修饰符
        for (var attrKey : toClean) {
            var holder = net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(attrKey);
            if (holder.isEmpty()) continue;
            var inst = player.getAttribute(holder.get());
            if (inst == null) continue;
            for (int i = 0; i < 6; i++) {
                inst.removeModifier(equipModId(i, attrKey));
            }
        }
        // 2) 重新应用
        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipment.getItem(i);
            if (stack.isEmpty()) continue;
            // 检测是否为武器/工具：有正攻击伤害 且 有负攻击速度
            boolean isWeapon = false;
            double atkDmg = 0, atkSpd = 0;
            for (var entry : stack.getAttributeModifiers().modifiers()) {
                if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))
                    atkDmg += entry.modifier().amount();
                if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED))
                    atkSpd += entry.modifier().amount();
            }
            isWeapon = atkDmg > 0 && atkSpd < 0;
            for (var entry : stack.getAttributeModifiers().modifiers()) {
                var attr = entry.attribute();
                double amount = entry.modifier().amount();
                // 武器/工具的攻速归一化：游戏内显示值 = 4 + 原始偏移
                if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED) && isWeapon)
                    amount = 4.0 + amount;  // 例如 -2.4 → 1.6
                var inst = player.getAttribute(attr);
                if (inst == null) continue;
                var rl = equipModId(i, net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getKey(attr.value()));
                inst.removeModifier(rl);
                inst.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    rl, amount, entry.modifier().operation()));
            }
            if (stack.getItem() instanceof net.minecraft.client.yiz.api.IEquipmentItem ei)
                ei.onEquip(player, stack, i);
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
        ItemStack[] now = new ItemStack[13];
        now[0] = bigLoad.getItem(0);
        for (int i = 0; i < 3; i++) now[1 + i] = skillLoad.getItem(i);
        for (int i = 0; i < 3; i++) now[4 + i] = passiveLoad.getItem(i);
        for (int i = 0; i < 6; i++) now[7 + i] = equipment.getItem(i);

        for (int i = 0; i < 13; i++) {
            ItemStack before = loadSnapshot[i];
            ItemStack current = now[i];
            if (before.isEmpty()) continue;
            if (!ItemStack.isSameItem(before, current)) {
                String regName = net.minecraft.client.yiz.api.EffectSources.regNameOf(before);
                if (!regName.isEmpty()) {
                    boolean isPassive = i >= 4 && i < 7;
                    boolean isEquip = i >= 7;
                    String sourceKey = isEquip ? "equip:" + regName
                        : (isPassive ? "passive:" + regName : "skill:" + regName);
                    net.minecraft.client.yiz.api.EffectRegistry.clearSource(p.getUUID(), sourceKey);
                    if (isPassive && before.getItem() instanceof net.minecraft.client.yiz.api.IPassiveItem pi) {
                        for (String tagKey : pi.getProvidedTags(before)) {
                            net.minecraft.client.yiz.api.EffectRegistry.clearSource(p.getUUID(), "tag:" + tagKey);
                        }
                    }
                }
                // 装备卸载回调
                if (i >= 7 && before.getItem() instanceof net.minecraft.client.yiz.api.IEquipmentItem ei) {
                    ei.onUnequip(player, before, i - 7);
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
