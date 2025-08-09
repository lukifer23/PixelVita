package com.example.androiddiffusion.ml.diffusers

// Android imports
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap

// ONNX Runtime imports
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.TensorInfo

// App imports
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.config.MemoryManager
import com.example.androiddiffusion.util.NativeMemoryManager
import com.example.androiddiffusion.service.ModelLoadingService
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.config.SchedulerType

// Dependency injection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Kotlin coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

// Java/Kotlin standard library
import java.io.File
import java.nio.FloatBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import kotlin.math.sqrt

// App-specific imports
import com.example.androiddiffusion.ml.diffusers.schedulers.DDIMScheduler
import com.example.androiddiffusion.ml.diffusers.schedulers.DDPMScheduler
import com.example.androiddiffusion.ml.diffusers.schedulers.EulerScheduler
import com.example.androiddiffusion.ml.diffusers.schedulers.Scheduler
import com.example.androiddiffusion.config.UnifiedMemoryManager
import com.example.androiddiffusion.config.UnifiedMemoryManager.AllocationType

@Singleton
class DiffusersPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val memoryManager: UnifiedMemoryManager,
    private val tokenizer: TextTokenizer,
    private val onProgressUpdate: (Float) -> Unit = {}
) : AutoCloseable {
    companion object {
        private const val COMPONENT = "DiffusersPipeline"
        private const val DEFAULT_WIDTH = ModelConfig.InferenceSettings.DEFAULT_IMAGE_SIZE
        private const val DEFAULT_HEIGHT = ModelConfig.InferenceSettings.DEFAULT_IMAGE_HEIGHT
        private const val LATENT_CHANNELS = 4
        private const val TEXT_EMBEDDING_SIZE = 768
        private const val MAX_TEXT_LENGTH = 77
        private const val BATCH_SIZE = 1
        private const val MAX_MEMORY_BYTES = 2048L * 1024 * 1024  // 2GB
        private const val MIN_MEMORY_BYTES = 1024L * 1024 * 1024  // 1GB
        private const val SESSION_INIT_TIMEOUT_MS = 10000L // 10 seconds
    }

    private var textEncoder: OnnxModel? = null
    private var unet: OnnxModel? = null
    private var vaeDecoder: OnnxModel? = null
    private var scheduler: Scheduler? = null
    private var isLowMemoryMode = true
    private var lastGCTime = 0L
    private val GC_INTERVAL_MS = 30000L // 30 seconds between GC calls
    private var session: OrtSession? = null
    private val sessionLock = Any()
    private var isInitialized = false
    private var env: OrtEnvironment? = null
    private var sessionOptions: OrtSession.SessionOptions? = null
    private var currentModel: DiffusionModel? = null
    private var lastError: Exception? = null
    private var latentsBuffer: FloatArray? = null
    private var combinedLatentsBuffer: FloatArray? = null
    private var combinedEmbeddingsBuffer: FloatArray? = null
    private var guidedPredBuffer: FloatArray? = null
    private var scaledLatentsBuffer: FloatArray? = null
    
    init {
        Logger.d(COMPONENT, "Pipeline created in low memory mode")
        if (isLowMemoryMode) {
            Logger.d(COMPONENT, "Low memory mode enabled")
        }
        Runtime.getRuntime().addShutdownHook(Thread {
            cleanup()
        })
        setupOnnxRuntime()
    }

    private fun setupOnnxRuntime() {
        env = OrtEnvironment.getEnvironment()
        sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(ModelConfig.Performance.NUM_THREADS)
            setMemoryPatternOptimization(ModelConfig.Performance.ENABLE_MEMORY_PATTERN)
            addConfigEntry("session.graph_optimization_level", "ORT_ENABLE_BASIC")
            addConfigEntry("session.use_memory_arena", "1")
            addConfigEntry("session.force_sequential_execution", "1")
        }
    }

    private fun createSession(modelPath: String): OrtSession {
        return env?.createSession(modelPath, sessionOptions) 
            ?: throw IllegalStateException("Failed to create ONNX session")
    }

    private fun checkMemoryForComponent(requiredMemoryMB: Long, componentName: String) {
        val result = memoryManager.allocateMemory(
            sizeMB = requiredMemoryMB,
            type = AllocationType.MODEL,
            id = "model_$componentName"
        )
        
        if (result.isFailure) {
            throw OutOfMemoryError("Not enough memory to load $componentName. Required: ${requiredMemoryMB}MB")
        }
    }

    fun setLowMemoryMode(enabled: Boolean) {
        isLowMemoryMode = enabled
        Logger.d(COMPONENT, "Low memory mode ${if (enabled) "enabled" else "disabled"}")
    }

    private fun initializeIfNeeded() {
        if (!isInitialized) {
            Logger.d(COMPONENT, "Initializing pipeline")
            sessionOptions?.apply {
                // Core optimizations
                addConfigEntry("session.graph_optimization_level", "ORT_ENABLE_ALL")
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
                setMemoryPatternOptimization(true)
                
                // Basic memory optimizations that don't require large allocations
                addConfigEntry("session.use_memory_arena", "1")
                addConfigEntry("session.force_sequential_execution", "1")
            }
            isInitialized = true
        }
    }

    private fun getAvailableMemory(): Long {
        return memoryManager.getAvailableMemoryMB() * 1024 * 1024
    }

    private fun getMemoryUsage(): Float {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory.toFloat() / runtime.maxMemory()
    }

    private fun ensureMemoryAvailable(requiredMemoryMB: Long) {
        val result = memoryManager.allocateMemory(
            sizeMB = requiredMemoryMB,
            type = AllocationType.MODEL,
            id = "temp_check_${System.currentTimeMillis()}"
        )
        
        if (result.isFailure) {
            throw OutOfMemoryError(
                "Insufficient memory for operation. Required: ${requiredMemoryMB}MB"
            )
        }
        
        // Free the temporary allocation
        memoryManager.freeMemory("temp_check_${System.currentTimeMillis()}")
    }

    private fun checkMemoryAndCleanup() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGCTime > GC_INTERVAL_MS) {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toFloat() / maxMemory

            when {
                memoryUsage > ModelConfig.MemorySettings.MEMORY_THRESHOLD_CRITICAL -> {
                    Logger.w(COMPONENT, "Critical memory usage: ${(memoryUsage * 100).toInt()}%")
                    cleanup()
                    System.gc()
                    Runtime.getRuntime().gc()
                    Thread.sleep(100)  // Give GC time to work
                }
                memoryUsage > ModelConfig.MemorySettings.MEMORY_THRESHOLD_WARNING -> {
                    Logger.w(COMPONENT, "High memory usage: ${(memoryUsage * 100).toInt()}%")
                    System.gc()
                }
            }
            lastGCTime = currentTime
        }
    }

    private suspend fun initializeSession(modelFile: File) = withContext(Dispatchers.IO) {
        try {
            withTimeout(SESSION_INIT_TIMEOUT_MS) {
                Logger.d(COMPONENT, "Creating new session")
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val availableMemory = maxMemory - usedMemory
                
                Logger.d(COMPONENT, "Memory before session creation:")
                Logger.d(COMPONENT, "Max memory: ${maxMemory / (1024 * 1024)}MB")
                Logger.d(COMPONENT, "Used memory: ${usedMemory / (1024 * 1024)}MB")
                Logger.d(COMPONENT, "Available memory: ${availableMemory / (1024 * 1024)}MB")
                
                if (availableMemory < MIN_MEMORY_BYTES) {
                    Logger.w(COMPONENT, "Low memory condition detected, forcing garbage collection")
                    System.gc()
                    Runtime.getRuntime().gc()
                    Thread.sleep(100) // Give GC time to work
                }
                
                val newSession = createSession(modelFile.path)
                
                synchronized(sessionLock) {
                    session = newSession
                    isInitialized = true
                    logModelInfo()
                }
                Logger.d(COMPONENT, "Session initialized successfully")
                
                // Verify session is working
                try {
                    val inputInfo = session?.inputInfo
                    val outputInfo = session?.outputInfo
                    Logger.d(COMPONENT, "Session verification - Inputs: ${inputInfo?.size}, Outputs: ${outputInfo?.size}")
                } catch (e: Exception) {
                    Logger.e(COMPONENT, "Session verification failed", e)
                    throw ModelLoadException("Session verification failed: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            throw ModelLoadException("Session initialization failed: ${e.message}", e)
        }
    }

    suspend fun loadModel(modelPath: String, onProgress: (String, Int) -> Unit) = withContext(Dispatchers.Default) {
        try {
            Logger.d(COMPONENT, "=== Starting Model Loading Process ===")
            logInitialMemoryState()
            onProgress("init", 0)

            // Force cleanup and GC before starting
            cleanup()
            onProgress("cleanup", 5)
            
            // Verify available memory
            val requiredMemory = ModelConfig.MemorySettings.TOTAL_MODEL_MEMORY_MB
            val memoryResult = memoryManager.allocateMemory(
                sizeMB = requiredMemory,
                type = AllocationType.MODEL,
                id = "model_total"
            )
            
            if (memoryResult.isFailure) {
                throw OutOfMemoryError("Insufficient memory to load models. Required: ${requiredMemory}MB")
            }
            onProgress("memory_check", 10)

            // Verify model files exist
            val requiredFiles = listOf(
                "$modelPath/text_encoder.onnx",
                "$modelPath/unet.onnx",
                "$modelPath/vae_decoder.onnx"
            )
            
            requiredFiles.forEach { path ->
                try {
                    context.assets.open(path).use { 
                        Logger.d(COMPONENT, "Verified model file: $path")
                        it.close() 
                    }
                } catch (e: Exception) {
                    throw ModelLoadException("Missing required model file: $path", e)
                }
            }
            onProgress("verify_files", 15)

            try {
                // Step 1: Initialize scheduler (lightweight operation)
                Logger.d(COMPONENT, "=== Phase 1: Scheduler Initialization ===")
                scheduler = when (ModelConfig.InferenceSettings.SCHEDULER) {
                    SchedulerType.DDPM -> DDPMScheduler()
                    SchedulerType.EULER -> EulerScheduler()
                    else -> DDIMScheduler()
                }
                scheduler?.setTimesteps(ModelConfig.InferenceSettings.DEFAULT_STEPS)
                Logger.d(COMPONENT, "Scheduler initialized successfully")
                onProgress("scheduler", 20)
                logMemoryState("After scheduler initialization")

                // Step 2: Load text encoder
                Logger.d(COMPONENT, "=== Phase 2: Text Encoder Loading ===")
                val textEncoderMemory = ModelConfig.MemorySettings.TEXT_ENCODER_MEMORY_MB
                val textEncoderResult = memoryManager.allocateMemory(
                    sizeMB = textEncoderMemory,
                    type = AllocationType.MODEL,
                    id = "model_text_encoder"
                )
                
                if (textEncoderResult.isFailure) {
                    throw OutOfMemoryError("Insufficient memory for text encoder")
                }
                
                onProgress("text_encoder_init", 25)
                textEncoder = OnnxModel(
                    context = context,
                    modelPath = "$modelPath/text_encoder.onnx",
                    isLowMemoryMode = memoryManager.memoryState.value is UnifiedMemoryManager.MemoryState.Low,
                    memoryManager = memoryManager
                )
                
                textEncoder?.loadModel { stage, progress -> 
                    onProgress("text_encoder_$stage", 25 + (progress * 0.15).toInt())
                }
                onProgress("text_encoder_verify", 40)
                Logger.d(COMPONENT, "Text encoder loaded successfully")
                logMemoryState("After text encoder loading")

                // Step 3: Load UNet
                Logger.d(COMPONENT, "=== Phase 3: UNet Loading ===")
                val unetMemory = ModelConfig.MemorySettings.UNET_MEMORY_MB
                val unetResult = memoryManager.allocateMemory(
                    sizeMB = unetMemory,
                    type = AllocationType.MODEL,
                    id = "model_unet"
                )
                
                if (unetResult.isFailure) {
                    throw OutOfMemoryError("Insufficient memory for UNet")
                }
                
                onProgress("unet_init", 45)
                unet = OnnxModel(
                    context = context,
                    modelPath = "$modelPath/unet.onnx",
                    isLowMemoryMode = memoryManager.memoryState.value is UnifiedMemoryManager.MemoryState.Low,
                    memoryManager = memoryManager
                )
                
                unet?.loadModel { stage, progress ->
                    onProgress("unet_$stage", 45 + (progress * 0.25).toInt())
                }
                onProgress("unet_verify", 70)
                Logger.d(COMPONENT, "UNet loaded successfully")
                logMemoryState("After UNet loading")

                // Step 4: Load VAE
                Logger.d(COMPONENT, "=== Phase 4: VAE Loading ===")
                val vaeMemory = ModelConfig.MemorySettings.VAE_MEMORY_MB
                val vaeResult = memoryManager.allocateMemory(
                    sizeMB = vaeMemory,
                    type = AllocationType.MODEL,
                    id = "model_vae"
                )
                
                if (vaeResult.isFailure) {
                    throw OutOfMemoryError("Insufficient memory for VAE")
                }
                
                onProgress("vae_init", 75)
                vaeDecoder = OnnxModel(
                    context = context,
                    modelPath = "$modelPath/vae_decoder.onnx",
                    isLowMemoryMode = memoryManager.memoryState.value is UnifiedMemoryManager.MemoryState.Low,
                    memoryManager = memoryManager
                )
                
                vaeDecoder?.loadModel { stage, progress ->
                    onProgress("vae_$stage", 75 + (progress * 0.20).toInt())
                }
                onProgress("vae_verify", 95)
                Logger.d(COMPONENT, "VAE loaded successfully")
                logMemoryState("After VAE loading")

                onProgress("complete", 100)
                Logger.d(COMPONENT, "=== Model Loading Complete ===")
                logFinalMemoryState()

            } catch (e: Exception) {
                Logger.e(COMPONENT, "Error during model loading", e)
                cleanup()
                throw e
            }
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Fatal error during model loading", e)
            cleanup()
            throw e
        }
    }

    private fun logInitialMemoryState() {
        Logger.d(COMPONENT, "Initial Memory Status:")
        Logger.d(COMPONENT, "Available Memory: ${memoryManager.getAvailableMemoryMB()}MB")
        Logger.d(COMPONENT, "Total Memory: ${memoryManager.getTotalMemoryMB()}MB")
        Logger.d(COMPONENT, "Low Memory Mode: $isLowMemoryMode")
    }

    private fun logMemoryState(phase: String) {
        Logger.d(COMPONENT, "Memory Status - $phase:")
        Logger.d(COMPONENT, "Java Heap - Available: ${getAvailableMemory() / (1024 * 1024)}MB")
        Logger.d(COMPONENT, "Memory Usage: ${(getMemoryUsage() * 100).toInt()}%")
    }

    private fun logFinalMemoryState() {
        Logger.d(COMPONENT, "Final Memory Status:")
        Logger.d(COMPONENT, "System Memory:")
        Logger.d(COMPONENT, "- Available Memory: ${memoryManager.getAvailableMemoryMB()}MB")
        Logger.d(COMPONENT, "- Total Memory: ${memoryManager.getTotalMemoryMB()}MB")
        Logger.d(COMPONENT, "- Memory Usage: ${(getMemoryUsage() * 100).toInt()}%")
        
        Logger.d(COMPONENT, "Component Status:")
        val componentStatus = getLoadedComponents()
        Logger.d(COMPONENT, "- Text Encoder: ${componentStatus.textEncoder}")
        Logger.d(COMPONENT, "- UNet: ${componentStatus.unet}")
        Logger.d(COMPONENT, "- VAE: ${componentStatus.vae}")
        Logger.d(COMPONENT, "- Scheduler: ${componentStatus.scheduler}")
        
        Logger.d(COMPONENT, "Pipeline State:")
        Logger.d(COMPONENT, "- Initialization Status: $isInitialized")
        Logger.d(COMPONENT, "- Current Model: ${currentModel?.name ?: "None"}")
        Logger.d(COMPONENT, "- Last Error: ${lastError?.message ?: "None"}")
    }

    private data class ComponentStatus(
        val textEncoder: String,
        val unet: String,
        val vae: String,
        val scheduler: String
    )

    private fun getLoadedComponents(): ComponentStatus {
        return ComponentStatus(
            textEncoder = textEncoder?.let { 
                if (it.isSessionInitialized()) "Initialized" else "Loaded but not initialized"
            } ?: "Not loaded",
            unet = unet?.let {
                if (it.isSessionInitialized()) "Initialized" else "Loaded but not initialized"
            } ?: "Not loaded",
            vae = vaeDecoder?.let {
                if (it.isSessionInitialized()) "Initialized" else "Loaded but not initialized"
            } ?: "Not loaded",
            scheduler = if (scheduler != null) "Initialized" else "Not loaded"
        )
    }

    private fun performGarbageCollection() {
        Logger.d(COMPONENT, "Initiating garbage collection...")
        Logger.d(COMPONENT, "Pre-GC Memory State:")
        logFinalMemoryState()
        
        System.gc()
        Runtime.getRuntime().gc()
        Thread.sleep(100)
        
        Logger.d(COMPONENT, "Post-GC Memory State:")
        logFinalMemoryState()
        Logger.d(COMPONENT, "Garbage collection completed")
    }

    private fun releaseComponentMemory(component: String) {
        memoryManager.freeMemory("component_$component")
        performGarbageCollection()
    }

    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "",
        numInferenceSteps: Int = ModelConfig.InferenceSettings.DEFAULT_STEPS,
        guidanceScale: Float = ModelConfig.InferenceSettings.DEFAULT_GUIDANCE_SCALE,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        seed: Long = System.currentTimeMillis(),
        initImage: FloatArray? = null,
        mask: FloatArray? = null,
        onProgress: suspend (Int) -> Unit = {}
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            validateAndPrepareInference()
            
            // 1. Encode text
            val promptEmbedding = encodeText(prompt)
            val negativeEmbedding = if (negativePrompt.isNotEmpty()) {
                encodeText(negativePrompt)
            } else {
                createEmptyEmbedding()
            }

            // 2. Generate initial latents
            var latents = initImage ?: generateInitialLatents(seed, width, height)

            // 3. Diffusion process
            val timesteps = scheduler!!.getTimesteps()
            for (step in 0 until numInferenceSteps) {
                val progress = ((step + 1) * 100) / numInferenceSteps
                onProgress(progress)

                performDiffusionStep(
                    latents = latents,
                    timestep = timesteps[step],
                    promptEmbedding = promptEmbedding,
                    negativeEmbedding = negativeEmbedding,
                    guidanceScale = guidanceScale
                )

                if (initImage != null && mask != null) {
                    for (i in latents.indices) {
                        latents[i] = initImage[i] * mask[i] + latents[i] * (1f - mask[i])
                    }
                }
            }

            // 4. Decode latents to image
            decodeLatentsToImage(latents, width, height)
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error generating image", e)
            throw GenerationException("Failed to generate image: ${e.message}", e)
        }
    }

    suspend fun imageToImage(
        prompt: String,
        inputImage: Bitmap,
        negativePrompt: String = "",
        numInferenceSteps: Int = ModelConfig.InferenceSettings.DEFAULT_STEPS,
        guidanceScale: Float = ModelConfig.InferenceSettings.DEFAULT_GUIDANCE_SCALE,
        strength: Float = 0.8f,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        seed: Long = System.currentTimeMillis(),
        onProgress: suspend (Int) -> Unit = {}
    ): Bitmap {
        val initLatents = preprocessImageToLatents(inputImage, width, height)
        val noise = generateInitialLatents(seed, width, height)
        for (i in initLatents.indices) {
            initLatents[i] = initLatents[i] * (1f - strength) + noise[i] * strength
        }
        return generateImage(
            prompt = prompt,
            negativePrompt = negativePrompt,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            width = width,
            height = height,
            seed = seed,
            initImage = initLatents,
            onProgress = onProgress
        )
    }

    suspend fun inpaint(
        prompt: String,
        inputImage: Bitmap,
        mask: Bitmap,
        negativePrompt: String = "",
        numInferenceSteps: Int = ModelConfig.InferenceSettings.DEFAULT_STEPS,
        guidanceScale: Float = ModelConfig.InferenceSettings.DEFAULT_GUIDANCE_SCALE,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        seed: Long = System.currentTimeMillis(),
        onProgress: suspend (Int) -> Unit = {}
    ): Bitmap {
        val initLatents = preprocessImageToLatents(inputImage, width, height)
        val maskTensor = preprocessMask(mask, width, height)
        return generateImage(
            prompt = prompt,
            negativePrompt = negativePrompt,
            numInferenceSteps = numInferenceSteps,
            guidanceScale = guidanceScale,
            width = width,
            height = height,
            seed = seed,
            initImage = initLatents,
            mask = maskTensor,
            onProgress = onProgress
        )
    }

    private fun validateAndPrepareInference() {
        if (!isInitialized) {
            throw IllegalStateException("Pipeline not initialized")
        }

        val memoryInfo = memoryManager.getMemoryInfo()
        val requiredMemory = ModelConfig.MemorySettings.INFERENCE_MEMORY_MB

        if (memoryInfo.freeMemoryMB < requiredMemory) {
            Logger.w(COMPONENT, "Low memory condition detected before inference")
            performGarbageCollection()
            
            val updatedMemoryInfo = memoryManager.getMemoryInfo()
            if (updatedMemoryInfo.freeMemoryMB < requiredMemory) {
                throw OutOfMemoryError("Insufficient memory for inference. Required: ${requiredMemory}MB, Available: ${updatedMemoryInfo.freeMemoryMB}MB")
            }
        }

        validateComponents()
    }

    private fun validateComponents() {
        if (textEncoder?.loadingState?.value !is OnnxModel.LoadingState.Loaded) {
            throw IllegalStateException("Text encoder not loaded")
        }
        if (unet?.loadingState?.value !is OnnxModel.LoadingState.Loaded) {
            throw IllegalStateException("UNet not loaded")
        }
        if (vaeDecoder?.loadingState?.value !is OnnxModel.LoadingState.Loaded) {
            throw IllegalStateException("VAE decoder not loaded")
        }
        if (scheduler == null) {
            throw IllegalStateException("Scheduler not initialized")
        }
    }

    private suspend fun encodeText(text: String): FloatArray {
        Logger.d(COMPONENT, "Encoding text: '$text'")
        Logger.memory(COMPONENT)
        
        if (textEncoder?.isSessionInitialized() != true) {
            throw IllegalStateException("Text encoder not initialized")
        }
        
        return try {
            val tokens = tokenizer.tokenize(text)
            Logger.d(COMPONENT, "Text tokenized to ${tokens.size} tokens")
            
            val inputShape = longArrayOf(1, tokens.size.toLong())
            val inputTensor = TensorUtils.createInputTensor(tokens, inputShape)
            Logger.d(COMPONENT, "Created text encoder input tensor with shape: ${inputShape.joinToString()}")
            
            Logger.d(COMPONENT, "Running text encoder inference")
            val outputs = withContext(Dispatchers.Default) {
                textEncoder?.runInference(mapOf("input_ids" to inputTensor))
                    ?: throw IllegalStateException("Text encoder returned null output")
            }
            
            val lastHiddenState = outputs["last_hidden_state"]
                ?: throw IllegalStateException("Missing last_hidden_state in output")
            
            TensorUtils.tensorToFloatArray(lastHiddenState)
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error encoding text", e)
            throw e
        }
    }

    private fun createEmptyEmbedding(): FloatArray {
        return FloatArray(BATCH_SIZE * MAX_TEXT_LENGTH * TEXT_EMBEDDING_SIZE)
    }

    private suspend fun generateInitialLatents(seed: Long, width: Int, height: Int): FloatArray = withContext(Dispatchers.Default) {
        Logger.d(COMPONENT, "Generating initial latents with seed: $seed")
        Logger.memory(COMPONENT)

        val random = Random(seed)
        val latentWidth = width / 8
        val latentHeight = height / 8
        val totalElements = BATCH_SIZE * LATENT_CHANNELS * latentWidth * latentHeight
        
        Logger.d(COMPONENT, "Latent dimensions: ${latentWidth}x${latentHeight}, total elements: $totalElements")
        
        // Generate random latents using Box-Muller transform
        val latents = TensorUtils.reuseOrCreateBuffer(latentsBuffer, totalElements)
        var i = 0
        while (i < totalElements) {
            val u1 = random.nextDouble()
            val u2 = random.nextDouble()
            
            val z1 = sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
            latents[i] = z1.toFloat()
            i++
            
            if (i < totalElements) {
                val z2 = sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.sin(2.0 * kotlin.math.PI * u2)
                latents[i] = z2.toFloat()
                i++
            }
        }
        
        // Scale latents
        val noiseStd = scheduler!!.getInitialNoiseStd()
        Logger.d(COMPONENT, "Scaling latents with noise std: $noiseStd")
        for (j in latents.indices) {
            latents[j] *= noiseStd
        }
        
        Logger.memory(COMPONENT)
        latentsBuffer = latents
        latents
    }

    private fun preprocessImageToLatents(image: Bitmap, width: Int, height: Int): FloatArray {
        val latentWidth = width / 8
        val latentHeight = height / 8
        val resized = Bitmap.createScaledBitmap(image, latentWidth, latentHeight, true)
        val imageTensor = TensorUtils.bitmapToFloatArray(resized)
        val hw = latentWidth * latentHeight
        val latents = FloatArray(LATENT_CHANNELS * hw)
        for (i in 0 until hw) {
            latents[i] = imageTensor[i]
            latents[i + hw] = imageTensor[i + hw]
            latents[i + 2 * hw] = imageTensor[i + 2 * hw]
            latents[i + 3 * hw] = 0f
        }
        return latents
    }

    private fun preprocessMask(mask: Bitmap, width: Int, height: Int): FloatArray {
        val latentWidth = width / 8
        val latentHeight = height / 8
        val resized = Bitmap.createScaledBitmap(mask, latentWidth, latentHeight, true)
        val pixels = IntArray(latentWidth * latentHeight)
        resized.getPixels(pixels, 0, latentWidth, 0, 0, latentWidth, latentHeight)
        val hw = latentWidth * latentHeight
        val result = FloatArray(LATENT_CHANNELS * hw)
        for (i in 0 until hw) {
            val color = pixels[i]
            val v = (color shr 16 and 0xFF) / 255f
            for (c in 0 until LATENT_CHANNELS) {
                result[i + c * hw] = v
            }
        }
        return result
    }

    private suspend fun performDiffusionStep(
        latents: FloatArray,
        timestep: Int,
        promptEmbedding: FloatArray,
        negativeEmbedding: FloatArray,
        guidanceScale: Float
    ) = withContext(Dispatchers.Default) {
        Logger.d(COMPONENT, "Performing diffusion step at timestep: $timestep")
        Logger.memory(COMPONENT)

        var latentTensor: OnnxTensor? = null
        var timestepTensor: OnnxTensor? = null
        var embeddingTensor: OnnxTensor? = null
        var result: Map<String, OnnxTensor>? = null

        try {
            // Allocate memory for tensors
            val tensorMemory = memoryManager.allocateMemory(
                sizeMB = ModelConfig.MemorySettings.TENSOR_MEMORY_MB,
                type = AllocationType.TENSOR,
                id = "tensor_diffusion_step_$timestep"
            )
            
            if (tensorMemory.isFailure) {
                throw OutOfMemoryError("Failed to allocate memory for tensors")
            }

            // Create tensors for UNet input
            val latentShape = longArrayOf(BATCH_SIZE.toLong() * 2, LATENT_CHANNELS.toLong(), (DEFAULT_HEIGHT / 8).toLong(), (DEFAULT_WIDTH / 8).toLong())
            val timestepShape = longArrayOf(1)
            val embeddingShape = longArrayOf(BATCH_SIZE.toLong() * 2, MAX_TEXT_LENGTH.toLong(), TEXT_EMBEDDING_SIZE.toLong())
            
            Logger.d(COMPONENT, "Creating UNet input tensors")
            
            // Concatenate latents for conditional and unconditional using reusable buffers
            val combinedLatents = TensorUtils.reuseOrCreateBuffer(combinedLatentsBuffer, latents.size * 2)
            for (i in latents.indices) {
                combinedLatents[i] = latents[i]
                combinedLatents[i + latents.size] = latents[i]
            }
            combinedLatentsBuffer = combinedLatents

            val combinedEmbeddings = TensorUtils.reuseOrCreateBuffer(
                combinedEmbeddingsBuffer,
                negativeEmbedding.size + promptEmbedding.size
            )
            System.arraycopy(negativeEmbedding, 0, combinedEmbeddings, 0, negativeEmbedding.size)
            System.arraycopy(promptEmbedding, 0, combinedEmbeddings, negativeEmbedding.size, promptEmbedding.size)
            combinedEmbeddingsBuffer = combinedEmbeddings

            latentTensor = TensorUtils.createInputTensor(combinedLatents, latentShape)
            timestepTensor = TensorUtils.createInputTensor(floatArrayOf(timestep.toFloat()), timestepShape)
            embeddingTensor = TensorUtils.createInputTensor(combinedEmbeddings, embeddingShape)
            
            Logger.d(COMPONENT, "Created UNet input tensors")
            
            // Run UNet inference
            Logger.d(COMPONENT, "Running UNet inference")
            val inputs = mapOf(
                "sample" to latentTensor,
                "timestep" to timestepTensor,
                "encoder_hidden_states" to embeddingTensor
            )
            
            result = unet!!.runInference(inputs)
            val noisePred = result["sample"] ?: throw IllegalStateException("No output tensor found")
            val noiseArray = TensorUtils.tensorToFloatArray(noisePred)
            Logger.d(COMPONENT, "UNet inference completed")
            val splitPoint = noiseArray.size / 2
            Logger.d(COMPONENT, "Applying classifier-free guidance with scale: $guidanceScale")
            val guidedPred = TensorUtils.reuseOrCreateBuffer(guidedPredBuffer, splitPoint)
            for (i in 0 until splitPoint) {
                val uncond = noiseArray[i]
                val cond = noiseArray[i + splitPoint]
                guidedPred[i] = uncond + guidanceScale * (cond - uncond)
            }
            guidedPredBuffer = guidedPred

            // Scheduler step (in-place)
            Logger.d(COMPONENT, "Applying scheduler step")
            scheduler!!.step(guidedPred, timestep, latents)
            Logger.memory(COMPONENT)

            // Free tensor memory
            memoryManager.freeMemory("tensor_diffusion_step_$timestep")
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error in diffusion step", e)
            throw e
        } finally {
            // Cleanup tensors
            latentTensor?.close()
            timestepTensor?.close()
            embeddingTensor?.close()
            result?.values?.forEach { it.close() }
        }
    }

    private suspend fun decodeLatentsToImage(latents: FloatArray, width: Int, height: Int): Bitmap = withContext(Dispatchers.Default) {
        Logger.d(COMPONENT, "Decoding latents to image")
        Logger.memory(COMPONENT)

        var inputTensor: OnnxTensor? = null
        var result: Map<String, OnnxTensor>? = null

        try {
            // Allocate memory for VAE tensors
            val tensorMemory = memoryManager.allocateMemory(
                sizeMB = ModelConfig.MemorySettings.VAE_TENSOR_MEMORY_MB,
                type = AllocationType.TENSOR,
                id = "tensor_vae_decode"
            )
            
            if (tensorMemory.isFailure) {
                throw OutOfMemoryError("Failed to allocate memory for VAE tensors")
            }

            // Scale latents for VAE decoder using reusable buffer
            val scaledLatents = TensorUtils.reuseOrCreateBuffer(scaledLatentsBuffer, latents.size)
            for (i in latents.indices) {
                scaledLatents[i] = latents[i] / 0.18215f
            }
            scaledLatentsBuffer = scaledLatents
            
            // Create input tensor for VAE
            val shape = longArrayOf(BATCH_SIZE.toLong(), LATENT_CHANNELS.toLong(), (height / 8).toLong(), (width / 8).toLong())
            inputTensor = TensorUtils.createInputTensor(scaledLatents, shape)
            Logger.d(COMPONENT, "Created VAE input tensor")
            
            // Run VAE decoder
            Logger.d(COMPONENT, "Running VAE decoder inference")
            val inputs = mapOf("latent" to inputTensor)
            result = vaeDecoder!!.runInference(inputs)
            val decodedTensor = result["sample"] ?: throw IllegalStateException("No output tensor found")
            val decodedArray = TensorUtils.tensorToFloatArray(decodedTensor)
            Logger.d(COMPONENT, "VAE decoding completed")
            
            // Convert to bitmap
            Logger.d(COMPONENT, "Converting decoded tensor to bitmap")
            val bitmap = TensorUtils.floatArrayToBitmap(
                decodedArray,
                width,
                height,
                denormalize = true
            )
            Logger.memory(COMPONENT)
            
            // Free tensor memory
            memoryManager.freeMemory("tensor_vae_decode")
            
            bitmap
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error decoding latents", e)
            throw e
        } finally {
            // Cleanup tensors
            inputTensor?.close()
            result?.values?.forEach { it.close() }
        }
    }

    override fun close() {
        cleanup()
    }

    fun cleanup() {
        try {
            Logger.d(COMPONENT, "Cleaning up resources...")
            textEncoder?.close()
            textEncoder = null
            unet?.close()
            unet = null
            vaeDecoder?.close()
            vaeDecoder = null
            scheduler = null
            
            // Free all model memory
            memoryManager.freeMemory("model_total")
            memoryManager.freeMemory("model_text_encoder")
            memoryManager.freeMemory("model_unet")
            memoryManager.freeMemory("model_vae")
            
            Logger.memory(COMPONENT)
            Logger.d(COMPONENT, "Cleanup completed successfully")
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error during cleanup", e)
        }
    }

    private fun createOnnxTensor(data: FloatArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(data), shape)
    }

    private fun runUnetInference(
        latentModelInput: FloatArray,
        timestep: Long,
        encoderHiddenStates: FloatArray
    ): FloatArray {
        val shape = longArrayOf(BATCH_SIZE.toLong(), LATENT_CHANNELS.toLong(), (DEFAULT_HEIGHT / 8).toLong(), (DEFAULT_WIDTH / 8).toLong())
        val latentTensor = TensorUtils.createInputTensor(latentModelInput, shape)
        val timestepTensor = TensorUtils.createInputTensor(floatArrayOf(timestep.toFloat()), longArrayOf(1))
        val encoderTensor = TensorUtils.createInputTensor(encoderHiddenStates, longArrayOf(BATCH_SIZE.toLong(), 77, 768))

        Logger.d(COMPONENT, "Running UNet inference")
        val inputs = mapOf(
            "sample" to latentTensor,
            "timestep" to timestepTensor,
            "encoder_hidden_states" to encoderTensor
        )
        
        val result = unet!!.runInference(inputs)
        val outputTensor = result["out_sample"] ?: throw IllegalStateException("No UNet output tensor found")
        val output = outputTensor.floatBuffer.array()
        
        // Cleanup
        latentTensor.close()
        timestepTensor.close()
        encoderTensor.close()
        result.values.forEach { it.close() }
        
        return output
    }

    private fun runVaeInference(sample: FloatArray): FloatArray {
        val shape = longArrayOf(BATCH_SIZE.toLong(), LATENT_CHANNELS.toLong(), (DEFAULT_HEIGHT / 8).toLong(), (DEFAULT_WIDTH / 8).toLong())
        val inputTensor = TensorUtils.createInputTensor(sample, shape)
        
        Logger.d(COMPONENT, "Running VAE inference")
        val inputs = mapOf("latent" to inputTensor)
        val result = vaeDecoder!!.runInference(inputs)
        val decodedTensor = result["sample"] ?: throw IllegalStateException("No VAE output tensor found")
        val output = decodedTensor.floatBuffer.array()
        
        // Cleanup
        inputTensor.close()
        result.values.forEach { it.close() }
        
        return output
    }

    private fun runTextEncoderInference(inputIds: LongArray): FloatArray {
        val shape = longArrayOf(BATCH_SIZE.toLong(), 77)
        val inputTensor = TensorUtils.createInputTensor(inputIds.map { it.toFloat() }.toFloatArray(), shape)
        
        Logger.d(COMPONENT, "Running text encoder inference")
        val inputs = mapOf("input_ids" to inputTensor)
        val result = textEncoder!!.runInference(inputs)
        val encodedTensor = result["last_hidden_state"] ?: throw IllegalStateException("No encoder output tensor found")
        val output = encodedTensor.floatBuffer.array()
        
        // Cleanup
        inputTensor.close()
        result.values.forEach { it.close() }
        
        return output
    }

    private fun logModelInfo() {
        session?.let { currentSession ->
            Logger.d(COMPONENT, "Model Info:")
            currentSession.inputInfo.forEach { (name, info) ->
                val tensorInfo = info.info as TensorInfo
                Logger.d(COMPONENT, "Input '$name' - Type: ${tensorInfo.type}, Shape: ${tensorInfo.shape?.contentToString()}")
            }
            currentSession.outputInfo.forEach { (name, info) ->
                val tensorInfo = info.info as TensorInfo
                Logger.d(COMPONENT, "Output '$name' - Type: ${tensorInfo.type}, Shape: ${tensorInfo.shape?.contentToString()}")
            }
        }
    }

    class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
    class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
} 
