package net.minecraft.client.yiz.handler;

import net.minecraft.client.yiz.api.IEquipmentItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 特殊装备路由编译层（模块 C）。
 *
 * <p>职责：
 * <ol>
 *   <li>扫描 6 个装备槽，提取含 {@code UniquePassiveGroup} 的装备</li>
 *   <li>同组去重：只保留第一个扫描到的物品生成上下文</li>
 *   <li>合并去重后的列表与未分组装备的上下文</li>
 * </ol>
 *
 * <p>编译极轻量（最多 6 个槽位），直接返回结果，无持久化缓存。
 * 装备物品本身已通过 {@code SkillConfigStorage → PlayerDataAPI → NeoForge Attachment}
 * 获得完整的死亡/维度切换持久化保障。</p>
 */
public final class SpecialGearRouter {

    private SpecialGearRouter() {}

    /**
     * 编译当前装备槽，返回去重后的上下文列表。
     * 调用时机：装备变更、登录、重生、Tick/Attack 事件中用到即调。
     */
    public static List<SpecialGearContext> compile(Player player, Container equipmentCont) {
        List<SpecialGearContext> all = new ArrayList<>();
        Map<String, Integer> seenPassive = new HashMap<>();

        for (int i = 0; i < 6; i++) {
            ItemStack stack = equipmentCont.getItem(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof IEquipmentItem ei)) continue;

            String passiveGroup = ei.getUniquePassiveGroup();
            if (!passiveGroup.isEmpty()) {
                if (seenPassive.containsKey(passiveGroup)) continue;
                seenPassive.put(passiveGroup, i);
            }

            all.add(new SpecialGearContext(i, stack.copy(), passiveGroup));
        }

        return Collections.unmodifiableList(all);
    }
}
