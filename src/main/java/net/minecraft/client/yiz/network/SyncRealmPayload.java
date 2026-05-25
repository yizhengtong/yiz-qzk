package net.minecraft.client.yiz.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 境界同步包 (S2C)
 * 服务端 → 客户端：同步玩家当前境界 ID。
 * 在突破时自动发送，或在登录/重生时手动发送。
 */
public record SyncRealmPayload(
    String realmStageId
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncRealmPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "sync_realm"));

    public static final StreamCodec<FriendlyByteBuf, SyncRealmPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncRealmPayload decode(FriendlyByteBuf buf) {
            return new SyncRealmPayload(buf.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncRealmPayload payload) {
            buf.writeUtf(payload.realmStageId);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：更新本地境界状态 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            PlayerDataAPI.set(player, RealmProgressionAPI.DATA_KEY, realmStageId);
        });
    }
}
