package net.minecraft.client.yiz.network;

import net.minecraft.client.yiz.api.ShockedEntityAPI;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 感电视觉 S2C 事件包 — 服务端在感电触发点发，客户端收包渲染。
 *
 * <p>两种事件（{@link #kind}）：
 * <ul>
 *   <li><b>0 = APPLY</b>：实体体表游离电弧 + surface 着色器叠加（带去重），持续 = durationTicks</li>
 *   <li><b>1 = BURST</b>：center→targetIds 链式闪电电弧（短寿命，不叠加体表电弧）。
 *       体表电弧统一走 kind=0 去重路径。</li>
 * </ul>
 */
public record S2CShockFxPayload(
    int kind,               // 0=APPLY(体表电流+去重), 1=BURST(链式电弧)
    int centerId,           // entity.getId()：APPLY=目标实体; BURST=电弧起点
    int durationTicks,      // APPLY=体表持续tick; BURST=未使用
    List<Integer> targetIds // BURST=电弧终点实体列表; APPLY=空
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CShockFxPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "s2c_shock_fx"));

    public static final StreamCodec<FriendlyByteBuf, S2CShockFxPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public S2CShockFxPayload decode(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            int centerId = buf.readVarInt();
            int durationTicks = buf.readVarInt();
            int n = buf.readVarInt();
            List<Integer> targetIds = new ArrayList<>(n);
            for (int i = 0; i < n; i++) targetIds.add(buf.readVarInt());
            return new S2CShockFxPayload(kind, centerId, durationTicks, targetIds);
        }

        @Override
        public void encode(FriendlyByteBuf buf, S2CShockFxPayload payload) {
            buf.writeVarInt(payload.kind);
            buf.writeVarInt(payload.centerId);
            buf.writeVarInt(payload.durationTicks);
            buf.writeVarInt(payload.targetIds.size());
            for (int id : payload.targetIds) buf.writeVarInt(id);
        }
    };

    /** 实体 ID → 体表电弧过期时间戳（ms），防重复堆积。 */
    private static final ConcurrentHashMap<Integer, Long> SURFACE_ARC_EXPIRY = new ConcurrentHashMap<>();
    /** 格子坐标(long) → 体表电弧过期时间戳，同格多实体只渲染一份体表电流。 */
    private static final ConcurrentHashMap<Long, Long> CELL_SURFACE_EXPIRY = new ConcurrentHashMap<>();
    /** (fromId, toId) 配对 → 链式电弧过期时间戳，防密集 AoE 电弧堆积。 */
    private static final ConcurrentHashMap<Long, Long> ARC_PAIR_EXPIRY = new ConcurrentHashMap<>();
    private static long lastCleanupMs = 0L;

    private static long cellKey(double x, double y, double z) {
        int ix = (int)Math.floor(x), iy = (int)Math.floor(y), iz = (int)Math.floor(z);
        return ((long)ix << 42) ^ ((long)iy << 21) ^ (long)iz;
    }
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;

            long nowMs = System.currentTimeMillis();
            // 每 500ms 清理一次过期条目，防止内存泄漏
            if (nowMs - lastCleanupMs > 500L) {
                lastCleanupMs = nowMs;
                SURFACE_ARC_EXPIRY.values().removeIf(exp -> exp < nowMs);
                CELL_SURFACE_EXPIRY.values().removeIf(exp -> exp < nowMs);
                ARC_PAIR_EXPIRY.values().removeIf(exp -> exp < nowMs);
            }

            float lifeSec = durationTicks / 20f;

            if (kind == 0) {
                // APPLY：体表游离电弧 + surface 着色器叠加
                // 两层去重：①同实体1s内跳过 ②同格子500ms内跳过
                Long expiry = SURFACE_ARC_EXPIRY.get(centerId);
                if (expiry != null && nowMs < expiry) return;
                SURFACE_ARC_EXPIRY.put(centerId, nowMs + (long)(lifeSec * 1000));

                Entity target = mc.level.getEntity(centerId);
                if (target == null) return;

                // 同格去重：多个实体在同一格子只渲染一份体表电流
                long cell = cellKey(target.getX(), target.getY(), target.getZ());
                Long cellExp = CELL_SURFACE_EXPIRY.get(cell);
                if (cellExp != null && nowMs < cellExp) return;
                CELL_SURFACE_EXPIRY.put(cell, nowMs + 500L);

                LightningFX.spawnSurfaceArc(target, lifeSec, 0.028f,
                        LightningFX.DEFAULT_R, LightningFX.DEFAULT_G, LightningFX.DEFAULT_B);
                ShockedEntityAPI.putClient(centerId, durationTicks);
            } else if (kind == 1) {
                // BURST：链式电弧 — targetIds 已按远→近排序，闪电从最远往中心汇聚
                Entity center = mc.level.getEntity(centerId);
                if (center == null) return;
                // 从最远实体开始，逐段连回中心
                Vec3 prev = null;
                for (int tid : targetIds) {
                    Entity t = mc.level.getEntity(tid);
                    if (!(t instanceof LivingEntity tle)) continue;
                    Vec3 pos = tle.position().add(0, tle.getBbHeight() * 0.5, 0);
                    if (prev != null) {
                        LightningFX.spawnArc(prev, pos, 0.5f, 0.05f,
                                LightningFX.DEFAULT_R, LightningFX.DEFAULT_G, LightningFX.DEFAULT_B);
                    }
                    prev = pos;
                }
                // 最后一条：最近实体 → 中心，汇聚收束
                if (prev != null) {
                    Vec3 centerPos = center.position().add(0, center.getBbHeight() * 0.5, 0);
                    LightningFX.spawnArc(prev, centerPos, 0.5f, 0.05f,
                            LightningFX.DEFAULT_R, LightningFX.DEFAULT_G, LightningFX.DEFAULT_B);
                }
            }
        });
    }
}
