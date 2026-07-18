package net.minecraft.client.yiz.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C：奔雷袭 AoE 窗口同步包。
 * 服务端 tickBenleixi 创建/续期窗口时发送，客户端收到后本地计时，
 * AutoAttackMixin 在窗口期内自动蓄力攻击。
 *
 * @param active true=窗口激活，客户端开始自动攻击；false=窗口结束
 * @param durationTicks 窗口剩余 tick 数（active=true 时有效）
 */
public record S2CBenleixiWindowPayload(
    boolean active,
    int durationTicks
) implements CustomPacketPayload {

    public static final Type<S2CBenleixiWindowPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "benleixi_window"));

    public static final StreamCodec<FriendlyByteBuf, S2CBenleixiWindowPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public S2CBenleixiWindowPayload decode(FriendlyByteBuf buf) {
                return new S2CBenleixiWindowPayload(buf.readBoolean(), buf.readVarInt());
            }

            @Override
            public void encode(FriendlyByteBuf buf, S2CBenleixiWindowPayload p) {
                buf.writeBoolean(p.active);
                buf.writeVarInt(p.durationTicks);
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ── 客户端处理 ──

    /** 客户端收到后更新本地窗口计时器 */
    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            BenleixiWindowClientTracker.set(player, active, durationTicks);
        });
    }

    // ── 服务端发送 ──

    /** 窗口激活时发送 */
    public static void sendWindowStart(ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayer(player,
            new S2CBenleixiWindowPayload(true, durationTicks));
    }

    /** 窗口结束时发送 */
    public static void sendWindowEnd(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new S2CBenleixiWindowPayload(false, 0));
    }
}
