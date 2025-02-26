#ifndef MEMORY_MANAGER_H
#define MEMORY_MANAGER_H

#include <jni.h>
#include <string>
#include <memory>
#include <unordered_map>
#include <mutex>

class MemoryManager {
public:
    static MemoryManager& getInstance();
    
    // Memory allocation and deallocation
    void* allocateMemory(size_t size, const std::string& tag);
    bool freeMemory(void* ptr);
    void freeAllMemory();
    
    // Memory tracking
    size_t getTotalAllocated() const;
    size_t getAvailableMemory() const;
    bool isMemoryAvailable(size_t requestedSize) const;
    
    // Memory defragmentation
    void defragmentMemory();
    float getFragmentationRatio() const;
    void mergeFreeBlocks();
    
    // Memory pool management
    void initializeMemoryPool(size_t poolSize);
    void resizeMemoryPool(size_t newSize);
    
    // New functions for memory optimization
    void optimizeMemoryUsage();
    void logMemoryUsage() const;
    
private:
    MemoryManager();
    ~MemoryManager();
    
    // Prevent copying
    MemoryManager(const MemoryManager&) = delete;
    MemoryManager& operator=(const MemoryManager&) = delete;
    
    struct MemoryBlock {
        void* ptr;
        size_t size;
        std::string tag;
        bool isUsed;
    };
    
    std::unordered_map<void*, MemoryBlock> memoryBlocks;
    std::mutex mutex;
    size_t totalPoolSize;
    size_t totalAllocated;
    void* memoryPool;
    
    void* findFreeBlock(size_t size);
};

// JNI function declarations
extern "C" {
    JNIEXPORT jlong JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_allocateMemory(
        JNIEnv* env, jobject obj, jlong size, jstring tag);
        
    JNIEXPORT jboolean JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_freeMemory(
        JNIEnv* env, jobject obj, jlong ptr);
        
    JNIEXPORT void JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_freeAllMemory(
        JNIEnv* env, jobject obj);
        
    JNIEXPORT jlong JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_getTotalAllocated(
        JNIEnv* env, jobject obj);
        
    JNIEXPORT jlong JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_getAvailableMemory(
        JNIEnv* env, jobject obj);
        
    JNIEXPORT void JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_defragmentMemory(
        JNIEnv* env, jobject obj);
        
    JNIEXPORT jfloat JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_getFragmentationRatio(
        JNIEnv* env, jobject obj);
    
    JNIEXPORT void JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_optimizeMemoryUsage(
        JNIEnv* env, jobject obj);
    
    JNIEXPORT void JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_logMemoryUsage(
        JNIEnv* env, jobject obj);
}

#endif // MEMORY_MANAGER_H
