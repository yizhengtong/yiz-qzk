package net.minecraft.client.yiz.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.yiz.handler.MultiJumpTracker;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 客户端请求消耗一次多段跳（空中再跳）。
 * 服务端权威消耗剩余次数，通过 PlayerDataAPI 自动 S2C 同步纠正客户端。
 */
public record C2SMultiJumpPayload() implements CustomPacketPayload {

    public static final Type<C2SMultiJumpPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "multijump"));

    public static final StreamCodec<ByteBuf, C2SMultiJumpPayload> STREAM_CODEC =
        StreamCodec.unit(new C2SMultiJumpPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送。 */
    public static void send() {
        PacketDistributor.sendToServer(new C2SMultiJumpPayload());
    }

    /** 服务端接收：权威消耗一次多段跳。 */
    public static void handle(C2SMultiJumpPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            MultiJumpTracker.tryConsume(sp);
        });
    }
}
