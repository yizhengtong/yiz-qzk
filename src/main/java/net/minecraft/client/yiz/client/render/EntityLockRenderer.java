package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 锁定目标图标渲染器 — 视野前方 60° 内最近存活实体碰撞箱中点绘制 billboard 图标。
 */
public final class EntityLockRenderer {

    private static final ResourceLocation LOCK_ICON =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_icon.png");

    private static final float SIZE = 0.4f;
    private static final double RANGE = 16.0;
    private static final double ANGLE_COS = 0.5; // cos(60°)

    private EntityLockRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.level instanceof ClientLevel cl)) return;

        // 视野前方 60° 内找最近存活实体
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getLookAngle();
        Entity target = null;
        double closestDist = RANGE * RANGE;

        AABB searchBox = mc.player.getBoundingBox().inflate(RANGE);
        for (Entity e : cl.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive() || !(e instanceof LivingEntity)) continue;

            Vec3 toEntity = e.position().subtract(eyePos);
            double distSqr = toEntity.lengthSqr();
            if (distSqr > closestDist) continue;

            // 角度检查：dot(lookVec, toEntity.normalized()) >= cos(60°)
            double dot = lookVec.dot(toEntity) / Math.sqrt(distSqr);
            if (dot >= ANGLE_COS) {
                closestDist = distSqr;
                target = e;
            }
        }
        if (target == null) return;

        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = camera.getPosition();

        Vec3 center = target.getBoundingBox().getCenter();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, LOCK_ICON);

        poseStack.pushPose();
        poseStack.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
        poseStack.mulPose(camera.rotation());

        float h = SIZE / 2;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(-h, -h, 0).setUv(0, 0);
        builder.addVertex( h, -h, 0).setUv(1, 0);
        builder.addVertex( h,  h, 0).setUv(1, 1);
        builder.addVertex(-h,  h, 0).setUv(0, 1);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
