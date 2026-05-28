package net.minecraft.client.yiz.tool.abolish;

import net.minecraft.client.yiz.core.AbolitionStateManager;
import net.minecraft.client.yiz.core.VTableReplace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * VTable 入口覆写式物品废除工具。
 *
 * <p>将指定 Item 子类的功能方法入口指针抄回 {@link Item} 基类的空实现，
 * 使该物品丧失所有自定义行为，同时保留基础属性（耐久、攻击力等原版属性）。</p>
 *
 * <p>核心原理：每个 {@code @Override} 的方法在其子类的 vtable 中有独立的
 * {@code Method*} 条目。用 {@link VTableReplace#replaceMethodFromSource} 把
 * 基类 {@link Item} 的入口覆写过去，等于"撤销"了子类的 override。</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 一次性废除某个物品的所有功能
 * ItemAbolitionHelper.abolishItem(SomeModSword.class);
 *
 * // 只废除右键相关
 * ItemAbolitionHelper.abolishRightClick(SomeModWand.class);
 * </pre>
 */
public final class ItemAbolitionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemAbolition");

    // ══════════════════════════════════════════════════════════
    //  Item 方法描述符常量
    // ══════════════════════════════════════════════════════════

    /** {@code InteractionResultHolder<ItemStack> use(Level, Player, InteractionHand)} */
    private static final String DESC_USE =
            "(Lnet/minecraft/world/level/Level;" +
            "Lnet/minecraft/world/entity/player/Player;" +
            "Lnet/minecraft/world/InteractionHand;)" +
            "Lnet/minecraft/world/InteractionResultHolder;";

    /** {@code InteractionResult useOn(UseOnContext)} */
    private static final String DESC_USE_ON =
            "(Lnet/minecraft/world/item/context/UseOnContext;)" +
            "Lnet/minecraft/world/InteractionResult;";

    /** {@code boolean hurtEnemy(ItemStack, LivingEntity, LivingEntity)} */
    private static final String DESC_HURT_ENEMY =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/entity/LivingEntity;" +
            "Lnet/minecraft/world/entity/LivingEntity;)Z";

    /** {@code void inventoryTick(ItemStack, Level, Entity, int, boolean)} */
    private static final String DESC_INVENTORY_TICK =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/level/Level;" +
            "Lnet/minecraft/world/entity/Entity;IZ)V";

    /** {@code void releaseUsing(ItemStack, Level, LivingEntity, int)} */
    private static final String DESC_RELEASE_USING =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/level/Level;" +
            "Lnet/minecraft/world/entity/LivingEntity;I)V";

    /** {@code void appendHoverText(ItemStack, TooltipContext, List<Component>, TooltipFlag)} */
    private static final String DESC_APPEND_HOVER_TEXT =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/item/TooltipContext;" +
            "Ljava/util/List;" +
            "Lnet/minecraft/world/item/TooltipFlag;)V";

    /** {@code void onEquip(ItemStack, EquipmentSlot, LivingEntity)} */
    private static final String DESC_ON_EQUIP =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/entity/EquipmentSlot;" +
            "Lnet/minecraft/world/entity/LivingEntity;)V";

    /** {@code void onUnequip(ItemStack, EquipmentSlot, LivingEntity)} */
    private static final String DESC_ON_UNEQUIP = DESC_ON_EQUIP; // same descriptor

    /** {@code void onCraftedBy(ItemStack, Level, Player)} */
    private static final String DESC_ON_CRAFTED_BY =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/level/Level;" +
            "Lnet/minecraft/world/entity/player/Player;)V";

    /** {@code void onEntitySwing(ItemStack, LivingEntity)} */
    private static final String DESC_ON_ENTITY_SWING =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/entity/LivingEntity;)V";

    /** {@code int getUseDuration(ItemStack, LivingEntity)} */
    private static final String DESC_GET_USE_DURATION =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/entity/LivingEntity;)I";

    /** {@code void postHurtEnemy(ItemStack, LivingEntity, LivingEntity)} */
    private static final String DESC_POST_HURT_ENEMY =
            "(Lnet/minecraft/world/item/ItemStack;" +
            "Lnet/minecraft/world/entity/LivingEntity;" +
            "Lnet/minecraft/world/entity/LivingEntity;)V";

    // ══════════════════════════════════════════════════════════
    //  方法列表（全废除用）
    // ══════════════════════════════════════════════════════════

    /** 所有可废除的功能方法，按 {name, desc} 组织 */
    private static final String[][] ALL_ITEM_METHODS = {
            {"use",              DESC_USE},
            {"useOn",            DESC_USE_ON},
            {"hurtEnemy",        DESC_HURT_ENEMY},
            {"postHurtEnemy",    DESC_POST_HURT_ENEMY},
            {"inventoryTick",    DESC_INVENTORY_TICK},
            {"releaseUsing",     DESC_RELEASE_USING},
            {"appendHoverText",  DESC_APPEND_HOVER_TEXT},
            {"onEquip",          DESC_ON_EQUIP},
            {"onUnequip",        DESC_ON_UNEQUIP},
            {"onCraftedBy",      DESC_ON_CRAFTED_BY},
            {"onEntitySwing",    DESC_ON_ENTITY_SWING},
            {"getUseDuration",   DESC_GET_USE_DURATION},
    };

    private ItemAbolitionHelper() {}

    // ══════════════════════════════════════════════════════════
    //  废除方法
    // ══════════════════════════════════════════════════════════

    /**
     * 彻底废除指定物品类的所有功能方法（VTable 层 + Mixin 层双重保险）。
     * <p>
     * 调用后该物品的 {@code use()}、{@code useOn()}、{@code hurtEnemy()}、
     * {@code inventoryTick()}、{@code onEquip()} 等行为全部回退到
     * {@link Item} 基类的空行为。基础属性（耐久、攻击力标签等）不受影响。
     * </p>
     *
     * @param itemClass 目标物品类（如 {@code SomeModSword.class}）
     * @return 成功覆写的方法数量
     */
    public static int abolishItem(Class<? extends Item> itemClass) {
        int count = 0;

        // VTable 层（JVM 级入口覆写，可选但彻底）
        if (VTableReplace.isAvailable()) {
            for (String[] method : ALL_ITEM_METHODS) {
                if (VTableReplace.replaceMethodFromSource(
                        itemClass, method[0], method[1], Item.class)) {
                    count++;
                }
            }
        } else {
            LOGGER.warn("VTableReplace not available for {}, Mixin layer only", itemClass.getSimpleName());
        }

        LOGGER.info("Abolished {} methods on {} (VTable={}, Mixin=always)", 
                count, itemClass.getName(), VTableReplace.isAvailable());
        return count;
    }

    /**
     * 按物品 ID 废除（VTable + Mixin + AbolitionStateManager 三重联动）。
     */
    public static int abolishItemById(ResourceLocation itemId) {
        // 1. 先注册到状态管理器（Mixin 层立即生效）
        AbolitionStateManager.abolishItem(itemId);

        // 2. VTable 层（深层次覆写）
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item != null) {
            return abolishItem(item.getClass());
        }
        return 0;
    }

    /**
     * 按物品 ID 恢复（从状态管理器移除）。
     */
    public static void restoreItemById(ResourceLocation itemId) {
        AbolitionStateManager.restoreItem(itemId);
        LOGGER.info("Restored item: {}", itemId);
    }

    /**
     * 只废除右键相关方法：{@code use()} 和 {@code useOn()}。
     * <p>
     * 废除后该物品的右键功能完全失效（无法召唤闪电、无法使用等），
     * 但攻击效果、背包 tick、tooltip 等行为仍然保留。
     * </p>
     *
     * @param itemClass 目标物品类
     * @return 成功覆写的方法数量
     */
    public static int abolishRightClick(Class<? extends Item> itemClass) {
        if (!VTableReplace.isAvailable()) return 0;

        int count = 0;
        if (VTableReplace.replaceMethodFromSource(itemClass, "use", DESC_USE, Item.class)) count++;
        if (VTableReplace.replaceMethodFromSource(itemClass, "useOn", DESC_USE_ON, Item.class)) count++;
        LOGGER.info("Abolished right-click on {} ({} methods)", itemClass.getSimpleName(), count);
        return count;
    }

    /**
     * 只废除攻击相关方法：{@code hurtEnemy()} 和 {@code postHurtEnemy()}。
     * <p>
     * 废除后该物品攻击时不再触发特殊效果（如吸血、点燃、额外伤害等）。
     * </p>
     *
     * @param itemClass 目标物品类
     * @return 成功覆写的方法数量
     */
    public static int abolishAttackEffects(Class<? extends Item> itemClass) {
        if (!VTableReplace.isAvailable()) return 0;

        int count = 0;
        if (VTableReplace.replaceMethodFromSource(itemClass, "hurtEnemy", DESC_HURT_ENEMY, Item.class)) count++;
        if (VTableReplace.replaceMethodFromSource(itemClass, "postHurtEnemy", DESC_POST_HURT_ENEMY, Item.class)) count++;
        LOGGER.info("Abolished attack effects on {} ({} methods)", itemClass.getSimpleName(), count);
        return count;
    }

    /**
     * 废除装备相关方法：{@code onEquip()} 和 {@code onUnequip()}。
     *
     * @param itemClass 目标物品类
     * @return 成功覆写的方法数量
     */
    public static int abolishEquipEffects(Class<? extends Item> itemClass) {
        if (!VTableReplace.isAvailable()) return 0;

        int count = 0;
        if (VTableReplace.replaceMethodFromSource(itemClass, "onEquip", DESC_ON_EQUIP, Item.class)) count++;
        if (VTableReplace.replaceMethodFromSource(itemClass, "onUnequip", DESC_ON_UNEQUIP, Item.class)) count++;
        LOGGER.info("Abolished equip effects on {} ({} methods)", itemClass.getSimpleName(), count);
        return count;
    }

    /**
     * 废除背包 tick 方法：{@code inventoryTick()}。
     * <p>
     * 废除后物品在背包中不再触发每 tick 的持续效果（如光环、自动回复等）。
     * </p>
     *
     * @param itemClass 目标物品类
     * @return true 表示覆写成功
     */
    public static boolean abolishInventoryTick(Class<? extends Item> itemClass) {
        if (!VTableReplace.isAvailable()) return false;
        boolean ok = VTableReplace.replaceMethodFromSource(
                itemClass, "inventoryTick", DESC_INVENTORY_TICK, Item.class);
        if (ok) LOGGER.info("Abolished inventoryTick on {}", itemClass.getSimpleName());
        return ok;
    }

    /**
     * 废除 tooltip 显示：{@code appendHoverText()}。
     * <p>
     * 废除后该物品不再显示自定义 tooltip 文本。
     * </p>
     *
     * @param itemClass 目标物品类
     * @return true 表示覆写成功
     */
    public static boolean abolishHoverText(Class<? extends Item> itemClass) {
        if (!VTableReplace.isAvailable()) return false;
        boolean ok = VTableReplace.replaceMethodFromSource(
                itemClass, "appendHoverText", DESC_APPEND_HOVER_TEXT, Item.class);
        if (ok) LOGGER.info("Abolished appendHoverText on {}", itemClass.getSimpleName());
        return ok;
    }
}
