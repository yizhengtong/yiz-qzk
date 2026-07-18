package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 单个充能效果的 HUD 描述符（通用充能 HUD 框架）。
 *
 * <p>每个有"充能进度"的效果（被动/技能/标签）注册一个 ChargeHudEntry，
 * 声明自己的图标、最大格数、以及如何从客户端状态读当前进度。
 * {@link ChargeHud} 遍历所有"激活"的条目，纵向排列渲染，每个条目一行（图标 + 充能格）。</p>
 *
 * <h3>注册示例</h3>
 * <pre>{@code
 * ChargeHudRegistry.register(ChargeHudEntry.builder("tianleiyin")
 *     .icon(TIAN_LEI_YIN_ITEM)        // 源图标
 *     .max(6)                          // 6 格
 *     .active(p -> 被动槽有天雷引)
 *     .count(p -> 读客户端 charge)
 *     .build());
 * }</pre>
 */
@FunctionalInterface
public interface ChargeHudEntry {

    /**
     * 该条目对给定玩家的当前显示状态。
     *
     * @param player 客户端玩家（可能为 null，editMode 时）
     * @return 状态；active=false 时该条目不渲染
     */
    Display display(net.minecraft.world.entity.player.Player player);

    /**
     * 单次渲染的状态快照。
     *
     * @param active     是否激活（决定显不显示）
     * @param icon       源图标 ItemStack（空则不画图标）
     * @param count      当前充能格数（0~max，超过 max 按 max 显示）
     * @param max        最大格数
     * @param highlight  是否高亮（如满层 buff 态）
     */
    record Display(boolean active, ItemStack icon, int count, int max, boolean highlight) {
        /** 不显示。 */
        public static Display hidden() { return new Display(false, ItemStack.EMPTY, 0, 0, false); }
    }
}
