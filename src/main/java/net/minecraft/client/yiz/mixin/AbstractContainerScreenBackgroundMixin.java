package net.minecraft.client.yiz.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.yiz.client.render.WorldGuiPanelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界空间容器 GUI：离屏渲染时去掉全屏暗色背景。
 *
 * <p>{@link AbstractContainerScreen#renderBackground} 默认会先调
 * {@code renderTransparentBackground}（画全屏暗色 dirt 背景），再调 {@code renderBg}（箱子面板背景图）。
 * 离屏渲染进 FBO 时，全屏背景会把整个 FBO 涂暗、且在世界面板里不需要 → 在离屏渲染中标志为 true 时，
 * 跳过 renderTransparentBackground，只保留 renderBg（箱子本身的边框/背景纹理）。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenBackgroundMixin {

    @Shadow
    protected abstract void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY);

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void yiz$skipFullscreenBackgroundOnOffscreen(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                         float partialTick, CallbackInfo ci) {
        if (!WorldGuiPanelManager.isOffscreenRendering()) return;
        // 离屏渲染：只画箱子面板背景图，跳过全屏暗色背景
        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
        ci.cancel();
    }
}
