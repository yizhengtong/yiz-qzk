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

    // === 锁定参数 ===
    private static final double LOCK_RANGE = 32.0;        // 最大锁定距离
    private static final double LOCK_CONE_DOT = 0.5;      // cos(60°) 圆锥
    private static final double BODY_HEIGHT_FACTOR = 0.7; // 身体中心高度比例

    // === 缩放参数 ===
    private static final float SIZE_BASE = 0.5f;           // 12+格时的框半边长（世界单位）
    private static final float SIZE_NEAR_MIN = 0.2f;       // 0格时的框相对大小
    private static final float SIZE_NEAR_MAX = 1.0f;       // 12+格时框相对大小
    private static final float SIZE_RANGE = 12f;            // 缩放范围（格）

    // === 透明度参数 ===
    private static final float ALPHA_FADE_IN = 3f;         // 淡入起始距离
    private static final float ALPHA_START = 0.4f;         // 淡入起点透明度
    private static final float ALPHA_FULL = 12f;           // 完全不透明距离
    private static final float ALPHA_INVISIBLE = 3f;       // 完全透明距离

    // === 旋转参数 ===
    private static final double ROTATION_SPEED = Math.PI / 6; // 弧度/秒 (30°/s)

    // === 角片纹理 ===
    private static final float CORNER_TEX_SIZE = 0.15f;     // 角片固定半边长
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

    /** 根据距离计算框缩放因子 [SIZE_NEAR_MIN, SIZE_NEAR_MAX] */
    public static float getScaleFactor(float dist) {
        float t = Math.clamp(dist / SIZE_RANGE, 0, 1);
        return SIZE_NEAR_MIN + t * (SIZE_NEAR_MAX - SIZE_NEAR_MIN);
    }

    /** 根据距离计算透明度 [0, 1] */
    public static float getAlpha(float dist) {
        if (dist <= ALPHA_INVISIBLE) return 0f;
        if (dist >= ALPHA_FULL) return 1f;
        return ALPHA_START + (dist - ALPHA_FADE_IN) / (ALPHA_FULL - ALPHA_FADE_IN) * (1f - ALPHA_START);
    }

    /** 获取当前时间的旋转弧度（绕 forward 轴） */
    public static double getRotationAngle() {
        return (System.currentTimeMillis() / 1000.0) * ROTATION_SPEED;
    }

    // ════════════════════════════════════════════
    //  渲染入口
    // ════════════════════════════════════════════

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 60° 锥找最接近屏幕中心的实体
        Entity target = findTarget(mc);
        if (target == null) return;

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        // 身体中心
        Vec3 bodyCenter = new Vec3(
            target.getX(), target.getY() + target.getBbHeight() * BODY_HEIGHT_FACTOR, target.getZ());

        // 面向玩家坐标系 + 旋转
        Vec3 forward = camPos.subtract(bodyCenter).normalize();
        Vec3 right = new Vec3(0, 1, 0).cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();

        double angle = getRotationAngle();
        double cosa = Math.cos(angle), sina = Math.sin(angle);
        Vec3 r = right.scale(cosa).add(up.scale(sina));
        Vec3 u = right.scale(-sina).add(up.scale(cosa));

        float dist = (float) bodyCenter.distanceTo(camPos);
        float hs = SIZE_BASE * getScaleFactor(dist);
        float alpha = getAlpha(dist);
        if (alpha <= 0) return;

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
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            float cs = CORNER_TEX_SIZE * getScaleFactor(dist);
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

    private static Entity findTarget(Minecraft mc) {
        Vec3 eyePos = mc.player.getEyePosition();
        var lookVec = mc.player.getLookAngle();
        AABB searchBox = mc.player.getBoundingBox().inflate(LOCK_RANGE);
        Entity target = null;
        double bestDot = LOCK_CONE_DOT;
        for (Entity e : mc.level.getEntities(mc.player, searchBox,
                e -> e instanceof LivingEntity && e != mc.player && e.isAlive())) {
            Vec3 toEntity = e.position().subtract(eyePos);
            double distSqr = toEntity.lengthSqr();
            if (distSqr > LOCK_RANGE * LOCK_RANGE) continue;
            double dot = lookVec.dot(toEntity) / Math.sqrt(distSqr);
            if (dot > bestDot) {
                bestDot = dot;
                target = e;
            }
        }
        return target;
    }
}
