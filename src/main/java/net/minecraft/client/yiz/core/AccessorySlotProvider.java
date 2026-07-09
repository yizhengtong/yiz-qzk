package net.minecraft.client.yiz.core;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 饰品槽物品提供者 —— 库通过此接口读取玩家的饰品槽物品，<b>避免库直接依赖下游 {@code AccessoryContainer}</b>。
 *
 * <p>下游模组（提供饰品槽的一方）实现此接口并在初始化时调用
 * {@link AccessorySlotProvider#register(AccessorySlotProvider)} 注册实例。
 * 库的 {@code EquipmentAttributeSync} 等全槽位汇总逻辑通过 {@link #getAccessoryStacks(Player)}
 * 拿到饰品槽物品列表。</p>
 *
 * <p>未注册任何提供者时，{@link #getAccessoryStacks(Player)} 返回空列表
 * （库仍可汇总原版主手/副手/盔甲槽）。</p>
 */
@FunctionalInterface
public interface AccessorySlotProvider {

    /**
     * 返回玩家当前饰品槽中的全部物品（含空槽位也行，调用方会跳过空 stack）。
     * <p>必须在调用线程上下文中安全返回（通常服务端 tick 线程）。</p>
     */
    List<ItemStack> getAccessoryStacks(Player player);

    // ── 注册中心 ─────────────────────────────────────────────────

    /** 当前注册的提供者（null 表示无下游饰品槽）。volatile 保证可见性。 */
    AccessorySlotProvider HOLDER[] = new AccessorySlotProvider[]{null};

    /** 注册饰品槽提供者。下游在模组构造器中调用一次。重复注册覆盖。 */
    static void register(AccessorySlotProvider provider) {
        HOLDER[0] = provider;
    }

    /** 取已注册的提供者，未注册返回 null。 */
    static AccessorySlotProvider get() {
        return HOLDER[0];
    }
}
