package net.minecraft.client.yiz.handler;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * 临时属性辅助 — 支持"给玩家加临时属性 modifier，到期自动移除"。
 * 由 {@code tizMod.onPlayerTick} 调用 {@link #tick} 推动。
 */
public final class TempAttributeHelper {

    private TempAttributeHelper() {}

    private record Entry(Holder<Attribute> attr, ResourceLocation modId, long expireTick) {}

    private static final Map<UUID, List<Entry>> PENDING = new HashMap<>();

    /**
     * 安排到期移除。
     * @param expireTick 绝对游戏 tick，到期时移除 modifier
     */
    public static void scheduleRemoval(Player player, Holder<Attribute> attr,
                                       ResourceLocation modId, long expireTick) {
        PENDING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
               .add(new Entry(attr, modId, expireTick));
    }

    /** 每 tick 由 tizMod 调用，检查并移除到期 modifier。 */
    public static void tick(Player player) {
        List<Entry> list = PENDING.get(player.getUUID());
        if (list == null || list.isEmpty()) return;
        long now = player.level().getGameTime();
        var iter = list.iterator();
        while (iter.hasNext()) {
            Entry e = iter.next();
            if (now >= e.expireTick) {
                var inst = player.getAttribute(e.attr);
                if (inst != null) inst.removeModifier(e.modId);
                iter.remove();
            }
        }
        if (list.isEmpty()) PENDING.remove(player.getUUID());
    }

    /** 玩家登出/切换世界时清理。 */
    public static void clear(Player player) {
        PENDING.remove(player.getUUID());
    }
}
