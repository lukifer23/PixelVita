#include "memory_manager.h"
#include <jni.h>
#include <string>
#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>
#include <errno.h>
#include <malloc.h>
#include <algorithm>
#include <cstring>

#define LOG_TAG "NativeMemoryManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

MemoryManager& MemoryManager::getInstance() {
    static MemoryManager instance;
    return instance;
}

MemoryManager::MemoryManager()
    : totalPoolSize(0)
    , totalAllocated(0)
    , memoryPool(nullptr) {
}

MemoryManager::~MemoryManager() {
    freeAllMemory();
}

void MemoryManager::initializeMemoryPool(size_t poolSize) {
    std::lock_guard<std::mutex> lock(mutex);
    
    if (memoryPool) {
        free(memoryPool);
        memoryBlocks.clear();
    }
    
    memoryPool = malloc(poolSize);
    if (!memoryPool) {
        LOGE("Failed to initialize memory pool of size %zu", poolSize);
        return;
    }
    
    totalPoolSize = poolSize;
    totalAllocated = 0;
    
    // Initialize first block as free
    MemoryBlock block{memoryPool, poolSize, "", false};
    memoryBlocks[memoryPool] = block;
    
    LOGI("Memory pool initialized with size %zu bytes", poolSize);
}

void* MemoryManager::allocateMemory(size_t size, const std::string& tag) {
    std::lock_guard<std::mutex> lock(mutex);
    
    if (!memoryPool) {
        LOGE("Memory pool not initialized");
        return nullptr;
    }
    
    // Find a suitable free block
    void* block = findFreeBlock(size);
    if (!block) {
        LOGE("No suitable memory block found for size %zu", size);
        return nullptr;
    }
    
    auto& existingBlock = memoryBlocks[block];
    
    // Split block if it's significantly larger
    if (existingBlock.size > size + sizeof(MemoryBlock)) {
        void* newBlock = static_cast<char*>(block) + size;
        MemoryBlock splitBlock{newBlock, existingBlock.size - size, "", false};
        memoryBlocks[newBlock] = splitBlock;
        existingBlock.size = size;
    }
    
    existingBlock.isUsed = true;
    existingBlock.tag = tag;
    totalAllocated += size;
    
    LOGI("Allocated %zu bytes with tag '%s'", size, tag.c_str());
    return block;
}

bool MemoryManager::freeMemory(void* ptr) {
    std::lock_guard<std::mutex> lock(mutex);
    
    auto it = memoryBlocks.find(ptr);
    if (it == memoryBlocks.end()) {
        LOGE("Invalid pointer passed to freeMemory");
        return false;
    }
    
    it->second.isUsed = false;
    totalAllocated -= it->second.size;
    
    mergeFreeBlocks();
    LOGI("Freed memory block of size %zu", it->second.size);
    return true;
}

void MemoryManager::freeAllMemory() {
    std::lock_guard<std::mutex> lock(mutex);
    
    if (memoryPool) {
        free(memoryPool);
        memoryPool = nullptr;
    }
    
    memoryBlocks.clear();
    totalAllocated = 0;
    totalPoolSize = 0;
    
    LOGI("All memory freed");
}

void* MemoryManager::findFreeBlock(size_t size) {
    for (auto& [ptr, block] : memoryBlocks) {
        if (!block.isUsed && block.size >= size) {
            return ptr;
        }
    }
    return nullptr;
}

void MemoryManager::mergeFreeBlocks() {
    bool merged;
    do {
        merged = false;
        for (auto it = memoryBlocks.begin(); it != memoryBlocks.end(); ++it) {
            if (!it->second.isUsed) {
                void* nextPtr = static_cast<char*>(it->first) + it->second.size;
                auto nextIt = memoryBlocks.find(nextPtr);
                
                if (nextIt != memoryBlocks.end() && !nextIt->second.isUsed) {
                    it->second.size += nextIt->second.size;
                    memoryBlocks.erase(nextIt);
                    merged = true;
                    break;
                }
            }
        }
    } while (merged);
}

size_t MemoryManager::getTotalAllocated() const {
    return totalAllocated;
}

size_t MemoryManager::getAvailableMemory() const {
    return totalPoolSize - totalAllocated;
}

float MemoryManager::getFragmentationRatio() const {
    if (memoryBlocks.empty()) return 0.0f;
    
    size_t freeBlockCount = 0;
    size_t totalFreeSize = 0;
    
    for (const auto& [ptr, block] : memoryBlocks) {
        if (!block.isUsed) {
            freeBlockCount++;
            totalFreeSize += block.size;
        }
    }
    
    if (totalFreeSize == 0) return 0.0f;
    return static_cast<float>(freeBlockCount) / (totalFreeSize / 1024.0f); // blocks per KB
}

// JNI Implementation
extern "C" {
    JNIEXPORT jlong JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_allocateMemory(
            JNIEnv* env, jobject obj, jlong size, jstring tag) {
        const char* tagStr = env->GetStringUTFChars(tag, nullptr);
        void* ptr = MemoryManager::getInstance().allocateMemory(size, tagStr);
        env->ReleaseStringUTFChars(tag, tagStr);
        return reinterpret_cast<jlong>(ptr);
    }
    
    JNIEXPORT jboolean JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_freeMemory(
            JNIEnv* env, jobject obj, jlong ptr) {
        return MemoryManager::getInstance().freeMemory(reinterpret_cast<void*>(ptr));
    }
    
    JNIEXPORT void JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_freeAllMemory(
            JNIEnv* env, jobject obj) {
        MemoryManager::getInstance().freeAllMemory();
    }
    
    JNIEXPORT jlong JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_getTotalAllocated(
            JNIEnv* env, jobject obj) {
        return MemoryManager::getInstance().getTotalAllocated();
    }
    
    JNIEXPORT jlong JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_getAvailableMemory(
            JNIEnv* env, jobject obj) {
        return MemoryManager::getInstance().getAvailableMemory();
    }
    
    JNIEXPORT void JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_defragmentMemory(
            JNIEnv* env, jobject obj) {
        MemoryManager::getInstance().mergeFreeBlocks();
    }
    
    JNIEXPORT jfloat JNICALL Java_com_example_androiddiffusion_util_NativeMemoryManager_getFragmentationRatio(
            JNIEnv* env, jobject obj) {
        return MemoryManager::getInstance().getFragmentationRatio();
    }
} 