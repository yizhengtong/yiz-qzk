package net.minecraft.client.yiz.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体锁定 API — 服务端追踪 + 自动同步到客户端渲染。
 * <p>
 * 任意效果可在锁定/解锁时调用此 API，客户端自动在锁定实体上方渲染图标。
 * </p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * EntityLockAPI.lock(player, target, ICON);
 * // ... 渲染器自动在 target 碰撞箱中点画 ICON ...
 * EntityLockAPI.unlock(player);
 * }</pre>
 */
public final class EntityLockAPI {

    private EntityLockAPI() {}

    // playerUUID → targetEntityUUID
    private static final ConcurrentHashMap<UUID, UUID> LOCKS = new ConcurrentHashMap<>();
    // playerUUID → icon ResourceLocation
    private static final ConcurrentHashMap<UUID, ResourceLocation> ICONS = new ConcurrentHashMap<>();

    /**
     * 锁定目标实体，客户端会在目标上渲染图标。
     * 已锁定时再次调用会更新目标。
     */
    public static void lock(Player player, Entity target, ResourceLocation icon) {
        if (player.level().isClientSide) return;
        UUID puid = player.getUUID();
        LOCKS.put(puid, target.getUUID());
        ICONS.put(puid, icon);
        sync((ServerPlayer) player, target.getUUID(), icon, true);
    }

    /**
     * 解锁，移除图标。
     */
    public static void unlock(Player player) {
        if (player.level().isClientSide) return;
        UUID puid = player.getUUID();
        LOCKS.remove(puid);
        ICONS.remove(puid);
        sync((ServerPlayer) player, null, null, false);
    }

    /** 获取当前锁定目标 UUID，没有返回 null */
    public static UUID getLockedTargetUuid(Player player) {
        return LOCKS.get(player.getUUID());
    }

    /** 获取当前锁定图标，没有返回 null */
    public static ResourceLocation getLockIcon(Player player) {
        return ICONS.get(player.getUUID());
    }

    /** 检查玩家是否有锁定目标 */
    public static boolean hasLock(Player player) {
        return LOCKS.containsKey(player.getUUID());
    }

    // ==================== 同步到客户端 ====================

    private static void sync(ServerPlayer player, UUID targetUuid, ResourceLocation icon, boolean locked) {
        var payload = new net.minecraft.client.yiz.network.SyncLockPayload(targetUuid, icon, locked);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    /** 客户端缓存（由 SyncLockPayload.handle 填充） */
    static final ConcurrentHashMap<UUID, ClientLockEntry> CLIENT_CACHE = new ConcurrentHashMap<>();

    public record ClientLockEntry(UUID targetUuid, ResourceLocation icon) {}

    /** 由客户端网络包处理写入 */
    public static void putClient(UUID playerUuid, UUID targetUuid, ResourceLocation icon) {
        if (targetUuid == null || icon == null) {
            CLIENT_CACHE.remove(playerUuid);
        } else {
            CLIENT_CACHE.put(playerUuid, new ClientLockEntry(targetUuid, icon));
        }
    }

    /** 客户端读取（供渲染器使用） */
    public static ClientLockEntry getClient() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return null;
        return CLIENT_CACHE.get(mc.player.getUUID());
    }

    /**
     * 清除所有锁定（世界退出时调用）。
     */
    public static void clearAll() {
        LOCKS.clear();
        ICONS.clear();
        CLIENT_CACHE.clear();
    }
}
