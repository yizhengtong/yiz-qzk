package net.minecraft.client.yiz.hud;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通用充能 HUD 注册表。
 *
 * <p>任何有"充能进度"的效果注册一个 {@link ChargeHudEntry}，{@link ChargeHud} 渲染时
 * 遍历全部条目，对激活的逐个纵向排列显示（图标 + 充能格），互不干扰。</p>
 *
 * <p>典型用法：满6充能+技能次数的天雷引、满6给技能充能+1的 PassiveChargeTracker，
 * 将来还有更多充能效果——各自注册，各自显示。</p>
 */
public final class ChargeHudRegistry {

    private ChargeHudRegistry() {}

    private static final List<ChargeHudEntry> ENTRIES = new CopyOnWriteArrayList<>();

    /** 注册一个充能 HUD 条目（按注册顺序渲染）。 */
    public static void register(ChargeHudEntry entry) {
        ENTRIES.add(entry);
    }

    /** 收集所有对当前玩家激活的条目状态（editMode 时返回全部，用于 HUD 编辑器预览）。 */
    public static List<ChargeHudEntry.Display> collect(Player player, boolean editMode) {
        List<ChargeHudEntry.Display> out = new ArrayList<>();
        for (ChargeHudEntry e : ENTRIES) {
            ChargeHudEntry.Display d = e.display(player);
            if (editMode || d.active()) out.add(d);
        }
        return out;
    }
}
