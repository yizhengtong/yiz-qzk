package net.minecraft.client.yiz.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体锁定 API — 服务端追踪 + 自动同步到客户端渲染。
 * 支持充能进度（透明度渐变）和就绪状态（红色框）。
 */
// 大白话: 锁定方法
public final class EntityLockAPI {

    private EntityLockAPI() {}

    private static final ConcurrentHashMap<UUID, UUID> LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Float> CHARGES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> READY = new ConcurrentHashMap<>();

    /**
     * 锁定目标 + 充能状态。
     * @param charge 0~1，充能进度（0=完全透明，1=完全不透明）
     * @param ready true=充能完成，框变红
     */
    public static void lock(Player player, Entity target, float charge, boolean ready) {
        if (player.level().isClientSide) return;
        UUID puid = player.getUUID();
        LOCKS.put(puid, target.getUUID());
        CHARGES.put(puid, charge);
        READY.put(puid, ready);
        sync((ServerPlayer) player, target.getUUID(), charge, ready, true);
    }

    public static void unlock(Player player) {
        if (player.level().isClientSide) return;
        UUID puid = player.getUUID();
        LOCKS.remove(puid);
        CHARGES.remove(puid);
        READY.remove(puid);
        sync((ServerPlayer) player, null, 0, false, false);
    }

    public static UUID getLockedTargetUuid(Player player) {
        return LOCKS.get(player.getUUID());
    }

    public static boolean hasLock(Player player) {
        return LOCKS.containsKey(player.getUUID());
    }

    // ==================== 同步 ====================

    private static void sync(ServerPlayer player, UUID targetUuid, float charge, boolean ready, boolean locked) {
        var payload = new net.minecraft.client.yiz.network.SyncLockPayload(targetUuid, charge, ready, locked);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    // ==================== 客户端缓存 ====================

    static final ConcurrentHashMap<UUID, ClientLockEntry> CLIENT_CACHE = new ConcurrentHashMap<>();

    public record ClientLockEntry(UUID targetUuid, float charge, boolean ready) {}

    public static void putClient(UUID playerUuid, UUID targetUuid, float charge, boolean ready) {
        if (targetUuid == null) {
            CLIENT_CACHE.remove(playerUuid);
        } else {
            CLIENT_CACHE.put(playerUuid, new ClientLockEntry(targetUuid, charge, ready));
        }
    }

    public static ClientLockEntry getClient() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return null;
        return CLIENT_CACHE.get(mc.player.getUUID());
    }

    public static void clearAll() {
        LOCKS.clear();
        CHARGES.clear();
        READY.clear();
        CLIENT_CACHE.clear();
    }
}
