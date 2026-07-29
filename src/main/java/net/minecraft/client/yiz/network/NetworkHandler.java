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
        // 感电视觉：S2C 事件包（体表游离电弧 + 链式闪电）
        registrar.playToClient(
            S2CShockFxPayload.TYPE,
            S2CShockFxPayload.STREAM_CODEC,
            S2CShockFxPayload::handle
        );
        // 卢登激荡溅射视觉：S2C（死亡目标体表 + center→victim 闪电链 + victim 体表）
        registrar.playToClient(
            S2CLudenFxPayload.TYPE,
            S2CLudenFxPayload.STREAM_CODEC,
            S2CLudenFxPayload::handle
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
        // 技能释放：C2S 请求施放
        registrar.playToServer(
            C2SSkillCastPayload.TYPE,
            C2SSkillCastPayload.STREAM_CODEC,
            C2SSkillCastPayload::handle
        );
        // 多段跳：C2S 请求消耗一次空中跳
        registrar.playToServer(
            C2SMultiJumpPayload.TYPE,
            C2SMultiJumpPayload.STREAM_CODEC,
            C2SMultiJumpPayload::handle
        );
        // 奔雷袭窗口同步：S2C 通知客户端窗口期间自动攻击
        registrar.playToClient(
            S2CBenleixiWindowPayload.TYPE,
            S2CBenleixiWindowPayload.STREAM_CODEC,
            S2CBenleixiWindowPayload::handle
        );
        registrar.playToServer(
            net.minecraft.client.yiz.editor.C2SSkillEnhancePayload.TYPE,
            net.minecraft.client.yiz.editor.C2SSkillEnhancePayload.STREAM_CODEC,
            net.minecraft.client.yiz.editor.C2SSkillEnhancePayload::handle
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

    /** 卢登激荡溅射视觉：向 center 附近 64 格内玩家广播特效包。 */
    public static void sendLudenFx(net.minecraft.server.level.ServerLevel level,
                                    net.minecraft.world.phys.Vec3 center, int centerId,
                                    java.util.List<Integer> victimIds) {
        var pkt = new S2CLudenFxPayload(centerId, center.x, center.y, center.z, victimIds);
        double maxDistSq = 64.0 * 64.0;
        for (var sp : level.players()) {
            if (sp.distanceToSqr(center) <= maxDistSq) {
                PacketDistributor.sendToPlayer(sp, pkt);
            }
        }
    }
}