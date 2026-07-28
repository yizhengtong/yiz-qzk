package net.minecraft.client.yiz.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.yiz.client.render.OpModeState;
import net.minecraft.client.yiz.client.render.WorldGuiPanelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世界光屏「假关闭」：被世界面板接管的容器屏（右键箱子打开的）按 ESC 时，
 * 不真正关闭容器，只把画面切回世界（{@code setScreen(null)}）。
 *
 * <p>原版 ESC 走 {@link AbstractContainerScreen#onClose} → {@code player.closeContainer()}
 * 会发 {@code ServerboundContainerClosePacket}，服务端把 {@code player.containerMenu} 切回背包，
 * 之后对留存 screen 调 {@code mouseClicked} 发的 click 包会被服务端静默忽略（containerId 不匹配），
 * 槽位不会变 → 准星右键操作光屏的链路断掉。</p>
 *
 * <p>本 Mixin 拦 {@code onClose} HEAD：仅当该 screen 被世界面板管理（{@link OpModeState#isManagedScreen}）
 * 时短路——{@code ci.cancel()} 跳过 {@code closeContainer()}（不发 close 包），再 {@code setScreen(null)}
 * 恢复自由视角+准星。{@code setScreen(null)} 的副作用已核实安全：不调 onClose、不发 close 包、
 * {@code removed()}→{@code menu.removed()} 客户端路径空操作、{@code grabMouse()} 恢复视角。</p>
 *
 * <p>非世界面板（玩家背包 InventoryScreen 等，{@code isManagedScreen}=false）原样放行，原版真关。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenFakeCloseMixin {

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void yiz$fakeCloseManagedPanel(CallbackInfo ci) {
        org.slf4j.Logger L = org.slf4j.LoggerFactory.getLogger("FakeCloseMixin");
        if (!WorldGuiPanelManager.isEnabled()) return;
        @SuppressWarnings("unchecked")
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        boolean managed = OpModeState.isManagedScreen(self);
        if (!managed) return;

        ci.cancel();
        Minecraft.getInstance().setScreen(null);
        OpModeState.markFakeClosed(self);
        L.debug("假关闭 {} activePanel={}", self.getClass().getSimpleName(), OpModeState.getActivePanel());
    }
}
