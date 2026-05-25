package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.api.DaoPalace;
import net.minecraft.client.yiz.api.DaoPalaceAPI;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.core.registry.ModAttachments;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;
import java.util.Set;

/**
 * 网络处理器
 * 注册 CustomPacketPayload 类型，提供发送工具方法。
 */
public final class NetworkHandler {

    private NetworkHandler() {}

    /**
     * 在 RegisterPayloadHandlersEvent 中注册数据包类型。
     * 由 tizMod 在 mod 事件总线上订阅。
     */
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("yizmodqzk");
        registrar.playToClient(
            SyncUnlocksPayload.TYPE,
            SyncUnlocksPayload.STREAM_CODEC,
            SyncUnlocksPayload::handle
        );
        registrar.playToClient(
            SyncPlayerDataPayload.TYPE,
            SyncPlayerDataPayload.STREAM_CODEC,
            SyncPlayerDataPayload::handle
        );
        registrar.playToClient(
            SyncRealmPayload.TYPE,
            SyncRealmPayload.STREAM_CODEC,
            SyncRealmPayload::handle
        );
        registrar.playToClient(
            SyncDaoPalacePayload.TYPE,
            SyncDaoPalacePayload.STREAM_CODEC,
            SyncDaoPalacePayload::handle
        );
        registrar.playToClient(
            SyncLockPayload.TYPE,
            SyncLockPayload.STREAM_CODEC,
            SyncLockPayload::handle
        );
    }

    /**
     * 向指定玩家发送完整的解锁状态同步包。
     * 在玩家登录或 unlock() 时调用。
     */
    public static void syncPlayerUnlocks(ServerPlayer player) {
        Set<ResourceLocation> unlocked = UnlockManager.getUnlockedEffects(player.getUUID());
        var payload = new SyncUnlocksPayload(player.getUUID(), List.copyOf(unlocked));
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * 注册 PlayerDataAPI 同步回调。
     * 在 tizMod 初始化时调用一次，使每次 set() 自动同步到客户端。
     */
    public static void registerPlayerDataSync() {
        PlayerDataAPI.setSyncCallback((player, dataJson) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                var payload = new SyncPlayerDataPayload(dataJson);
                PacketDistributor.sendToPlayer(serverPlayer, payload);
            }
        });
    }

    /**
     * 注册境界同步回调。
     * 在 tizMod 初始化时调用一次，使每次 breakthrough() 自动同步到客户端。
     */
    public static void registerRealmSync() {
        RealmProgressionAPI.setSyncCallback((player, stageId) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                var payload = new SyncRealmPayload(stageId);
                PacketDistributor.sendToPlayer(serverPlayer, payload);
            }
        });
    }

    /**
     * 向指定玩家同步当前境界。
     * 在玩家登录或重生时调用。
     */
    public static void syncPlayerRealm(ServerPlayer player) {
        String stageId = net.minecraft.client.yiz.api.PlayerDataAPI.get(
            player, RealmProgressionAPI.DATA_KEY
        );
        if (stageId != null && !stageId.isEmpty()) {
            var payload = new SyncRealmPayload(stageId);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * 注册道宫同步回调。
     * 在 tizMod 初始化时调用一次，使每次 placeBlock/activateAnchor 自动同步到客户端。
     */
    public static void registerDaoPalaceSync() {
        DaoPalaceAPI.setSyncCallback((player, palaces) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                List<SyncDaoPalacePayload.DaoPalaceEntry> entries = new java.util.ArrayList<>();
                for (var p : palaces) {
                    entries.add(new SyncDaoPalacePayload.DaoPalaceEntry(
                        p.anchorId(),
                        p.centerPos(),
                        p.sideLength(),
                        p.placedBlocks().size(),
                        p.totalAnchors(),
                        p.costToExpand(),
                        p.influenceRange()
                    ));
                }
                var payload = new SyncDaoPalacePayload(entries);
                PacketDistributor.sendToPlayer(serverPlayer, payload);
            }
        });
    }

    /**
     * 向指定玩家同步所有道宫数据。
     * 在玩家登录或重生时调用。
     */
    public static void syncPlayerDaoPalaces(ServerPlayer player) {
        List<DaoPalace> palaces = DaoPalaceAPI.getPalaces(player);
        List<SyncDaoPalacePayload.DaoPalaceEntry> entries = new java.util.ArrayList<>();
        for (var p : palaces) {
            entries.add(new SyncDaoPalacePayload.DaoPalaceEntry(
                p.anchorId(),
                p.centerPos(),
                p.sideLength(),
                p.placedBlocks().size(),
                p.totalAnchors(),
                p.costToExpand(),
                p.influenceRange()
            ));
        }
        if (!entries.isEmpty()) {
            var payload = new SyncDaoPalacePayload(entries);
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
