package net.minecraft.client.yiz.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.core.registry.ModAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 玩家数据同步包 (S2C)
 * 服务端 → 客户端：同步某玩家的 PlayerDataAPI 全部数据。
 * 在 PlayerDataAPI.set() 被调用时自动发送。
 */
public record SyncPlayerDataPayload(
    String dataJson
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncPlayerDataPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "sync_player_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncPlayerDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncPlayerDataPayload decode(FriendlyByteBuf buf) {
            return new SyncPlayerDataPayload(buf.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncPlayerDataPayload payload) {
            buf.writeUtf(payload.dataJson);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ── 同步完成回调：下游模组监听，在 attachment 更新后做本地缓存清理 ──

    private static final java.util.List<Runnable> onSyncCallbacks = new java.util.ArrayList<>();

    public static void addSyncCallback(Runnable callback) {
        onSyncCallbacks.add(callback);
    }

    /** 客户端处理：将数据写入本地玩家的 Attachment，然后通知下游刷新缓存。 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            try {
                CompoundTag tag = TagParser.parseTag(dataJson);
                player.setData(ModAttachments.PLAYER_DATA_ATTACHMENT.get(), dataJson);
            } catch (Exception e) {
                // parse error, skip
            }
            // 通知下游（如 yizxian1.21.1 的 AccessoryContainer）数据已刷新
            for (Runnable cb : onSyncCallbacks) {
                try { cb.run(); } catch (Exception ex) { /* 不阻断后续回调 */ }
            }
        });
    }
}
