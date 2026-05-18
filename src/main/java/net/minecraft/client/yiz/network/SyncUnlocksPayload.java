package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 解锁状态同步包 (S2C)
 * 服务端 → 客户端：同步某玩家的所有已解锁效果ID。
 * 在玩家登录时全量发送，或在 unlock() 调用时增量发送。
 */
public record SyncUnlocksPayload(
    UUID playerUuid,
    List<ResourceLocation> unlockedIds
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncUnlocksPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "sync_unlocks"));

    public static final StreamCodec<FriendlyByteBuf, SyncUnlocksPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncUnlocksPayload decode(FriendlyByteBuf buf) {
            UUID uuid = buf.readUUID();
            int size = buf.readVarInt();
            List<ResourceLocation> ids = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ids.add(ResourceLocation.parse(buf.readUtf()));
            }
            return new SyncUnlocksPayload(uuid, ids);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncUnlocksPayload payload) {
            buf.writeUUID(payload.playerUuid);
            buf.writeVarInt(payload.unlockedIds.size());
            for (ResourceLocation id : payload.unlockedIds) {
                buf.writeUtf(id.toString());
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：替换该玩家的解锁状态 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            UnlockManager.clearPlayer(playerUuid);
            for (ResourceLocation id : unlockedIds) {
                UnlockManager.unlock(playerUuid, id);
            }
        });
    }
}
