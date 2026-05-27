#pragma warning(disable: 4819)

#include "DXGICapture.h"
#include <cstdio>
#include <tchar.h>

DXGICapture::DXGICapture() = default;
DXGICapture::~DXGICapture() { release(); }

bool DXGICapture::initialize(HWND hwnd) {
    m_hwnd = hwnd;
    if (!createDevice()) return false;
    if (!findOutputForWindow(hwnd)) return false;
    if (!setupDuplication()) return false;

    // Get initial window size
    RECT rect;
    if (GetClientRect(hwnd, &rect)) {
        m_width = rect.right - rect.left;
        m_height = rect.bottom - rect.top;
    }

    m_initialized = true;
    fprintf(stderr, "[WindowCapture] DXGI initialized for hwnd=%p (%dx%d)\n",
            (void*)hwnd, m_width, m_height);
    return true;
}

bool DXGICapture::createDevice() {
    D3D_FEATURE_LEVEL featureLevels[] = {
        D3D_FEATURE_LEVEL_11_0,
        D3D_FEATURE_LEVEL_10_1,
        D3D_FEATURE_LEVEL_10_0,
    };

    UINT flags = D3D11_CREATE_DEVICE_BGRA_SUPPORT;
#ifdef _DEBUG
    flags |= D3D11_CREATE_DEVICE_DEBUG;
#endif

    D3D_FEATURE_LEVEL selectedLevel;
    HRESULT hr = D3D11CreateDevice(
        nullptr,                    // default adapter
        D3D_DRIVER_TYPE_HARDWARE,
        nullptr,                    // no software rasterizer
        flags,
        featureLevels, ARRAYSIZE(featureLevels),
        D3D11_SDK_VERSION,
        &m_device,
        &selectedLevel,
        &m_context
    );

    if (FAILED(hr)) {
        fprintf(stderr, "[WindowCapture] D3D11CreateDevice failed: 0x%08lx\n", hr);
        // Try WARP (software) as fallback within DXGI engine
        hr = D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_WARP,
            nullptr, flags, featureLevels, ARRAYSIZE(featureLevels),
            D3D11_SDK_VERSION, &m_device, &selectedLevel, &m_context);
        if (FAILED(hr)) {
            fprintf(stderr, "[WindowCapture] D3D11 WARP fallback also failed: 0x%08lx\n", hr);
            return false;
        }
        fprintf(stderr, "[WindowCapture] Using WARP software adapter.\n");
    }
    return true;
}

bool DXGICapture::findOutputForWindow(HWND hwnd) {
    ComPtr<IDXGIDevice> dxgiDevice;
    HRESULT hr = m_device.As(&dxgiDevice);
    if (FAILED(hr)) {
        fprintf(stderr, "[WindowCapture] QueryInterface IDXGIDevice failed: 0x%08lx\n", hr);
        return false;
    }

    ComPtr<IDXGIAdapter> adapter;
    hr = dxgiDevice->GetAdapter(&adapter);
    if (FAILED(hr)) {
        fprintf(stderr, "[WindowCapture] GetAdapter failed: 0x%08lx\n", hr);
        return false;
    }

    // Find which monitor the window is on
    HMONITOR hMonitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
    MONITORINFOEXW monitorInfo = {};
    monitorInfo.cbSize = sizeof(MONITORINFOEXW);
    GetMonitorInfoW(hMonitor, (MONITORINFO*)&monitorInfo);

    // Enumerate outputs on the adapter
    ComPtr<IDXGIOutput> output;
    UINT i = 0;
    while (adapter->EnumOutputs(i, &output) != DXGI_ERROR_NOT_FOUND) {
        DXGI_OUTPUT_DESC desc;
        output->GetDesc(&desc);

        // Match by monitor name
        if (_wcsicmp(desc.DeviceName, monitorInfo.szDevice) == 0) {
            // Not storing output since we get it via adapter->EnumOutputs again in setupDuplication
            return true;
        }
        i++;
    }

    // Fallback: use first output
    i = 0;
    if (adapter->EnumOutputs(0, &output) != DXGI_ERROR_NOT_FOUND) {
        fprintf(stderr, "[WindowCapture] Using first adapter output (monitor match failed).\n");
        return true;
    }

    fprintf(stderr, "[WindowCapture] No DXGI outputs found.\n");
    return false;
}

bool DXGICapture::setupDuplication() {
    ComPtr<IDXGIDevice> dxgiDevice;
    HRESULT hr = m_device.As(&dxgiDevice);
    if (FAILED(hr)) return false;

    ComPtr<IDXGIAdapter> adapter;
    hr = dxgiDevice->GetAdapter(&adapter);
    if (FAILED(hr)) return false;

    // Enumerate outputs and try DuplicateOutput on each
    ComPtr<IDXGIOutput> output;
    UINT i = 0;
    while (adapter->EnumOutputs(i, &output) != DXGI_ERROR_NOT_FOUND) {
        ComPtr<IDXGIOutput1> output1;
        hr = output.As(&output1);
        if (SUCCEEDED(hr)) {
            hr = output1->DuplicateOutput(m_device.Get(), &m_duplication);
            if (SUCCEEDED(hr)) return true;
        }
        output.Reset();
        i++;
    }

    fprintf(stderr, "[WindowCapture] DuplicateOutput failed for all outputs: 0x%08lx\n", hr);
    return false;
}

bool DXGICapture::capture(uint8_t* outBuffer, int* outWidth, int* outHeight) {
    if (!m_initialized || !m_duplication) return false;

    ComPtr<IDXGIResource> desktopResource;
    DXGI_OUTDUPL_FRAME_INFO frameInfo = {};

    HRESULT hr = m_duplication->AcquireNextFrame(
        33,     // 33ms timeout (~30fps max)
        &frameInfo,
        &desktopResource
    );

    if (hr == DXGI_ERROR_WAIT_TIMEOUT) return false;
    if (hr == DXGI_ERROR_ACCESS_LOST) {
        fprintf(stderr, "[WindowCapture] DXGI access lost, reinitializing...\n");
        m_duplication.Reset();
        setupDuplication();
        return false;
    }
    if (FAILED(hr)) {
        fprintf(stderr, "[WindowCapture] AcquireNextFrame failed: 0x%08lx\n", hr);
        return false;
    }

    // Get the desktop texture
    ComPtr<ID3D11Texture2D> desktopTexture;
    hr = desktopResource.As(&desktopTexture);
    if (FAILED(hr)) goto cleanup;

    // Crop to window region
    cropToWindow(desktopTexture.Get(), outBuffer, outWidth, outHeight);

cleanup:
    m_duplication->ReleaseFrame();
    return SUCCEEDED(hr);
}

bool DXGICapture::cropToWindow(ID3D11Texture2D* desktopTexture,
                                uint8_t* outBuffer, int* outWidth, int* outHeight) {
    // Get window position in screen coordinates
    RECT windowRect;
    if (!GetWindowRect(m_hwnd, &windowRect)) {
        // Fallback: GetClientRect + ClientToScreen
        GetClientRect(m_hwnd, &windowRect);
        POINT pt = {0, 0};
        ClientToScreen(m_hwnd, &pt);
        windowRect.left = pt.x;
        windowRect.top = pt.y;
        windowRect.right = pt.x + windowRect.right;
        windowRect.bottom = pt.y + windowRect.bottom;
    }

    int width = windowRect.right - windowRect.left;
    int height = windowRect.bottom - windowRect.top;

    if (width <= 0 || height <= 0) return false;

    *outWidth = width;
    *outHeight = height;
    m_width = width;
    m_height = height;

    // Get desktop texture description
    D3D11_TEXTURE2D_DESC texDesc;
    desktopTexture->GetDesc(&texDesc);

    // Clamp to desktop bounds
    windowRect.left = max(0, min((int)texDesc.Width, windowRect.left));
    windowRect.top = max(0, min((int)texDesc.Height, windowRect.top));
    windowRect.right = max(0, min((int)texDesc.Width, windowRect.right));
    windowRect.bottom = max(0, min((int)texDesc.Height, windowRect.bottom));

    int croppedW = windowRect.right - windowRect.left;
    int croppedH = windowRect.bottom - windowRect.top;

    if (croppedW <= 0 || croppedH <= 0) return false;

    // Create/lazy resize staging texture for the crop
    if (!m_stagingTexture ||
        [&]() {
            D3D11_TEXTURE2D_DESC d;
            m_stagingTexture->GetDesc(&d);
            return d.Width != (UINT)croppedW || d.Height != (UINT)croppedH;
        }()) {
        m_stagingTexture.Reset();

        D3D11_TEXTURE2D_DESC stagingDesc = {};
        stagingDesc.Width = croppedW;
        stagingDesc.Height = croppedH;
        stagingDesc.MipLevels = 1;
        stagingDesc.ArraySize = 1;
        stagingDesc.Format = texDesc.Format;
        stagingDesc.SampleDesc.Count = 1;
        stagingDesc.Usage = D3D11_USAGE_STAGING;
        stagingDesc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;

        HRESULT hr = m_device->CreateTexture2D(&stagingDesc, nullptr, &m_stagingTexture);
        if (FAILED(hr)) {
            fprintf(stderr, "[WindowCapture] CreateTexture2D staging failed: 0x%08lx\n", hr);
            return false;
        }
    }

    // Copy subresource region
    D3D11_BOX srcBox = {
        (UINT)windowRect.left, (UINT)windowRect.top, 0,
        (UINT)windowRect.right, (UINT)windowRect.bottom, 1
    };
    m_context->CopySubresourceRegion(m_stagingTexture.Get(), 0, 0, 0, 0,
                                      desktopTexture, 0, &srcBox);

    // Map and read
    D3D11_MAPPED_SUBRESOURCE mapped = {};
    HRESULT hr = m_context->Map(m_stagingTexture.Get(), 0, D3D11_MAP_READ, 0, &mapped);
    if (FAILED(hr)) {
        fprintf(stderr, "[WindowCapture] Map failed: 0x%08lx\n", hr);
        return false;
    }

    // Copy row by row (handle potential stride mismatch)
    // DXGI outputs BGRA8, which is GL_BGRA compatible
    size_t rowSize = croppedW * 4;
    for (int y = 0; y < croppedH; y++) {
        memcpy(outBuffer + y * rowSize,
               (uint8_t*)mapped.pData + y * mapped.RowPitch,
               rowSize);
    }

    m_context->Unmap(m_stagingTexture.Get(), 0);
    return true;
}

void DXGICapture::release() {
    m_stagingTexture.Reset();
    m_duplication.Reset();
    m_context.Reset();
    m_device.Reset();
    m_initialized = false;
}
