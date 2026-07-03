package net.minecraft.client.yiz.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.api.ISkillWeapon;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.SkillSlotEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 饰品/技能槽面板查询适配器（单例）。
 *
 * <p><b>历史</b>：本类曾是纯客户端饰品面板（自带 {@code SimpleContainer}、
 * {@code PanelMouseHandler} 点击拦截、{@code PanelItemStorage} 持久化、
 * 背包界面渲染）。该旧系统与 yizxianmod 的「单例 AccessoryContainer 注入为
 * 真正 menu slot + 服务器权威持久化」新系统冲突 —— 旧 {@code InventoryScreenMixin}
 * 在 mouseClicked HEAD 抢先拦截并取消原版处理，导致新 menu slot 收不到点击，
 * 表现为「物品图标出现但实际没存入/取出无反应」。</p>
 *
 * <p><b>现状</b>：旧交互/渲染/持久化逻辑已全部移除（拦截 mixin 已删除）。
 * 存取完全由 yizxianmod 的真 menu slot + 原版 ContainerMenu 协议 + 服务器权威
 * 持久化（{@code AccessorySlots} → PlayerDataAPI）处理。本类<b>仅保留为查询
 * 适配器</b>，让 {@code YizModQZKAPI} 的技能槽查询方法继续可用：直接从
 * PlayerDataAPI 读取 {@code yizxianmod:accessory_slots} 同步数据解析，不持有
 * 任何容器、不做任何点击/渲染/持久化。</p>
 *
 * <p>依赖方向安全：本类（前置库 yizmodqzk）只读 PlayerDataAPI 的 SNBT 字符串，
 * 不反向 import yizxianmod 的类。若 yizxianmod 未加载（key 未注册），查询静默
 * 返回空，不抛异常。</p>
 */
public class InventoryPanel {

    /** 饰品槽在 PlayerDataAPI 中的数据键（由 yizxianmod 注册并写入）。 */
    public static final String ACCESSORY_DATA_KEY = "yizxianmod:accessory_slots";

    /** 饰品槽位数量（与 yizxianmod 的 AccessorySlots.SIZE 保持一致）。 */
    public static final int SIZE = 9;

    /** 面板额外高度（保留常量供历史引用兼容）。 */
    public static final int PANEL_EXTRA = 34;
    /** 原版库存界面高度（保留常量供历史引用兼容）。 */
    public static final int ORIGINAL_HEIGHT = 166;

    private static final InventoryPanel INSTANCE = new InventoryPanel();

    /** 获取面板单例（始终非 null，兼容旧 API 契约）。 */
    public static InventoryPanel getInstance() {
        return INSTANCE;
    }

    private InventoryPanel() {}

    // ══════════════════════════════════════════════════════════════
    //  数据读取：从 PlayerDataAPI 解析当前 9 个饰品槽
    // ══════════════════════════════════════════════════════════════

    /**
     * 读取本地玩家当前 9 个饰品槽的物品（长度恒为 {@link #SIZE}，空槽为 EMPTY）。
     * 数据来自服务器同步到客户端的 PlayerDataAPI 值。
     */
    private List<ItemStack> readSlots() {
        List<ItemStack> result = new ArrayList<>(SIZE);
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            for (int i = 0; i < SIZE; i++) result.add(ItemStack.EMPTY);
            return result;
        }

        String raw;
        try {
            raw = PlayerDataAPI.get(player, ACCESSORY_DATA_KEY);
        } catch (IllegalArgumentException e) {
            // yizxianmod 未加载 → key 未注册，静默返回空槽
            for (int i = 0; i < SIZE; i++) result.add(ItemStack.EMPTY);
            return result;
        }

        if (raw == null || raw.isEmpty()) {
            for (int i = 0; i < SIZE; i++) result.add(ItemStack.EMPTY);
            return result;
        }

        try {
            CompoundTag wrapper = TagParser.parseTag(raw);
            ListTag list = wrapper.getList("Slots", Tag.TAG_COMPOUND);
            for (int i = 0; i < SIZE; i++) {
                CompoundTag ct = i < list.size() ? list.getCompound(i) : new CompoundTag();
                if (!ct.isEmpty()) {
                    ItemStack.parse(player.registryAccess(), ct).ifPresentOrElse(
                        result::add,
                        () -> result.add(ItemStack.EMPTY));
                } else {
                    result.add(ItemStack.EMPTY);
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < SIZE; i++) result.add(ItemStack.EMPTY);
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  技能槽位查询 API（供 YizModQZKAPI 调用，保持原有签名）
    // ══════════════════════════════════════════════════════════════

    /**
     * 获取所有饰品槽位的条目信息（含空槽位）。
     *
     * @return 按槽位索引排序的条目列表（索引 0-8）
     */
    public List<SkillSlotEntry> getSkillSlotEntries() {
        List<SkillSlotEntry> entries = new ArrayList<>();
        List<ItemStack> slots = readSlots();
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                entries.add(new SkillSlotEntry(
                    i, ItemStack.EMPTY, null, 0, 1.0, java.util.List.of()));
                continue;
            }

            if (stack.getItem() instanceof ISkillWeapon weapon) {
                ISkillWeapon.SkillType skillType = weapon.getSkillType();
                double attackDamage = weapon.getAttackDamage(stack);
                double attackSpeed = weapon.getAttackSpeed(stack);

                java.util.List<net.minecraft.client.yiz.effect.AbstractEffect> effects =
                    net.minecraft.client.yiz.core.data.EffectNBTHandler.getItemEffects(stack);

                entries.add(new SkillSlotEntry(
                    i, stack, skillType, attackDamage, attackSpeed, effects));
            } else {
                entries.add(new SkillSlotEntry(
                    i, ItemStack.EMPTY, null, 0, 1.0, java.util.List.of()));
            }
        }
        return entries;
    }

    /**
     * 获取所有非空饰品槽位的条目信息。
     */
    public List<SkillSlotEntry> getOccupiedSkillSlots() {
        List<SkillSlotEntry> all = getSkillSlotEntries();
        return all.stream()
            .filter(e -> !e.isEmpty())
            .toList();
    }

    /**
     * 获取指定技能类型的槽位占用数量。
     */
    public int countBySkillType(ISkillWeapon.SkillType type) {
        int count = 0;
        for (ItemStack stack : readSlots()) {
            if (!stack.isEmpty() && stack.getItem() instanceof ISkillWeapon weapon) {
                if (weapon.getSkillType() == type) count++;
            }
        }
        return count;
    }
}
