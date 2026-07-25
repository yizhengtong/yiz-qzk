package net.minecraft.client.yiz.hud;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 单个装备 buff 叠层效果的 HUD 描述符（通用 buff 叠层 HUD 框架）。
 *
 * <p>每个有"叠层"的装备（如鬼索 Guinsoo 攻击叠 CDR 层）注册一个 {@code BuffHudEntry}，
 * 声明图标 + 如何从客户端读层数。{@link BuffHud} 遍历所有激活条目，纵向排列渲染，
 * 每行一个（图标 + x层数）。</p>
 *
 * <p>{@link #display} 返回 **List&lt;Display&gt;**：一个 entry 可贡献多行
 * （如玩家在多个装备槽放了同种叠层装备，每槽各一行）。</p>
 */
@FunctionalInterface
public interface BuffHudEntry {

    /**
     * 该条目对给定玩家的所有显示行（一个 entry 可贡献多行）。
     *
     * @param player 客户端玩家（可能为 null，editMode 时）
     * @return 显示行列表；active=false 的行不渲染（editMode 除外）
     */
    List<Display> display(Player player);

    /**
     * 单次渲染的状态快照。
     *
     * @param active 是否激活（决定显不显示）
     * @param icon   源图标 ItemStack（空则不画图标）
     * @param stacks 当前层数
     */
    record Display(boolean active, ItemStack icon, int stacks) {
        /** 不显示。 */
        public static Display hidden() { return new Display(false, ItemStack.EMPTY, 0); }
    }
}
