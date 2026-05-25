package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
 * 锁定目标图标渲染器 — 沿玩家视线射线追踪目标，在实体碰撞箱垂直中点绘制
 * 始终保持面对玩家的 billboard 图标，保持固定屏幕空间大小。
 */
public final class EntityLockRenderer {

    private static final ResourceLocation LOCK_ICON =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_icon.png");

    private static final double RANGE = 32.0;
    private static final float SCREEN_SIZE = 0.4f; // 固定屏幕比例大小

    private EntityLockRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 射线追踪
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
        Vec3 camPos = camera.getPosition();
        // 目标碰撞箱垂直中点（从上到下高度一半）
        var bb = target.getBoundingBox();
        Vec3 worldPos = new Vec3(target.getX(), (bb.minY + bb.maxY) * 0.5, target.getZ());

        // 计算距离 → 缩放（屏幕大小固定）
        float dist = (float) worldPos.distanceTo(camPos);
        float scale = Math.max(0.1f, dist * SCREEN_SIZE);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // 世界坐标 → 相对相机
        poseStack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        // 旋转到面对相机的 billboard
        poseStack.mulPose(camera.rotation());

        // 上传矩阵到 RenderSystem
        RenderSystem.getModelViewStack().set(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, LOCK_ICON);

        float h = scale / 2;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(-h, -h, 0).setUv(0, 0);
        builder.addVertex( h, -h, 0).setUv(1, 0);
        builder.addVertex( h,  h, 0).setUv(1, 1);
        builder.addVertex(-h,  h, 0).setUv(0, 1);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        poseStack.popPose();
        // 恢复 RenderSystem 矩阵
        RenderSystem.getModelViewStack().set(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
