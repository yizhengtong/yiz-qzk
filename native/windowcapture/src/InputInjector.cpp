#include "InputInjector.h"
#include <cstdio>
#include <cmath>
#include <windowsx.h>
#include <unordered_map>
#include <mutex>

// ──────────────────────────────────────────────────────────────────────
//  Per-hwnd cached client-area coordinates (set by sendMouseMove)
// ──────────────────────────────────────────────────────────────────────

namespace {
    struct CachedPos { LONG x; LONG y; };
    std::unordered_map<HWND, CachedPos> g_lastClientPos;
    std::mutex g_posMutex;

    POINT getCachedClientPos(HWND hwnd) {
        std::lock_guard<std::mutex> lock(g_posMutex);
        auto it = g_lastClientPos.find(hwnd);
        if (it != g_lastClientPos.end()) {
            return POINT{ it->second.x, it->second.y };
        }
        return POINT{ 0, 0 };
    }

    void setCachedClientPos(HWND hwnd, LONG x, LONG y) {
        std::lock_guard<std::mutex> lock(g_posMutex);
        g_lastClientPos[hwnd] = { x, y };
    }

    bool isWindowUsable(HWND hwnd) {
        if (!IsWindow(hwnd)) return false;
        if (!IsWindowVisible(hwnd)) return false;
        if (IsIconic(hwnd)) return false; // minimized
        return true;
    }
}

void InputInjector::sendMouseMove(HWND hwnd, double nx, double ny) {
    if (!isWindowUsable(hwnd)) return;
    POINT pt = normalizedToClient(hwnd, nx, ny);
    setCachedClientPos(hwnd, pt.x, pt.y);
    LPARAM lParam = MAKELPARAM(pt.x, pt.y);
    PostMessage(hwnd, WM_MOUSEMOVE, 0, lParam);
}

POINT InputInjector::normalizedToClient(HWND hwnd, double nx, double ny) {
    RECT clientRect;
    GetClientRect(hwnd, &clientRect);

    int cx = (int)round(nx * (clientRect.right - clientRect.left));
    int cy = (int)round(ny * (clientRect.bottom - clientRect.top));

    POINT pt = { cx, cy };
    return pt;
}

void InputInjector::sendMouseButton(HWND hwnd, int button, bool down) {
    if (!isWindowUsable(hwnd)) return;
    UINT msg;
    WPARAM wParam;
    switch (button) {
        case 0: msg = down ? WM_LBUTTONDOWN : WM_LBUTTONUP; wParam = MK_LBUTTON; break;
        case 1: msg = down ? WM_RBUTTONDOWN : WM_RBUTTONUP; wParam = MK_RBUTTON; break;
        case 2: msg = down ? WM_MBUTTONDOWN : WM_MBUTTONUP; wParam = MK_MBUTTON; break;
        default: return;
    }
    POINT pt = getCachedClientPos(hwnd);
    PostMessage(hwnd, msg, wParam, MAKELPARAM(pt.x, pt.y));
}

void InputInjector::sendMouseScroll(HWND hwnd, int delta) {
    if (!isWindowUsable(hwnd)) return;
    // WM_MOUSEWHEEL takes SCREEN coordinates in lParam, not client.
    POINT clientPt = getCachedClientPos(hwnd);
    POINT screenPt = clientPt;
    ClientToScreen(hwnd, &screenPt);
    PostMessage(hwnd, WM_MOUSEWHEEL,
                MAKEWPARAM(0, (WORD)delta),
                MAKELPARAM(screenPt.x, screenPt.y));
}

void InputInjector::sendKeyEvent(HWND hwnd, int vkCode, bool down) {
    if (!isWindowUsable(hwnd)) return;
    // PostMessage only — SendInput would steal focus.
    UINT msg = down ? WM_KEYDOWN : WM_KEYUP;
    UINT scan = MapVirtualKey(vkCode, MAPVK_VK_TO_VSC);
    LPARAM lParam = (LPARAM)1                       // repeat count
                  | ((LPARAM)(scan & 0xFF) << 16)   // scan code
                  | (down ? 0 : (LPARAM)1 << 30)    // previous state
                  | (down ? 0 : (LPARAM)1 << 31);   // transition state
    PostMessage(hwnd, msg, (WPARAM)vkCode, lParam);
}

void InputInjector::sendChar(HWND hwnd, wchar_t ch) {
    if (!isWindowUsable(hwnd)) return;
    PostMessage(hwnd, WM_CHAR, (WPARAM)ch, 0);
}
