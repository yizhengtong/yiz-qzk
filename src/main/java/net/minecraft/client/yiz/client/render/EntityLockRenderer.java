package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.yiz.api.EntityLockAPI;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 锁定目标图标渲染器 — 在锁定实体的碰撞箱中点绘制 billboard 图标。
 * 通过 RenderLevelStageEvent.AFTER_ENTITIES 阶段注入。
 */
public final class EntityLockRenderer {

    private static final ResourceLocation LOCK_ICON =
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock_icon.png");

    private static final float SIZE = 0.4f; // 世界空间中的图标大小

    private EntityLockRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (Minecraft.getInstance().player == null) return;

        var lockEntry = EntityLockAPI.getClient();
        if (lockEntry == null) return;

        var clientLevel = Minecraft.getInstance().level;
        if (!(clientLevel instanceof ClientLevel cl)) return;

        Entity target = null;
        for (var e : cl.entitiesForRendering()) {
            if (e != null && e.getUUID().equals(lockEntry.targetUuid())) {
                target = e;
                break;
            }
        }
        if (target == null) return;

        Camera camera = event.getCamera();
        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = camera.getPosition();

        // 锁定实体碰撞箱中点
        Vec3 center = target.getBoundingBox().getCenter();

        // 保存渲染状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, LOCK_ICON);

        poseStack.pushPose();
        // 平移到实体位置（相对相机）
        poseStack.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
        // 面向相机
        poseStack.mulPose(camera.rotation());

        // 4 顶点 billboard
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
