package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record SyncLockPayload(
    UUID targetUuid,
    float charge,
    boolean ready,
    boolean locked
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncLockPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "sync_lock"));

    public static final StreamCodec<FriendlyByteBuf, SyncLockPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncLockPayload decode(FriendlyByteBuf buf) {
            boolean locked = buf.readBoolean();
            if (locked) {
                return new SyncLockPayload(buf.readUUID(), buf.readFloat(), buf.readBoolean(), true);
            }
            return new SyncLockPayload(null, 0, false, false);
        }
        @Override
        public void encode(FriendlyByteBuf buf, SyncLockPayload payload) {
            buf.writeBoolean(payload.locked);
            if (payload.locked) {
                buf.writeUUID(payload.targetUuid);
                buf.writeFloat(payload.charge);
                buf.writeBoolean(payload.ready);
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;
            if (locked && targetUuid != null) {
                EntityLockAPI.putClient(mc.player.getUUID(), targetUuid, charge, ready);
            } else {
                EntityLockAPI.putClient(mc.player.getUUID(), null, 0, false);
            }
        });
    }
}
