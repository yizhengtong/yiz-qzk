#pragma once

#include <windows.h>

/**
 * Injects mouse and keyboard input into a target window via PostMessage.
 *
 * <p>All mouse coordinates are cached per-hwnd by sendMouseMove; subsequent
 * sendMouseButton / sendMouseScroll calls reuse the last cached client-area
 * coordinates rather than the physical cursor position. This is essential
 * when the physical cursor lives in a different window (e.g. Minecraft) but
 * we want to drive a virtual cursor inside the target window.</p>
 *
 * <p>sendKeyEvent uses ONLY PostMessage — never SendInput — to avoid
 * stealing foreground focus from the host application.</p>
 */
class InputInjector {
public:
    /**
     * Cache normalized coordinates for the target window AND post WM_MOUSEMOVE.
     * @param nx 0.0 (left) to 1.0 (right)
     * @param ny 0.0 (top) to 1.0 (bottom)
     */
    static void sendMouseMove(HWND hwnd, double nx, double ny);

    /**
     * Send a mouse button event at the LAST cached client coordinates for hwnd
     * (set by sendMouseMove). If no cache exists, falls back to client (0,0).
     *
     * @param button 0=left, 1=right, 2=middle
     * @param down   true for press, false for release
     */
    static void sendMouseButton(HWND hwnd, int button, bool down);

    /** Send mouse wheel delta at last cached coordinates. */
    static void sendMouseScroll(HWND hwnd, int delta);

    /**
     * Send a keyboard event to the target window via PostMessage only.
     * Does NOT call SendInput — that would steal foreground focus.
     */
    static void sendKeyEvent(HWND hwnd, int vkCode, bool down);

    /** Send a Unicode character via WM_CHAR. */
    static void sendChar(HWND hwnd, wchar_t ch);

private:
    /** Convert normalized coordinates to client coordinates for the window. */
    static POINT normalizedToClient(HWND hwnd, double nx, double ny);
};
