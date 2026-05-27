package net.minecraft.client.yiz.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.client.render.HandheldPanelRenderer.PanelGeometry;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager.ManagedSession;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * FIXED 面板的"准星即鼠标"悬停模式。
 *
 * <p>没有显式的进入/退出状态。FIXED 之后，玩家正常走动转视角；只要准星射线命中某个
 * FIXED 面板矩形，那一帧的鼠标输入就被路由到对应窗口；准星不在面板上时一切恢复 vanilla。</p>
 *
 * <p>每 tick：raycast 所有 FIXED 面板 → 命中最近的 → sendMouseMove(nx, ny)。
 * 鼠标按键/滚轮事件由调用方在 setCanceled 之前先调本类的 onMouseButton/onMouseScroll，
 * 返回 true 表示已转发并应取消 vanilla 处理。</p>
 */
public final class PanelInteractionManager {

    private static volatile int hoveredPanelId = -1;
    private static volatile double lastNx;
    private static volatile double lastNy;

    private PanelInteractionManager() {}

    /** 当前准星是否悬停在某个 FIXED 面板上 */
    public static boolean isHovering() {
        return hoveredPanelId > 0;
    }

    public static int getHoveredPanelId() {
        return hoveredPanelId;
    }

    // ════════════════════════════════════════════
    //  每 tick 钩子
    // ════════════════════════════════════════════

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            hoveredPanelId = -1;
            return;
        }
        // 暂停菜单/聊天等屏幕打开时，不接管鼠标
        if (mc.screen != null) {
            hoveredPanelId = -1;
            return;
        }

        List<PanelGeometry> fixed = HandheldPanelRenderer.listFixedGeometry();
        if (fixed.isEmpty()) {
            hoveredPanelId = -1;
            return;
        }

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        org.joml.Vector3f look = cam.getLookVector();
        Vec3 lookVec = new Vec3(look.x, look.y, look.z);

        PanelRaycast.Hit hit = PanelRaycast.firstHit(eye, lookVec, fixed);
        if (hit == null) {
            hoveredPanelId = -1;
            return;
        }

        hoveredPanelId = hit.panelId;
        lastNx = hit.nx;
        lastNy = hit.ny;

        ManagedSession s = HandheldPanelRenderer.getSession(hoveredPanelId);
        if (s != null) {
            WindowCaptureManager.sendMouseMove(s.handle(), hit.nx, hit.ny);
        }
    }

    // ════════════════════════════════════════════
    //  鼠标事件转发
    // ════════════════════════════════════════════

    /** @return true 表示事件已转发到面板，调用方应取消 vanilla 行为 */
    public static boolean onMouseButton(int button, boolean down) {
        if (!isHovering()) return false;
        if (button < 0 || button > 2) return false;
        ManagedSession s = HandheldPanelRenderer.getSession(hoveredPanelId);
        if (s == null) {
            hoveredPanelId = -1;
            return false;
        }
        WindowCaptureManager.sendMouseButton(s.handle(), button, down);
        // PostMessage 可能让 QQ 抢焦点导致 MC 卡住——立即抢回
        restoreGameFocus();
        return true;
    }

    private static void restoreGameFocus() {
        Minecraft mc = Minecraft.getInstance();
        long glfwWin = mc.getWindow().getWindow();
        if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(glfwWin, org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == org.lwjgl.glfw.GLFW.GLFW_FALSE) {
            org.lwjgl.glfw.GLFW.glfwFocusWindow(glfwWin);
        }
    }

    /** @return true 表示事件已转发到面板 */
    public static boolean onMouseScroll(double scrollDelta) {
        if (!isHovering()) return false;
        ManagedSession s = HandheldPanelRenderer.getSession(hoveredPanelId);
        if (s == null) {
            hoveredPanelId = -1;
            return false;
        }
        int delta = (int) Math.signum(scrollDelta) * 120;
        WindowCaptureManager.sendMouseScroll(s.handle(), delta);
        return true;
    }
}
