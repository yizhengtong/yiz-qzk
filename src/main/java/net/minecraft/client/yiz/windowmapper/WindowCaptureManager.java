package net.minecraft.client.yiz.windowmapper;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JNI bridge for capturing desktop window contents on Windows.
 *
 * <p>Manages the native {@code WindowCapture.dll} lifecycle. Each active capture
 * is a "session" identified by an opaque {@code long} handle.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   long hwnd = WindowCaptureManager.getWindows()[0].hwnd();
 *   long handle = WindowCaptureManager.initCapture(hwnd);
 *   ByteBuffer frame = WindowCaptureManager.captureFrame(handle);
 *   // ... upload frame to GL texture ...
 *   WindowCaptureManager.releaseCapture(handle);
 * }</pre>
 */
public final class WindowCaptureManager {

    private static final Logger LOG = LoggerFactory.getLogger("WindowCapture");
    private static final AtomicBoolean loaded = new AtomicBoolean(false);
    private static volatile boolean available;

    /**
     * Parsed window info from native enumeration.
     */
    public record WindowInfo(long hwnd, String title, int width, int height, boolean minimized) {}

    private WindowCaptureManager() {}

    // ==================== DLL Loading ====================

    public static boolean isAvailable() {
        ensureLoaded();
        return available;
    }

    private static void ensureLoaded() {
        if (!loaded.compareAndSet(false, true)) return;
        try {
            System.loadLibrary("WindowCapture");
            available = true;
            LOG.info("WindowCapture.dll loaded from java.library.path");
        } catch (UnsatisfiedLinkError e1) {
            try {
                String dllPath = System.getProperty("user.dir") + "/WindowCapture.dll";
                System.load(dllPath);
                available = true;
                LOG.info("WindowCapture.dll loaded from {}", dllPath);
            } catch (UnsatisfiedLinkError e2) {
                available = false;
                LOG.warn("WindowCapture.dll not found. Window mapping unavailable.");
                LOG.debug("DLL load error", e2);
            }
        }
    }

    // ==================== Native Methods ====================

    /**
     * Enumerate all visible top-level windows.
     * @return array of {@code "hwnd:title"} strings, or empty if DLL unavailable
     */
    public static native String[] enumWindows();

    /**
     * Initialize capture for the given window handle.
     * @param hwnd Windows HWND (from {@link WindowInfo#hwnd})
     * @return session handle (>0), or 0 on failure
     */
    public static native long initCapture(long hwnd);

    /**
     * Get the current pixel dimensions of the captured window.
     * @return int[2] = [width, height], or null if session invalid
     */
    public static native int[] getWindowSize(long handle);

    /**
     * Capture the current frame contents.
     * <p>The returned buffer is a DirectByteBuffer in BGRA format and is
     * <b>only valid until the next call to {@code captureFrame}</b> on this session.</p>
     *
     * @return DirectByteBuffer with BGRA pixel data, or null on failure/timeout
     */
    public static native ByteBuffer captureFrame(long handle);

    /**
     * Release all resources for this capture session.
     */
    public static native void releaseCapture(long handle);

    /**
     * Send a mouse move event at normalized coordinates.
     * @param nx 0.0 (left) to 1.0 (right)
     * @param ny 0.0 (top) to 1.0 (bottom)
     */
    public static native void sendMouseMove(long handle, double nx, double ny);

    /**
     * Send a mouse button press or release.
     * @param button 0=left, 1=right, 2=middle
     */
    public static native void sendMouseButton(long handle, int button, boolean down);

    /** Send mouse wheel delta (positive = scroll up). */
    public static native void sendMouseScroll(long handle, int delta);

    /**
     * Send a keyboard key press or release.
     * @param vkCode Windows virtual key code (e.g., {@code VK_RETURN = 0x0D})
     */
    public static native void sendKeyEvent(long handle, int vkCode, boolean down);

    /**
     * Force-send variants. Bypass the lifecycle paused check; only used by
     * PanelLifecycle when transitioning LIVE→DORMANT to flush already-pressed
     * keys/buttons so the target window doesn't see a stuck key.
     */
    public static native void sendKeyEventForce(long handle, int vkCode, boolean down);
    public static native void sendMouseButtonForce(long handle, int button, boolean down);

    /**
     * Lifecycle gates. While paused, captureFrame returns null and all
     * non-force send* calls are silent no-ops. Used for ATTACHED_DORMANT.
     */
    public static native void pauseSession(long handle);
    public static native void resumeSession(long handle);

    /** Returns false if the session is gone OR the underlying HWND has been destroyed. */
    public static native boolean isSessionAlive(long handle);

    // ==================== High-Level Helpers ====================

    /**
     * Asynchronously enumerate windows and parse them into structured records.
     */
    public static CompletableFuture<WindowInfo[]> getWindowsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            ensureLoaded();
            if (!available) return new WindowInfo[0];

            String[] raw = enumWindows();
            if (raw == null) return new WindowInfo[0];

            WindowInfo[] result = new WindowInfo[raw.length];
            for (int i = 0; i < raw.length; i++) {
                String s = raw[i];
                int sep = s.indexOf(':');
                long hwnd = Long.parseLong(s.substring(0, sep));
                String title = s.substring(sep + 1);

                // Size will be filled when initialized
                result[i] = new WindowInfo(hwnd, title, 0, 0, false);
            }
            return result;
        });
    }

    /**
     * Create a managed capture session that automatically cleans up via {@link Cleaner}.
     */
    public static ManagedSession createManaged(long hwnd) {
        long handle = initCapture(hwnd);
        if (handle == 0) return null;
        return new ManagedSession(handle);
    }

    /**
     * Auto-closing capture session wrapper.
     */
    public static final class ManagedSession implements AutoCloseable {
        private final long handle;
        private final Cleaner.Cleanable cleanable;

        ManagedSession(long handle) {
            this.handle = handle;
            this.cleanable = Cleaner.create().register(this, () -> releaseCapture(handle));
        }

        public long handle() { return handle; }

        public int[] getSize() { return getWindowSize(handle); }

        public ByteBuffer captureFrame() {
            return WindowCaptureManager.captureFrame(handle);
        }

        public void pause()  { WindowCaptureManager.pauseSession(handle); }
        public void resume() { WindowCaptureManager.resumeSession(handle); }
        public boolean isAlive() { return WindowCaptureManager.isSessionAlive(handle); }

        public void forceKeyEvent(int vk, boolean down) {
            WindowCaptureManager.sendKeyEventForce(handle, vk, down);
        }
        public void forceMouseButton(int button, boolean down) {
            WindowCaptureManager.sendMouseButtonForce(handle, button, down);
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }
}
