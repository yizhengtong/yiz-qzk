package net.minecraft.client.yiz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.api.StarShaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class ItemRendererStarMixin {

    @Shadow
    public void renderModelLists(
            BakedModel pModel, ItemStack pStack,
            int pCombinedLight, int pCombinedOverlay,
            PoseStack pPoseStack, VertexConsumer pBuffer
    ) {}

    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void yizmodqzk$renderItemWithStar(
            ItemStack itemStack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int combinedLight,
            int combinedOverlay,
            BakedModel model,
            CallbackInfo ci
    ) {
        if (itemStack.isEmpty()) return;
        if (model.isCustomRenderer()) return;

        poseStack.pushPose();

        BakedModel transformed = net.neoforged.neoforge.client.ClientHooks.handleCameraTransforms(
                poseStack, model, displayContext, leftHand
        );
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        boolean hasStar = StarShaderRegistry.hasStarEffect(itemStack);
        // 检查着色器是否已加载（RegisterShadersEvent 之前回退）
        ShaderInstance shader = StarShaderRegistry.getStarShader();
        boolean shaderReady = hasStar && shader != null;
        if (shaderReady && shader.getUniform("iTime") != null) {
            shader.getUniform("iTime").set((float) (System.currentTimeMillis() % 100000L) / 1000.0F);
        }

        for (BakedModel pass : transformed.getRenderPasses(itemStack, true)) {
            for (RenderType rt : pass.getRenderTypes(itemStack, true)) {
                VertexConsumer buf;
                if (shaderReady) {
                    RenderType starType;
                    if (displayContext == ItemDisplayContext.GUI) {
                        starType = StarShaderRegistry.starGlint();
                    } else if (displayContext.firstPerson()) {
                        starType = StarShaderRegistry.starGlintDirect();
                    } else {
                        starType = StarShaderRegistry.starEntityGlint();
                    }
                    buf = bufferSource.getBuffer(starType);
                } else {
                    buf = bufferSource.getBuffer(rt);
                }
                this.renderModelLists(pass, itemStack, combinedLight, combinedOverlay, poseStack, buf);
            }
        }

        poseStack.popPose();
        ci.cancel();
    }
}
