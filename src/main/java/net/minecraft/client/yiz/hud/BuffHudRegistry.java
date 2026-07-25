package net.minecraft.client.yiz.hud;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通用 buff 叠层 HUD 注册表。
 *
 * <p>任何有"叠层"的装备注册一个 {@link BuffHudEntry}，{@link BuffHud} 渲染时
 * 遍历全部条目，把每个条目返回的所有 Display 行合并后纵向排列显示（图标 + x层数）。</p>
 *
 * <p>典型用法：鬼索 Guinsoo（攻击叠 CDR 层，无上限，5 秒衰减），
 * 将来还有更多叠层装备——各自注册，各自显示。一个 entry 可返回多行（多槽同装备）。</p>
 */
public final class BuffHudRegistry {

    private BuffHudRegistry() {}

    private static final List<BuffHudEntry> ENTRIES = new CopyOnWriteArrayList<>();

    /** 注册一个 buff HUD 条目（按注册顺序渲染）。 */
    public static void register(BuffHudEntry entry) {
        ENTRIES.add(entry);
    }

    /** 收集所有条目对当前玩家返回的显示行（editMode 时返回全部含 inactive，用于 HUD 编辑器预览）。 */
    public static List<BuffHudEntry.Display> collect(Player player, boolean editMode) {
        List<BuffHudEntry.Display> out = new ArrayList<>();
        for (BuffHudEntry e : ENTRIES) {
            for (BuffHudEntry.Display d : e.display(player)) {
                if (editMode || d.active()) out.add(d);
            }
        }
        return out;
    }
}
