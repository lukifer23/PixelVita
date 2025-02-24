#ifndef NATIVE_OPTIMIZATIONS_H
#define NATIVE_OPTIMIZATIONS_H

#include <jni.h>
#include <android/log.h>

#ifdef __cplusplus
extern "C" {
#endif

// Function declarations for JNI methods
JNIEXPORT jboolean JNICALL
Java_com_example_androiddiffusion_ml_NativeOptimizations_isDeviceSupported(
        JNIEnv *env,
        jobject thiz);

JNIEXPORT jint JNICALL
Java_com_example_androiddiffusion_ml_NativeOptimizations_getOptimalNumThreadsNative(
        JNIEnv *env,
        jobject thiz);

#ifdef __cplusplus
}
#endif

#endif // NATIVE_OPTIMIZATIONS_H 