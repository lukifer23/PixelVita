#include "include/native_optimizations.h"
#include <cpu-features.h>
#include <thread>

#define LOG_TAG "NativeOptimizations"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_androiddiffusion_ml_NativeOptimizations_isDeviceSupported(
        JNIEnv *env,
        jobject /* this */) {
    AndroidCpuFamily family = android_getCpuFamily();
    uint64_t features = android_getCpuFeatures();
    
    // Check for ARM devices
    if (family == ANDROID_CPU_FAMILY_ARM) {
        return (features & ANDROID_CPU_ARM_FEATURE_ARMv7) != 0;
    }
    
    // Check for ARM64 devices
    if (family == ANDROID_CPU_FAMILY_ARM64) {
        return (features & ANDROID_CPU_ARM64_FEATURE_ASIMD) != 0;
    }
    
    // For x86 and x86_64, we'll assume support if they have SSE4.2
    if (family == ANDROID_CPU_FAMILY_X86 || family == ANDROID_CPU_FAMILY_X86_64) {
        return (features & ANDROID_CPU_X86_FEATURE_SSE4_2) != 0;
    }
    
    return false;
}

JNIEXPORT jint JNICALL
Java_com_example_androiddiffusion_ml_NativeOptimizations_getOptimalNumThreadsNative(
        JNIEnv *env,
        jobject /* this */) {
    // Get the number of CPU cores
    int numCores = android_getCpuCount();
    
    // Get the number of hardware threads available
    int numThreads = std::thread::hardware_concurrency();
    
    // If hardware_concurrency() returns 0, fall back to CPU count
    if (numThreads == 0) {
        numThreads = numCores;
    }
    
    // For optimal performance, we'll use 75% of available threads
    // but ensure we have at least 1 thread and at most 8 threads
    int optimalThreads = std::max(1, std::min(8, (int)(numThreads * 0.75)));
    
    LOGI("Optimal number of threads: %d (cores: %d, hardware threads: %d)",
         optimalThreads, numCores, numThreads);
    
    return optimalThreads;
}

} // extern "C" 