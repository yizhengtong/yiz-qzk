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

    /** 上次拿起物品的时间戳。100ms 内收到的新事件（while(consumeClick) 累积）跳过，
     *  避免"瞬间拿起又放下"。超过 100ms 的认为是独立点击，正常处理。 */
    private static long lastPickupMs = 0;

    private WorldPanelInteractionHandler() {}

    @SubscribeEvent
    public static void onUseItem(InputEvent.InteractionKeyMappingTriggered event) {
        if (!WorldGuiPanelManager.isEnabled()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        // 刚拿起物品后 100ms 内跳过（while(consumeClick) 循环累积的事件）
        if (System.currentTimeMillis() - lastPickupMs < 100) return;

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

        // 击中光屏空白区（GUI槽位范围外）→ 不 cancel，放行原版。
        // 光屏只是渲染四边形无碰撞体，vanilla mc.hitResult 指向背后方块 → RightClickBlock/LeftClickBlock 正常触发。
        var acc = (net.minecraft.client.yiz.mixin.AbstractContainerScreenAccessor) hit.record.screen;
        int left = acc.getLeftPos(), top = acc.getTopPos();
        int imgW = acc.getImageWidth(), imgH = acc.getImageHeight();
        if (hit.guiX < left || hit.guiX >= left + imgW || hit.guiY < top || hit.guiY >= top + imgH) {
            return;
        }

        if (hit.record.blockPos.equals(OpModeState.getActivePanel())) {
            var s = hit.record.screen;
            // 左键(button=0)不 cancel——startAttack() 在 cancel 时调 keyAttack.release()
            // → isDown=false → 物理键还按着 → 下一 tick 重注册 click → 无限循环"拿起又放下"。
            // 改为把 mc.hitResult 设 MISS 让后续攻击走空，event 放行但不破坏方块。
            if (button == 0) {
                mc.hitResult = null;
            } else {
                event.setCanceled(true);
            }
            event.setSwingHand(false);
            boolean handled;
            boolean wasEmpty = s.getMenu().getCarried().isEmpty();
            if (wasEmpty) {
                handled = s.mouseClicked(hit.guiX, hit.guiY, button);
                acc.setQuickCrafting(false);
                // 记录拿起时间戳，100ms 内跳过累积事件
                if (!s.getMenu().getCarried().isEmpty()) lastPickupMs = System.currentTimeMillis();
            } else {
                acc.setSkipNextRelease(false);
                acc.setQuickCrafting(false);
                handled = s.mouseReleased(hit.guiX, hit.guiY, button);
            }
            LOG.debug("世界光屏准星{}键 @ gui=({},{}) carried={} handled={}",
                    button == 0 ? "左" : "右", (int) hit.guiX, (int) hit.guiY, s.getMenu().getCarried().getCount(), handled);
        } else if (event.isUseItem()) {
            // 仅右键触发多光屏切换（左键打光屏没意义，放行原版让玩家攻击背后的方块）
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
        // 左键命中非活跃光屏：不 cancel → 放行原版，让玩家攻击/破坏背后方块
    }
}
