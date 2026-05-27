#pragma once

#include <windows.h>

/**
 * Injects mouse and keyboard input into a target window.
 * Uses SendInput for precise simulation and PostMessage for background windows.
 */
class InputInjector {
public:
    /**
     * Move mouse cursor to the specified position in client coordinates
     * of the target window, then optionally send a click event.
     *
     * @param hwnd    Target window handle
     * @param nx      Normalized X coordinate (0.0 to 1.0, left to right)
     * @param ny      Normalized Y coordinate (0.0 to 1.0, top to bottom)
     */
    static void sendMouseMove(HWND hwnd, double nx, double ny);

    /**
     * Send a mouse button event at the current cursor position.
     *
     * @param button  0=left, 1=right, 2=middle
     * @param down    true for press, false for release
     */
    static void sendMouseButton(HWND hwnd, int button, bool down);

    /**
     * Send mouse wheel delta.
     * @param delta Positive = scroll up, negative = scroll down (WHEEL_DELTA units).
     */
    static void sendMouseScroll(HWND hwnd, int delta);

    /**
     * Send a keyboard event to the target window.
     *
     * @param vkCode  Windows virtual key code (VK_*)
     * @param down    true for key down, false for key up
     */
    static void sendKeyEvent(HWND hwnd, int vkCode, bool down);

    /**
     * Send a Unicode character.
     */
    static void sendChar(HWND hwnd, wchar_t ch);

private:
    /** Convert normalized coordinates to client coordinates for the window. */
    static POINT normalizedToClient(HWND hwnd, double nx, double ny);
};
