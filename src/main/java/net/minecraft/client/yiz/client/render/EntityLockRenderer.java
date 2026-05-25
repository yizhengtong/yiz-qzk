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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 锁定目标图标渲染器 — 视野前方 60° 内最近存活实体碰撞箱中点绘制 billboard 图标。
 */
public final class EntityLockRenderer {

    private static final ResourceLocation LOCK_ICON =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_icon.png");

    private static final float SIZE = 0.5f;
    private static final double RANGE = 16.0;

    private EntityLockRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.level instanceof ClientLevel cl)) return;

        // 沿视线射线追踪第一个命中实体（精确瞄准）
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(RANGE));
        AABB searchBox = mc.player.getBoundingBox().expandTowards(lookVec.scale(RANGE)).inflate(1.0);

        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
            mc.player, eyePos, endPos, searchBox,
            e -> e instanceof LivingEntity && e != mc.player && e.isAlive(),
            RANGE * RANGE
        );
        if (hitResult == null) return;
        Entity target = hitResult.getEntity();
        if (target == null) return;

        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = camera.getPosition();

        Vec3 center = new Vec3(target.getX(), target.getBoundingBox().maxY + 0.8, target.getZ());

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
