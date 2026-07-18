package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 被动技能/饰品物品接口。
 *
 * <p>实现此接口的 Item 放入被动槽后，每玩家 tick 自动调用
 * {@link #onWornTick}（服务端权威）。框架负责从被动槽中提取装备
 * 并统一分发 tick。</p>
 *
 * <p>被动物品可通过覆写 {@link IEnhanceable#getProvidedTags} 对外提供触发标签，
 * 主动技能在强化槽中激活这些标签后，释放时自动执行标签效果。</p>
 */
public interface IPassiveItem extends IEnhanceable {

    /** 每玩家 tick 调用一次（服务端） */
    void onWornTick(Player player, ItemStack stack);

    /**
     * 玩家攻击命中时调用（服务端）。target 为被攻击的实体。
     * <p>需要"每次攻击"响应的被动（如天雷引充能）覆写此方法。
     * 由框架在攻击事件中遍历被动槽分发。默认空。</p>
     */
    default void onAttack(Player player, ItemStack stack,
                          net.minecraft.world.entity.LivingEntity target) {}

    /**
     * 被动从装配槽卸载时调用（服务端）。清除自身产生的 transient 状态。
     * <p>默认空。配合 {@link EffectRegistry}：被动装配时登记清理回调，
     * 或直接覆写此方法由框架在卸载时调用。</p>
     */
    default void onUnequip(Player player, ItemStack stack) {}
}
