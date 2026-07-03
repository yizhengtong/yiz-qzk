package net.minecraft.client.yiz.util;

import net.minecraft.client.yiz.item.StagedItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 同一资源多物品 ID 快速注册工具。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 3 个等级共用一张纹理的通用物品
 * public static final List<Supplier<Item>> MY_STAGES =
 *     StagedItemHelper.registerStaged(ITEMS, "my_item", 3);
 *
 * // 自定义每个等级的构造逻辑
 * public static final List<Supplier<Item>> TERRAPRISMAS =
 *     StagedItemHelper.registerStaged(ITEMS, "terraprisma_scroll", 3,
 *         level -> new TerraprismaScrollItem(level));
 * }</pre>
 */
public final class StagedItemHelper {

    private StagedItemHelper() {}

    /**
     * 注册 N 个分级物品，同一纹理，独立 itemId。
     *
     * @param register  消费模组的物品注册器 (e.g. {@code YizxianMod.ITEMS})
     * @param baseName  基础名称，如 {@code "terraprisma_scroll"}；注册 ID 为 {@code baseName_1, baseName_2, ...}
     * @param count     阶段数 N，N >= 1
     * @param factory   接收 level (1..N) 返回 Item 实例
     * @return 按 level 排序的 {@code Supplier<Item>} 列表（下标 0 = level 1）
     */
    public static List<Supplier<Item>> registerStaged(
            DeferredRegister<Item> register,
            String baseName,
            int count,
            IntFunction<? extends Item> factory) {

        List<Supplier<Item>> result = new ArrayList<>(count);
        for (int level = 1; level <= count; level++) {
            final int lv = level;
            String name = baseName + "_" + lv;
            var supplier = register.register(name, () -> factory.apply(lv));
            @SuppressWarnings("unchecked")
            Supplier<Item> s = (Supplier<Item>) supplier;
            result.add(s);
        }
        return result;
    }

    /**
     * {@link #registerStaged(DeferredRegister, String, int, IntFunction)} 的简化版，
     * 使用 {@link StagedItem} 桩类（实现 {@code IGeneralItem}）。
     */
    public static List<Supplier<Item>> registerStaged(
            DeferredRegister<Item> register, String baseName, int count) {
        return registerStaged(register, baseName, count,
            level -> new StagedItem(new Item.Properties(), level));
    }

    /**
     * 返回该分级物品系列的语言键列表。
     * 用于辅助编写 {@code zh_cn.json / en_us.json}。
     */
    public static List<String> getTranslationKeys(String modId, String baseName, int count) {
        List<String> keys = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            keys.add("item." + modId + "." + baseName + "_" + i);
        }
        return keys;
    }

    // ═══════════════════════════════════════════════════════════
    //  分级发光色 — 所有分级物品通用
    // ═══════════════════════════════════════════════════════════

    /**
     * level=1..5 → 对应等级的描边色 (R,G,B,A)。level=5 传说返回 null 表示用默认动画色板。
     * @deprecated 使用 {@link net.minecraft.client.yiz.weapon.QualityTier#DEFAULT_5} 和
     *             {@link net.minecraft.client.yiz.weapon.QualityTier#glowColorForLevel} 替代。
     */
    @Deprecated
    public static Vector4f glowColorForLevel(int level) {
        return switch (level) {
            case 1  -> new Vector4f(0.80f, 0.80f, 0.80f, 0.4f);  // 平凡 灰白
            case 2  -> new Vector4f(0.30f, 0.90f, 0.40f, 0.5f);  // 优秀 翠绿
            case 3  -> new Vector4f(0.30f, 0.60f, 1.00f, 0.5f);  // 精良 冰蓝
            case 4  -> new Vector4f(0.70f, 0.30f, 1.00f, 0.6f);  // 史诗 紫罗兰
            default -> null; // Lv5+ 传说 default=动画色板
        };
    }

    // ═══════════════════════════════════════════════════════════
    //  通用右键 NBT 计数 — 任何分级物品复用
    // ═══════════════════════════════════════════════════════════

    private static final String STAGED_COUNT_KEY = "yizmodqzk:staged_count";

    public static int getStagedCount(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getInt(STAGED_COUNT_KEY);
    }

    public static void setStagedCount(ItemStack stack, int count) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = cd != null ? cd.copyTag() : new CompoundTag();
        tag.putInt(STAGED_COUNT_KEY, Math.max(0, count));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 通用 use() 实现：右键+1 / Shift右键-1，上限 maxCount。子类 use() 中直接 return 本方法结果即可。 */
    public static InteractionResultHolder<ItemStack> stagedUse(
            ItemStack held, Player player, InteractionHand hand, int maxCount) {
        Level level = player.level();
        if (level.isClientSide) return InteractionResultHolder.success(held);

        boolean shift = player.isShiftKeyDown();
        int count = getStagedCount(held);
        if (shift) {
            if (count > 0) setStagedCount(held, count - 1);
        } else {
            if (count < maxCount) setStagedCount(held, count + 1);
        }
        return InteractionResultHolder.success(held);
    }
}
