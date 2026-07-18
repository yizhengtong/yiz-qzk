package net.minecraft.client.yiz.network;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端奔雷袭窗口追踪器。
 * 由 S2CBenleixiWindowPayload 更新，AutoAttackMixin 读取。
 */
public final class BenleixiWindowClientTracker {

    private BenleixiWindowClientTracker() {}

    private record Window(long endTick) {}
    private static final ConcurrentHashMap<UUID, Window> WINDOWS = new ConcurrentHashMap<>();

    /** 服务端通知窗口状态变更 */
    public static void set(Player player, boolean active, int durationTicks) {
        if (active && durationTicks > 0) {
            var level = Minecraft.getInstance().level;
            long now = level != null ? level.getGameTime() : System.currentTimeMillis() / 50;
            WINDOWS.put(player.getUUID(), new Window(now + durationTicks));
        } else {
            WINDOWS.remove(player.getUUID());
        }
    }

    /** 当前是否处于奔雷袭 AoE 窗口内 */
    public static boolean isActive(Player player) {
        Window w = WINDOWS.get(player.getUUID());
        if (w == null) return false;
        var level = Minecraft.getInstance().level;
        if (level != null && level.getGameTime() > w.endTick) {
            WINDOWS.remove(player.getUUID());
            return false;
        }
        return true;
    }
}
