#include <jni.h>
#include <string>
#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>
#include <errno.h>
#include <malloc.h>

#define LOG_TAG "NativeMemoryManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_androiddiffusion_util_NativeMemoryManager_allocateMemory(
        JNIEnv *env,
        jobject /* this */,
        jlong size) {
    
    // Try to allocate memory using mmap
    void* ptr = mmap(NULL, size, PROT_READ | PROT_WRITE,
                    MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    
    if (ptr == MAP_FAILED) {
        LOGE("Failed to allocate memory: %s", strerror(errno));
        return 0;
    }
    
    // Lock the memory to prevent swapping
    if (mlock(ptr, size) != 0) {
        LOGE("Failed to lock memory: %s", strerror(errno));
        munmap(ptr, size);
        return 0;
    }
    
    LOGI("Successfully allocated %lld bytes at %p", (long long)size, ptr);
    return reinterpret_cast<jlong>(ptr);
}

JNIEXPORT void JNICALL
Java_com_example_androiddiffusion_util_NativeMemoryManager_freeMemory(
        JNIEnv *env,
        jobject /* this */,
        jlong ptr,
        jlong size) {
    
    if (ptr == 0) return;
    
    void* memory = reinterpret_cast<void*>(ptr);
    
    // Unlock the memory
    munlock(memory, size);
    
    // Unmap the memory
    if (munmap(memory, size) != 0) {
        LOGE("Failed to free memory: %s", strerror(errno));
    } else {
        LOGI("Successfully freed %lld bytes at %p", (long long)size, memory);
    }
}

JNIEXPORT jlong JNICALL
Java_com_example_androiddiffusion_util_NativeMemoryManager_getAvailableMemory(
        JNIEnv *env,
        jobject /* this */) {
    
    // Get page size
    long pageSize = sysconf(_SC_PAGESIZE);
    // Get number of available pages
    long availPages = sysconf(_SC_AVPHYS_PAGES);
    
    return static_cast<jlong>(pageSize * availPages);
}

JNIEXPORT jboolean JNICALL
Java_com_example_androiddiffusion_util_NativeMemoryManager_lockMemory(
        JNIEnv *env,
        jobject /* this */,
        jlong ptr,
        jlong size) {
    
    if (ptr == 0) return JNI_FALSE;
    
    void* memory = reinterpret_cast<void*>(ptr);
    
    if (mlock(memory, size) != 0) {
        LOGE("Failed to lock memory: %s", strerror(errno));
        return JNI_FALSE;
    }
    
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_androiddiffusion_util_NativeMemoryManager_unlockMemory(
        JNIEnv *env,
        jobject /* this */,
        jlong ptr,
        jlong size) {
    
    if (ptr == 0) return JNI_FALSE;
    
    void* memory = reinterpret_cast<void*>(ptr);
    
    if (munlock(memory, size) != 0) {
        LOGE("Failed to unlock memory: %s", strerror(errno));
        return JNI_FALSE;
    }
    
    return JNI_TRUE;
}

} 