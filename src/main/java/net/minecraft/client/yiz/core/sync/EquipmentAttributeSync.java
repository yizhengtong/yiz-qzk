package net.minecraft.client.yiz.core.sync;

import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全槽位属性同步器 —— 每 tick 把玩家<b>主手 / 副手 / 4 盔甲槽 / 饰品槽</b>所有物品上的
 * {@code yizmodqzk:*} 自定义属性 modifier 按属性累加，挂到玩家 {@code AttributeInstance}。
 *
 * <p><b>为什么需要这个</b>：原版 Minecraft 的物品 {@code ATTRIBUTE_MODIFIERS} 只对标准盔甲槽
 * （HEAD/CHEST/LEGS/FEET）自动生效；主手 / 副手 / 饰品槽的 modifier <b>不会</b>自动加到玩家。
 * 本同步器补齐这个缺口，实现"自定义属性在全部槽位生效"。</p>
 *
 * <h3>只同步 yizmodqzk 自定义属性（避免双倍）</h3>
 * <p>原版属性（ARMOR / ATTACK_DAMAGE 等）的原版装备 modifier 会自动生效，若同步器再汇总一次会
 * 双倍。因此本同步器<b>只处理命名空间为 {@code yizmodqzk} 的自定义属性</b>——这些原版完全不认识，
 * 不会自动生效，必须手动汇总，天然不会双倍。</p>
 *
 * <h3>去重</h3>
 * <p>每个属性用固定 id（{@code yizmodqzk:sync_<注册名>}）的 modifier，每 tick 先 remove 再 add，
 * 复用 {@link ItemAttributeHandler#setEntityAttribute} 的稳定 id 机制。</p>
 *
 * <h3>注册时机</h3>
 * <p>由 {@code tizMod.onPlayerTick} 每 tick 调用 {@link #sync(Player)}。客户端 / 服务端均可调用
 * （客户端用于本地 HUD 显示，服务端用于实际伤害计算）。</p>
 */
public final class EquipmentAttributeSync {

    /** modifier 的稳定 id 前缀（与 ItemAttributeHandler 的 entity_ 区分，避免冲突）。 */
    private static final String ID_PREFIX = "sync_";

    private EquipmentAttributeSync() {}

    /**
     * 同步玩家<b>饰品槽</b>的 yizmodqzk 自定义属性。每 tick 调用。
     *
     * <p>统一支持<b>主手 / 副手 / 4 盔甲槽 / 技能装载槽</b>的 yizmodqzk 自定义属性汇总。</p>
     *
     * <p>原版 Minecraft 只对<b>原版注册的属性</b>（generic.armor 等）自动累加装备槽 modifier
     * 到玩家属性值；yizmodqzk 自定义属性原版不认识，装备槽 modifier 不会自动生效，必须由本
     * 同步器补齐。技能装载槽是下游自定义容器，同样由本同步器处理。</p>
     *
     * <p><b>不会双倍</b>：collectYizAttributes 只统计 yizmodqzk 命名空间属性（isYizAttribute
     * 过滤），原版属性完全不参与，因此与原版的自动累加互不干扰。</p>
     *
     * <p><b>饰品槽系统已废弃移除</b>：不再收集饰品槽物品，属性生效槽位统一为
     * 主手/副手/装备槽/技能装载槽。</p>
     */
    public static void sync(Player player) {
        List<ItemStack> stacks = new ArrayList<>();

        // 收集主手 / 副手 / 4 盔甲槽物品
        addVanillaEquipmentStacks(player, stacks);

        // 收集技能装载槽物品（从 PlayerDataAPI 同步的 load_slots）
        collectLoadSlots(player, stacks);

        // 按 yizmodqzk 自定义属性累加 modifier 值
        Map<Holder<Attribute>, Double> sum = collectYizAttributes(stacks);

        // 写入玩家属性（值=0 则移除）
        applyToPlayer(player, sum);
    }

    /**
     * 收集主手 / 副手 / 4 盔甲槽物品加入列表。
     *
     * <p>原版 Minecraft 只对<b>原版注册的属性</b>（generic.armor 等）自动累加装备槽 modifier 到
     * 玩家属性值；yizmodqzk 自定义属性原版不认识，装备槽 modifier 不会自动生效。本方法把这些
     * 槽位的物品也纳入收集，由 collectYizAttributes 统一汇总（仅 yiz 属性，不会与原版双倍）。</p>
     */
    private static void addVanillaEquipmentStacks(Player player, List<ItemStack> stacks) {
        // 主手 / 副手
        addIfPresent(player.getMainHandItem(), stacks);
        addIfPresent(player.getOffhandItem(), stacks);
        // 4 盔甲槽
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                addIfPresent(player.getItemBySlot(slot), stacks);
            }
        }
    }

    private static void addIfPresent(ItemStack stack, List<ItemStack> stacks) {
        if (stack != null && !stack.isEmpty()) stacks.add(stack);
    }

    /** 从 PlayerDataAPI 读取装载槽物品加入收集列表。 */
    private static void collectLoadSlots(Player player, List<ItemStack> stacks) {
        try {
            String raw = net.minecraft.client.yiz.api.PlayerDataAPI.get(player, "yizmodqzk:load_slots");
            if (raw == null || raw.isEmpty()) return;
            com.google.gson.JsonObject json =
                com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            for (String k : new String[]{"big", "s0", "s1", "s2"}) {
                String snbt = json.has(k) ? json.get(k).getAsString() : "";
                if (snbt.isEmpty()) continue;
                try {
                    net.minecraft.world.item.ItemStack.parse(
                        player.registryAccess(),
                        net.minecraft.nbt.TagParser.parseTag(snbt))
                        .ifPresent(s -> { if (!s.isEmpty()) stacks.add(s); });
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /**
     * 收集一组物品上所有 yizmodqzk 命名空间的属性 modifier，按属性累加。
     * <p>只认 ADD_VALUE 操作的 modifier（全槽位汇总语义为加法叠加）；
     * ADD_MULTIPLIED_* 操作不参与（那类需在伤害计算时即时取值，不预先汇总）。</p>
     */
    private static Map<Holder<Attribute>, Double> collectYizAttributes(List<ItemStack> stacks) {
        Map<Holder<Attribute>, Double> sum = new HashMap<>();
        for (ItemStack stack : stacks) {
            ItemAttributeModifiers mods = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
            for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
                Holder<Attribute> attr = entry.attribute();
                // 只处理 yizmodqzk 命名空间的自定义属性
                if (!isYizAttribute(attr)) continue;
                AttributeModifier mod = entry.modifier();
                if (mod.operation() != Operation.ADD_VALUE) continue;
                sum.merge(attr, mod.amount(), Double::sum);
            }
        }
        return sum;
    }

    /** 判断某属性是否属于 yizmodqzk 命名空间（原版属性返回 false）。 */
    private static boolean isYizAttribute(Holder<Attribute> attr) {
        return attr.unwrapKey()
            .map(key -> key.location().getNamespace().equals("yizmodqzk"))
            .orElse(false);
    }

    /**
     * 把汇总值写入玩家属性。
     * <p>对本次收集到的每个属性：值>0 则挂 ADD_VALUE modifier，值=0 则移除。
     * id 用 {@code yizmodqzk:sync_<注册名>}，复用 setEntityAttribute 的稳定 id 去重。</p>
     */
    private static void applyToPlayer(Player player, Map<Holder<Attribute>, Double> sum) {
        for (var e : sum.entrySet()) {
            Holder<Attribute> attr = e.getKey();
            double value = e.getValue();
            String regName = attr.unwrapKey()
                .map(key -> key.location().getPath())
                .orElse(attr.toString());
            ItemAttributeHandler.setEntityAttribute(
                player, attr, ID_PREFIX + regName, value, Operation.ADD_VALUE);
        }
        // 清理上次挂过、但本次未出现的属性（装备已卸下）
        cleanupStale(player, sum);
    }

    /**
     * 清理"上次挂过但本次没收集到"的属性（玩家卸下了提供该属性的物品）。
     * <p>遍历 {@link #TRACKED_ATTRIBUTES}（已声明走全槽位汇总的属性），若本次 sum 不再包含它，
     * 则传 0 移除其 sync_ modifier（卸装即时生效）。只清 sync_ 前缀，不动别处挂的 modifier。</p>
     */
    private static void cleanupStale(Player player, Map<Holder<Attribute>, Double> currentSum) {
        // 本次仍有贡献的属性 key 集合
        java.util.Set<net.minecraft.resources.ResourceKey<Attribute>> currentKeys = new java.util.HashSet<>();
        for (Holder<Attribute> attr : currentSum.keySet()) {
            attr.unwrapKey().ifPresent(currentKeys::add);
        }
        // 追踪集中本次缺失的 → 移除 sync_ modifier
        for (Holder<Attribute> attr : TRACKED_ATTRIBUTES) {
            if (currentKeys.contains(attr.unwrapKey().orElse(null))) continue;
            String regName = attr.unwrapKey()
                .map(key -> key.location().getPath())
                .orElse(null);
            if (regName == null) continue;
            ItemAttributeHandler.setEntityAttribute(
                player, attr, ID_PREFIX + regName, 0, Operation.ADD_VALUE);
        }
    }

    // ── 追踪属性集（用于清理孤立 modifier）─────────────────────────

    /** 已声明"走全槽位汇总"的 yizmodqzk 属性。下游/库注册后纳入清理范围。 */
    private static final java.util.Set<Holder<Attribute>> TRACKED_ATTRIBUTES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 声明某 yizmodqzk 属性走全槽位汇总（纳入清理追踪）。
     * <p>库在注册属性时调用（如 DAMAGE_REDUCTION）。未声明的属性仍会被汇总，
     * 但卸下装备后不会主动清理 modifier（残留 0 值 modifier 不影响实际效果，仅不够干净）。</p>
     */
    public static void registerTrackedAttribute(Holder<Attribute> attr) {
        TRACKED_ATTRIBUTES.add(attr);
    }
}
