#pragma warning(disable: 4819)

#include "DXGICapture.h"
#include "GDICapture.h"
#include "WindowEnumerator.h"
#include "InputInjector.h"

#include <jni.h>
#include <cstdio>
#include <cstring>
#include <unordered_map>
#include <memory>
#include <mutex>

// ==================== Session Management ====================

struct CaptureSession {
    int64_t id;
    HWND hwnd;
    std::unique_ptr<CaptureEngine> engine;
    uint8_t* pixelBuffer;    // pre-allocated buffer for current frame
    size_t bufferSize;
    int width;
    int height;
    bool alive;
};

static std::unordered_map<int64_t, std::unique_ptr<CaptureSession>> g_sessions;
static std::mutex g_sessionsMutex;
static int64_t g_nextSessionId = 1;

static CaptureSession* createSession(HWND hwnd) {
    auto session = std::make_unique<CaptureSession>();
    session->id = g_nextSessionId++;
    session->hwnd = hwnd;
    session->alive = true;

    // Prefer DXGI: Desktop Duplication captures what's actually on screen.
    // For HW-accelerated windows (Electron/Chromium/QQ NT), GDI PrintWindow returns black.
    // Fallback to GDI for legacy GDI-rendered windows.
    auto dxgi = std::make_unique<DXGICapture>();
    if (dxgi->initialize(hwnd)) {
        session->engine = std::move(dxgi);
        fprintf(stderr, "[WindowCapture] Session %lld using DXGI capture.\n", session->id);
    } else {
        auto gdi = std::make_unique<GDICapture>();
        if (gdi->initialize(hwnd)) {
            session->engine = std::move(gdi);
            fprintf(stderr, "[WindowCapture] Session %lld using GDI capture (fallback).\n", session->id);
        } else {
            fprintf(stderr, "[WindowCapture] Session %lld: all engines failed.\n", session->id);
            return nullptr;
        }
    }

    // 用 GetWindowRect 分配 buffer（DXGI cropToWindow 也是用 GetWindowRect）。
    // 引擎的 getWidth/getHeight 可能用 GetClientRect，会偏小导致 buffer 溢出。
    RECT wndRect;
    if (GetWindowRect(hwnd, &wndRect)) {
        session->width  = wndRect.right  - wndRect.left;
        session->height = wndRect.bottom - wndRect.top;
    } else {
        session->width  = session->engine->getWidth();
        session->height = session->engine->getHeight();
    }
    session->bufferSize = (size_t)session->width * session->height * 4;
    session->pixelBuffer = new uint8_t[session->bufferSize];
    memset(session->pixelBuffer, 0, session->bufferSize);

    auto* ptr = session.get();
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    g_sessions[session->id] = std::move(session);
    return ptr;
}

static CaptureSession* getSession(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(handle);
    if (it != g_sessions.end() && it->second->alive) {
        return it->second.get();
    }
    return nullptr;
}

static bool ensureBufferSize(CaptureSession* session, int width, int height) {
    size_t needed = (size_t)width * height * 4;
    if (needed > session->bufferSize) {
        delete[] session->pixelBuffer;
        session->pixelBuffer = new uint8_t[needed];
        session->bufferSize = needed;
    }
    session->width = width;
    session->height = height;
    return true;
}

static void destroySession(int64_t handle) {
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) return;

    auto& session = it->second;
    session->alive = false;
    if (session->engine) {
        session->engine->release();
        session->engine.reset();
    }
    delete[] session->pixelBuffer;
    session->pixelBuffer = nullptr;
    g_sessions.erase(it);
}

// ==================== JNI Entry Point ====================

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    fprintf(stderr, "[WindowCapture] DLL loaded.\n");
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    std::lock_guard<std::mutex> lock(g_sessionsMutex);
    for (auto& pair : g_sessions) {
        auto& s = pair.second;
        if (s->engine) s->engine->release();
        delete[] s->pixelBuffer;
    }
    g_sessions.clear();
    fprintf(stderr, "[WindowCapture] DLL unloaded, all sessions cleaned.\n");
}

// ==================== Native Method Implementations ====================

extern "C" {

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    enumWindows
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_enumWindows(
    JNIEnv* env, jclass cls)
{
    auto windows = WindowEnumerator::enumerate();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(
        (jsize)windows.size(), stringClass, nullptr);

    for (size_t i = 0; i < windows.size(); i++) {
        char buf[512];
        snprintf(buf, sizeof(buf), "%llu:%s",
                 (unsigned long long)windows[i].hwnd,
                 windows[i].title.c_str());
        jstring str = env->NewStringUTF(buf);
        env->SetObjectArrayElement(result, (jsize)i, str);
        env->DeleteLocalRef(str);
    }

    return result;
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    initCapture
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_initCapture(
    JNIEnv* env, jclass cls, jlong hwnd)
{
    HWND target = reinterpret_cast<HWND>((uintptr_t)hwnd);
    if (!IsWindow(target)) {
        fprintf(stderr, "[WindowCapture] initCapture: invalid hwnd %p\n", (void*)target);
        return 0;
    }

    CaptureSession* session = createSession(target);
    if (!session) return 0;
    return (jlong)session->id;
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    getWindowSize
 * Signature: (J)[I
 */
JNIEXPORT jintArray JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_getWindowSize(
    JNIEnv* env, jclass cls, jlong handle)
{
    CaptureSession* session = getSession(handle);
    if (!session) return nullptr;

    jintArray result = env->NewIntArray(2);
    jint vals[2] = { session->width, session->height };
    env->SetIntArrayRegion(result, 0, 2, vals);
    return result;
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    captureFrame
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_captureFrame(
    JNIEnv* env, jclass cls, jlong handle)
{
    CaptureSession* session = getSession(handle);
    if (!session || !session->alive) return nullptr;

    // 每次 capture 前只扩 buffer 不更新 width/height——实际
    // crop 尺寸可能比 GetWindowRect 小（桌面边界裁剪等）
    RECT wndRect;
    if (GetWindowRect(session->hwnd, &wndRect)) {
        int w = wndRect.right  - wndRect.left;
        int h = wndRect.bottom - wndRect.top;
        if (w > 0 && h > 0) {
            size_t needed = (size_t)w * h * 4;
            if (needed > session->bufferSize) {
                delete[] session->pixelBuffer;
                session->pixelBuffer = new uint8_t[needed];
                session->bufferSize = needed;
            }
        }
    }

    int outWidth, outHeight;
    bool ok = session->engine->capture(session->pixelBuffer, &outWidth, &outHeight);

    if (!ok) return nullptr;

    if (outWidth != session->width || outHeight != session->height) {
        ensureBufferSize(session, outWidth, outHeight);
    }

    // Wrap the buffer in a DirectByteBuffer — caller reads, won't write
    // so it's safe to keep the same buffer across calls
    return env->NewDirectByteBuffer(session->pixelBuffer,
                                    (jlong)(outWidth * outHeight * 4));
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    releaseCapture
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_releaseCapture(
    JNIEnv* env, jclass cls, jlong handle)
{
    destroySession(handle);
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    sendMouseMove
 * Signature: (JDD)V
 */
JNIEXPORT void JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_sendMouseMove(
    JNIEnv* env, jclass cls, jlong handle, jdouble nx, jdouble ny)
{
    CaptureSession* session = getSession(handle);
    if (!session) return;
    InputInjector::sendMouseMove(session->hwnd, (double)nx, (double)ny);
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    sendMouseButton
 * Signature: (JIZ)V
 */
JNIEXPORT void JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_sendMouseButton(
    JNIEnv* env, jclass cls, jlong handle, jint button, jboolean down)
{
    CaptureSession* session = getSession(handle);
    if (!session) return;
    InputInjector::sendMouseButton(session->hwnd, (int)button, (bool)down);
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    sendMouseScroll
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_sendMouseScroll(
    JNIEnv* env, jclass cls, jlong handle, jint delta)
{
    CaptureSession* session = getSession(handle);
    if (!session) return;
    InputInjector::sendMouseScroll(session->hwnd, (int)delta);
}

/*
 * Class:     net_minecraft_client_yiz_windowmapper_WindowCaptureManager
 * Method:    sendKeyEvent
 * Signature: (JIZ)V
 */
JNIEXPORT void JNICALL
Java_net_minecraft_client_yiz_windowmapper_WindowCaptureManager_sendKeyEvent(
    JNIEnv* env, jclass cls, jlong handle, jint vkCode, jboolean down)
{
    CaptureSession* session = getSession(handle);
    if (!session) return;
    InputInjector::sendKeyEvent(session->hwnd, (int)vkCode, (bool)down);
}

} // extern "C"
