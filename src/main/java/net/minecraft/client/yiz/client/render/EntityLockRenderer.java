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

        // 身体中心世界坐标
        Vec3 bodyCenter = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ());

        // 面向玩家的局部坐标系
        Vec3 forward = camPos.subtract(bodyCenter).normalize(); // Z = 指向玩家
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = new Vec3(worldUp.x, worldUp.y, worldUp.z).cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();

        // 框大小
        float dist = (float) bodyCenter.distanceTo(camPos);
        float t = Math.clamp((dist - 3f) / 9f, 0, 1);
        float hs = 0.5f * (0.4f + t * 0.6f);

        var bufferSource = mc.renderBuffers().bufferSource();
        var consumer = bufferSource.getBuffer(RenderType.LINES);

        float s = 0.06f;
        // 4 个角的局部坐标（上下左右），用局部坐标系展开成世界坐标
        float[][] localCorners = {{-hs, hs}, {hs, hs}, {hs, -hs}, {-hs, -hs}};
        for (float[] lc : localCorners) {
            Vec3 worldPos = bodyCenter.add(right.scale(lc[0])).add(up.scale(lc[1]));
            double rx = worldPos.x - camPos.x;
            double ry = worldPos.y - camPos.y;
            double rz = worldPos.z - camPos.z;

            PoseStack ps = new PoseStack();
            ps.translate(rx, ry, rz);
            LevelRenderer.renderLineBox(ps, consumer,
                new AABB(-s, -s, -s,  s,  s,  s), 1f, 0.2f, 0.2f, 1f);
        }
    }
}
