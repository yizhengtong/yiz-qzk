package net.minecraft.client.yiz.editor;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: 客户端请求对属性编辑台放置槽中的物品应用属性值。
 *
 * <p>玩家在属性编辑台 GUI 中点击 ③ 某行属性 → 客户端发此 payload
 * → 服务端从 BlockEntity 取出放置槽物品 → 调用对应 setter → 写回 → 同步。</p>
 */
public record C2SAttributeEditorPayload(String attrId, double value)
        implements CustomPacketPayload {

    public static final Type<C2SAttributeEditorPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "attr_editor_apply"));

    public static final StreamCodec<ByteBuf, C2SAttributeEditorPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SAttributeEditorPayload::attrId,
            ByteBufCodecs.DOUBLE,      C2SAttributeEditorPayload::value,
            C2SAttributeEditorPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送 */
    public static void send(String attrId, double value) {
        PacketDistributor.sendToServer(new C2SAttributeEditorPayload(attrId, value));
    }

    /** 服务端接收 */
    public static void handle(C2SAttributeEditorPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof AttributeEditorMenu menu)) return;

            // 从 BlockEntity 取放置槽物品
            var container = menu.getContainer();
            ItemStack stack = container.getItem(0);
            if (stack.isEmpty()) return;

            // 查 EditableAttribute（含下游注册的 EffectTag）→ 调 setter
            EditableAttribute attr = EditableAttribute.getAll().stream()
                .filter(a -> a.id().equals(payload.attrId))
                .findFirst().orElse(null);
            if (attr == null) return;

            attr.setter().accept(stack, payload.value);
            container.setItem(0, stack);
            menu.broadcastChanges();
        });
    }
}
