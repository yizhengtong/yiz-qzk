package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 锁定同步包 (S2C)
 * 服务端 → 客户端：通知客户端当前玩家的锁定目标。
 */
public record SyncLockPayload(
    UUID targetUuid,
    ResourceLocation icon,
    boolean locked
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncLockPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "sync_lock"));

    public static final StreamCodec<FriendlyByteBuf, SyncLockPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncLockPayload decode(FriendlyByteBuf buf) {
            boolean locked = buf.readBoolean();
            if (locked) {
                UUID targetUuid = buf.readUUID();
                ResourceLocation icon = buf.readResourceLocation();
                return new SyncLockPayload(targetUuid, icon, true);
            }
            return new SyncLockPayload(null, null, false);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncLockPayload payload) {
            buf.writeBoolean(payload.locked);
            if (payload.locked && payload.targetUuid != null && payload.icon != null) {
                buf.writeUUID(payload.targetUuid);
                buf.writeResourceLocation(payload.icon);
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (locked && targetUuid != null && icon != null) {
                EntityLockAPI.putClient(
                    net.minecraft.client.Minecraft.getInstance().player.getUUID(),
                    targetUuid, icon
                );
            } else {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    EntityLockAPI.putClient(mc.player.getUUID(), null, null);
                }
            }
        });
    }
}
