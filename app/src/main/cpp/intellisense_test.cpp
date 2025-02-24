#include "include/native_optimizations.h"
#include <jni.h>
#include <cpu-features.h>
#include <android/log.h>
#include <thread>

// This is a test file to verify that IntelliSense is working correctly
// It should not show any red squiggles for the includes above

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_androiddiffusion_ml_NativeOptimizations_testIntelliSense(
        JNIEnv *env,
        jobject /* this */) {
    
    // Test JNI types
    jstring testString = env->NewStringUTF("IntelliSense Test");
    jint testInt = 42;
    
    // Test CPU features
    AndroidCpuFamily family = android_getCpuFamily();
    uint64_t features = android_getCpuFeatures();
    
    // Log test message
    __android_log_print(ANDROID_LOG_INFO, "IntelliSenseTest", 
                        "Testing IntelliSense with CPU family: %d", family);
    
    // Test thread functionality
    int numThreads = std::thread::hardware_concurrency();
    
    return (family != ANDROID_CPU_FAMILY_UNKNOWN) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C" 