package net.minecraft.client.yiz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.yiz.tool.abolish.EntityAbolitionStateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体废除渲染层 Mixin — 阻止已废除实体的渲染。
 *
 * <p>通过在 {@link EntityRenderDispatcher#render} 入口注入检查，
 * 阻止废除实体的所有渲染（包括模型、名称标签、粒子等）。</p>
 *
 * <p>这是四层架构的第二层（Mixin）的渲染部分。实体仍可能存在于
 * 世界中（如果未在服务端层被拦截），但客户端完全不可见。</p>
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityAbolitionRenderMixin {

    /**
     * EntityRenderDispatcher.render() 入口拦截。
     * <p>签名：{@code render(E entity, double x, double y, double z,
     * float yRot, float partialTick, PoseStack, MultiBufferSource, int)}</p>
     *
     * <p>如果实体类型在废除列表中 → 直接跳过渲染。</p>
     */
    @Inject(
            method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <E extends Entity> void yizmodqzk$onRender(
            E entity,
            double x, double y, double z,
            float yRot, float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (EntityAbolitionStateManager.isEntityAbolished(entity)) {
            ci.cancel();
        }
    }
}
