package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 装备物品接口。
 *
 * <p>实现此接口的 Item 放入装备槽后，物品上声明的
 * {@code ATTRIBUTE_MODIFIERS} 组件会直接加成到玩家身上。
 * 取出时自动移除。</p>
 *
 * <h3>唯一组标记</h3>
 * <ul>
 *   <li><b>UniqueEquipmentGroup</b>：相同组别禁止同时穿戴多件（交互层拦截）</li>
 *   <li><b>UniquePassiveGroup</b>：相同组别只生效一个被动（路由层去重）</li>
 * </ul>
 * 空字符串 = 不限制。
 *
 * <h3>测试开关</h3>
 * {@link #ALLOW_ANY_ITEM}：设为 {@code true} 允许任意物品放入装备槽（调试用），默认关闭。
 */
public interface IEquipmentItem {

    /** 全局测试开关：允许任意物品放入装备槽。默认 false，仅 IEquipmentItem 可放入。 */
    boolean ALLOW_ANY_ITEM = false;

    /** 唯一装备组：空=不限制。相同组别禁止同时穿戴多件。 */
    default String getUniqueEquipmentGroup() { return ""; }

    /** 唯一被动组：空=不限制。相同组别只生效一个被动效果。 */
    default String getUniquePassiveGroup() { return ""; }

    /** 装备放入槽位时调用（服务端）。 */
    default void onEquip(Player player, ItemStack stack, int slot) {}

    /** 装备从槽位移除时调用（服务端）。 */
    default void onUnequip(Player player, ItemStack stack, int slot) {}
}
