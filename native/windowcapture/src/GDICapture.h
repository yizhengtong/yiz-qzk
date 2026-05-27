#pragma once

#include "CaptureEngine.h"

/**
 * GDI-based capture engine (fallback).
 * Uses BitBlt/PrintWindow to capture window contents.
 * Works on any Windows version, but slower and CPU-bound.
 */
class GDICapture : public CaptureEngine {
public:
    GDICapture();
    ~GDICapture() override;

    bool initialize(HWND hwnd) override;
    bool capture(uint8_t* outBuffer, int* outWidth, int* outHeight) override;
    int getWidth() const override { return m_width; }
    int getHeight() const override { return m_height; }
    void release() override;

private:
    HWND m_hwnd = nullptr;
    int m_width = 0;
    int m_height = 0;

    HDC m_windowDC = nullptr;
    HDC m_memDC = nullptr;
    HBITMAP m_bitmap = nullptr;
    HBITMAP m_oldBitmap = nullptr;

    bool m_initialized = false;
};
