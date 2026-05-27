package net.minecraft.client.yiz.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager;

import java.util.HashSet;
import java.util.Set;

/**
 * 单面板生命周期状态机。每个 {@link HandheldPanelRenderer.Panel} 持有一个实例。
 *
 * <p>是否抓帧、是否渲染、是否接受交互，全由 {@link #state} 决定。
 * 所有状态转换都通过 {@link #transition(State)} 触发，附带的 native 调用
 * （pause/resume、补发按键 release）在转换时一次性执行。</p>
 *
 * <p>权威源是 {@link #onClientTick(Minecraft, boolean)}：每 client tick 采样
 * MC 的状态（screen / pause / player / window 焦点），决定面板下一帧的状态。</p>
 */
public final class PanelLifecycle {

    public enum State {
        /** 未绑定面板（不应该出现，PanelLifecycle 总是绑定到 Panel 实例上）。*/
        DETACHED,
        /** 面板存活，MC 正常运行：抓帧、渲染、接受交互全开。*/
        LIVE,
        /** MC 暂停 / GUI 打开 / 失焦 / 玩家不在世界：保留资源，但停止一切 native I/O。*/
        DORMANT,
        /** hwnd 失效或用户主动移除：等待外部清理。*/
        DEAD,
    }

    private final long handle;
    private State state = State.LIVE;

    /** 当前还按着的鼠标键 mask（bit 0=left, 1=right, 2=middle）。LIVE→DORMANT 时补发 up。*/
    private int pressedMouseMask = 0;
    /** 当前还按着的虚拟键码集合。LIVE→DORMANT 时补发 release。*/
    private final Set<Integer> pressedVks = new HashSet<>();

    public PanelLifecycle(long sessionHandle) {
        this.handle = sessionHandle;
    }

    public State state() { return state; }

    public boolean canCapture()    { return state == State.LIVE; }
    public boolean canForward()    { return state == State.LIVE; }
    public boolean canReceiveKey() { return state == State.LIVE; }

    /**
     * 由外部（PanelInteractionManager）记录已转发的鼠标按下，
     * 以便 LIVE→DORMANT 时正确补发 up。
     */
    public void notePressedMouse(int button) { pressedMouseMask |= (1 << button); }
    public void noteReleasedMouse(int button) { pressedMouseMask &= ~(1 << button); }
    public boolean isMousePressed(int button) { return (pressedMouseMask & (1 << button)) != 0; }

    public void notePressedKey(int vk) { pressedVks.add(vk); }
    public void noteReleasedKey(int vk) { pressedVks.remove(vk); }

    /**
     * 每 client tick 调用：采样 MC 状态，必要时切换状态。
     *
     * @param mc Minecraft 单例
     * @param hwndStillValid 由调用方判断的 hwnd 有效性（HandheldPanelRenderer 知道
     *                       哪些 panel 的 hwnd 还活着）。false 时进入 DEAD。
     */
    public void onClientTick(Minecraft mc, boolean hwndStillValid) {
        State desired = computeDesired(mc, hwndStillValid);
        if (desired != state) transition(desired);
    }

    private State computeDesired(Minecraft mc, boolean hwndStillValid) {
        if (state == State.DEAD) return State.DEAD;        // 终态
        if (!hwndStillValid) return State.DEAD;
        if (mc.player == null || mc.level == null) return State.DORMANT;
        if (mc.screen != null) return State.DORMANT;
        if (mc.isPaused()) return State.DORMANT;
        // GLFW 焦点查询：MC 的 Window 类没暴露 isFocused()
        long handleW = mc.getWindow().getWindow();
        if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(handleW, org.lwjgl.glfw.GLFW.GLFW_FOCUSED) == 0) {
            return State.DORMANT;
        }
        return State.LIVE;
    }

    /** 标记为 DEAD（hwnd 失效或外部移除）。后续不会再转出。*/
    public void markDead() {
        if (state == State.DEAD) return;
        transition(State.DEAD);
    }

    private void transition(State next) {
        State prev = state;
        state = next;

        if (prev == State.LIVE && (next == State.DORMANT || next == State.DEAD)) {
            // 出 LIVE：补发已按下的键/按键的 release（force 通道，不被 paused 拦截）
            flushPressedInputs();
            // 通知 native 进入 paused
            try { WindowCaptureManager.pauseSession(handle); } catch (Throwable ignored) {}
        } else if ((prev == State.DORMANT) && next == State.LIVE) {
            // 进入 LIVE：通知 native 解除 paused
            try { WindowCaptureManager.resumeSession(handle); } catch (Throwable ignored) {}
        } else if (next == State.DEAD) {
            // 任何状态进入 DEAD 都先 flush 一次（以防 LIVE 直接 → DEAD 路径）
            if (prev == State.LIVE) flushPressedInputs();
            try { WindowCaptureManager.pauseSession(handle); } catch (Throwable ignored) {}
        }
    }

    private void flushPressedInputs() {
        for (int btn = 0; btn < 3; btn++) {
            if ((pressedMouseMask & (1 << btn)) != 0) {
                try { WindowCaptureManager.sendMouseButtonForce(handle, btn, false); } catch (Throwable ignored) {}
            }
        }
        pressedMouseMask = 0;

        for (int vk : pressedVks) {
            try { WindowCaptureManager.sendKeyEventForce(handle, vk, false); } catch (Throwable ignored) {}
        }
        pressedVks.clear();
    }

    /**
     * 主动 flush 已按下的鼠标键和虚拟键（不切状态）。
     * 由 PanelInteractionManager.disableKeyboardForwarding 在用户主动关闭键盘转发时调用，
     * 避免目标窗口卡键。
     */
    public void flushKeysOnly() {
        for (int vk : pressedVks) {
            try { WindowCaptureManager.sendKeyEventForce(handle, vk, false); } catch (Throwable ignored) {}
        }
        pressedVks.clear();
    }
}
