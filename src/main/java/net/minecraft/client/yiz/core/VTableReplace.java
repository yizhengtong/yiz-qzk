package net.minecraft.client.yiz.core;

import sun.misc.Unsafe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-Java vtable method replacement using {@code sun.misc.Unsafe}.
 *
 * <p>Directly overwrites HotSpot internal {@code Method} entry points
 * ({@code _from_interpreted_entry} / {@code _from_compiled_entry})
 * to redirect virtual method dispatch — without ASM, Agent, bytecode
 * modification, or class redefinition.</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Get the {@code Klass*} from an instance's object header (same
 *       technique as {@link PlayerClassSwapper})</li>
 *   <li>Locate the vtable within the {@code InstanceKlass} structure</li>
 *   <li>Iterate vtable entries ({@code Method*} pointers), reading each
 *       method's name via the {@code ConstMethod → Symbol} chain</li>
 *   <li>Match against the target method name + descriptor</li>
 *   <li>Find the corresponding donor no-op method in
 *       {@link EmptyImplementations}</li>
 *   <li>Copy donor's {@code _from_interpreted_entry} and
 *       {@code _from_compiled_entry} to the target {@code Method*}</li>
 * </ol>
 *
 * <p>All JVM-structure offsets are determined at class-init time via
 * empirical probing — no hardcoded offsets.</p>
 */
@SuppressWarnings("removal")
public final class VTableReplace {

    private static final Unsafe U;
    private static final long KLASS_OFFSET;
    private static final boolean KLASS_COMPRESSED;

    // ── InstanceKlass vtable fields ──────────────────────────
    /** Offset of {@code _vtable_len} from klass base */
    static long VTABLE_LEN_OFFSET = -1;
    /** Offset of first vtable entry from klass base */
    static long VTABLE_BASE_OFFSET = -1;

    // ── Method structure fields ──────────────────────────────
    /** Offset of {@code _constMethod} from Method* */
    static long METHOD_CONST_METHOD_OFFSET = -1;
    /** Offset of {@code _from_interpreted_entry} from Method* */
    static long METHOD_FROM_INTERPRETED_OFFSET = -1;
    /** Offset of {@code _from_compiled_entry} from Method* */
    static long METHOD_FROM_COMPILED_OFFSET = -1;

    // ── ConstMethod structure fields ─────────────────────────
    /** Offset of {@code _name} (Symbol*) from ConstMethod* */
    static long CONST_METHOD_NAME_OFFSET = -1;
    /** Offset of {@code _signature} (Symbol*) from ConstMethod* */
    static long CONST_METHOD_SIGNATURE_OFFSET = -1;

    // ── Symbol structure fields ──────────────────────────────
    /** Offset of byte body from Symbol* */
    static long SYMBOL_BODY_OFFSET = 2;

    // ── Donor cache ──────────────────────────────────────────
    /** method-descriptor → donor entry-point addresses */
    private static final Map<String, long[]> DONOR_CACHE = new ConcurrentHashMap<>();
    /** Donor klass address */
    private static long donorKlassAddr;

    // ── Narrow klass decode ──────────────────────────────────
    /** Descompression base for narrow klass pointers */
    static long NARROW_KLASS_BASE;
    /** Descompression shift for narrow klass pointers (0 = no compression) */
    static int NARROW_KLASS_SHIFT;

    // ── State ────────────────────────────────────────────────
    private static volatile boolean initialized;
    private static volatile boolean probesPassed;
    private static String initError;

    static {
        U = getUnsafe();
        KLASS_OFFSET = determineKlassOffset();
        KLASS_COMPRESSED = isCompressedKlass();
        NARROW_KLASS_BASE = 0;
        NARROW_KLASS_SHIFT = 0;

        if (KLASS_COMPRESSED) {
            // Try to resolve narrow klass encoding by reading the hidden
            // full-Klass* field from java.lang.Class objects on the heap.
            // HotSpot injects a 64-bit Klass* into each Class mirror object.
            if (resolveNarrowKlassFromClassObject()) {
                try {
                    probeOffsets();
                    probesPassed = true;
                    System.out.println("[VTableReplace] VTable replacement available");
                } catch (Exception e) {
                    initError = e.getMessage();
                    probesPassed = false;
                    System.err.println("[VTableReplace] WARNING: " + initError);
                }
            } else {
                probesPassed = false;
                initError = "Compressed klass pointers detected, resolution failed. " +
                        "VTable replacement unavailable.";
                System.err.println("[VTableReplace] " + initError);
            }
        } else {
            try {
                probeOffsets();
                probesPassed = true;
            } catch (Exception e) {
                initError = e.getMessage();
                probesPassed = false;
                System.err.println("[VTableReplace] WARNING: offset probing failed: " + initError);
                System.err.println("[VTableReplace] vtable replacement will be disabled");
            }
        }
        initialized = true;
    }

    private VTableReplace() {}

    // ══════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════

    /**
     * Returns whether the probing succeeded and vtable replacement is available.
     */
    public static boolean isAvailable() {
        return initialized && probesPassed;
    }

    /**
     * Returns the initialization error message, or null if probing passed.
     */
    public static String getInitError() {
        return initError;
    }

    /**
     * Initialize donor cache. Call once after Minecraft classes are loaded.
     * Forces JIT compilation of donor methods so compiled entry points exist.
     */
    public static void initDonors() {
        if (!isAvailable()) return;

        EmptyImplementations.forceJit();

        try {
            Object phantom = U.allocateInstance(EmptyImplementations.class);
            donorKlassAddr = getKlass(phantom);
            cacheDonorMethods();
        } catch (Exception e) {
            System.err.println("[VTableReplace] Donor init failed: " + e.getMessage());
        }
    }

    /**
     * Replace a method in the given class with a no-op implementation.
     *
     * @param targetClass the class whose method to replace
     * @param methodName  JVM method name (e.g. "setHealth", "kill")
     * @param methodDesc  JVM method descriptor (e.g. "(F)V", "()V")
     * @return true if the replacement succeeded
     */
    public static boolean replaceMethod(Class<?> targetClass, String methodName, String methodDesc) {
        if (!isAvailable()) {
            System.err.println("[VTableReplace] Not available: " + initError);
            return false;
        }
        if (donorKlassAddr == 0) {
            System.err.println("[VTableReplace] Donor not initialized, call initDonors() first");
            return false;
        }

        try {
            // Get target klass from a phantom instance
            Object phantom = U.allocateInstance(targetClass);
            long targetKlass = getKlass(phantom);

            // Find target Method* in target vtable
            long targetMethod = findMethodInVTable(targetKlass, methodName, methodDesc);
            if (targetMethod == 0) {
                System.err.println("[VTableReplace] Method not found: " +
                        targetClass.getName() + "." + methodName + methodDesc);
                return false;
            }

            // Get donor entry points for this descriptor
            long[] donorEntryPoints = DONOR_CACHE.get(methodDesc);
            if (donorEntryPoints == null) {
                // Try to find and cache now
                long donorMethod = findMethodInVTable(donorKlassAddr, null, methodDesc);
                if (donorMethod == 0) {
                    System.err.println("[VTableReplace] No donor for descriptor: " + methodDesc);
                    return false;
                }
                donorEntryPoints = readEntryPoints(donorMethod);
                DONOR_CACHE.put(methodDesc, donorEntryPoints);
            }

            // Overwrite target entry points
            long oldInterpreted = writeEntryPoints(targetMethod, donorEntryPoints);

            System.out.println("[VTableReplace] Replaced " +
                    targetClass.getSimpleName() + "." + methodName + methodDesc +
                    " interpreted_entry: " + Long.toHexString(oldInterpreted) +
                    " → " + Long.toHexString(donorEntryPoints[0]));
            return true;

        } catch (Exception e) {
            System.err.println("[VTableReplace] replaceMethod failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Convenience: replace by method name and parameter types.
     * The descriptor is derived from the parameter types and void return.
     * Only works for void-returning methods.
     */
    public static boolean replaceVoidMethod(Class<?> targetClass, String methodName,
                                            Class<?>... paramTypes) {
        String desc = buildDescriptor(void.class, paramTypes);
        return replaceMethod(targetClass, methodName, desc);
    }

    // ══════════════════════════════════════════════════════════
    //  VTable navigation
    // ══════════════════════════════════════════════════════════

    static long getVTableBase(long klassAddr) {
        return klassAddr + VTABLE_BASE_OFFSET;
    }

    static int getVTableLength(long klassAddr) {
        return U.getInt(klassAddr + VTABLE_LEN_OFFSET);
    }

    /**
     * Scan the vtable for a method matching the given name and descriptor.
     * If {@code methodName} is null, match only by descriptor.
     */
    static long findMethodInVTable(long klassAddr, String methodName, String methodDesc) {
        long vtableBase = getVTableBase(klassAddr);
        int vtableLen = getVTableLength(klassAddr);

        for (int i = 0; i < vtableLen; i++) {
            long methodPtr = U.getLong(vtableBase + (long) i * 8);
            if (methodPtr == 0) continue;

            try {
                String name = readMethodName(methodPtr);
                String desc = readMethodSignature(methodPtr);

                boolean nameMatch = (methodName == null) || methodName.equals(name);
                boolean descMatch = methodDesc.equals(desc);

                if (nameMatch && descMatch) {
                    return methodPtr;
                }
            } catch (Exception e) {
                // Corrupt entry or wrong offset — skip
            }
        }
        return 0;
    }

    // ══════════════════════════════════════════════════════════
    //  Method struct reading
    // ══════════════════════════════════════════════════════════

    /**
     * Read a MetaspaceObj pointer (Method*, ConstMethod*, Symbol*).
     * These are ALWAYS full 64-bit pointers — never compressed, even when
     * UseCompressedClassPointers is enabled. Compression only applies to
     * klass pointers in object headers.
     */
    static long readMetaPtr(long addr) {
        return U.getLong(addr);
    }

    static String readMethodName(long methodPtr) {
        long constMethod = readMetaPtr(methodPtr + METHOD_CONST_METHOD_OFFSET);
        long nameSymbol = U.getLong(constMethod + CONST_METHOD_NAME_OFFSET);
        return readSymbol(nameSymbol);
    }

    static String readMethodSignature(long methodPtr) {
        long constMethod = readMetaPtr(methodPtr + METHOD_CONST_METHOD_OFFSET);
        long sigSymbol = U.getLong(constMethod + CONST_METHOD_SIGNATURE_OFFSET);
        return readSymbol(sigSymbol);
    }

    /**
     * Read a HotSpot Symbol (UTF-8 string).
     * Symbol layout: [length:u2] [refcount:u2] [identity_hash:?] [body:byte[]]
     */
    static String readSymbol(long symbolAddr) {
        if (symbolAddr == 0) return "<null>";
        int length = U.getShort(symbolAddr) & 0xFFFF;
        if (length <= 0 || length > 4096) return "<bad-length:" + length + ">";
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = U.getByte(symbolAddr + SYMBOL_BODY_OFFSET + i);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════
    //  Entry point manipulation
    // ══════════════════════════════════════════════════════════

    /**
     * Read both entry points from a Method*.
     * Returns [interpretedEntry, compiledEntry].
     */
    static long[] readEntryPoints(long methodPtr) {
        long interpreted = U.getLong(methodPtr + METHOD_FROM_INTERPRETED_OFFSET);
        long compiled = U.getLong(methodPtr + METHOD_FROM_COMPILED_OFFSET);
        return new long[]{interpreted, compiled};
    }

    /**
     * Overwrite entry points on a Method*.
     * Returns the old interpreted entry for logging.
     */
    static long writeEntryPoints(long methodPtr, long[] entryPoints) {
        long old = U.getLong(methodPtr + METHOD_FROM_INTERPRETED_OFFSET);
        U.putLong(methodPtr + METHOD_FROM_INTERPRETED_OFFSET, entryPoints[0]);
        U.putLong(methodPtr + METHOD_FROM_COMPILED_OFFSET, entryPoints[1]);
        return old;
    }

    // ══════════════════════════════════════════════════════════
    //  Donor caching
    // ══════════════════════════════════════════════════════════

    private static void cacheDonorMethods() {
        String[][] donorMethods = {
                {"emptyVoid", "()V"},
                {"emptyFloat", "(F)V"},
                {"emptyInt", "(I)V"},
                {"emptyObject", "(Ljava/lang/Object;)V"},
                {"emptyBool", "()Z"},
                {"emptyDamageSourceBoolean", "(Lnet/minecraft/world/damagesource/DamageSource;)Z"},
                {"emptyDamageSourceFloat", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z"},
                {"emptyDie", "(Lnet/minecraft/world/damagesource/DamageSource;)V"},
                {"emptyRemove", "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},
        };

        for (String[] entry : donorMethods) {
            String name = entry[0];
            String desc = entry[1];
            long methodPtr = findMethodInVTable(donorKlassAddr, name, desc);
            if (methodPtr != 0) {
                long[] eps = readEntryPoints(methodPtr);
                DONOR_CACHE.put(desc, eps);
                System.out.println("[VTableReplace] Cached donor: " + name + desc +
                        " i=" + Long.toHexString(eps[0]) +
                        " c=" + Long.toHexString(eps[1]));
            } else {
                System.err.println("[VTableReplace] Donor method not found: " + name + desc);
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Offset probing
    // ══════════════════════════════════════════════════════════

    // Probe classes for vtable detection
    @SuppressWarnings("unused")
    static class VTableProbeBase {
        public void probeBaseA() {}
        public void probeBaseB() {}
        public void probeBaseC() {}
    }
    @SuppressWarnings("unused")
    static class VTableProbeSub extends VTableProbeBase {
        public void probeSubA() {}
        public void probeSubB() {}
    }

    // Probe classes for Method-struct detection — methods with highly
    // distinctive names so we can find them in memory
    @SuppressWarnings("unused")
    static class MethodProbeA {
        public void XYZW_METHOD_PROBE_A_12345() {}
        public void XYZW_METHOD_PROBE_SIG_FLOAT_67890(float x) {}
    }
    @SuppressWarnings("unused")
    static class MethodProbeB {
        public void XYZW_METHOD_PROBE_B_99999() {}
    }

    private static final String PROBE_NAME_A = "XYZW_METHOD_PROBE_A_12345";
    private static final String PROBE_NAME_B = "XYZW_METHOD_PROBE_B_99999";
    private static final String PROBE_SIG_FLOAT = "(F)V";

    private static void probeOffsets() {
        // ── Phase 1: Find vtable ──
        probeVTable();

        // ── Phase 2: Find ConstMethod offset in Method ──
        probeConstMethodOffset();

        // ── Phase 3: Find name & signature offsets in ConstMethod ──
        probeNameAndSignatureOffsets();

        // ── Phase 4: Find entry point offsets in Method ──
        probeEntryPointOffsets();

        System.out.println("[VTableReplace] All probes passed:");
        System.out.println("  VTABLE_LEN_OFFSET    = " + VTABLE_LEN_OFFSET);
        System.out.println("  VTABLE_BASE_OFFSET    = " + VTABLE_BASE_OFFSET);
        System.out.println("  CONST_METHOD_OFFSET   = " + METHOD_CONST_METHOD_OFFSET);
        System.out.println("  FROM_INTERPRETED_OFF  = " + METHOD_FROM_INTERPRETED_OFFSET);
        System.out.println("  FROM_COMPILED_OFF     = " + METHOD_FROM_COMPILED_OFFSET);
        System.out.println("  NAME_OFFSET           = " + CONST_METHOD_NAME_OFFSET);
        System.out.println("  SIGNATURE_OFFSET      = " + CONST_METHOD_SIGNATURE_OFFSET);
    }

    private static void probeVTable() {
        Object base = new VTableProbeBase();
        Object sub = new VTableProbeSub();
        long baseKlass = getKlass(base);
        long subKlass = getKlass(sub);

        System.out.println("[VTableReplace] probeVTable: baseKlass=0x" +
                Long.toHexString(baseKlass) + " subKlass=0x" +
                Long.toHexString(subKlass));

        // Scan for the vtable by looking for consecutive valid Method* pointers.
        // The vtable is a contiguous array of Metaspace pointers (each 8 bytes).
        // HotSpot stores Metadata* (Method*, ConstMethod*, etc.) as full 64-bit
        // addresses even with compressed oops/klass.
        //
        // A valid vtable entry is a non-zero value in the Metaspace range.
        // Metaspace on 64-bit is typically in the same range as the klass itself.

        for (long off = 0; off < 0x800; off += 8) {
            // Count consecutive valid pointers
            int baseCount = countConsecutivePtrs(baseKlass + off);
            int subCount = countConsecutivePtrs(subKlass + off);

            // A vtable has at least 5 entries (Object's methods) and at most ~500
            if (baseCount >= 5 && baseCount <= 500
                    && subCount == baseCount + 2) { // 2 extra methods in sub
                // Found it. Now find _vtable_len (4-byte int) just before the entries.
                VTABLE_BASE_OFFSET = off;
                // _vtable_len should be 4 bytes before (with possible padding)
                if (U.getInt(baseKlass + off - 4) == baseCount) {
                    VTABLE_LEN_OFFSET = off - 4;
                } else if (U.getInt(baseKlass + off - 8) == baseCount) {
                    VTABLE_LEN_OFFSET = off - 8;
                } else {
                    VTABLE_LEN_OFFSET = off - 4; // best guess
                }

                System.out.println("[VTableReplace] Found vtable at base_off=0x" +
                        Long.toHexString(off) + " len_off=0x" +
                        Long.toHexString(VTABLE_LEN_OFFSET) +
                        " baseCount=" + baseCount + " subCount=" + subCount);
                return;
            }
        }

        throw new RuntimeException("Cannot find vtable in InstanceKlass");
    }

    /**
     * Count consecutive valid Metaspace pointers starting at the given address.
     */
    private static int countConsecutivePtrs(long addr) {
        int count = 0;
        for (int i = 0; i < 500; i++) {
            long ptr = U.getLong(addr + (long) i * 8);
            if (ptr == 0) break;
            if (!isValidMetaPointer(ptr)) break;
            // Also check: ptr should be in the same general region as our klasses
            if (ptr < 0x100000000L || ptr > 0x800000000000L) break;
            count++;
        }
        return count;
    }

    private static void probeConstMethodOffset() {
        // Get Method* for two probe methods with DIFFERENT names
        Object phantomA = null;
        Object phantomB = null;
        try {
            phantomA = U.allocateInstance(MethodProbeA.class);
            phantomB = U.allocateInstance(MethodProbeB.class);
        } catch (InstantiationException e) {
            throw new RuntimeException("Cannot allocate probe instances", e);
        }

        long klassA = getKlass(phantomA);
        long klassB = getKlass(phantomB);

        long methodA = findMethodInVTableByProbeName(MethodProbeA.class, PROBE_NAME_A);
        long methodB = findMethodInVTableByProbeName(MethodProbeB.class, PROBE_NAME_B);

        if (methodA == 0 || methodB == 0) {
            throw new RuntimeException("Cannot find probe methods in vtable");
        }

        // Scan Method* for the ConstMethod* field.
        // Skip offset 0 (C++ vtable pointer on MSVC).
        // The two methods have DIFFERENT ConstMethod objects.

        for (long off = 4; off < 0x60; off += 4) {
            int valA = U.getInt(methodA + off);
            int valB = U.getInt(methodB + off);
            long longA = U.getLong(methodA + off);
            long longB = U.getLong(methodB + off);

            if (valA != valB && isValidMetaPointer(longA) && isValidMetaPointer(longB)) {
                METHOD_CONST_METHOD_OFFSET = off;
                System.out.println("[VTableReplace] Found _constMethod at offset " + off);
                return;
            }
        }

        // Try 8-byte aligned, full pointers
        for (long off = 8; off < 0x60; off += 8) {
            long valA = U.getLong(methodA + off);
            long valB = U.getLong(methodB + off);

            if (valA != valB && isValidMetaPointer(valA) && isValidMetaPointer(valB)) {
                METHOD_CONST_METHOD_OFFSET = off;
                System.out.println("[VTableReplace] Found _constMethod at offset " + off);
                return;
            }
        }

        throw new RuntimeException("Cannot find _constMethod offset in Method");
    }

    private static void probeNameAndSignatureOffsets() {
        // Get ConstMethod* for two methods with different names and signatures
        Object phantom = null;
        try {
            phantom = U.allocateInstance(MethodProbeA.class);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
        long klassA = getKlass(phantom);

        long methodA = findMethodInVTableByProbeName(MethodProbeA.class, PROBE_NAME_A);
        long methodB = findMethodInVTableByProbeName(MethodProbeA.class, null); // will match first probe, need second

        // Actually, let's find two methods with different names AND different signatures:
        // MethodProbeA has:
        //   1. XYZW_METHOD_PROBE_A_12345 ()V
        //   2. XYZW_METHOD_PROBE_SIG_FLOAT_67890 (F)V
        long method1 = methodA; // name=PROBE_NAME_A, sig=()V
        long method2 = 0;

        // Scan vtable for the float-signature method
        long vtableBase = getVTableBase(klassA);
        int vtableLen = getVTableLength(klassA);
        for (int i = 0; i < vtableLen; i++) {
            long mptr = U.getLong(vtableBase + (long) i * 8);
            if (mptr == 0 || mptr == method1) continue;
            try {
                long constMethod = readMetaPtr(mptr + METHOD_CONST_METHOD_OFFSET);
                long sigSymbol = U.getLong(constMethod + CONST_METHOD_NAME_OFFSET); // placeholder
                // At this point NAME_OFFSET isn't set yet, so we can't read the name.
                // Use a different approach: find by comparing fields.
            } catch (Exception e) {}
        }

        // Alternative: Compare the ConstMethod structures of the two methods.
        // They have: different _name (Symbol*), different _signature (Symbol*)
        // same: _fingerprint (probably different too), _max_locals (0 vs 1)
        //        _max_stack (0 vs 0), _constants (same class → same pool)

        long constMethod1 = readMetaPtr(method1 + METHOD_CONST_METHOD_OFFSET);

        // Find method2 — the one with signature (F)V
        long constMethod2 = 0;
        long method2Ptr = 0;
        for (int i = 0; i < vtableLen; i++) {
            long mptr = U.getLong(vtableBase + (long) i * 8);
            if (mptr == 0 || mptr == method1) continue;
            long cm = readMetaPtr(mptr + METHOD_CONST_METHOD_OFFSET);
            if (cm != constMethod1 && cm != 0 && isValidMetaPointer(cm)) {
                constMethod2 = cm;
                method2Ptr = mptr;
                break;
            }
        }

        if (constMethod2 == 0) {
            throw new RuntimeException("Cannot find second probe method");
        }

        // Now compare constMethod1 and constMethod2 to find where the
        // name and signature Symbol* pointers live.
        // name will differ (different method names)
        // signature will differ (()V vs (F)V)

        // We expect Symbol* pointers (full 8-byte). Scan for two fields
        // where both differ between the two ConstMethods and whose values
        // look like valid Metaspace pointers.
        long nameOffset = -1, sigOffset = -1;

        for (long off = 0; off < 0x80; off += 8) {
            long val1 = U.getLong(constMethod1 + off);
            long val2 = U.getLong(constMethod2 + off);

            if (val1 == val2) continue; // same value → same field (e.g., _constants)
            if (!isValidMetaPointer(val1) || !isValidMetaPointer(val2)) continue;

            // Try to read these as Symbol pointers
            try {
                String s1 = readSymbolAt(val1);
                String s2 = readSymbolAt(val2);

                if (PROBE_NAME_A.equals(s1)) {
                    nameOffset = off;
                    continue;
                }
                if ("(F)V".equals(s1) || "()V".equals(s1)) {
                    sigOffset = off;
                    continue;
                }
            } catch (Exception ignored) {}
        }

        if (nameOffset < 0 || sigOffset < 0) {
            // Try with narrow (4-byte) read
            for (long off = 0; off < 0x80; off += 4) {
                int val1 = U.getInt(constMethod1 + off);
                int val2 = U.getInt(constMethod2 + off);
                if (val1 == val2) continue;
                // Try as narrow pointers
                try {
                    long full1 = val1 & 0xFFFFFFFFL;
                    long full2 = val2 & 0xFFFFFFFFL;
                    String s1 = readSymbolAt(full1);
                    if (PROBE_NAME_A.equals(s1)) {
                        nameOffset = off;
                    }
                    if ("(F)V".equals(s1) || "()V".equals(s1)) {
                        sigOffset = off;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (nameOffset < 0 || sigOffset < 0) {
            System.err.println("[VTableReplace] WARNING: name/signature probing incomplete. " +
                    "nameOffset=" + nameOffset + " sigOffset=" + sigOffset);
            // Use reasonable defaults for JDK 21
            if (nameOffset < 0) nameOffset = 0x18;
            if (sigOffset < 0) sigOffset = 0x20;
            System.err.println("[VTableReplace] Falling back to defaults: name=" +
                    nameOffset + " sig=" + sigOffset);
        }

        CONST_METHOD_NAME_OFFSET = nameOffset;
        CONST_METHOD_SIGNATURE_OFFSET = sigOffset;
        System.out.println("[VTableReplace] Found _name at " + nameOffset +
                " _signature at " + sigOffset + " in ConstMethod");
    }

    private static void probeEntryPointOffsets() {
        // Use the Method* for a donor method, and compare before/after JIT.
        // Before JIT: _from_compiled_entry is usually 0
        // After JIT: it becomes a valid code-cache address
        //
        // The _from_interpreted_entry is always valid (points to interpreter stub).

        Object phantom;
        try {
            phantom = U.allocateInstance(MethodProbeA.class);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
        long klass = getKlass(phantom);

        // Find the methodProbeA method
        long methodPtr = findMethodInVTableByProbeName(MethodProbeA.class, PROBE_NAME_A);
        if (methodPtr == 0) {
            // Try to find by scanning for the name string in vtable
            throw new RuntimeException("Cannot find probe method for entry point probing");
        }

        // Take a snapshot before JIT
        long[] before = new long[20]; // 20 × 8 = 160 bytes
        for (int i = 0; i < 20; i++) {
            before[i] = U.getLong(methodPtr + (long) i * 8);
        }

        // Force JIT: create a REAL instance (not phantom) and call many times
        MethodProbeA real = new MethodProbeA();
        for (int i = 0; i < 15_000; i++) {
            real.XYZW_METHOD_PROBE_A_12345();
        }

        // Take snapshot after JIT
        long[] after = new long[20];
        for (int i = 0; i < 20; i++) {
            after[i] = U.getLong(methodPtr + (long) i * 8);
        }

        // Find fields that changed from 0 to non-zero (compiled entry)
        // and a field that was always a valid code pointer (interpreted entry)
        long compiledOffset = -1;
        long interpretedOffset = -1;

        for (int i = 0; i < 20; i++) {
            long offset = i * 8;
            if (before[i] == 0 && after[i] != 0 && isCodeAddress(after[i])) {
                compiledOffset = offset;
            }
            if (before[i] != 0 && isCodeAddress(before[i]) && before[i] == after[i]) {
                // Could be the interpreted entry — it's stable across JIT
                if (interpretedOffset < 0 || offset < interpretedOffset) {
                    interpretedOffset = offset;
                }
            }
        }

        if (compiledOffset < 0) {
            // JIT might not have triggered. Try heuristic: find a slot that's 0
            // and is at a known position after the interpreted entry.
            // In JDK 21, _from_compiled is usually 8 or 16 bytes after _from_interpreted
            for (int i = 0; i < 20; i++) {
                long offset = i * 8;
                if (isCodeAddress(before[i])) {
                    interpretedOffset = offset;
                    // The compiled entry is often at a fixed offset after
                    if (i + 1 < 20 && before[i + 1] == 0) {
                        compiledOffset = (i + 1) * 8;
                    } else if (i + 2 < 20 && before[i + 2] == 0) {
                        compiledOffset = (i + 2) * 8;
                    }
                    break;
                }
            }
        }

        if (interpretedOffset < 0 || compiledOffset < 0) {
            // Use JDK 21 defaults
            interpretedOffset = 0x38;
            compiledOffset = 0x48;
            System.err.println("[VTableReplace] WARNING: entry point probe failed, " +
                    "using JDK 21 defaults");
        }

        METHOD_FROM_INTERPRETED_OFFSET = interpretedOffset;
        METHOD_FROM_COMPILED_OFFSET = compiledOffset;
        System.out.println("[VTableReplace] Found interpreted_entry at " +
                interpretedOffset + " compiled_entry at " + compiledOffset);
    }

    /**
     * Find a method in the vtable by matching name only.
     * Uses brute-force scanning of Method → ConstMethod → Symbol chain.
     */
    private static long findMethodInVTableByProbeName(Class<?> probeClass, String targetName) {
        // Hardcoded: probe classes extend Object directly.
        // From probe output, VTableProbeBase (Object + 3 own methods) = 10 entries.
        // Object's vtable entries = 10 - 3 = 7.
        // MethodProbeA has methods at indices 7 and 8.
        // MethodProbeB has its method at index 7.
        try {
            Object phantom = U.allocateInstance(probeClass);
            long klassAddr = getKlass(phantom);
            long vtableBase = klassAddr + VTABLE_BASE_OFFSET;

            // Count vtable entries (all virtual methods including Object's)
            int vtableLen = 0;
            while (true) {
                long ptr = U.getLong(vtableBase + (long) vtableLen * 8);
                if (ptr == 0 || !isValidMetaPointer(ptr)) break;
                vtableLen++;
            }

            // Object methods count = 10 - 3 = 7 (for VTableProbeBase with 3 own methods)
            // For probe classes: own_methods = declaredMethods count
            int ownMethods = probeClass.getDeclaredMethods().length;
            int objMethods = vtableLen - ownMethods;

            // First vtable entry for a probe class = objMethods + ownMethodIndex
            // MethodProbeA: ownMethods=2, XYZW_..._12345 is first (index 0 in own)
            // MethodProbeB: ownMethods=1, XYZW_..._99999 is first (index 0 in own)
            // For targetName=null, return the last Object method (vtableLen - ownMethods - 1)
            if (targetName == null) {
                int idx = Math.max(0, objMethods - 1);
                return U.getLong(vtableBase + (long) idx * 8);
            }

            // First own method is always at vtable index = objMethods
            long methodPtr = U.getLong(vtableBase + (long) objMethods * 8);
            if (methodPtr != 0) return methodPtr;
        } catch (Exception e) {
            System.err.println("[VTableReplace] probe lookup: " + e.getMessage());
        }
        return 0;
    }

    //  Pointer validation heuristics
    // ══════════════════════════════════════════════════════════

    static boolean isValidMetaPointer(long ptr) {
        // Metaspace pointers in HotSpot on 64-bit are typically:
        // - In the range 0x7F0000000000 to 0x7FFFFFFFFFFF or similar
        // - Not 0, not all-1s
        return ptr != 0
                && ptr != 0xFFFFFFFFFFFFFFFFL
                && ptr > 0x1000L
                && ptr < 0x800000000000L;
    }

    static boolean isCodeAddress(long addr) {
        // Code cache addresses typically in the 0x7FFF... range
        return addr != 0
                && addr > 0x100000L
                && addr < 0x800000000000L;
    }

    static String readSymbolAt(long symbolAddr) {
        return readSymbolAt(symbolAddr, (int) SYMBOL_BODY_OFFSET);
    }

    static String readSymbolAt(long symbolAddr, int bodyOffset) {
        int length = U.getShort(symbolAddr) & 0xFFFF;
        if (length <= 0 || length > 2048) return null;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = U.getByte(symbolAddr + bodyOffset + i);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════
    //  Descriptor builder
    // ══════════════════════════════════════════════════════════

    static String buildDescriptor(Class<?> returnType, Class<?>... paramTypes) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : paramTypes) {
            sb.append(classToDescriptor(p));
        }
        sb.append(")");
        sb.append(classToDescriptor(returnType));
        return sb.toString();
    }

    static String classToDescriptor(Class<?> c) {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + classToDescriptor(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    // ══════════════════════════════════════════════════════════
    //  Klass pointer utilities (same technique as PlayerClassSwapper)
    // ══════════════════════════════════════════════════════════

    /**
     * Get the FULL (decompressed) klass address from an object's header.
     * <p>Handles both compressed (narrow) and uncompressed klass pointers.
     * When the upper 32 bits of the 8-byte header slot are non-zero, it's a
     * full 64-bit address. Otherwise we decompress using probed base+shift.</p>
     */
    private static long getKlass(Object obj) {
        long full = U.getLong(obj, KLASS_OFFSET);
        // If upper 32 bits are non-zero, this is a full 64-bit address
        if ((full & 0xFFFFFFFF00000000L) != 0) {
            return full;
        }
        // Upper 32 bits are 0 — either narrow (compressed) or
        // a full address mapped in low 4GB (rare on 64-bit).
        int narrow = (int) full;
        if (NARROW_KLASS_SHIFT != 0 || NARROW_KLASS_BASE != 0) {
            return decompressKlass(narrow);
        }
        // No compression → treat as full address
        return narrow & 0xFFFFFFFFL;
    }

    /**
     * Decompress a narrow klass pointer to a full address.
     */
    static long decompressKlass(int narrow) {
        long narrowUnsigned = narrow & 0xFFFFFFFFL;
        if (NARROW_KLASS_SHIFT == 0) {
            return NARROW_KLASS_BASE + narrowUnsigned;
        }
        return (narrowUnsigned << NARROW_KLASS_SHIFT) + NARROW_KLASS_BASE;
    }

    /**
     * Resolve narrow klass base via HotSpot attach API (self-attach).
     *
     * <p>Uses Unsafe to bypass Java module-system restrictions on
     * {@code sun.tools.attach}. The module system prevents normal reflection
     * ({@code setAccessible}) on classes in non-exported packages, but Unsafe
     * field access and the {@code AccessibleObject.override} flag bypass all
     * module checks.</p>
     *
     * <p>Shift defaults to 3 (klass objects are 8-byte aligned).</p>
     *
     * @return true if the base was successfully resolved
     */
    private static boolean resolveNarrowKlassFromClassObject() {
        // HotSpot injects a hidden 64-bit full Klass* field into every
        // java.lang.Class object on the Java heap. By reading this field
        // from Object.class, we can compute the narrow klass encoding.
        //
        // Class object layout on 64-bit HotSpot:
        //   [mark:8] [narrow_klass:4] [pad:4] [fields...] [hidden_klass*:8]
        //
        // We scan the Class object for an 8-byte value that:
        //   1. Is in the Metaspace range (0x700000000000 - 0x800000000000)
        //   2. When used as fullKlass, gives a page-aligned base

        try {
            // Get narrow klass from an Object instance
            Object probeObj = new Object();
            int narrowKlass = U.getInt(probeObj, KLASS_OFFSET);
            if (narrowKlass <= 0) return false;

            // Scan Object.class for the hidden full Klass* field
            Class<?> objectClass = Object.class;
            long foundFullKlass = 0;

            // Scan 8-byte aligned offsets in the Class object, starting after
            // the object header (12 bytes for compressed klass).
            for (long off = 16; off < 256; off += 8) {
                try {
                    long val = U.getLong(objectClass, off);
                    // Metaspace address range on 64-bit
                    if (val > 0x700000000000L && val < 0x800000000000L) {
                        // Compute candidate base with shift=3
                        long base = val - (((long) narrowKlass & 0xFFFFFFFFL) << 3);
                        // Base must be page-aligned (multiple of 0x1000)
                        if ((base & 0xFFF) == 0) {
                            foundFullKlass = val;
                            NARROW_KLASS_BASE = base;
                            NARROW_KLASS_SHIFT = 3;
                            System.out.println("[VTableReplace] Found full Klass* at " +
                                    "Class offset " + off + ": 0x" + Long.toHexString(val) +
                                    " base=0x" + Long.toHexString(base));
                            return true;
                        }
                    }
                } catch (Exception ignored) {}
            }

            System.err.println("[VTableReplace] Could not find full Klass* in Class object. " +
                    "Scanned 0x10-0x100, narrow=0x" + Integer.toHexString(narrowKlass));
        } catch (Exception e) {
            System.err.println("[VTableReplace] Class-object scan failed: " + e.getMessage());
        }
        return false;
    }

    // Legacy: kept for reference but not currently used
    /**
     * Resolve narrow klass base via HotSpot attach API (self-attach).
     */
    private static boolean resolveNarrowKlassBase() {
        try {
            String pid = java.lang.management.ManagementFactory.getRuntimeMXBean()
                    .getName().split("@")[0];

            // Step 1: Use Unsafe to set ALLOW_ATTACH_SELF = true
            // (bypasses module system — no setAccessible needed)
            Class<?> hsvmClass = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
            Field allowSelf = hsvmClass.getDeclaredField("ALLOW_ATTACH_SELF");
            long allowOffset = U.staticFieldOffset(allowSelf);
            Object allowBase = U.staticFieldBase(allowSelf);
            U.putBoolean(allowBase, allowOffset, true);

            // Step 2: Attach to self
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Method attachMethod = vmClass.getMethod("attach", String.class);
            Object vm = attachMethod.invoke(null, pid);

            // Step 3: Get executeJCmd method and force its override flag via Unsafe
            // (module system blocks setAccessible, but Unsafe can write the flag)
            Method jcmdMethod = hsvmClass.getMethod("executeJCmd", String.class);
            forceAccessible(jcmdMethod);

            String vmInfo = (String) jcmdMethod.invoke(vm, "VM.info");

            // Step 4: Detach
            Method detachMethod = vm.getClass().getMethod("detach");
            forceAccessible(detachMethod);
            detachMethod.invoke(vm);

            // Step 5: Parse compressed class space base from output
            for (String line : vmInfo.split("\n")) {
                if (line.contains("Compressed") && line.contains("0x")) {
                    int hexStart = line.indexOf("0x");
                    if (hexStart < 0) continue;
                    int hexEnd = hexStart + 2;
                    while (hexEnd < line.length() &&
                            Character.digit(line.charAt(hexEnd), 16) >= 0) {
                        hexEnd++;
                    }
                    if (hexEnd > hexStart + 2) {
                        String hexStr = line.substring(hexStart, hexEnd);
                        long base = Long.parseUnsignedLong(hexStr, 16);

                        NARROW_KLASS_BASE = base;
                        NARROW_KLASS_SHIFT = 3;
                        System.out.println("[VTableReplace] Narrow klass resolved: " +
                                "base=0x" + Long.toHexString(base) + " shift=3 " +
                                "(line: " + line.trim() + ")");
                        return true;
                    }
                }
            }

            System.err.println("[VTableReplace] VM.info did not contain " +
                    "compressed class space line. First 500 chars:\n" +
                    (vmInfo.length() > 500 ? vmInfo.substring(0, 500) : vmInfo));
        } catch (Exception e) {
            System.err.println("[VTableReplace] Attach-API resolution failed: " + e);
        }
        return false;
    }

    /**
     * Offset of {@code AccessibleObject.override} (boolean) from object start.
     * On 64-bit HotSpot: mark(8) + klass(4) = header(12), override at offset 12.
     * With uncompressed klass: mark(8) + klass(8) = header(16), override at 16.
     */
    private static long OVERRIDE_OFFSET = -1;

    /**
     * Force a reflective object (Method/Field) to bypass module access checks
     * by writing its {@code override} flag directly via Unsafe.
     */
    private static void forceAccessible(Object accessor) {
        if (OVERRIDE_OFFSET < 0) {
            // On 64-bit HotSpot, override is the first instance field after
            // the object header. With compressed klass: 8+4=12. Without: 8+8=16.
            OVERRIDE_OFFSET = KLASS_COMPRESSED ? 12L : 16L;
        }
        U.putBoolean(accessor, OVERRIDE_OFFSET, true);
    }

    private static boolean isCompressedKlass() {
        Object probe = new Object();
        long full = U.getLong(probe, KLASS_OFFSET);
        // If the upper 32 bits are all zero, the klass is stored as a 32-bit
        // narrow value (compressed) or the full address is in the low 4GB.
        // If non-zero, it's definitely a full 64-bit uncompressed klass.
        return (full & 0xFFFFFFFF00000000L) == 0 && (int) full != 0;
    }

    private static Unsafe getUnsafe() {
        try {
            // Try constructor first (bypasses module restrictions)
            var c = Unsafe.class.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e1) {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return (Unsafe) f.get(null);
            } catch (Exception e2) {
                throw new ExceptionInInitializerError(e2);
            }
        }
    }

    private static long determineKlassOffset() {
        Object probe = new Object();
        long markWord = U.getLong(probe, 0L);

        for (long off : new long[]{8L, 12L, 16L, 4L}) {
            try {
                int maybeKlass = U.getInt(probe, off);
                if (maybeKlass != 0
                        && (maybeKlass & 0xFFFFFFFFL) != (markWord & 0xFFFFFFFFL)) {
                    return off;
                }
            } catch (Exception ignored) {}
        }
        return 8L;
    }
}
