package net.minecraft.client.yiz.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.client.render.OpModeState;
import net.minecraft.client.yiz.client.render.WorldGuiInputHandler;
import net.minecraft.client.yiz.client.render.WorldGuiPanelManager;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 防止长按左键时 continueAttack 透过光屏破坏后方方块。
 *
 * <p>handleKeybinds() 每帧在 gameRenderer.pick()（重新计算 hitResult）后运行。
 * 第一帧的 startAttack() 靠 WorldPanelInteractionHandler 把 hitResult 设 MISS 保护了，
 * 但后续帧的 handleKeybinds 里 consumeClick() 返回 false、flag2=false，
 * continueAttack 会读到 pick() 刚恢复的真实方块 hitResult → 破坏箱子。</p>
 *
 * <p>本 Mixin 在 handleKeybinds HEAD（pick() 后、startAttack/continueAttack 前）注入，
 * 检测准星是否命中光屏，命中则把 hitResult 重置为 MISS。</p>
 */
@Mixin(Minecraft.class)
public class MinecraftHoldKeyMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void yiz$protectPanelsOnHold(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (!WorldGuiPanelManager.isEnabled()) return;
        if (!OpModeState.isFakeClosed()) return;
        if (!self.options.keyAttack.isDown()) return;

        WorldGuiInputHandler.CrosshairHit hit = WorldGuiInputHandler.getCrosshairHit();
        if (hit != null && hit.record.panelAnchor != null) {
            self.hitResult = BlockHitResult.miss(
                    hit.record.panelAnchor, Direction.UP, hit.record.blockPos);
        }
    }
}
