package net.minecraft.client.yiz.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 锁定目标瞄准框 — 仿 F3+B 碰撞箱渲染，纯世界坐标。
 */
public final class EntityLockRenderer {

    private static final double RANGE = 32.0;

    private EntityLockRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 60° 圆锥找最接近屏幕中心的实体
        Vec3 eyePos = mc.player.getEyePosition();
        var lookVec = mc.player.getLookAngle();
        AABB searchBox = mc.player.getBoundingBox().inflate(RANGE);

        Entity target = null;
        double bestDot = 0.5;
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

        // 实体位置（相对相机）— 与 F3+B 碰撞箱完全相同的定位方式
        double ex = target.getX() - camPos.x;
        double ey = target.getY() - camPos.y;
        double ez = target.getZ() - camPos.z;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(ex, ey, ez);

        // 身体中心（实体局部坐标：X=0, Z=0, Y=半高偏下）
        float cy = (float)(target.getBbHeight() * 0.7f);

        // 固定缩放框大小（只随距离变化，不随实体大小）
        float dist = (float) target.position().distanceTo(camPos);
        float t = Math.clamp((dist - 3f) / 9f, 0, 1);
        float factor = 0.4f + t * 0.6f;
        float hs = 0.5f * factor;

        var bufferSource = mc.renderBuffers().bufferSource();
        var consumer = bufferSource.getBuffer(RenderType.LINES);

        // 4 个红色角点标记（固定正方形框）
        float s = 0.06f; // 小方块的半边长
        LevelRenderer.renderLineBox(poseStack, consumer,
            new AABB(-hs - s, cy + hs - s, -hs - s, -hs + s, cy + hs + s, -hs + s), 1f, 0.2f, 0.2f, 1f);
        LevelRenderer.renderLineBox(poseStack, consumer,
            new AABB( hs - s, cy + hs - s, -hs - s,  hs + s, cy + hs + s, -hs + s), 1f, 0.2f, 0.2f, 1f);
        LevelRenderer.renderLineBox(poseStack, consumer,
            new AABB( hs - s, cy - hs - s, -hs - s,  hs + s, cy - hs + s, -hs + s), 1f, 0.2f, 0.2f, 1f);
        LevelRenderer.renderLineBox(poseStack, consumer,
            new AABB(-hs - s, cy - hs - s, -hs - s, -hs + s, cy - hs + s, -hs + s), 1f, 0.2f, 0.2f, 1f);

        poseStack.popPose();
    }
}
