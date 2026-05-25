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

public final class EntityLockRenderer {

    private static final double RANGE = 32.0;

    private static final ResourceLocation[] CORNER_TEX = {
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tr.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_tl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_bl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_br.png"),
    };

    private EntityLockRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

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

        // 身体中心 + 面向玩家局部坐标系
        Vec3 bodyCenter = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ());
        Vec3 forward = camPos.subtract(bodyCenter).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();

        // 缓慢旋转（绕 forward 轴，约 30°/秒）
        double angle = (System.currentTimeMillis() / 1000.0) * Math.PI / 6;
        double cosa = Math.cos(angle);
        double sina = Math.sin(angle);
        Vec3 rotatedRight = right.scale(cosa).add(up.scale(sina));
        Vec3 rotatedUp = right.scale(-sina).add(up.scale(cosa));
        right = rotatedRight;
        up = rotatedUp;

        // 框大小 + 透明度（近处缩小虚化，12格外全尺寸）
        float dist = (float) bodyCenter.distanceTo(camPos);
        float t = Math.clamp(dist / 12f, 0, 1);
        float factor = 0.2f + t * 0.8f; // 近处 20%，远处 100%
        float hs = 0.5f * factor;
        float alpha;
        if (dist <= 3) alpha = 0f;
        else if (dist >= 12) alpha = 1f;
        else if (dist >= 9) alpha = 0.8f + (dist - 9) / 3f * 0.2f;
        else if (dist >= 6) alpha = 0.5f + (dist - 6) / 3f * 0.3f;
        else alpha = (dist - 3) / 3f * 0.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        float cs = 0.15f + t * 0.05f; // 角片本身也微缩
        PoseStack ps = event.getPoseStack();
        float[][] localCorners = {{-hs, hs}, {hs, hs}, {hs, -hs}, {-hs, -hs}};
        for (int i = 0; i < 4; i++) {
            Vec3 worldPos = bodyCenter.add(right.scale(localCorners[i][0])).add(up.scale(localCorners[i][1]));
            ps.pushPose();
            ps.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
            ps.mulPose(camera.rotation());

            RenderSystem.setShaderTexture(0, CORNER_TEX[i]);
            RenderSystem.setShaderColor(1, 1, 1, alpha);
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
