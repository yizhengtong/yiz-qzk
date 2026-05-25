package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 锁定目标图标渲染器 — 4 角片包围碰撞箱的瞄准框。
 * <p>
 * 近处角片相对碰撞箱内缩，远处完全贴合碰撞箱四角。
 * </p>
 */
public final class EntityLockRenderer {

    private static final ResourceLocation[] CORNER_TEX = {
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tl.png"),  // 左上
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tr.png"),  // 右上
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_br.png"),  // 右下
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_bl.png"),  // 左下
    };

    private static final double RANGE = 32.0;
    private static final double CLOSE_DIST = 3.0;
    private static final double FAR_DIST = 12.0;

    private EntityLockRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 60° 圆锥内找最接近屏幕中心的实体
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getLookAngle();
        AABB searchBox = mc.player.getBoundingBox().inflate(RANGE);

        Entity target = null;
        double bestDot = 0.5; // cos(60°) 门槛
        for (Entity e : mc.level.getEntities(mc.player, searchBox,
                e -> e instanceof LivingEntity && e != mc.player && e.isAlive())) {
            Vec3 toEntity = e.position().subtract(eyePos);
            double distSqr = toEntity.lengthSqr();
            if (distSqr > RANGE * RANGE) continue;
            double dot = lookVec.dot(toEntity) / Math.sqrt(distSqr);
            if (dot > bestDot) {
                bestDot = dot;
                target = e;
            }
        }
        if (target == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        var bb = target.getBoundingBox();

        // 碰撞箱中心 + 正方形框大小
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cy = (bb.minY + bb.maxY) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        float boxSize = (float) Math.max(bb.maxX - bb.minX, bb.maxY - bb.minY) * 0.6f;

        // 距离比例缩放
        float dist = (float) camPos.distanceTo(new Vec3(cx, cy, cz));
        float t = Math.clamp((dist - (float)CLOSE_DIST) / (float)(FAR_DIST - CLOSE_DIST), 0, 1);
        float factor = 0.4f + t * 0.6f;
        float hs = boxSize * factor * 0.5f;

        // 正方形 4 角
        Vec3[] corners = {
            new Vec3(cx - hs, cy + hs, cz),  // 左上
            new Vec3(cx + hs, cy + hs, cz),  // 右上
            new Vec3(cx + hs, cy - hs, cz),  // 右下
            new Vec3(cx - hs, cy - hs, cz),  // 左下
        };

        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        for (int i = 0; i < 4; i++) {
            RenderSystem.setShaderTexture(0, CORNER_TEX[i]);

            var cornerPos = corners[i];
            float cSize = 0.25f; // 角片固定大小

            poseStack.pushPose();
            poseStack.translate(cornerPos.x - camPos.x, cornerPos.y - camPos.y, cornerPos.z - camPos.z);
            poseStack.mulPose(camera.rotation());

            RenderSystem.getModelViewStack().set(poseStack.last().pose());
            RenderSystem.applyModelViewMatrix();

            BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float chs = 0.125f; // 角片自身半尺寸
            builder.addVertex(-chs, -chs, 0).setUv(0, 0);
            builder.addVertex( chs, -chs, 0).setUv(1, 0);
            builder.addVertex( chs,  chs, 0).setUv(1, 1);
            builder.addVertex(-chs,  chs, 0).setUv(0, 1);
            BufferUploader.drawWithShader(builder.buildOrThrow());

            poseStack.popPose();
        }

        // 恢复 RenderSystem 矩阵
        RenderSystem.getModelViewStack().set(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
