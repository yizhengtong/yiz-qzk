package net.minecraft.client.yiz.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 道宫数据网络同步包 — 把服务端的道宫列表推送到客户端。
 */
public record SyncDaoPalacePayload(List<DaoPalaceEntry> palaces) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncDaoPalacePayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "sync_dao_palace"));

    public static final StreamCodec<FriendlyByteBuf, SyncDaoPalacePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncDaoPalacePayload decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<DaoPalaceEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                entries.add(DaoPalaceEntry.decode(buf));
            }
            return new SyncDaoPalacePayload(entries);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncDaoPalacePayload payload) {
            buf.writeVarInt(payload.palaces.size());
            for (DaoPalaceEntry entry : payload.palaces) {
                entry.encode(buf);
            }
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：更新本地道宫缓存 */
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            SyncDaoPalaceClientCache.update(palaces);
        });
    }

    // ==================== 内部条目 ====================

    public record DaoPalaceEntry(
        ResourceLocation anchorId,
        BlockPos centerPos,
        int sideLength,
        int placedBlockCount,
        int totalAnchors,
        int costToExpand,
        double influenceRange
    ) {
        static DaoPalaceEntry decode(FriendlyByteBuf buf) {
            return new DaoPalaceEntry(
                ResourceLocation.parse(buf.readUtf()),
                BlockPos.STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readDouble()
            );
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(anchorId.toString());
            BlockPos.STREAM_CODEC.encode(buf, centerPos);
            buf.writeVarInt(sideLength);
            buf.writeVarInt(placedBlockCount);
            buf.writeVarInt(totalAnchors);
            buf.writeVarInt(costToExpand);
            buf.writeDouble(influenceRange);
        }
    }
}
