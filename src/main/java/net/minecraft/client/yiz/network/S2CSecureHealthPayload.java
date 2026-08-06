package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.tool.health.SecureHealthClosure;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 血量隐匿同步 S2C 包 — 服务端把真实血量发给客户端，客户端更新本地实体闭包。
 *
 * <p>血量隐匿（{@code SECURE_PULSE} &gt; 0）实体的真实血量只在服务端闭包里，
 * vanilla 血量字段是诱饵（防内存扫描），DataParameter 同步给客户端的是诱饵。
 * 若客户端 {@code getHealth()} 读诱饵 → 血条/伤害反馈显示异常。
 * 本包把服务端真实血量经私有通道发给客户端闭包 → 客户端 getHealth 返回真值，
 * 血条/HUD/受伤反馈正常。</p>
 */
public record S2CSecureHealthPayload(
    int entityId,       // entity.getId()：目标实体
    float realHealth    // 服务端真实血量
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CSecureHealthPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "s2c_secure_health"));

    public static final StreamCodec<FriendlyByteBuf, S2CSecureHealthPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CSecureHealthPayload decode(FriendlyByteBuf buf) {
            return new S2CSecureHealthPayload(buf.readVarInt(), buf.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buf, S2CSecureHealthPayload payload) {
            buf.writeVarInt(payload.entityId);
            buf.writeFloat(payload.realHealth);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            if (mc.level.getEntity(entityId) instanceof LivingEntity le) {
                // 客户端同步外部表（服务端权威血量 → 客户端显示用；客户端不参与逻辑判定）
                if (SecureHealthClosure.isSecure(le)) {
                    SecureHealthClosure.setHealth(le, realHealth);
                }
            }
        });
    }
}
