package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.lightning.LightningFX;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 卢登激荡溅射视觉 S2C 包 — 服务端在击杀溅射时发，客户端画：
 * <ul>
 *   <li>死亡目标：体表闪电一闪（若客户端实体还在；life=0.4s）</li>
 *   <li>center(死亡位置) → 每个 victim：一道闪电链（spawnArc，0.4s）</li>
 *   <li>每个 victim：体表闪电 0.4s（spawnSurfaceArc）</li>
 * </ul>
 * <p>center 同时带位置（cx/cy/cz，闪电链起点，可靠）与 centerId（体表尝试，实体可能已消失则跳过）。</p>
 */
public record S2CLudenFxPayload(
    int centerId,               // 死亡目标 entityId（尝试体表，客户端拿不到则跳过）
    double cx, double cy, double cz,  // center 位置（闪电链起点）
    List<Integer> victimIds     // 溅射目标 entityId 列表
) implements CustomPacketPayload {

    public static final Type<S2CLudenFxPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "s2c_luden_fx"));

    public static final StreamCodec<FriendlyByteBuf, S2CLudenFxPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public S2CLudenFxPayload decode(FriendlyByteBuf buf) {
            int centerId = buf.readVarInt();
            double cx = buf.readDouble();
            double cy = buf.readDouble();
            double cz = buf.readDouble();
            int n = buf.readVarInt();
            List<Integer> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readVarInt());
            return new S2CLudenFxPayload(centerId, cx, cy, cz, ids);
        }
        @Override public void encode(FriendlyByteBuf buf, S2CLudenFxPayload p) {
            buf.writeVarInt(p.centerId);
            buf.writeDouble(p.cx);
            buf.writeDouble(p.cy);
            buf.writeDouble(p.cz);
            buf.writeVarInt(p.victimIds.size());
            for (int id : p.victimIds) buf.writeVarInt(id);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            // 死亡目标体表闪电（实体还在则显示，已消失则跳过）
            Entity ce = mc.level.getEntity(centerId);
            if (ce instanceof LivingEntity cle) {
                LightningFX.spawnSurfaceArc(cle, 0.4f, 0.028f,
                        LightningFX.DEFAULT_R, LightningFX.DEFAULT_G, LightningFX.DEFAULT_B);
            }

            Vec3 center = new Vec3(cx, cy, cz);
            // center → 每个 victim：闪电链 + victim 体表 0.4s
            for (int vid : victimIds) {
                Entity ve = mc.level.getEntity(vid);
                if (!(ve instanceof LivingEntity vle)) continue;
                Vec3 vp = vle.position().add(0, vle.getBbHeight() * 0.5, 0);
                LightningFX.spawnArc(center, vp, 0.4f, 0.05f,
                        LightningFX.DEFAULT_R, LightningFX.DEFAULT_G, LightningFX.DEFAULT_B);
                LightningFX.spawnSurfaceArc(vle, 0.4f, 0.028f,
                        LightningFX.DEFAULT_R, LightningFX.DEFAULT_G, LightningFX.DEFAULT_B);
            }
        });
    }
}
