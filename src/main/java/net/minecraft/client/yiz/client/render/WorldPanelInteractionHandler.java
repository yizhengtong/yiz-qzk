package net.minecraft.client.yiz.client.render;

import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 世界光屏「准星右键操作」：玩家没打开任何 GUI（mc.screen==null，自由视角，准星可见）时，
 * 准星对准世界里已存在的留存光屏、右键 → 直接操作该光屏上的槽位（拿一半/放一个）。
 *
 * <p>配合 {@link AbstractContainerScreenFakeCloseMixin}（ESC 假关闭，保留服务端容器）使用：
 * ESC 后 mc.screen==null 但服务端容器仍打开，此时本 handler 在右键事件里拦截。</p>
 *
 * <h3>事件点</h3>
 * {@link InputEvent.InteractionKeyMappingTriggered} 在 {@code Minecraft.startUseItem} 内触发
 * （仅 mc.screen==null），在原版 useItemOn/useItem/onEmptyClick 全部分支之前，可 cancel。
 * 比 RightClickBlock/Item/Empty 更统一（一处拦全部分支）。
 *
 * <h3>职责划分（按准星位置区分）</h3>
 * <ul>
 *   <li>准星命中活跃光屏（== {@link OpModeState#getActivePanel()}）→ 取消原版右键，
 *       调 {@code screen.mouseClicked(guiX, guiY, 1)}（button=1=ClickAction.SECONDARY=拿一半/放一个）。</li>
 *   <li>准星命中非活跃光屏 → 切换服务端容器到目标后再操作（阶段4，待实现）。</li>
 *   <li>准星未命中任何光屏 → 不取消，放行原版右键（放方块/吃东西）。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class WorldPanelInteractionHandler {

    private static final Logger LOG = LoggerFactory.getLogger("WorldPanelInteractionHandler");

    /** 上次操作的时间戳。拿起/放下后 100ms 内跳过累积事件，避免闪烁。 */
    private static long lastActionMs = 0;

    private WorldPanelInteractionHandler() {}

    @SubscribeEvent
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (!WorldGuiPanelManager.isEnabled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        // 上次操作后 100ms 内跳过（拿起/放下后 while(consumeClick) 累积的事件）
        if (System.currentTimeMillis() - lastActionMs < 100) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();

        final int button;
        if (event.isAttack())       button = 0;  // 左键：拿全部/放全部
        else if (event.isUseItem()) button = 1;  // 右键：拿一半/放一个
        else return;

        // 切换进行中：锁，防重入
        if (OpModeState.getSwitchingTo() != null) return;
        if (!OpModeState.isFakeClosed()) return;

        WorldGuiInputHandler.CrosshairHit hit = WorldGuiInputHandler.getCrosshairHit();
        if (hit == null) return;
        if (hit.record.screen == null) return;

        // 全区域保护：光屏任意位置（含空白区）的左键都不破坏后方方块。
        // 左键不 cancel（否则 startAttack() 调 keyAttack.release()→无限循环），
        // 改为 hitResult 设 MISS 让后续攻击走空。
        if (button == 0) {
            mc.hitResult = net.minecraft.world.phys.BlockHitResult.miss(
                    hit.record.panelAnchor, net.minecraft.core.Direction.UP, hit.record.blockPos);
        } else {
            event.setCanceled(true); // 右键正常cancel（startUseItem 无 release 循环问题）
        }
        event.setSwingHand(false);

        // 击中光屏空白区（GUI槽位范围外）→ 放行原版（右键打开箱子/左键因 hitResult 已置 MISS 不破坏）
        var acc = (net.minecraft.client.yiz.mixin.AbstractContainerScreenAccessor) hit.record.screen;
        int left = acc.getLeftPos(), top = acc.getTopPos();
        int imgW = acc.getImageWidth(), imgH = acc.getImageHeight();
        if (hit.guiX < left || hit.guiX >= left + imgW || hit.guiY < top || hit.guiY >= top + imgH) {
            return;
        }

        if (hit.record.blockPos.equals(OpModeState.getActivePanel())) {
            var s = hit.record.screen;
            event.setSwingHand(false);
            boolean handled;
            boolean wasEmpty = s.getMenu().getCarried().isEmpty();
            if (wasEmpty) {
                handled = s.mouseClicked(hit.guiX, hit.guiY, button);
                acc.setQuickCrafting(false);
                // 记录操作时间戳，100ms 内跳过累积事件
                lastActionMs = System.currentTimeMillis();
            } else {
                acc.setSkipNextRelease(false);
                acc.setQuickCrafting(false);
                handled = s.mouseReleased(hit.guiX, hit.guiY, button);
                lastActionMs = System.currentTimeMillis();
            }
            LOG.debug("世界光屏准星{}键 @ gui=({},{}) carried={} handled={}",
                    button == 0 ? "左" : "右", (int) hit.guiX, (int) hit.guiY, s.getMenu().getCarried().getCount(), handled);
        } else if (event.isUseItem()) {
            // 仅右键触发多光屏切换
            net.minecraft.core.BlockPos target = hit.record.blockPos;
            OpModeState.setSwitchingTo(target);
            net.minecraft.world.phys.Vec3 hitVec = net.minecraft.world.phys.Vec3.atCenterOf(target).add(0, 0.5, 0);
            net.minecraft.world.phys.BlockHitResult bhr = new net.minecraft.world.phys.BlockHitResult(
                    hitVec, net.minecraft.core.Direction.UP, target, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            LOG.info("多光屏切换开始 @ {}，等待服务端打开容器...", target);
        }
        // 左键命中非活跃光屏：不 cancel → 放行原版，让玩家攻击/破坏背后方块
    }
}
