package com.example.androiddiffusion.ml.acceleration

import ai.onnxruntime.*
import android.content.Context
import android.util.Log
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.util.addConfigEntry
import com.example.androiddiffusion.util.setMemoryPatternOptimization
import com.example.androiddiffusion.util.setOptimizationLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwareAccelerationManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "HardwareAcceleration"
    }

    private var vulkanAvailable: Boolean = false
    private var nnapiAvailable: Boolean = false

    init {
        checkAvailableAccelerators()
    }

    private fun checkAvailableAccelerators() {
        try {
            val env = OrtEnvironment.getEnvironment()
            
            // Attempt to get available providers via reflection
            val providers: List<String> = try {
                val method = env.javaClass.getMethod("getAvailableProviders")
                method.invoke(env) as? List<String> ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            vulkanAvailable = providers.contains("VulkanExecutionProvider")
            nnapiAvailable = providers.contains("NnapiExecutionProvider")

            Logger.i(TAG, "Hardware acceleration availability: Vulkan=$vulkanAvailable, NNAPI=$nnapiAvailable")
        } catch (e: Exception) {
            Logger.e(TAG, "Error initializing hardware acceleration", e)
        }
    }

    fun configureSessionOptions(options: OrtSession.SessionOptions) {
        try {
            // Configure thread settings
            with(ModelConfig.OptimizationSettings) {
                options.setIntraOpNumThreads(MAX_THREADS)
                options.setInterOpNumThreads(MAX_THREADS)
                if (THREAD_AFFINITY_ENABLED) {
                    options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                }
            }

            // Configure memory settings
            with(ModelConfig.OptimizationSettings.MemorySettings) {
                if (ENABLE_MEMORY_PATTERN) {
                    options.setMemoryPatternOptimization(true)
                }
                if (ENABLE_MEMORY_ARENA) {
                    // Use "BASIC" as replacement
                    options.setOptimizationLevel("BASIC")
                }
                options.addConfigEntry("session.memory_arena_cfg", MEMORY_POOL_SIZE.toString())
            }

            // Add Vulkan provider if available and enabled
            if (vulkanAvailable && ModelConfig.OptimizationSettings.USE_VULKAN) {
                with(ModelConfig.OptimizationSettings.VulkanSettings) {
                    val vulkanProviderOptions = mapOf(
                        "device_id" to "0",
                        "gpu_mem_limit" to VULKAN_MEMORY_POOL_SIZE.toString(),
                        "device_cache_enable" to VULKAN_DEVICE_CACHE.toString(),
                        "queue_size" to VULKAN_QUEUE_SIZE.toString()
                    )
                    options.addConfigEntry("session.vulkan", vulkanProviderOptions.toString())
                    Logger.i(TAG, "Vulkan provider configured")
                }
            }

            // Add NNAPI provider if available and enabled
            if (nnapiAvailable && ModelConfig.OptimizationSettings.USE_NNAPI) {
                with(ModelConfig.OptimizationSettings.NNAPISettings) {
                    val nnapiProviderOptions = mapOf(
                        "NNAPI_ACCELERATION_MODE" to NNAPI_ACCELERATION_MODE.toString(),
                        "NNAPI_EXECUTION_PREFERENCE" to NNAPI_EXECUTION_PREFERENCE.toString(),
                        "NNAPI_ALLOW_FP16" to NNAPI_ALLOW_FP16.toString(),
                        "NNAPI_USE_NCHW" to NNAPI_USE_NCHW.toString(),
                        "NNAPI_CPU_DISABLED" to NNAPI_CPU_DISABLED.toString(),
                        "NNAPI_GPU_ENABLED" to NNAPI_GPU_ENABLED.toString()
                    )
                    options.addConfigEntry("session.nnapi", nnapiProviderOptions.toString())
                    Logger.i(TAG, "NNAPI provider configured")
                }
            }

            Logger.i(TAG, "Session options configured successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Error configuring session options", e)
            throw RuntimeException("Failed to configure hardware acceleration", e)
        }
    }

    fun isVulkanAvailable() = vulkanAvailable
    fun isNNAPIAvailable() = nnapiAvailable
} 