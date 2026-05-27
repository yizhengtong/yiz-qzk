#include "WindowEnumerator.h"
#include <vector>
#include <algorithm>
#include <TlHelp32.h>
#include <dwmapi.h>

std::vector<WindowInfo> WindowEnumerator::enumerate() {
    std::vector<WindowInfo> windows;
    EnumWindows(enumProc, reinterpret_cast<LPARAM>(&windows));

    // Sort: non-minimized first, then by title
    std::sort(windows.begin(), windows.end(),
        [](const WindowInfo& a, const WindowInfo& b) {
            if (a.minimized != b.minimized) return !a.minimized;
            return a.title < b.title;
        });

    return windows;
}

BOOL CALLBACK WindowEnumerator::enumProc(HWND hwnd, LPARAM lParam) {
    auto* windows = reinterpret_cast<std::vector<WindowInfo>*>(lParam);

    if (!isCapturable(hwnd)) return TRUE;

    WindowInfo info = {};
    info.hwnd = hwnd;

    // Get title
    int titleLen = GetWindowTextLengthA(hwnd);
    if (titleLen > 0) {
        char* buf = new char[titleLen + 1];
        GetWindowTextA(hwnd, buf, titleLen + 1);
        info.title = buf;
        delete[] buf;
    }

    // Check minimized
    info.minimized = IsIconic(hwnd);

    // Get size
    RECT rect;
    if (GetClientRect(hwnd, &rect)) {
        info.width = rect.right - rect.left;
        info.height = rect.bottom - rect.top;
    }

    windows->push_back(info);
    return TRUE;
}

bool WindowEnumerator::isCapturable(HWND hwnd) {
    if (!IsWindowVisible(hwnd)) return false;

    // Filter out windows without titles
    int titleLen = GetWindowTextLengthA(hwnd);
    if (titleLen == 0) return false;

    // Filter out the window if it has a parent (child windows)
    if (GetParent(hwnd) != nullptr) return false;

    // Filter out cloaked windows (invisible DWM composited)
    BOOL cloaked = FALSE;
    DwmGetWindowAttribute(hwnd, DWMWA_CLOAKED, &cloaked, sizeof(cloaked));
    if (cloaked) return false;

    // Filter out windows with zero client area
    RECT rect;
    if (GetClientRect(hwnd, &rect)) {
        if (rect.right - rect.left <= 0 || rect.bottom - rect.top <= 0) return false;
    }

    // Filter out the desktop window and other system windows
    wchar_t className[256];
    if (GetClassNameW(hwnd, className, 256)) {
        if (_wcsicmp(className, L"Progman") == 0) return false;    // Desktop
        if (_wcsicmp(className, L"WorkerW") == 0) return false;
        if (_wcsicmp(className, L"Shell_TrayWnd") == 0) return false;
        if (_wcsicmp(className, L"Windows.UI.Core.CoreWindow") == 0) return false;
    }

    return true;
}
