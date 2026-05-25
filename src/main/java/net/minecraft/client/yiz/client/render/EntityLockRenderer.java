package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class EntityLockRenderer {

    private static final double BODY_HEIGHT_FACTOR = 0.7;
    private static final float SIZE_BASE = 0.5f;
    private static final float SIZE_NEAR_MIN = 0.4f;
    private static final float SIZE_NEAR_MAX = 1.0f;
    private static final float SIZE_RANGE = 12f;
    private static final double ROTATION_SPEED = Math.PI / 6;

    private static final float CORNER_TEX_SIZE = 0.15f;
    private static final ResourceLocation[] CORNER_TEX = {
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tr.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_bl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_br.png"),
    };

    private EntityLockRenderer() {}

    // ════════════════════════════════════════════
    //  API
    // ════════════════════════════════════════════
    public static float getScaleFactor(float dist) {
        float t = Math.clamp(dist, 0, SIZE_RANGE) / SIZE_RANGE;
        return SIZE_NEAR_MIN + t * (SIZE_NEAR_MAX - SIZE_NEAR_MIN);
    }

    // ════════════════════════════════════════════
    //  渲染入口
    // ════════════════════════════════════════════

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.level instanceof ClientLevel cl)) return;

        // 从 API 读取当前玩家的锁数据
        var entry = EntityLockAPI.getClient();
        if (entry == null) return;

        Entity target = null;
        for (var e : cl.entitiesForRendering()) {
            if (e != null && e.getUUID().equals(entry.targetUuid())) {
                target = e;
                break;
            }
        }
        if (target == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        Vec3 bodyCenter = new Vec3(
            target.getX(), target.getY() + target.getBbHeight() * BODY_HEIGHT_FACTOR, target.getZ());
        Vec3 forward = camPos.subtract(bodyCenter).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();

        double angle = (System.currentTimeMillis() / 1000.0) * ROTATION_SPEED;
        double cosa = Math.cos(angle), sina = Math.sin(angle);
        Vec3 r = right.scale(cosa).add(up.scale(sina));
        Vec3 u = right.scale(-sina).add(up.scale(cosa));

        float dist = (float) bodyCenter.distanceTo(camPos);
        float hs = SIZE_BASE * getScaleFactor(dist);

        // 充能进度 → alpha；就绪 → 红色
        float alpha = entry.charge();
        boolean ready = entry.ready();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        PoseStack ps = event.getPoseStack();
        float[][] corners = {{-hs, hs}, {hs, hs}, {hs, -hs}, {-hs, -hs}};
        for (int i = 0; i < 4; i++) {
            Vec3 worldPos = bodyCenter.add(r.scale(corners[i][0])).add(u.scale(corners[i][1]));
            ps.pushPose();
            ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
            ps.mulPose(camera.rotation());

            RenderSystem.setShaderTexture(0, CORNER_TEX[i]);
            // 就绪 = 红色，充电 = 白色渐变
            if (ready) {
                RenderSystem.setShaderColor(1f, 0.2f, 0.2f, alpha);
            } else {
                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            }
            float cs = CORNER_TEX_SIZE;
            BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(ps.last().pose(), -cs, -cs, 0).setUv(0, 0);
            builder.addVertex(ps.last().pose(),  cs, -cs, 0).setUv(1, 0);
            builder.addVertex(ps.last().pose(),  cs,  cs, 0).setUv(1, 1);
            builder.addVertex(ps.last().pose(), -cs,  cs, 0).setUv(0, 1);
            BufferUploader.drawWithShader(builder.buildOrThrow());
            ps.popPose();
        }

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
