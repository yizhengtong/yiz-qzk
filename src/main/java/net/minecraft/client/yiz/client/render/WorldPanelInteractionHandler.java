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

    private WorldPanelInteractionHandler() {}

    @SubscribeEvent
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (!WorldGuiPanelManager.isEnabled()) return;
        if (!event.isUseItem()) return;                              // 只处理右键
        if (event.getHand() != InteractionHand.MAIN_HAND) return;    // 事件对 MAIN/OFF 各 fire 一次，只处理主手避免重复

        // 切换进行中：锁，防重入（阶段4）
        if (OpModeState.getSwitchingTo() != null) return;
        if (!OpModeState.isFakeClosed()) return;

        WorldGuiInputHandler.CrosshairHit hit = WorldGuiInputHandler.getCrosshairHit();
        if (hit == null) return;

        if (hit.record.screen == null) return;

        // 击中光屏空白区（GUI槽位范围外）→ 不 cancel，放行原版右键。
        // 光屏只是渲染四边形、无碰撞体，vanilla 的 mc.hitResult 仍指向背后方块，
        // RightClickBlock 会正常触发 → 重新打开箱子 → captureNewPanel 在面前重建光屏。
        var acc = (net.minecraft.client.yiz.mixin.AbstractContainerScreenAccessor) hit.record.screen;
        int left = acc.getLeftPos(), top = acc.getTopPos();
        int imgW = acc.getImageWidth(), imgH = acc.getImageHeight();
        if (hit.guiX < left || hit.guiX >= left + imgW || hit.guiY < top || hit.guiY >= top + imgH) {
            return;
        }

        if (hit.record.blockPos.equals(OpModeState.getActivePanel())) {
            var s = hit.record.screen;
            event.setCanceled(true);
            event.setSwingHand(false);
            boolean handled;
            if (s.getMenu().getCarried().isEmpty()) {
                handled = s.mouseClicked(hit.guiX, hit.guiY, 1);
                acc.setQuickCrafting(false);
            } else {
                acc.setSkipNextRelease(false);
                acc.setQuickCrafting(false);
                handled = s.mouseReleased(hit.guiX, hit.guiY, 1);
            }
            LOG.debug("世界光屏准星右键 @ gui=({},{}) carried={} handled={}",
                    (int) hit.guiX, (int) hit.guiY, s.getMenu().getCarried().getCount(), handled);
        } else {
            // 多光屏切换：命中非活跃光屏 → 主动让服务端打开目标方块的容器
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.core.BlockPos target = hit.record.blockPos;
            OpModeState.setSwitchingTo(target);
            event.setCanceled(true);
            event.setSwingHand(false);
            net.minecraft.world.phys.Vec3 hitVec = net.minecraft.world.phys.Vec3.atCenterOf(target).add(0, 0.5, 0);
            net.minecraft.world.phys.BlockHitResult bhr = new net.minecraft.world.phys.BlockHitResult(
                    hitVec, net.minecraft.core.Direction.UP, target, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
            LOG.info("多光屏切换开始 @ {}，等待服务端打开容器...", target);
        }
    }
}
