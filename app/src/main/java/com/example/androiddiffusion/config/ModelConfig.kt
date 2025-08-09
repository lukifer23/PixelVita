package com.example.androiddiffusion.config

import android.graphics.Bitmap
import com.example.androiddiffusion.config.QuantizationType
import com.example.androiddiffusion.config.QuantizationInfo

enum class OptimizationProfile {
    HIGH_PERFORMANCE,  // Maximum performance, higher memory usage
    BALANCED,         // Balance between performance and resource usage
    POWER_EFFICIENT   // Minimum resource usage, lower performance
}

object ModelConfig {
    // Base URLs for model downloads
    private const val HUGGINGFACE_BASE = "https://huggingface.co/stabilityai"
    
    // Model URLs and configurations
    val MODELS = mapOf(
        "sd35m_text_encoder" to ModelInfo(
            downloadUrl = "$HUGGINGFACE_BASE/stable-diffusion-3-medium/resolve/main/text_encoder/model.onnx",
            size = 1_249_902_592L, // ~1.25GB
            checksum = "",
            configUrl = "$HUGGINGFACE_BASE/stable-diffusion-3-medium/raw/main/model_index.json",
            quantization = QuantizationInfo(
                type = QuantizationType.INT8,
                originalSize = 1_249_902_592L
            )
        ),
        "sd35m_unet" to ModelInfo(
            downloadUrl = "$HUGGINGFACE_BASE/stable-diffusion-3-medium/resolve/main/unet/model.onnx",
            size = 2_147_483_648L, // ~2GB
            checksum = "",
            configUrl = null,
            quantization = QuantizationInfo(
                type = QuantizationType.INT8,
                originalSize = 2_147_483_648L
            )
        ),
        "sd35m_vae" to ModelInfo(
            downloadUrl = "$HUGGINGFACE_BASE/stable-diffusion-3-medium/resolve/main/vae_decoder/model.onnx",
            size = 335_544_320L, // ~320MB
            checksum = "",
            configUrl = null,
            quantization = QuantizationInfo(
                type = QuantizationType.INT8,
                originalSize = 335_544_320L
            )
        )
    )
    
    // Model optimization settings based on SD 3.5 Medium requirements
    object OptimizationSettings {
        const val USE_VULKAN = true
        const val USE_NNAPI = true
        const val USE_THREADS = true
        const val DEFAULT_TILE_SIZE = 512
        const val DEFAULT_BATCH_SIZE = 1
        const val MAX_THREADS = 4
        const val THREAD_AFFINITY_ENABLED = true
        const val MEMORY_PINNING_ENABLED = true
        
        // Hardware acceleration settings
        object VulkanSettings {
            const val ENABLE_VULKAN_COMPUTE = true
            const val VULKAN_DEVICE_CACHE = true
            const val VULKAN_MEMORY_POOL_SIZE = 512 * 1024 * 1024 // 512MB
            const val VULKAN_QUEUE_SIZE = 2
        }
        
        object NNAPISettings {
            const val NNAPI_ACCELERATION_MODE = 1 // NNAPI_ACCELERATION_SUSTAINED_SPEED
            const val NNAPI_EXECUTION_PREFERENCE = 1 // NNAPI_FAST_SINGLE_ANSWER
            const val NNAPI_ALLOW_FP16 = true
            const val NNAPI_USE_NCHW = true
            const val NNAPI_CPU_DISABLED = false
            const val NNAPI_GPU_ENABLED = true
        }
        
        object MemorySettings {
            const val ENABLE_MEMORY_PATTERN = true
            const val ENABLE_MEMORY_ARENA = true
            const val ARENA_EXTEND_STRATEGY = 0 // kNextPowerOfTwo
            const val MEMORY_POOL_SIZE = 1024 * 1024 * 1024 // 1GB
            const val MEMORY_POOL_ARENA_COUNT = 2
        }
    }
    
    // Inference settings optimized for SD 3.5 Medium
    object InferenceSettings {
        const val DEFAULT_IMAGE_SIZE = 512
        const val DEFAULT_IMAGE_HEIGHT = 512
        const val DEFAULT_STEPS = 20
        const val MAX_STEPS = 50
        const val DEFAULT_GUIDANCE_SCALE = 7.5f
        const val ENABLE_ATTENTION_SLICING = true
        const val ENABLE_VAE_TILING = true
        const val VAE_TILE_SIZE = 512
        const val USE_HALF_PRECISION = true
        var SCHEDULER = SchedulerType.DDIM
    }

    object MemorySettings {
        const val MIN_MEMORY_MB = 2048L   // Minimum 2GB
        const val MAX_MEMORY_MB = 8192L   // Maximum 8GB
        const val PREFERRED_MEMORY_MB = 4096L  // Preferred 4GB
        const val MAX_CACHE_SIZE_MB = 1024L    // 1GB max cache
        
        // Component memory requirements
        const val TEXT_ENCODER_MEMORY_MB = 512L
        const val UNET_MEMORY_MB = 2048L
        const val VAE_MEMORY_MB = 512L
        const val SCHEDULER_MEMORY_MB = 128L
        const val TENSOR_MEMORY_MB = 64L
        const val VAE_TENSOR_MEMORY_MB = 128L
        const val INFERENCE_MEMORY_MB = 768L // Minimum required for inference
        
        // Memory scaling factors
        const val MEMORY_SCALE_FACTOR_LOW = 0.6f
        const val MEMORY_SCALE_FACTOR_NORMAL = 0.85f
        
        // Memory thresholds
        const val MEMORY_THRESHOLD_WARNING = 0.80f
        const val MEMORY_THRESHOLD_CRITICAL = 0.90f
        const val BUFFER_MEMORY_MB = 256L
        const val GC_THRESHOLD_MB = 2048L
        const val MEMORY_TRIM_THRESHOLD_MB = 3072L
        
        // Total memory calculation
        val TOTAL_MODEL_MEMORY_MB = TEXT_ENCODER_MEMORY_MB + 
                                  UNET_MEMORY_MB + 
                                  VAE_MEMORY_MB +
                                  SCHEDULER_MEMORY_MB
    }

    object Performance {
        const val NUM_THREADS = 2
        const val ENABLE_MEMORY_PATTERN = true
        const val ENABLE_PARALLEL_EXECUTION = false
    }

    object StorageSettings {
        const val MIN_STORAGE_GB = 8L
        const val PREFERRED_STORAGE_GB = 16L
    }
}

data class ModelInfo(
    val downloadUrl: String,
    val size: Long,
    val checksum: String,
    val configUrl: String?,
    val quantization: QuantizationInfo? = null
) 