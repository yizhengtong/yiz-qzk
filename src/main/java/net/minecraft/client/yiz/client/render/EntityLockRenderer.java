package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 锁定目标瞄准框渲染器 — GUI 屏幕坐标渲染。
 * 将实体 3D 位置投影到屏幕后绘制 4 角框，不受视角转动影响。
 */
public final class EntityLockRenderer {

    private static final ResourceLocation[] CORNER_TEX = {
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tr.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_br.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_bl.png"),
    };

    private static final double RANGE = 32.0;
    private static final double CLOSE_DIST = 3.0;
    private static final double FAR_DIST = 12.0;

    private EntityLockRenderer() {}

    /**
     * 在 GUI 层渲染锁定框。由 ScreenEvent.Render.Post 调用。
     */
    public static void renderOverlay(PoseStack guiPoseStack) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 60° 圆锥找最接近屏幕中心的实体
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getLookAngle();
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

        // 实体碰撞箱中心
        var bb = target.getBoundingBox();
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cy = (bb.minY + bb.maxY) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        Vec3 worldPos = new Vec3(cx, cy, cz);

        // 投影到屏幕坐标
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        Vec3 projected = worldToScreen(worldPos, mc);

        if (projected == null) return; // 在屏幕外或背后

        float sx = (float) projected.x;
        float sy = (float) projected.y;

        // 框大小：基于距离缩放
        float dist = (float) worldPos.distanceTo(mc.player.getEyePosition());
        float t = Math.clamp((dist - (float)CLOSE_DIST) / (float)(FAR_DIST - CLOSE_DIST), 0, 1);
        float factor = 0.4f + t * 0.6f;
        float boxSize = (float) Math.max(bb.maxX - bb.minX, bb.maxY - bb.minY) * 0.6f * factor;

        // 屏幕空间中的框半边长（像素）
        float screenHalfSize = Math.max(8f, boxSize * 30f / Math.max(dist, 0.1f));

        // 屏幕 4 角位置
        float[][] corners = {
            {sx - screenHalfSize, sy - screenHalfSize},  // 左上
            {sx + screenHalfSize, sy - screenHalfSize},  // 右上
            {sx + screenHalfSize, sy + screenHalfSize},  // 右下
            {sx - screenHalfSize, sy + screenHalfSize},  // 左下
        };

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        float cornerSize = Math.max(4f, screenHalfSize * 0.3f); // 角片大小
        float chs = cornerSize / 2;

        for (int i = 0; i < 4; i++) {
            RenderSystem.setShaderTexture(0, CORNER_TEX[i]);
            float px = corners[i][0];
            float py = corners[i][1];

            BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex(guiPoseStack.last().pose(), px - chs, py - chs, 0).setUv(0, 0);
            builder.addVertex(guiPoseStack.last().pose(), px + chs, py - chs, 0).setUv(1, 0);
            builder.addVertex(guiPoseStack.last().pose(), px + chs, py + chs, 0).setUv(1, 1);
            builder.addVertex(guiPoseStack.last().pose(), px - chs, py + chs, 0).setUv(0, 1);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        }

        RenderSystem.disableBlend();
    }

    /** 3D 世界坐标 → 2D 屏幕坐标 */
    private static Vec3 worldToScreen(Vec3 worldPos, Minecraft mc) {
        var cam = mc.gameRenderer.getMainCamera();
        Vec3 relative = worldPos.subtract(cam.getPosition());
        var jfwd = cam.getLookVector();
        double dist = relative.x * jfwd.x() + relative.y * jfwd.y() + relative.z * jfwd.z();
        if (dist < 0.1) return null;

        var jleft = cam.getLeftVector();
        var jup = cam.getUpVector();

        double horiz = relative.x * (-jleft.x()) + relative.y * (-jleft.y()) + relative.z * (-jleft.z());
        double vert = relative.x * jup.x() + relative.y * jup.y() + relative.z * jup.z();

        double fov = mc.options.fov().get();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double viewScale = w / (2.0 * Math.tan(Math.toRadians(fov / 2.0)));

        double sx = w / 2.0 + horiz * viewScale / dist;
        double sy = h / 2.0 - vert * viewScale / dist;

        return new Vec3(sx, sy, 0);
    }
}
