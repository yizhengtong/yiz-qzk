package net.minecraft.client.yiz.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.client.render.HandheldPanelRenderer.PanelGeometry;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager.ManagedSession;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 面板交互。所有"是否能转发"的判断都委派给 {@link PanelLifecycle}。
 *
 * <p><b>鼠标</b>：准星命中面板 + 该面板 LIVE 时，鼠标点击/滚轮转发到该面板。
 * 不再每帧 sendMouseMove —— 准星位置只在按键发生那一刻通过 lParam 传递。</p>
 *
 * <p><b>键盘</b>：默认 OFF，按下"键盘转发开关键"切到 ON。
 * MC 进入 GUI/暂停时 lifecycle 自动转 DORMANT，转发自动失效。</p>
 */
public final class PanelInteractionManager {

    /** 当前准星命中的面板 id（-1 表示无）。每 tick 由 raycast 更新。*/
    private static volatile int hoveredPanelId = -1;
    /** 鼠标按下时锁定的目标面板，使 down/up 配对到同一个面板。*/
    private static int mousePressPanelId = -1;
    /** 已被本管理器消费的鼠标键 mask；用于配对吞掉对应的 release。*/
    private static int pressedMouseMask = 0;

    /** 键盘转发是否开启。仅当目标面板 LIVE 时生效。*/
    private static volatile boolean keyboardForwarding = false;
    private static volatile int activeKeyboardPanelId = -1;

    private PanelInteractionManager() {}

    public static int getHoveredPanelId()        { return hoveredPanelId; }
    public static boolean isHovering()           { return hoveredPanelId > 0; }
    public static boolean isKeyboardForwarding() { return keyboardForwarding; }
    public static int getKeyboardPanelId()       { return activeKeyboardPanelId; }

    // ════════════════════════════════════════════
    //  每 tick：仅做 raycast，更新 hoveredPanelId
    // ════════════════════════════════════════════

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        // 先驱动所有面板的 lifecycle（采样 MC 状态，必要时切 DORMANT/DEAD）。
        // 必须在 raycast/forwarding 判断之前完成。
        HandheldPanelRenderer.tickAll(mc);

        // raycast 仅在玩家可控（无 GUI、无暂停、玩家在世界里）时进行。
        if (mc.player == null || mc.level == null || mc.screen != null || mc.isPaused()) {
            hoveredPanelId = -1;
            // 键盘转发若开着，让它自然在 onKey 路径上失效
            return;
        }

        List<PanelGeometry> fixed = HandheldPanelRenderer.listFixedGeometry();
        if (fixed.isEmpty()) {
            hoveredPanelId = -1;
            keyboardForwarding = false;
            activeKeyboardPanelId = -1;
            return;
        }

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        org.joml.Vector3f look = cam.getLookVector();
        Vec3 lookVec = new Vec3(look.x, look.y, look.z);

        PanelRaycast.Hit hit = PanelRaycast.firstHit(eye, lookVec, fixed);
        hoveredPanelId = (hit != null) ? hit.panelId : -1;

        // 键盘转发的目标面板若已不存在，关闭转发
        if (keyboardForwarding) {
            PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(activeKeyboardPanelId);
            if (lc == null || !lc.canReceiveKey()) {
                keyboardForwarding = false;
                activeKeyboardPanelId = -1;
            }
        }
    }

    // ════════════════════════════════════════════
    //  鼠标按键
    // ════════════════════════════════════════════

    /**
     * 当准星命中面板时，先把当前命中点的 normalized 坐标推给 native，
     * 让随后的 mouse_event 落在正确的客户端坐标上。仅在点击那一刻调用一次。
     */
    private static void syncCursorToHit(int panelId) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        org.joml.Vector3f look = cam.getLookVector();
        List<PanelGeometry> fixed = HandheldPanelRenderer.listFixedGeometry();
        PanelRaycast.Hit hit = PanelRaycast.firstHit(
                cam.getPosition(),
                new Vec3(look.x, look.y, look.z),
                fixed);
        if (hit != null && hit.panelId == panelId) {
            ManagedSession s = HandheldPanelRenderer.getSession(panelId);
            if (s != null) {
                WindowCaptureManager.sendMouseMove(s.handle(), hit.nx, hit.ny);
            }
        }
    }

    /** @return true 表示事件已转发，调用方应 setCanceled */
    public static boolean onMouseButton(int button, boolean down) {
        if (button < 0 || button > 2) return false;
        int bit = 1 << button;

        if (!down) {
            // release：仅当对应 down 被我们消费过时才转发
            if ((pressedMouseMask & bit) == 0) return false;
            pressedMouseMask &= ~bit;
            int panelId = mousePressPanelId;
            if (pressedMouseMask == 0) mousePressPanelId = -1;
            PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(panelId);
            ManagedSession s = HandheldPanelRenderer.getSession(panelId);
            if (lc == null || !lc.canForward() || s == null) return true;  // 吞掉孤立 release
            WindowCaptureManager.sendMouseButton(s.handle(), button, false);
            lc.noteReleasedMouse(button);
            return true;
        }

        // press：仅当准星命中面板且 lifecycle LIVE 时转发
        if (hoveredPanelId <= 0) return false;
        PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(hoveredPanelId);
        if (lc == null || !lc.canForward()) return false;
        ManagedSession s = HandheldPanelRenderer.getSession(hoveredPanelId);
        if (s == null) return false;
        // 同步光标到点击位置（替代每帧 sendMouseMove）
        syncCursorToHit(hoveredPanelId);
        WindowCaptureManager.sendMouseButton(s.handle(), button, true);
        pressedMouseMask |= bit;
        mousePressPanelId = hoveredPanelId;
        lc.notePressedMouse(button);
        return true;
    }

    // ════════════════════════════════════════════
    //  鼠标滚轮
    // ════════════════════════════════════════════

    public static boolean onMouseScroll(double scrollDelta) {
        if (hoveredPanelId <= 0) return false;
        PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(hoveredPanelId);
        if (lc == null || !lc.canForward()) return false;
        ManagedSession s = HandheldPanelRenderer.getSession(hoveredPanelId);
        if (s == null) return false;
        int delta = (int) Math.signum(scrollDelta) * 120;
        if (delta == 0) return true;
        // 滚轮也需要光标位置正确（QQ 滚动针对鼠标下方控件）
        syncCursorToHit(hoveredPanelId);
        WindowCaptureManager.sendMouseScroll(s.handle(), delta);
        return true;
    }

    // ════════════════════════════════════════════
    //  键盘
    // ════════════════════════════════════════════

    public static boolean onKey(int key, int scancode, int action, int mods) {
        int toggleKey = GLFW.GLFW_KEY_Q; // 面板键盘转发（隐藏功能，未暴露键位绑定）

        // 切换键：仅当"准星已在面板上"或"键盘转发已开启"时才接管
        if (key == toggleKey) {
            if (!keyboardForwarding && hoveredPanelId <= 0) return false;
            if (action == GLFW.GLFW_PRESS) {
                if (keyboardForwarding) {
                    disableKeyboardForwarding();
                } else {
                    PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(hoveredPanelId);
                    if (lc != null && lc.canReceiveKey()) {
                        activeKeyboardPanelId = hoveredPanelId;
                        keyboardForwarding = true;
                    }
                }
            }
            return true;
        }

        // Ctrl+ESC 退出键盘转发（仅在转发开启时拦截）
        if (keyboardForwarding && key == GLFW.GLFW_KEY_ESCAPE) {
            boolean ctrlHeld = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
            if (ctrlHeld && action == GLFW.GLFW_PRESS) {
                disableKeyboardForwarding();
            }
            // 转发开启时吞掉所有 ESC（避免开暂停菜单）
            return true;
        }

        if (!keyboardForwarding) return false;

        PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(activeKeyboardPanelId);
        ManagedSession s = HandheldPanelRenderer.getSession(activeKeyboardPanelId);
        if (lc == null || !lc.canReceiveKey() || s == null) {
            disableKeyboardForwarding();
            return false;
        }

        int vk = GlfwToVk.translate(key);
        if (vk == 0) return true;  // 未映射的键吃掉

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            WindowCaptureManager.sendKeyEvent(s.handle(), vk, true);
            lc.notePressedKey(vk);
        } else if (action == GLFW.GLFW_RELEASE) {
            WindowCaptureManager.sendKeyEvent(s.handle(), vk, false);
            lc.noteReleasedKey(vk);
        }
        return true;
    }

    // ════════════════════════════════════════════
    //  键盘转发开关
    // ════════════════════════════════════════════

    public static boolean toggleKeyboardForwarding() {
        if (keyboardForwarding) {
            disableKeyboardForwarding();
            return true;
        }
        if (hoveredPanelId <= 0) return false;
        PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(hoveredPanelId);
        if (lc == null || !lc.canReceiveKey()) return false;
        activeKeyboardPanelId = hoveredPanelId;
        keyboardForwarding = true;
        return true;
    }

    /**
     * 关闭键盘转发。补发由 lifecycle 记录的所有已按下虚拟键的 release，
     * 避免目标窗口卡键。
     */
    public static void disableKeyboardForwarding() {
        if (!keyboardForwarding) return;
        PanelLifecycle lc = HandheldPanelRenderer.getLifecycle(activeKeyboardPanelId);
        if (lc != null) lc.flushKeysOnly();
        keyboardForwarding = false;
        activeKeyboardPanelId = -1;
    }
}
