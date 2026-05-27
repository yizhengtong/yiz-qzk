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
     * Caller passes maxBytes (the buffer's actual capacity in bytes) so the
     * engine refuses to write beyond it. Returning false signals the caller
     * to skip this frame and (typically) reallocate before the next try.
     * On success, outWidth/outHeight are set to the captured frame dimensions
     * and outBuffer holds outWidth*outHeight*4 BGRA bytes.
     */
    virtual bool capture(uint8_t* outBuffer, size_t maxBytes,
                         int* outWidth, int* outHeight) = 0;

    /** Get the current capture dimensions. */
    virtual int getWidth() const = 0;
    virtual int getHeight() const = 0;

    /** Release all resources. */
    virtual void release() = 0;
};
