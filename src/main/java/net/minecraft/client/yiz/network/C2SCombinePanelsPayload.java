package net.minecraft.client.yiz.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 客户端请求将两个留存光屏对应的箱子组合成一个 6 行大容器。
 * 服务端获取两个 BlockEntity 的 Container，构造 CompoundContainer + ChestMenu.sixRows，调用 player.openMenu。
 */
public record C2SCombinePanelsPayload(BlockPos leftPos, BlockPos rightPos) implements CustomPacketPayload {

    public static final Type<C2SCombinePanelsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "combine_panels"));

    public static final StreamCodec<ByteBuf, C2SCombinePanelsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, C2SCombinePanelsPayload::leftPos,
            BlockPos.STREAM_CODEC, C2SCombinePanelsPayload::rightPos,
            C2SCombinePanelsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送。 */
    public static void send(BlockPos left, BlockPos right) {
        PacketDistributor.sendToServer(new C2SCombinePanelsPayload(left, right));
    }

    /** 服务端接收：构造 CompoundContainer + ChestMenu.sixRows + openMenu。 */
    public static void handle(C2SCombinePanelsPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            var level = sp.serverLevel();
            var leftBe = level.getBlockEntity(payload.leftPos());
            var rightBe = level.getBlockEntity(payload.rightPos());
            if (!(leftBe instanceof BaseContainerBlockEntity leftChest)
                    || !(rightBe instanceof BaseContainerBlockEntity rightChest)) {
                sp.sendSystemMessage(Component.literal("§c拼凑失败：目标方块不是容器"));
                return;
            }
            if (!leftChest.canOpen(sp) || !rightChest.canOpen(sp)) return;

            if (leftChest instanceof RandomizableContainerBlockEntity rl) rl.unpackLootTable(sp);
            if (rightChest instanceof RandomizableContainerBlockEntity rr) rr.unpackLootTable(sp);
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new net.minecraft.client.yiz.CombinedContainerMenu(id, inv, leftChest, level),
                    Component.literal("组合容器")));
        });
    }
}
