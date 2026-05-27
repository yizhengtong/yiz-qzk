#pragma once

#include <cstdint>
#include <windows.h>

/**
 * Abstract capture engine interface.
 * Implementations: DXGICapture (primary), GDICapture (fallback).
 */
class CaptureEngine {
public:
    virtual ~CaptureEngine() = default;

    /** Initialize capture for the given window. Returns false on failure. */
    virtual bool initialize(HWND hwnd) = 0;

    /**
     * Capture one frame into the provided buffer.
     * Buffer must be at least width * height * 4 bytes (BGRA).
     * On success, outWidth/outHeight are set to the captured frame dimensions.
     */
    virtual bool capture(uint8_t* outBuffer, int* outWidth, int* outHeight) = 0;

    /** Get the current capture dimensions. */
    virtual int getWidth() const = 0;
    virtual int getHeight() const = 0;

    /** Release all resources. */
    virtual void release() = 0;
};
