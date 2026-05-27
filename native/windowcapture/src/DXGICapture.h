#pragma once

#include "CaptureEngine.h"
#include <d3d11.h>
#include <dxgi1_2.h>
#include <wrl/client.h>

using Microsoft::WRL::ComPtr;

/**
 * DXGI Desktop Duplication capture engine.
 * Captures the full desktop and crops to the target window region.
 * Preferred when GPU acceleration is available (Windows 8+).
 */
class DXGICapture : public CaptureEngine {
public:
    DXGICapture();
    ~DXGICapture() override;

    bool initialize(HWND hwnd) override;
    bool capture(uint8_t* outBuffer, int* outWidth, int* outHeight) override;
    int getWidth() const override { return m_width; }
    int getHeight() const override { return m_height; }
    void release() override;

private:
    /** Find the DXGI output that contains the target window. */
    bool findOutputForWindow(HWND hwnd);

    /** Create D3D11 device and get DXGI device. */
    bool createDevice();

    /** Set up desktop duplication on the found output. */
    bool setupDuplication();

    /** Crop the full desktop frame to the target window region. */
    bool cropToWindow(ID3D11Texture2D* desktopTexture, uint8_t* outBuffer,
                      int* outWidth, int* outHeight);

    HWND m_hwnd = nullptr;
    int m_width = 0;
    int m_height = 0;

    ComPtr<ID3D11Device> m_device;
    ComPtr<ID3D11DeviceContext> m_context;
    ComPtr<IDXGIOutputDuplication> m_duplication;
    ComPtr<ID3D11Texture2D> m_stagingTexture;

    bool m_initialized = false;
};
