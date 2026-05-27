package net.minecraft.client.yiz.core;

/**
 * JNI bridge for resolving narrow klass pointer encoding.
 *
 * <p>HotSpot stores compressed klass pointers in object headers as:
 *   full_klass = (narrow_klass &lt;&lt; shift) + base</p>
 *
 * <p>The native library ({@code narrow_klass.dll}) reads the encoding by
 * comparing the narrow klass from an object header with the full Klass*
 * obtained through JNI internals.</p>
 *
 * <p>If the native library is not available, instance-level protection
 * ({@link PlayerClassSwapper}) still works — only vtable replacement
 * ({@link VTableReplace}) is disabled.</p>
 */
final class NarrowKlassBridge {

    private static volatile boolean loaded;
    private static volatile boolean available;

    private NarrowKlassBridge() {}

    static boolean isAvailable() {
        if (!loaded) {
            loadNative();
            loaded = true;
        }
        return available;
    }

    private static void loadNative() {
        try {
            // Try to load from the working directory (run/)
            System.loadLibrary("narrow_klass");
            available = true;
            System.out.println("[NarrowKlassBridge] Native library loaded successfully");
        } catch (UnsatisfiedLinkError e1) {
            try {
                // Try with explicit path: same dir as the mod JAR
                String dllPath = System.getProperty("user.dir") + "/narrow_klass.dll";
                System.load(dllPath);
                available = true;
                System.out.println("[NarrowKlassBridge] Native library loaded from: " + dllPath);
            } catch (UnsatisfiedLinkError e2) {
                available = false;
                System.err.println("[NarrowKlassBridge] Native library not found. " +
                        "VTable replacement unavailable.");
            }
        }
    }

    /**
     * Get the narrow klass base. Must pass a sample Object (any Java object)
     * for JNI to extract the encoding from.
     *
     * @return the base, or 0 if unavailable
     */
    static native long getBase0(Object sample);

    /**
     * Get the narrow klass shift (typically 3 for 8-byte alignment).
     */
    static native int getShift0();

    /**
     * Resolve a narrow klass to a full address.
     *
     * @return full klass address, or 0 if unresolved
     */
    static long resolve(int narrowKlass) {
        if (!isAvailable()) return 0;
        try {
            long base = getBase0(new Object());
            int shift = getShift0();
            if (base == 0) return 0;
            long narrowUnsigned = narrowKlass & 0xFFFFFFFFL;
            return (narrowUnsigned << shift) + base;
        } catch (Exception e) {
            return 0;
        }
    }
}
