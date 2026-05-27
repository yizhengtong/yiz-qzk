#include "GDICapture.h"
#include <cstdio>

GDICapture::GDICapture() = default;
GDICapture::~GDICapture() { release(); }

bool GDICapture::initialize(HWND hwnd) {
    m_hwnd = hwnd;

    RECT rect;
    if (!GetClientRect(hwnd, &rect)) {
        fprintf(stderr, "[WindowCapture] GDI: GetClientRect failed for hwnd=%p\n", (void*)hwnd);
        return false;
    }

    m_width = rect.right - rect.left;
    m_height = rect.bottom - rect.top;

    if (m_width <= 0 || m_height <= 0) {
        fprintf(stderr, "[WindowCapture] GDI: window has zero size\n");
        return false;
    }

    m_windowDC = GetDC(hwnd);
    if (!m_windowDC) {
        fprintf(stderr, "[WindowCapture] GDI: GetDC failed\n");
        return false;
    }

    m_memDC = CreateCompatibleDC(m_windowDC);
    if (!m_memDC) {
        fprintf(stderr, "[WindowCapture] GDI: CreateCompatibleDC failed\n");
        return false;
    }

    m_bitmap = CreateCompatibleBitmap(m_windowDC, m_width, m_height);
    if (!m_bitmap) {
        fprintf(stderr, "[WindowCapture] GDI: CreateCompatibleBitmap failed\n");
        return false;
    }

    m_oldBitmap = (HBITMAP)SelectObject(m_memDC, m_bitmap);

    m_initialized = true;
    fprintf(stderr, "[WindowCapture] GDI initialized for hwnd=%p (%dx%d)\n",
            (void*)hwnd, m_width, m_height);
    return true;
}

bool GDICapture::capture(uint8_t* outBuffer, int* outWidth, int* outHeight) {
    if (!m_initialized) return false;

    // Check if window still exists and has valid size
    if (!IsWindow(m_hwnd)) return false;

    RECT rect;
    GetClientRect(m_hwnd, &rect);
    int w = rect.right - rect.left;
    int h = rect.bottom - rect.top;

    if (w <= 0 || h <= 0) return false;

    // Recreate bitmap if size changed
    if (w != m_width || h != m_height) {
        SelectObject(m_memDC, m_oldBitmap);
        DeleteObject(m_bitmap);
        m_bitmap = CreateCompatibleBitmap(m_windowDC, w, h);
        m_oldBitmap = (HBITMAP)SelectObject(m_memDC, m_bitmap);
        m_width = w;
        m_height = h;
    }

    // Capture: try PrintWindow first, fall back to BitBlt
    BOOL ok = PrintWindow(m_hwnd, m_memDC, PW_CLIENTONLY);
    if (!ok) {
        // Try PW_RENDERFULLCONTENT for DWM-composited windows
        ok = PrintWindow(m_hwnd, m_memDC, PW_RENDERFULLCONTENT);
    }
    if (!ok) {
        // Fallback to BitBlt
        ok = BitBlt(m_memDC, 0, 0, m_width, m_height,
                    m_windowDC, 0, 0, SRCCOPY);
    }
    if (!ok) return false;

    // Read pixels into buffer (BGRA format)
    BITMAPINFOHEADER bi = {};
    bi.biSize = sizeof(BITMAPINFOHEADER);
    bi.biWidth = m_width;
    bi.biHeight = -m_height;  // negative = top-down DIB
    bi.biPlanes = 1;
    bi.biBitCount = 32;
    bi.biCompression = BI_RGB;
    bi.biSizeImage = m_width * m_height * 4;

    int result = GetDIBits(m_memDC, m_bitmap, 0, m_height,
                           outBuffer, (BITMAPINFO*)&bi, DIB_RGB_COLORS);

    if (result == 0) {
        fprintf(stderr, "[WindowCapture] GDI: GetDIBits failed\n");
        return false;
    }

    *outWidth = m_width;
    *outHeight = m_height;
    return true;
}

void GDICapture::release() {
    if (m_memDC && m_oldBitmap) {
        SelectObject(m_memDC, m_oldBitmap);
        m_oldBitmap = nullptr;
    }
    if (m_bitmap) { DeleteObject(m_bitmap); m_bitmap = nullptr; }
    if (m_memDC) { DeleteDC(m_memDC); m_memDC = nullptr; }
    if (m_windowDC) { ReleaseDC(m_hwnd, m_windowDC); m_windowDC = nullptr; }
    m_initialized = false;
}
