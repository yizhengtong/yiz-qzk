#include "InputInjector.h"
#include <cstdio>
#include <cmath>
#include <windowsx.h>

void InputInjector::sendMouseMove(HWND hwnd, double nx, double ny) {
    // 只用 PostMessage 发 WM_MOUSEMOVE，不移动物理鼠标。
    // SetCursorPos 会破坏 Minecraft 的鼠标锁定，引发崩溃。
    POINT clientPt = normalizedToClient(hwnd, nx, ny);
    LPARAM lParam = MAKELPARAM(clientPt.x, clientPt.y);
    PostMessage(hwnd, WM_MOUSEMOVE, 0, lParam);
}

POINT InputInjector::normalizedToClient(HWND hwnd, double nx, double ny) {
    RECT clientRect;
    GetClientRect(hwnd, &clientRect);

    int cx = (int)round(nx * (clientRect.right - clientRect.left));
    int cy = (int)round(ny * (clientRect.bottom - clientRect.top));

    POINT pt = {cx, cy};
    return pt;
}

void InputInjector::sendMouseButton(HWND hwnd, int button, bool down) {
    UINT msg;
    WPARAM wParam;
    switch (button) {
        case 0: msg = down ? WM_LBUTTONDOWN : WM_LBUTTONUP; wParam = MK_LBUTTON; break;
        case 1: msg = down ? WM_RBUTTONDOWN : WM_RBUTTONUP; wParam = MK_RBUTTON; break;
        case 2: msg = down ? WM_MBUTTONDOWN : WM_MBUTTONUP; wParam = MK_MBUTTON; break;
        default: return;
    }
    DWORD pos = GetMessagePos();
    PostMessage(hwnd, msg, wParam, MAKELPARAM(GET_X_LPARAM(pos), GET_Y_LPARAM(pos)));
}

void InputInjector::sendMouseScroll(HWND hwnd, int delta) {
    DWORD pos = GetMessagePos();
    PostMessage(hwnd, WM_MOUSEWHEEL,
                MAKEWPARAM(0, (WORD)delta),
                MAKELPARAM(GET_X_LPARAM(pos), GET_Y_LPARAM(pos)));
}

void InputInjector::sendKeyEvent(HWND hwnd, int vkCode, bool down) {
    // Post WM_KEYDOWN/WM_KEYUP to target window
    UINT msg = down ? WM_KEYDOWN : WM_KEYUP;
    PostMessage(hwnd, msg, (WPARAM)vkCode, 0);

    // Also use SendInput for foreground-aware apps
    INPUT input = {};
    input.type = INPUT_KEYBOARD;
    input.ki.wVk = static_cast<WORD>(vkCode);
    input.ki.wScan = static_cast<WORD>(MapVirtualKey(vkCode, MAPVK_VK_TO_VSC));
    input.ki.dwFlags = down ? 0 : KEYEVENTF_KEYUP;
    input.ki.time = 0;
    input.ki.dwExtraInfo = 0;
    SendInput(1, &input, sizeof(INPUT));
}

void InputInjector::sendChar(HWND hwnd, wchar_t ch) {
    PostMessage(hwnd, WM_CHAR, (WPARAM)ch, 0);
}
