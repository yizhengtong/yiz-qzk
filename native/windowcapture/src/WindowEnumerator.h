#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <windows.h>

/**
 * Represents a visible top-level window available for capture.
 */
struct WindowInfo {
    HWND hwnd;
    std::string title;
    int width;
    int height;
    bool minimized;
};

/**
 * Enumerates all visible top-level windows on the desktop.
 */
class WindowEnumerator {
public:
    /**
     * Enumerate visible windows. Returns sorted by z-order (topmost first).
     * Filters out windows that cannot meaningfully be captured.
     */
    static std::vector<WindowInfo> enumerate();

private:
    static BOOL CALLBACK enumProc(HWND hwnd, LPARAM lParam);
    static bool isCapturable(HWND hwnd);
};
