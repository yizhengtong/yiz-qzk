package net.minecraft.client.yiz.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.yiz.core.CreativeProtectionHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 客户端请求快速重生（30 秒无敌）。
 * 服务端收到后标记玩家 UUID，下次 PlayerRespawnEvent 时给予 30 秒保护态。
 */
public record C2SFastRespawnPayload() implements CustomPacketPayload {

    public static final Type<C2SFastRespawnPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "fast_respawn"));

    public static final StreamCodec<ByteBuf, C2SFastRespawnPayload> STREAM_CODEC =
        StreamCodec.unit(new C2SFastRespawnPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送 */
    public static void send() {
        PacketDistributor.sendToServer(new C2SFastRespawnPayload());
    }

    /** 服务端接收：仅标记 UUID，重生由原版逻辑处理，标记在 PlayerRespawnEvent 中消费。 */
    public static void handle(C2SFastRespawnPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            CreativeProtectionHandler.markFastRespawn(sp.getUUID());
        });
    }
}
