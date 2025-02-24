package com.example.androiddiffusion

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase

import com.example.androiddiffusion.data.AppDatabase
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.ModelType
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.config.MemoryManager
import com.example.androiddiffusion.ml.diffusers.DiffusersPipeline
import com.example.androiddiffusion.util.ActivityLifecycleCallback
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.util.MemoryLeakDetector
import com.example.androiddiffusion.util.NativeMemoryManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DiffusionApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    @Inject
    lateinit var diffusersPipeline: DiffusersPipeline
    
    @Inject
    lateinit var memoryManager: MemoryManager
    
    @Inject
    lateinit var logger: Logger
    
    @Inject
    lateinit var memoryLeakDetector: MemoryLeakDetector
    
    @Inject
    lateinit var nativeMemoryManager: NativeMemoryManager
    
    private lateinit var _database: AppDatabase
    
    companion object {
        private const val TAG = "DiffusionApplication"
        private var instance: DiffusionApplication? = null
        
        fun getInstance(): DiffusionApplication = instance
            ?: throw IllegalStateException("Application not initialized")
        
        fun getAppContext(): Context = getInstance().applicationContext
        
        fun getDatabase(): AppDatabase = getInstance()._database
        fun getMemoryManager(): MemoryManager = getInstance().memoryManager
        fun getMemoryLeakDetector(): MemoryLeakDetector = getInstance().memoryLeakDetector
    }
    
    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.enableDefaults()
        }
        
        super.onCreate()
        instance = this
        
        // Initialize logger first to capture all memory operations
        Logger.setDebugEnabled(true)
        Logger.initialize(this)
        Logger.d(TAG, "=== Application Initialization Started ===")
        
        try {
            Logger.d(TAG, "Attempting to configure VM runtime for larger heap...")
            val vmRuntime = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntime.getDeclaredMethod("getRuntime")
            val runtime = getRuntime.invoke(null)
            
            // Configure heap growth and GC thresholds
            Logger.d(TAG, "Setting aggressive heap configuration...")
            
            // Set very low utilization to prevent premature GC
            vmRuntime.getDeclaredMethod("setTargetHeapUtilization", Float::class.java)
                .invoke(runtime, 0.05f)
            Logger.d(TAG, "Set target heap utilization to 5%")
            
            // Try to get the maximum possible heap size
            val maxPossibleHeap = 2048L * 1024L * 1024L // Try for 2GB
            Logger.d(TAG, "Requesting maximum heap size: ${maxPossibleHeap / (1024 * 1024)}MB")
            
            vmRuntime.getDeclaredMethod("setMinimumHeapSize", Long::class.java)
                .invoke(runtime, maxPossibleHeap)
            
            // Request large heap flag
            vmRuntime.getDeclaredMethod("requestHeapTrim").invoke(runtime)
            Logger.d(TAG, "Requested heap trim")
            
            // Force multiple GC passes with logging
            repeat(3) { iteration ->
                Logger.d(TAG, "=== GC Pass ${iteration + 1} ===")
                logBasicMemoryState("Before GC ${iteration + 1}")
                System.gc()
                Runtime.getRuntime().gc()
                Thread.sleep(500) // Give more time for GC
                logBasicMemoryState("After GC ${iteration + 1}")
            }
            
            // Try to grow the heap immediately
            vmRuntime.getDeclaredMethod("trimHeap").invoke(runtime)
            Thread.sleep(200)
            Logger.d(TAG, "Attempted heap trim")
            
            // Verify heap configuration
            val newMaxMemory = Runtime.getRuntime().maxMemory()
            val newTotalMemory = Runtime.getRuntime().totalMemory()
            Logger.d(TAG, "=== Heap Configuration Results ===")
            Logger.d(TAG, "Requested: ${maxPossibleHeap / (1024 * 1024)}MB")
            Logger.d(TAG, "Achieved Max: ${newMaxMemory / (1024 * 1024)}MB")
            Logger.d(TAG, "Current Total: ${newTotalMemory / (1024 * 1024)}MB")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to configure heap size", e)
            Logger.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\\n")}")
        }
        
        // Initialize components with detailed logging
        initializeComponentsWithLogging()
        
        // After Hilt has injected dependencies, we can initialize memory management
        setupMemoryManagementWithLogging()
        registerActivityCallbacks()
        
        // Initialize memory manager with logging
        Logger.d(TAG, "Initializing memory manager...")
        memoryManager.initialize()
        
        // Initialize default models without loading them
        initializeDefaultModels()
        
        // Now that everything is initialized, we can log the full memory state
        if (::nativeMemoryManager.isInitialized) {
            logDetailedMemoryState("Final")
        } else {
            logBasicMemoryState("Final")
        }
        
        Logger.d(TAG, "=== Application Initialization Completed ===")
    }
    
    private fun logBasicMemoryState(phase: String) {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        Logger.d(TAG, "=== Basic Memory State: $phase ===")
        Logger.d(TAG, "Java Heap:")
        Logger.d(TAG, "- Max Memory: ${maxMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Total Memory: ${totalMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Free Memory: ${freeMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Used Memory: ${usedMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Usage: ${(usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()}%")
    }
    
    private fun logDetailedMemoryState(phase: String) {
        if (!::nativeMemoryManager.isInitialized) {
            logBasicMemoryState(phase)
            return
        }
        
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val nativeMemory = nativeMemoryManager.getAvailableMemoryMB()
        
        Logger.d(TAG, "=== Detailed Memory State: $phase ===")
        Logger.d(TAG, "Java Heap:")
        Logger.d(TAG, "- Max Memory: ${maxMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Total Memory: ${totalMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Free Memory: ${freeMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Used Memory: ${usedMemory / (1024 * 1024)}MB")
        Logger.d(TAG, "- Usage: ${(usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()}%")
        
        Logger.d(TAG, "Native Memory:")
        Logger.d(TAG, "- Available: ${nativeMemory}MB")
        
        Logger.d(TAG, "Memory Manager State:")
        Logger.d(TAG, "- Available: ${memoryManager.getAvailableMemory()}MB")
        Logger.d(TAG, "- Total: ${memoryManager.getTotalMemory()}MB")
        Logger.d(TAG, "- Limit: ${memoryManager.getEffectiveMemoryLimit()}MB")
    }
    
    private fun initializeComponentsWithLogging() {
        Logger.d(TAG, "=== Initializing Components ===")
        
        Logger.d(TAG, "Setting up Room database...")
        _database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "diffusion_db"
        ).apply {
            fallbackToDestructiveMigration()
            enableMultiInstanceInvalidation()
            setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        }.build()
        Logger.d(TAG, "Room database initialized")
        
        logDetailedMemoryState("After Database Init")
    }
    
    private fun setupMemoryManagementWithLogging() {
        Logger.d(TAG, "=== Setting up Memory Management ===")
        
        // Configure aggressive memory management
        memoryManager.apply {
            Logger.d(TAG, "Configuring memory thresholds:")
            setLowMemoryThreshold(0.2f)
            Logger.d(TAG, "- Low memory threshold: 20%")
            setCriticalMemoryThreshold(0.1f)
            Logger.d(TAG, "- Critical memory threshold: 10%")
            setTargetMemoryUtilization(0.7f)
            Logger.d(TAG, "- Target utilization: 70%")
            enableAggressiveGC(true)
            Logger.d(TAG, "Aggressive GC enabled")
        }
        
        logDetailedMemoryState("After Memory Management Setup")
    }
    
    private fun initializeDefaultModels() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                Logger.d(TAG, "=== Starting Default Models Initialization ===")
                
                // Check if assets exist
                val assetModelsDir = "models/sd35_medium"
                val requiredFiles = listOf(
                    "$assetModelsDir/text_encoder.onnx",
                    "$assetModelsDir/unet.onnx",
                    "$assetModelsDir/vae_decoder.onnx",
                    "$assetModelsDir/model_config.json"
                )
                
                Logger.d(TAG, "Checking for required model files in assets directory: $assetModelsDir")
                
                // Verify all required files exist in assets
                val allFilesExist = requiredFiles.all { fileName ->
                    try {
                        applicationContext.assets.open(fileName).use { 
                            Logger.d(TAG, "Found model file: $fileName")
                            it.close() 
                        }
                        true
                    } catch (e: Exception) {
                        Logger.e(TAG, "Missing required model file: $fileName", e)
                        false
                    }
                }
                
                if (!allFilesExist) {
                    Logger.e(TAG, "Missing required model files in assets")
                    return@launch
                }
                
                Logger.d(TAG, "All required model files found in assets")
                
                val existingModels = _database.diffusionModelDao().getAllModels().first()
                Logger.d(TAG, "Found ${existingModels.size} existing models in database")
                
                // Create pre-installed model entry
                val preInstalledModel = DiffusionModel(
                    id = "sd35m",
                    name = "Stable Diffusion 3.5 Medium",
                    description = "A medium-sized version of Stable Diffusion 3.5 for efficient text-to-image generation.",
                    downloadUrl = "",  // Pre-installed, no download needed
                    localPath = assetModelsDir,
                    size = (ModelConfig.MODELS["sd35m_text_encoder"]?.size ?: 0L) +
                          (ModelConfig.MODELS["sd35m_unet"]?.size ?: 0L) +
                          (ModelConfig.MODELS["sd35m_vae"]?.size ?: 0L),
                    isDownloaded = true, // Mark as pre-installed
                    type = ModelType.STABLE_DIFFUSION,
                    version = "3.5",
                    quantization = ModelConfig.MODELS["sd35m_text_encoder"]?.quantization,
                    checksum = ""
                )
                
                Logger.d(TAG, "Created pre-installed model entry:")
                Logger.d(TAG, "- ID: ${preInstalledModel.id}")
                Logger.d(TAG, "- Name: ${preInstalledModel.name}")
                Logger.d(TAG, "- Path: ${preInstalledModel.localPath}")
                Logger.d(TAG, "- Size: ${preInstalledModel.size}")
                
                // Always ensure the pre-installed model is in the database
                val existingModel = existingModels.firstOrNull { it.id == preInstalledModel.id }
                if (existingModel == null) {
                    Logger.d(TAG, "Inserting new pre-installed model into database")
                    _database.diffusionModelDao().insertModel(preInstalledModel)
                } else {
                    Logger.d(TAG, "Updating existing pre-installed model in database")
                    _database.diffusionModelDao().updateModel(preInstalledModel.copy(
                        isDownloaded = true,
                        localPath = assetModelsDir
                    ))
                }
                
                Logger.d(TAG, "=== Default Models Initialization Complete ===")
            } catch (e: Exception) {
                Logger.e(TAG, "=== Error Initializing Default Models ===")
                Logger.e(TAG, "Error type: ${e.javaClass.simpleName}")
                Logger.e(TAG, "Error message: ${e.message}")
                Logger.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            }
        }
    }
    
    private fun registerActivityCallbacks() {
        registerActivityLifecycleCallbacks(ActivityLifecycleCallback(
            onTrimMemory = { level ->
                Logger.d(TAG, "Trim memory level: $level")
                memoryManager.handleTrimMemory(level)
            },
            onLowMemory = {
                Logger.d(TAG, "Low memory")
                memoryManager.handleLowMemory()
            }
        ))
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        memoryManager.handleTrimMemory(level)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        memoryManager.handleLowMemory()
    }
} 