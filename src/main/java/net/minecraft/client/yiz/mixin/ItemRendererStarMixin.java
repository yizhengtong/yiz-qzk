package net.minecraft.client.yiz.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.api.ShaderManager;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 8 方向描边 + 星空渲染 — 用 putBulkData 直接写颜色。
 */
@Mixin(ItemRenderer.class)
public class ItemRendererStarMixin {

    @Shadow
    public void renderModelLists(BakedModel m, ItemStack s, int l, int o, PoseStack p, VertexConsumer v) {}

    private static final Vector3f[] DIRS = {
        new Vector3f( 1,  1,  1), new Vector3f(-1,  1,  1),
        new Vector3f( 1, -1,  1), new Vector3f( 1,  1, -1),
        new Vector3f(-1, -1,  1), new Vector3f(-1,  1, -1),
        new Vector3f( 1, -1, -1), new Vector3f(-1, -1, -1)
    };

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$renderItemWithStar(ItemStack stack, ItemDisplayContext ctx, boolean lh,
            PoseStack ps, MultiBufferSource buf, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (stack.isEmpty() || model.isCustomRenderer() || ctx == ItemDisplayContext.GROUND) return;

        boolean gui = ctx == ItemDisplayContext.GUI;
        boolean star = ShaderManager.hasItemEffect(stack);

        // 非星空物品完全不受影响 — 不停留、不描边、不 cancel
        if (!star) return;

        // ═══ 星空物品：描边 + 原版 + Cosmic ═══
        ps.pushPose();
        BakedModel cam = net.neoforged.neoforge.client.ClientHooks.handleCameraTransforms(ps, model, ctx, lh);
        ps.translate(-0.5F, -0.5F, -0.5F);

        // ── 描边 ──
        if (buf instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
        float[] c = getColor();
        float off = 0.01f;
        RenderType glowRt = RenderType.entityTranslucent(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png"));
        VertexConsumer vc = buf.getBuffer(glowRt);
        for (int d = 0; d < 8; d++) {
            ps.pushPose();
            ps.translate(DIRS[d].x() * off, DIRS[d].y() * off, DIRS[d].z() * off);
            for (BakedModel pass : cam.getRenderPasses(stack, true))
                for (BakedQuad q : pass.getQuads(null, null, RandomSource.create()))
                    if (shouldRenderQuad(q, DIRS[d]))
                        vc.putBulkData(ps.last(), q, c[0], c[1], c[2], c[3], light, overlay, true);
            ps.popPose();
        }
        if (buf instanceof MultiBufferSource.BufferSource bs) bs.endBatch(glowRt);
        ps.popPose();

        // ── 原版 + Cosmic ──
        ps.pushPose();
        BakedModel cam2 = net.neoforged.neoforge.client.ClientHooks.handleCameraTransforms(ps, model, ctx, lh);
        ps.translate(-0.5F, -0.5F, -0.5F);
        for (BakedModel pass : cam2.getRenderPasses(stack, true))
            for (RenderType rt : pass.getRenderTypes(stack, true))
                renderModelLists(pass, stack, light, overlay, ps, buf.getBuffer(rt));
        ShaderInstance shader = ShaderManager.getActiveItemShader();
        if (shader != null) {
            if (buf instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
            if (shader.getUniform("iTime") != null)
                shader.getUniform("iTime").set((float)(System.currentTimeMillis()%100000L)/1000f);
            ShaderManager.applyCosmicUVs(shader);
            RenderType st = gui ? ShaderManager.getItemGuiRenderType()
                : ctx.firstPerson() ? ShaderManager.getItemDirectRenderType()
                : ShaderManager.getItemEntityRenderType();
            for (BakedModel pass : cam2.getRenderPasses(stack, true))
                renderModelLists(pass, stack, light, overlay, ps, buf.getBuffer(st));
            if (stack.hasFoil()) {
                RenderType foil = gui ? RenderType.glint() : RenderType.entityGlint();
                for (BakedModel pass : cam2.getRenderPasses(stack, true))
                    renderModelLists(pass, stack, light, overlay, ps, buf.getBuffer(foil));
            }
        }
        ps.popPose();
        ci.cancel();
    }

    private static float[] getColor() {
        int p; try { p = Integer.parseInt(System.getProperty("yizxian.outline.preset", "1")); }
        catch (Exception e) { p = 1; }
        float t = (System.currentTimeMillis() % 3000) / 3000f;
        float pulse = 0.5f + 0.5f * (float)Math.sin(t * Math.PI * 2);
        if (p == 0) return new float[]{1,1,1,0.5f};
        if (p == 1) { float h = (System.currentTimeMillis() % 5000) / 5000f; return hsv(h, 0.9f, 1f); }
        float[][] cols = {{0,0,0,0},{0,0,0,0},{1,0.3f,0.3f,0.5f},{0.7f,0.3f,1f,0.5f},{0.3f,0.55f,1f,0.5f},{0.3f,1f,0.47f,0.5f}};
        float[] b = cols[p >= 0 && p <= 5 ? p : 1];
        return new float[]{lerp(0.2f,b[0],pulse), lerp(0.2f,b[1],pulse), lerp(0.2f,b[2],pulse), 0.5f};
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static float[] hsv(float h,float s,float v){
        int i=(int)(h*6); float f=h*6-i, p=v*(1-s), q=v*(1-f*s), t=v*(1-(1-f)*s);
        return switch(i%6){case 0->new float[]{v,t,p,0.5f};case 1->new float[]{q,v,p,0.5f};case 2->new float[]{p,v,t,0.5f};case 3->new float[]{p,q,v,0.5f};case 4->new float[]{t,p,v,0.5f};default->new float[]{v,p,q,0.5f};};
    }
    /** 只渲染法线与偏移方向同向的 quad（边缘描边，不发糊） */
    private static boolean shouldRenderQuad(BakedQuad quad, Vector3f dir) {
        Direction face = quad.getDirection();
        if (face == null) return true;
        return dir.x() * face.getStepX() + dir.y() * face.getStepY() + dir.z() * face.getStepZ() > 0;
    }
}
