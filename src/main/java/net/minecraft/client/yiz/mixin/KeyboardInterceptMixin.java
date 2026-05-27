package net.minecraft.client.yiz.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.client.render.PanelInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 操作模式（INTERACT）下，键盘事件不应进入 vanilla（否则会触发玩家移动、聊天 GUI 等），
 * 全部转发到目标窗口，并在方法入口直接 return。
 *
 * <p>调用 PanelInteractionManager.onKey 完成转发；返回 true 表示已消费。</p>
 */
@Mixin(KeyboardHandler.class)
public class KeyboardInterceptMixin {

    @Inject(
        method = "keyPress",
        at = @At("HEAD"),
        cancellable = true
    )
    private void yizgzq$interceptKeyPress(
            long window, int key, int scancode, int action, int modifiers,
            CallbackInfo ci) {
        // 仅当窗口是当前 MC 主窗口时拦截，避免旁路其他 GLFW 回调
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow().getWindow() != window) return;

        if (PanelInteractionManager.onKey(key, scancode, action, modifiers)) {
            ci.cancel();
        }
    }
}
