package net.minecraft.client.yiz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.ShaderManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerStarMixin {

    @Unique
    private static final ThreadLocal<Boolean> IS_STAR_SLOT = ThreadLocal.withInitial(() -> false);

    @Unique
    private static boolean isZPreset() {
        String name = ShaderManager.getActivePresetName();
        return name != null && name.startsWith("z");
    }

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("HEAD")
    )
    private void yizmodqzk$flagStarSlot(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            LivingEntity livingEntity,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel<?> model,
            float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci
    ) {
        if (!(livingEntity instanceof Player player)) return;
        if (!(Boolean) PlayerDataAPI.get(player, "yizxgmod:star_body")) return;
        var armorStack = livingEntity.getItemBySlot(slot);
        if (armorStack.getItem() instanceof ArmorItem) {
            IS_STAR_SLOT.set(true);
        }
    }

    // z-series only: swap getBuffer to star shader (vanilla armor becomes invisible)
    @Redirect(
            method = "renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;ILnet/minecraft/resources/ResourceLocation;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
            ),
            require = 1
    )
    private VertexConsumer yizmodqzk$swapToStarBuffer(MultiBufferSource bufferSource, RenderType vanillaType) {
        if (IS_STAR_SLOT.get() && isZPreset()) {
            ShaderInstance shader = ShaderManager.getActiveArmorShader();
            if (shader != null) {
                if (shader.getUniform("iTime") != null) {
                    shader.getUniform("iTime").set((float) (System.currentTimeMillis() % 100000L) / 1000.0F);
                }
                ShaderManager.applyCosmicUVs(shader);
            }
            RenderType starType = ShaderManager.getArmorRenderType();
            if (starType != null) {
                return bufferSource.getBuffer(starType);
            }
        }
        return bufferSource.getBuffer(vanillaType);
    }

    // Skip renderTrim only for z-series
    @Inject(
            method = "renderTrim(Lnet/minecraft/core/Holder;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void yizmodqzk$cancelRenderTrim(
            Holder<ArmorMaterial> armorMaterial,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            ArmorTrim trim,
            net.minecraft.client.model.Model model,
            boolean innerTexture,
            CallbackInfo ci
    ) {
        if (IS_STAR_SLOT.get() && isZPreset()) {
            ci.cancel();
        }
    }

    // Skip renderGlint only for z-series
    @Inject(
            method = "renderGlint(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/model/Model;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void yizmodqzk$cancelRenderGlint(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            net.minecraft.client.model.Model model,
            CallbackInfo ci
    ) {
        if (IS_STAR_SLOT.get() && isZPreset()) {
            ci.cancel();
        }
    }

    // TAIL: for non-z, render star overlay on top of vanilla armor (original approach)
    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("TAIL")
    )
    private void yizmodqzk$renderStarOverlay(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            LivingEntity livingEntity,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel<?> model,
            float limbSwing, float limbSwingAmount, float partialTick,
            float ageInTicks, float netHeadYaw, float headPitch,
            CallbackInfo ci
    ) {
        try {
            if (!IS_STAR_SLOT.get()) return;
            if (isZPreset()) return; // z-series: already handled by @Redirect

            // Original approach: simple TAIL overlay on top of vanilla armor
            ShaderInstance shader = ShaderManager.getActiveArmorShader();
            if (shader == null) return;
            if (shader.getUniform("iTime") != null) {
                shader.getUniform("iTime").set((float) (System.currentTimeMillis() % 100000L) / 1000.0F);
            }
            ShaderManager.applyCosmicUVs(shader);

            RenderType starType = ShaderManager.getArmorRenderType();
            if (starType == null) return;

            VertexConsumer starBuffer = bufferSource.getBuffer(starType);
            model.renderToBuffer(poseStack, starBuffer, packedLight, OverlayTexture.NO_OVERLAY);
        } finally {
            IS_STAR_SLOT.remove();
        }
    }
}
