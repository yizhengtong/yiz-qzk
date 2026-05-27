#include <jni.h>
#include <stdint.h>
#include <stdio.h>

/**
 * Attempt to resolve narrow klass encoding by reading raw values from
 * JNI handles and object headers. Prints debug info to stderr.
 */
JNIEXPORT jlong JNICALL
Java_net_minecraft_client_yiz_core_NarrowKlassBridge_getBase0(
    JNIEnv *env, jclass bridgeClass, jobject sample)
{
    if (sizeof(void*) != 8) {
        fprintf(stderr, "[narrow_klass] not 64-bit\n");
        return 0;
    }

    // Read the oop (Java object address) from the JNI handle
    intptr_t oop = *(intptr_t*)sample;
    fprintf(stderr, "[narrow_klass] sample=%p oop=%p\n",
            (void*)sample, (void*)oop);
    if (oop == 0) return 0;

    // Read the mark word at oop+0 (should be a valid mark word)
    intptr_t mark = *(intptr_t*)(oop);
    fprintf(stderr, "[narrow_klass] mark=0x%016llx\n",
            (unsigned long long)mark);

    // Read narrow klass from object header at oop+8
    int narrowKlass = *(int*)(oop + 8);
    fprintf(stderr, "[narrow_klass] narrowKlass(header)=0x%x (%d)\n",
            narrowKlass, narrowKlass);

    // Also read as full 8 bytes
    intptr_t klass8 = *(intptr_t*)(oop + 8);
    fprintf(stderr, "[narrow_klass] klass8(header)=0x%016llx\n",
            (unsigned long long)klass8);

    if (narrowKlass <= 0) {
        narrowKlass = (int)klass8;
        if (narrowKlass <= 0) return 0;
    }

    // Get the class via JNI
    jclass objClass = (*env)->GetObjectClass(env, sample);
    fprintf(stderr, "[narrow_klass] objClass handle=%p\n", (void*)objClass);

    if (objClass == NULL) {
        fprintf(stderr, "[narrow_klass] GetObjectClass returned NULL\n");
        return 0;
    }

    // Read raw value from jclass handle slot
    intptr_t fullKlass = *(intptr_t*)objClass;
    fprintf(stderr, "[narrow_klass] raw jclass slot=0x%016llx\n",
            (unsigned long long)fullKlass);

    // Also try reading as a chain (handle → handle → klass?)
    // Some HotSpot versions store an indirection
    intptr_t indirect = *(intptr_t*)fullKlass;
    fprintf(stderr, "[narrow_klass] indirect deref=0x%016llx\n",
            (unsigned long long)indirect);

    (*env)->DeleteLocalRef(env, objClass);

    if (fullKlass == 0) return 0;

    // Compute base with shift=3
    intptr_t base = fullKlass - (((intptr_t)narrowKlass & 0xFFFFFFFF) << 3);
    fprintf(stderr, "[narrow_klass] computed base(shift=3)=0x%016llx\n",
            (unsigned long long)base);

    // Try shift=0
    intptr_t base0 = fullKlass - (intptr_t)(narrowKlass & 0xFFFFFFFF);
    fprintf(stderr, "[narrow_klass] computed base(shift=0)=0x%016llx\n",
            (unsigned long long)base0);

    // Try the indirect value as the real full klass with shift=3
    intptr_t base_indirect = indirect - (((intptr_t)narrowKlass & 0xFFFFFFFF) << 3);
    fprintf(stderr, "[narrow_klass] base via indirect shift=3=0x%016llx\n",
            (unsigned long long)base_indirect);

    // Use the indirection if it looks more like a Metaspace address
    // (Metaspace addresses are typically > 0x7F0000000000 on 64-bit)
    if (indirect > 0x700000000000LL && indirect < 0x800000000000LL) {
        fprintf(stderr, "[narrow_klass] using indirect as full klass\n");
        return (jlong)(indirect - (((intptr_t)narrowKlass & 0xFFFFFFFF) << 3));
    }

    return (jlong)base;
}

JNIEXPORT jint JNICALL
Java_net_minecraft_client_yiz_core_NarrowKlassBridge_getShift0(
    JNIEnv *env, jclass cls)
{
    return 3;
}
