package com.example.androiddiffusion.ml.diffusers

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.TensorInfo
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.config.UnifiedMemoryManager
import com.example.androiddiffusion.config.UnifiedMemoryManager.AllocationType
import com.example.androiddiffusion.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.asStateFlow

class OnnxModel(
    private val context: Context,
    private val modelPath: String,
    private val isLowMemoryMode: Boolean = false,
    private val memoryManager: UnifiedMemoryManager
) : AutoCloseable {
    companion object {
        private const val COMPONENT = "OnnxModel"
        private const val CACHE_DIR = "model_cache"
        private const val SESSION_INIT_TIMEOUT_MS = 30000L
        
        @Volatile
        private var isLibraryLoaded = false
        private val libraryLock = Object()
        
        private fun loadNativeLibrary() {
            if (!isLibraryLoaded) {
                synchronized(libraryLock) {
                    if (!isLibraryLoaded) {
                        try {
                            Logger.d(COMPONENT, "Loading ONNX Runtime native libraries")
                            Logger.d(COMPONENT, "Library search path: ${System.getProperty("java.library.path")}")
                            System.loadLibrary("onnxruntime")
                            isLibraryLoaded = true
                            Logger.d(COMPONENT, "ONNX Runtime native libraries loaded successfully")
                        } catch (e: UnsatisfiedLinkError) {
                            Logger.e(COMPONENT, "Failed to load ONNX Runtime native libraries", e)
                            throw RuntimeException("Failed to load ONNX Runtime: ${e.message}", e)
                        } catch (e: Exception) {
                            Logger.e(COMPONENT, "Unexpected error loading native library", e)
                            throw RuntimeException("Unexpected error loading ONNX Runtime: ${e.message}", e)
                        }
                    }
                }
            }
        }
    }

    init {
        loadNativeLibrary()
    }

    @Volatile
    private var session: OrtSession? = null
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions().apply {
        // Core optimizations
        addConfigEntry("session.graph_optimization_level", "ORT_ENABLE_BASIC")
        setIntraOpNumThreads(2)
        setInterOpNumThreads(1)
        
        // Memory optimizations
        addConfigEntry("session.enable_memory_pattern", "1")
        addConfigEntry("session.enable_memory_reuse", "1")
        addConfigEntry("session.enable_cpu_mem_arena", "1")
        addConfigEntry("session.use_arena_allocation", "1")
        addConfigEntry("session.coalesce_tensors", "1")
        addConfigEntry("session.enable_mem_pattern", "1")
        addConfigEntry("session.enable_mem_reuse", "1")
        
        // Conservative memory settings
        addConfigEntry("session.force_sequential_execution", "1")
        addConfigEntry("session.minimize_memory_use", "1")
        
        // Use our native memory allocator
        addConfigEntry("session.use_custom_memory_arena", "1")
        
        // Hardware acceleration settings
        try {
            // Disable NNAPI for now as it might be unstable
            addConfigEntry("session.use_nnapi", "0")
            Logger.d(COMPONENT, "NNAPI disabled for stability")
            
            // Log provider configuration
            Logger.d(COMPONENT, "Checking execution provider configuration...")
            try {
                val providers = listOf("CPU")
                providers.forEach { provider ->
                    Logger.d(COMPONENT, "Configured provider: $provider")
                }
            } catch (e: Exception) {
                Logger.w(COMPONENT, "Failed to log providers: ${e.message}")
            }
        } catch (e: Exception) {
            Logger.w(COMPONENT, "Hardware acceleration setup failed: ${e.message}")
        }
        
        // Dynamic memory limit
        val maxAvailableMemory = Runtime.getRuntime().maxMemory()
        val targetMemoryLimit = if (isLowMemoryMode) {
            (maxAvailableMemory * 0.6).toLong() // 60% of available memory
        } else {
            (maxAvailableMemory * 0.75).toLong() // 75% of available memory
        }
        
        addConfigEntry("session.max_memory_size", targetMemoryLimit.toString())
        addConfigEntry("session.memory_pattern_optimization", "1")
        addConfigEntry("session.memory_optimize", "1")
        addConfigEntry("session.memory_optimize_threshold", "1")
        
        // Additional memory optimizations
        addConfigEntry("session.use_deterministic_compute", "0") // Disable deterministic compute for better performance
        addConfigEntry("session.enable_profiling", "0") // Disable profiling to save memory
        addConfigEntry("session.planner_optimization_level", "3") // Maximum optimization
        addConfigEntry("session.execution_mode", "0") // Sequential execution
        addConfigEntry("session.inter_op_num_threads", "1") // Minimize thread overhead
    }

    private var isInitialized = false
    private val sessionLock = Object()
    private var modelBuffer: Long = 0L
    private var modelSize: Long = 0L
    private var modelAllocationId: String? = null

    fun getModelBuffer(): Long = modelBuffer

    sealed class LoadingState {
        object NotLoaded : LoadingState()
        data class Loading(val progress: Int, val stage: String) : LoadingState()
        object Loaded : LoadingState()
        data class Error(val message: String, val cause: Throwable? = null) : LoadingState()
    }

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.NotLoaded)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private fun calculateAndAllocateMemory(modelSizeMB: Int): Result<Long> {
        // Calculate required memory with more conservative estimates
        val workingMemoryMB = (modelSizeMB * 1.5).toInt() // Reduce from 2x to 1.5x
        val totalRequiredMB = modelSizeMB + workingMemoryMB

        Logger.d(COMPONENT, "Memory allocation calculation:")
        Logger.d(COMPONENT, "- Model size: ${modelSizeMB}MB")
        Logger.d(COMPONENT, "- Working memory: ${workingMemoryMB}MB")
        Logger.d(COMPONENT, "- Total required: ${totalRequiredMB}MB")

        modelAllocationId = "onnx_model_${System.currentTimeMillis()}"
        return memoryManager.allocateMemory(
            sizeMB = totalRequiredMB.toLong(),
            type = AllocationType.MODEL,
            id = modelAllocationId!!
        )
    }

    suspend fun loadModel(onProgress: (String, Int) -> Unit = { _, _ -> }) {
        if (_loadingState.value is LoadingState.Loading) {
            Logger.d(COMPONENT, "Model already loading, ignoring request")
            return
        }

        try {
            Logger.d(COMPONENT, "=== Starting Model Load ===")
            _loadingState.value = LoadingState.Loading(0, "Initializing")
            onProgress("init", 0)
            logMemoryState("Pre-load")

            synchronized(sessionLock) {
                if (isInitialized && session != null) {
                    Logger.d(COMPONENT, "Model already loaded")
                    _loadingState.value = LoadingState.Loaded
                    return@synchronized
                }
                session?.close()
                session = null
                isInitialized = false
            }

            // Copy model to cache with progress
            _loadingState.value = LoadingState.Loading(10, "Preparing model")
            onProgress("prepare", 10)
            val modelFile = copyModelToCache()
            modelSize = modelFile.length()

            // Calculate and allocate memory
            _loadingState.value = LoadingState.Loading(20, "Allocating memory")
            onProgress("memory", 20)
            val modelSizeMB = (modelSize / (1024 * 1024)).toInt()
            
            val memoryResult = calculateAndAllocateMemory(modelSizeMB)
            if (memoryResult.isFailure) {
                throw OutOfMemoryError("Failed to allocate memory for model: ${modelSizeMB}MB")
            }

            // Initialize session
            _loadingState.value = LoadingState.Loading(60, "Initializing session")
            onProgress("session", 60)
            initializeSession(modelFile)

            // Verify session
            _loadingState.value = LoadingState.Loading(80, "Verifying model")
            onProgress("verify", 80)
            verifySession()

            _loadingState.value = LoadingState.Loading(100, "Completing initialization")
            onProgress("complete", 100)
            
            Logger.d(COMPONENT, "=== Model Load Complete ===")
            _loadingState.value = LoadingState.Loaded
            logMemoryState("Final")

        } catch (e: Exception) {
            Logger.e(COMPONENT, "Model load failed", e)
            cleanup()
            _loadingState.value = LoadingState.Error(
                when (e) {
                    is OutOfMemoryError -> "Insufficient memory available"
                    is IllegalStateException -> e.message ?: "Session initialization failed"
                    else -> "Failed to load model: ${e.message}"
                },
                e
            )
            throw e
        }
    }

    private suspend fun initializeSession(modelFile: File) = withContext(Dispatchers.IO) {
        try {
            withTimeout(SESSION_INIT_TIMEOUT_MS) {
                Logger.d(COMPONENT, "Creating new session")
                
                // Force GC before session creation
                performGarbageCollection()
                
                val newSession = try {
                    env.createSession(modelFile.path, sessionOptions)
                } catch (e: Exception) {
                    Logger.e(COMPONENT, "Failed to create session", e)
                    throw ModelLoadException("Session creation failed: ${e.message}", e)
                }
                
                synchronized(sessionLock) {
                    session = newSession
                    isInitialized = true
                    logModelInfo()
                }
                Logger.d(COMPONENT, "Session initialized successfully")
            }
        } catch (e: Exception) {
            throw ModelLoadException("Session initialization failed: ${e.message}", e)
        }
    }

    private suspend fun copyModelToCache(): File = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
            val modelName = modelPath.substringAfterLast("/")
            val cachedModel = File(cacheDir, modelName)

            if (cachedModel.exists()) {
                Logger.d(COMPONENT, "Using cached model: ${cachedModel.path}")
                // Verify file integrity
                if (cachedModel.length() > 0) {
                    return@withContext cachedModel
                } else {
                    Logger.w(COMPONENT, "Cached model file is empty, deleting and re-copying")
                    cachedModel.delete()
                }
            }

            try {
                val inputStream = if (File(modelPath).exists()) {
                    File(modelPath).inputStream()
                } else {
                    context.assets.open(modelPath)
                }
                inputStream.use { input ->
                    // Create temp file first
                    val tempFile = File(cacheDir, "$modelName.tmp")
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    // Rename temp file to final name
                    if (!tempFile.renameTo(cachedModel)) {
                        throw IOException("Failed to rename temp file to $modelName")
                    }
                }
            } catch (e: Exception) {
                Logger.e(COMPONENT, "Error copying model file", e)
                // Clean up any partial files
                cachedModel.delete()
                throw e
            }
            
            Logger.d(COMPONENT, "Model copied to cache: ${cachedModel.path}")
            return@withContext cachedModel
        } catch (e: Exception) {
            throw ModelLoadException("Failed to copy model to cache: ${e.message}", e)
        }
    }

    fun runInference(inputs: Map<String, OnnxTensor>): Map<String, OnnxTensor> {
        synchronized(sessionLock) {
            if (!isInitialized || session == null) {
                throw IllegalStateException("Session not initialized")
            }
            
            Logger.d(COMPONENT, "Starting inference")
            logMemoryState("Pre-inference")
            
            return try {
                val startTime = System.nanoTime()
                val prepStartTime = System.nanoTime()
                
                // Log input tensor shapes
                inputs.forEach { (name, tensor) ->
                    Logger.d(COMPONENT, "Input tensor '$name' shape: ${tensor.info.shape.contentToString()}")
                }
                val prepEndTime = System.nanoTime()
                
                val inferenceStartTime = System.nanoTime()
                val result = session!!.run(inputs)
                val inferenceEndTime = System.nanoTime()
                
                val postStartTime = System.nanoTime()
                val outputs = mutableMapOf<String, OnnxTensor>().apply {
                    session!!.outputInfo.forEach { (name, info) ->
                        put(name, result[name].get() as OnnxTensor)
                        Logger.d(COMPONENT, "Output tensor '$name' shape: ${get(name)?.info?.shape?.contentToString()}")
                    }
                }
                val postEndTime = System.nanoTime()
                
                val endTime = System.nanoTime()
                
                // Log detailed timing information
                Logger.d(COMPONENT, "Inference timing breakdown:")
                Logger.d(COMPONENT, "- Preparation: ${(prepEndTime - prepStartTime) / 1_000_000}ms")
                Logger.d(COMPONENT, "- Inference: ${(inferenceEndTime - inferenceStartTime) / 1_000_000}ms")
                Logger.d(COMPONENT, "- Post-processing: ${(postEndTime - postStartTime) / 1_000_000}ms")
                Logger.d(COMPONENT, "- Total time: ${(endTime - startTime) / 1_000_000}ms")
                
                logMemoryState("Post-inference")
                outputs
            } catch (e: Exception) {
                Logger.e(COMPONENT, "Inference failed", e)
                throw e
            }
        }
    }

    private fun logModelInfo() {
        session?.let { sess ->
            sess.inputInfo.forEach { (name, info) ->
                val tensorInfo = info.info as TensorInfo
                Logger.d(COMPONENT, "Input '$name' - Type: ${tensorInfo.type}, Shape: ${tensorInfo.shape?.contentToString()}")
            }
            sess.outputInfo.forEach { (name, info) ->
                val tensorInfo = info.info as TensorInfo
                Logger.d(COMPONENT, "Output '$name' - Type: ${tensorInfo.type}, Shape: ${tensorInfo.shape?.contentToString()}")
            }
        }
    }

    fun isSessionInitialized(): Boolean {
        synchronized(sessionLock) {
            return isInitialized && session != null
        }
    }

    override fun close() {
        cleanup()
    }

    private fun cleanup() {
        synchronized(sessionLock) {
            try {
                session?.close()
                session = null
                modelAllocationId?.let { id ->
                    memoryManager.freeMemory(id)
                    modelAllocationId = null
                }
                isInitialized = false
                Logger.d(COMPONENT, "Session cleaned up")
            } catch (e: Exception) {
                Logger.e(COMPONENT, "Error during cleanup", e)
            }
        }
    }

    private fun logMemoryState(phase: String) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val usedMemory = totalMemory - freeMemory
        
        Logger.d(COMPONENT, "Memory State - $phase:")
        Logger.d(COMPONENT, "- Max Memory: ${maxMemory}MB")
        Logger.d(COMPONENT, "- Total Memory: ${totalMemory}MB")
        Logger.d(COMPONENT, "- Free Memory: ${freeMemory}MB")
        Logger.d(COMPONENT, "- Used Memory: ${usedMemory}MB")
        Logger.d(COMPONENT, "- Usage: ${(usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()}%")
        Logger.d(COMPONENT, "- Native Memory Available: ${memoryManager.getAvailableMemoryMB()}MB")
    }

    private fun performGarbageCollection() {
        Logger.d(COMPONENT, "Performing garbage collection...")
        System.gc()
        Runtime.getRuntime().gc()
        Thread.sleep(100)
        Logger.d(COMPONENT, "Garbage collection completed")
    }

    private fun verifySession() {
        val inputInfo = session?.inputInfo
        val outputInfo = session?.outputInfo
        
        Logger.d(COMPONENT, "Verifying session configuration:")
        Logger.d(COMPONENT, "Input nodes: ${inputInfo?.size}")
        inputInfo?.forEach { (name, info) ->
            val tensorInfo = info.info as TensorInfo
            Logger.d(COMPONENT, "- Input '$name': Type=${tensorInfo.type}, Shape=${tensorInfo.shape?.contentToString()}")
        }
        
        Logger.d(COMPONENT, "Output nodes: ${outputInfo?.size}")
        outputInfo?.forEach { (name, info) ->
            val tensorInfo = info.info as TensorInfo
            Logger.d(COMPONENT, "- Output '$name': Type=${tensorInfo.type}, Shape=${tensorInfo.shape?.contentToString()}")
        }
    }
    
    class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
} 
