package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.core.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

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
            SyncPlayerDataPayload.TYPE,
            SyncPlayerDataPayload.STREAM_CODEC,
            SyncPlayerDataPayload::handle
        );
        registrar.playToClient(
            SyncLockPayload.TYPE,
            SyncLockPayload.STREAM_CODEC,
            SyncLockPayload::handle
        );
        // 快速重生：C2S 请求 30 秒无敌重生
        registrar.playToServer(
            C2SFastRespawnPayload.TYPE,
            C2SFastRespawnPayload.STREAM_CODEC,
            C2SFastRespawnPayload::handle
        );
        // 属性编辑台：C2S 应用属性
        registrar.playToServer(
            net.minecraft.client.yiz.editor.C2SAttributeEditorPayload.TYPE,
            net.minecraft.client.yiz.editor.C2SAttributeEditorPayload.STREAM_CODEC,
            net.minecraft.client.yiz.editor.C2SAttributeEditorPayload::handle
        );
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
}