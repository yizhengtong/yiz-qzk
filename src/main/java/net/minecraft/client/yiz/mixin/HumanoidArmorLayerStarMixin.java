package net.minecraft.client.yiz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.api.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerStarMixin {

    @Inject(
            method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V",
            at = @At("TAIL")
    )
    private void yizmodqzk$renderStarArmorOverlay(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            LivingEntity livingEntity,
            EquipmentSlot slot,
            int packedLight,
            HumanoidModel<?> model,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        // 检查该槽位是否有盔甲物品
        var armorStack = livingEntity.getItemBySlot(slot);
        if (!(armorStack.getItem() instanceof net.minecraft.world.item.ArmorItem)) return;

        // 直接读取渲染实体的星空体数据
        if (!(livingEntity instanceof Player player)) return;
        boolean hasStar = PlayerDataAPI.get(player, "yizxgmod:star_body");
        if (!hasStar) return;

        ShaderInstance shader = ShaderManager.getActiveArmorShader();
        if (shader == null) return;
        if (shader.getUniform("iTime") != null) {
            shader.getUniform("iTime").set((float) (System.currentTimeMillis() % 100000L) / 1000.0F);
        }

        RenderType starType = ShaderManager.getArmorRenderType();
        if (starType == null) return;
        VertexConsumer starBuffer = bufferSource.getBuffer(starType);
        model.renderToBuffer(poseStack, starBuffer, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
