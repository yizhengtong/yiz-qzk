package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 世界 GUI 命中检测 + 坐标重映射（逆投影）。
 *
 * <p>玩家用准星指向世界光屏、左键点击时，拦截原版 mouseClicked（它用真实鼠标坐标，对世界光屏是错的），
 * 改用「玩家视线射线 vs 光屏平面」求交算出命中点的 GUI 像素坐标，调 screen.mouseClicked。</p>
 *
 * <h3>算法（逆投影四步）</h3>
 * <ol>
 *   <li>观察射线：Eye = 玩家眼睛，Dir = 视线方向。P(t) = Eye + t·Dir</li>
 *   <li>射线-平面求交：把射线变换到面板局部系（局部 z=0 是平面），t = -relEye.z / localDir.z</li>
 *   <li>局部坐标 (lx, ly) → GUI 像素：lx∈[-hw,hw] → guiX∈[0,screenW]；ly∈[-hh,hh] → guiY∈[0,screenH]</li>
 *   <li>取消原版 mouseClicked，用算出的 (guiX, guiY) 调 screen.mouseClicked</li>
 * </ol>
 *
 * <p>局部系求交天然规避透视畸变（在面板局部系里平面是 z=0，求交线性）。
 * UV 约定与 {@link HandheldPanelRenderer#renderQuadWithTexture}（uLeft=0,vTop=1,uRight=1,vBottom=0）一致。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WorldGuiInputHandler {

    private static final Logger LOG = LoggerFactory.getLogger("WorldGuiInputHandler");

    /** 命中结果：命中的面板记录 + GUI 像素坐标。 */
    private static final class Hit {
        final OpModeState.PanelRecord record;
        final double guiX, guiY;
        Hit(OpModeState.PanelRecord record, double guiX, double guiY) {
            this.record = record; this.guiX = guiX; this.guiY = guiY;
        }
    }

    /** 准星命中结果（public，供 WorldPanelInteractionHandler 用）。 */
    public static final class CrosshairHit {
        public final OpModeState.PanelRecord record;
        public final double guiX, guiY;
        public CrosshairHit(OpModeState.PanelRecord record, double guiX, double guiY) {
            this.record = record; this.guiX = guiX; this.guiY = guiY;
        }
    }

    private WorldGuiInputHandler() {}

    // ════════════════════════════════════════════════════
    //  鼠标按下
    // ════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!WorldGuiPanelManager.isEnabled()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        // 只拦截被世界面板接管的容器屏（右键箱子）。玩家背包等无世界面板的容器屏放行原版，否则点击失效。
        @SuppressWarnings("unchecked")
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        if (!OpModeState.isManagedScreen(screen)) return;

        Hit hit = raycastHit();
        if (hit == null) {
            // 世界化开启但未命中任何光屏：吞掉点击，避免原版用真实鼠标坐标乱操作看不见的贴脸 GUI
            event.setCanceled(true);
            return;
        }

        // 命中 → 取消原版 mouseClicked，用算出的光屏坐标调
        event.setCanceled(true);
        boolean handled = screen.mouseClicked(hit.guiX, hit.guiY, event.getButton());
        LOG.debug("世界GUI点击 @ gui=({},{}) button={} handled={}", (int) hit.guiX, (int) hit.guiY, event.getButton(), handled);
    }

    // ════════════════════════════════════════════════════
    //  鼠标释放（拖拽物品的 release 也要用正确坐标）
    // ════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!WorldGuiPanelManager.isEnabled()) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
        // 只拦截被世界面板接管的容器屏（右键箱子）。玩家背包等放行原版。
        @SuppressWarnings("unchecked")
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        if (!OpModeState.isManagedScreen(screen)) return;

        Hit hit = raycastHit();
        if (hit == null) {
            // 未命中也吞掉 release，避免原版在看不见的贴脸 GUI 上触发释放
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        screen.mouseReleased(hit.guiX, hit.guiY, event.getButton());
    }

    // ════════════════════════════════════════════════════
    //  射线-平面求交（逆投影）
    // ════════════════════════════════════════════════════

    /**
     * 当前准星命中的 GUI 像素坐标（与 {@link #onMousePressed} / {@link #onMouseReleased} 同源、同一套逆投影）。
     * <p>供离屏渲染传鼠标坐标用：vanilla {@code AbstractContainerScreen.render} 画拖拽物品（floating item）
     * 和 mouseDragged 完全用传入的 mouseX/mouseY（见 {@code renderFloatingItem(g, stack, mouseX-leftPos-8, mouseY-topPos-i2)}）。
     * 若传真实鼠标坐标，拖拽物品会「中心贴合、四角偏」（真实鼠标坐标对世界光屏是错的）。
     * 改用本方法返回的命中坐标，拖拽物品就画在准星命中处，与点击点严格一致。</p>
     *
     * @return {@code double[]{guiX, guiY}}；未启用/未命中任何光屏返回 {@code null}
     */
    public static double[] getHoverGuiPos() {
        Hit h = raycastHit();
        return h == null ? null : new double[]{h.guiX, h.guiY};
    }

    /**
     * 鼠标屏幕坐标逆投影成 3D 射线，raycast 所有世界面板，返回命中的面板 + GUI 像素坐标。
     * 未命中返回 null。视角锁死时相机不动，但鼠标在屏幕移动 → 鼠标位置对应一条从相机出发的射线。
     */
    private static Hit raycastHit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null || mc.screen == null) return null;

        // 鼠标 NDC：mouseHandler.xpos() 是屏幕像素，视口=framebuffer 像素，用 screenWidth/screenHeight 换算 NDC。
        // 注意不能用 guiScaledWidth（那是 GUI 逻辑坐标，与视口无关）——它若出现在分子分母会被约掉，反而掩盖真实换算。
        float ndcX = (float) (2.0 * mc.mouseHandler.xpos() / mc.getWindow().getScreenWidth() - 1.0);
        float ndcY = (float) (1.0 - 2.0 * mc.mouseHandler.ypos() / mc.getWindow().getScreenHeight());
        return raycastWithNdc(mc, ndcX, ndcY);
    }

    /**
     * 准星（屏幕中心）命中检测。供 mc.screen==null 时的"准星右键操作光屏"用：
     * 玩家没打开任何 GUI、自由转视角，准星=屏幕中心，逆投影 NDC=(0,0)。
     * 与 {@link #raycastHit()} 共用 {@link #raycastWithNdc} 核心，仅 NDC 来源不同。
     *
     * @return 命中的面板 + GUI 像素；未启用/无相机/未命中返回 null
     */
    public static CrosshairHit getCrosshairHit() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.cameraEntity == null) return null;
        Hit h = raycastWithNdc(mc, 0f, 0f);
        return h == null ? null : new CrosshairHit(h.record, h.guiX, h.guiY);
    }

    /**
     * 核心：给定屏幕 NDC，逆投影成世界射线，遍历所有面板求最近命中。
     * 鼠标点击（NDC 来自鼠标位置）与准星（NDC=0,0）共用此方法，避免两套逆投影漂移。
     */
    private static Hit raycastWithNdc(Minecraft mc, float ndcX, float ndcY) {
        // 用世界渲染时保存的透视投影矩阵（比读 RenderSystem 全局值准——点击时全局值可能已被 GUI 正交投影覆盖）
        org.joml.Matrix4f invProj = new org.joml.Matrix4f(WorldGuiPanelManager.getWorldProjection()).invert();
        org.joml.Vector4f nearH = invProj.transform(new org.joml.Vector4f(ndcX, ndcY, -1f, 1f));
        org.joml.Vector4f farH = invProj.transform(new org.joml.Vector4f(ndcX, ndcY, 1f, 1f));
        nearH.div(nearH.w); farH.div(farH.w);
        org.joml.Vector3f localDir = new org.joml.Vector3f(farH.x - nearH.x, farH.y - nearH.y, farH.z - nearH.z).normalize();

        // 旋转到世界系
        Camera cam = mc.gameRenderer.getMainCamera();
        Quaternionf camRot = new Quaternionf(cam.rotation());
        org.joml.Vector3f worldDir = new org.joml.Vector3f(localDir).rotate(camRot);
        Vec3 dir = new Vec3(worldDir.x, worldDir.y, worldDir.z);
        Vec3 eye = cam.getPosition();

        List<OpModeState.PanelRecord> records = OpModeState.list();
        Hit best = null;
        double bestT = Double.MAX_VALUE;
        for (OpModeState.PanelRecord r : records) {
            if (r.screen == null) continue;
            double[] uvt = raycastPanel(eye, dir, r);
            if (uvt == null) continue;
            if (uvt[2] < bestT) {
                bestT = uvt[2];
                double guiX = uvt[0] * r.screen.width;
                double guiY = (1.0 - uvt[1]) * r.screen.height;
                best = new Hit(r, guiX, guiY);
            }
        }
        return best;
    }

    /**
     * 射线 vs 单面板求交。返回 [u, v, t]：u∈[0,1]左0右1，v∈[0,1]下0上1，t=距离；未命中返回 null。
     * 在面板局部系求交（局部 z=0 是平面），规避透视畸变。
     */
    private static double[] raycastPanel(Vec3 eye, Vec3 dir, OpModeState.PanelRecord r) {
        // 面板半宽/半高：必须与 drawWorldQuad 用同一个函数、同一份入参（r.fbo 尺寸），
        // 否则命中框形状与实际面板不一致（中心贴合、四角偏）。FBO 未就绪=没渲染=不命中。
        if (r.fbo == null) return null;
        float[] sz = WorldGuiPanelManager.computeGuiPanelSize(r.fbo.width, r.fbo.height);
        float hw = sz[0] * 0.5f;
        float hh = sz[1] * 0.5f;

        // 把 eye、dir 变换到面板局部系（局部 = rotation^-1 * (world - anchor)）
        Quaternionf invRot = new Quaternionf(r.panelRotation).conjugate();
        Vector3f relEye = new Vector3f(
                (float) (eye.x - r.panelAnchor.x),
                (float) (eye.y - r.panelAnchor.y),
                (float) (eye.z - r.panelAnchor.z));
        invRot.transform(relEye);
        Vector3f localDir = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        invRot.transform(localDir);

        // 射线与 z=0 平面求交
        if (Math.abs(localDir.z) < 1e-6f) return null; // 平行
        float t = -relEye.z / localDir.z;
        if (t <= 0) return null; // 背面

        float lx = relEye.x + t * localDir.x;
        float ly = relEye.y + t * localDir.y;
        if (lx < -hw || lx > hw) return null; // 水平出界
        if (ly < -hh || ly > hh) return null; // 垂直出界

        // 局部坐标 → UV：lx∈[-hw,hw]→u∈[0,1]（左0右1）；ly∈[-hh,hh]→v∈[0,1]（下0上1）
        double u = (lx + hw) / (2.0 * hw);
        double v = (ly + hh) / (2.0 * hh);
        return new double[]{u, v, t};
    }
}
