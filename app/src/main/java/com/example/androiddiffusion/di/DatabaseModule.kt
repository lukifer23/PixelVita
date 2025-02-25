package com.example.androiddiffusion.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.ModelType
import com.example.androiddiffusion.data.dao.DiffusionModelDao
import com.example.androiddiffusion.data.db.AppDatabase
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.util.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import javax.inject.Singleton

private const val TAG = "DatabaseModule"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val databaseScope = MainScope()
    private var isInitializing = false

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        Logger.d(TAG, "Starting database initialization")
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "diffusion_db"
        )
        .fallbackToDestructiveMigration()
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)  // Use WAL mode instead of TRUNCATE
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Logger.d(TAG, "Database onCreate called - first time initialization")
                if (!isInitializing) {
                    isInitializing = true
                    databaseScope.launch {
                        try {
                            Logger.d(TAG, "Starting model initialization in onCreate")
                            withContext(Dispatchers.IO) {
                                initializeDefaultModel(context, provideAppDatabase(context))
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to initialize default model in onCreate", e)
                        } finally {
                            isInitializing = false
                        }
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Logger.d(TAG, "Database onOpen called - checking model initialization")
                if (!isInitializing) {
                    isInitializing = true
                    databaseScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val database = provideAppDatabase(context)
                                val modelDao = database.diffusionModelDao()
                                val models = modelDao.getAllModels()
                                Logger.d(TAG, "Found ${models.size} models in database")
                                if (models.isEmpty()) {
                                    Logger.d(TAG, "No models found, initializing default model in onOpen")
                                    initializeDefaultModel(context, database)
                                } else {
                                    Logger.d(TAG, "Models already initialized: ${models.map { it.id }}")
                                }
                            }
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error checking model initialization in onOpen", e)
                        } finally {
                            isInitializing = false
                        }
                    }
                }
            }
        })
        .build()
        .also {
            Logger.d(TAG, "Database initialization completed")
        }
    }

    @Provides
    @Singleton
    fun provideDiffusionModelDao(database: AppDatabase): DiffusionModelDao {
        return database.diffusionModelDao()
    }

    private suspend fun initializeDefaultModel(context: Context, database: AppDatabase) {
        withContext(Dispatchers.IO) {
            try {
                Logger.d(TAG, "Starting model initialization")
                
                // Define model paths
                val assetModelDir = "models/sd35_medium_optimized"
                val modelFiles = listOf(
                    "text_encoder.onnx",
                    "unet.onnx",
                    "vae_decoder.onnx",
                    "model_config.json"
                )

                // Verify assets exist first
                modelFiles.forEach { fileName ->
                    try {
                        context.assets.open("$assetModelDir/$fileName").use { }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Missing required model file: $fileName", e)
                        throw IOException("Missing required model file: $fileName")
                    }
                }

                // Create model directory in app's files directory
                val modelDir = File(context.filesDir, "models/sd35_medium_optimized").apply {
                    if (!exists() && !mkdirs()) {
                        throw IOException("Failed to create model directory: $absolutePath")
                    }
                }
                Logger.d(TAG, "Created/verified model directory: ${modelDir.absolutePath}")

                // Calculate total size and copy files
                var totalSize = 0L
                modelFiles.forEach { fileName ->
                    val destFile = File(modelDir, fileName)
                    if (!destFile.exists()) {
                        Logger.d(TAG, "Copying $fileName to ${destFile.absolutePath}")
                        try {
                            context.assets.open("$assetModelDir/$fileName").use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                    output.fd.sync() // Ensure write is complete
                                }
                            }
                            totalSize += destFile.length()
                            Logger.d(TAG, "Successfully copied $fileName (${destFile.length()} bytes)")
                        } catch (e: Exception) {
                            Logger.e(TAG, "Failed to copy $fileName", e)
                            // Clean up any partially copied files
                            destFile.delete()
                            throw e
                        }
                    } else {
                        Logger.d(TAG, "$fileName already exists at ${destFile.absolutePath}")
                        totalSize += destFile.length()
                    }
                }

                // Verify all files exist and have correct sizes
                val missingFiles = modelFiles.filter { !File(modelDir, it).exists() }
                if (missingFiles.isNotEmpty()) {
                    throw IOException("Missing model files after copy: $missingFiles")
                }

                // Create or update model entry in database
                val defaultModel = DiffusionModel(
                    id = "sd35m",
                    name = "Stable Diffusion 3.5 Medium",
                    description = "Optimized INT8 quantized version of SD 3.5 Medium for efficient mobile inference",
                    type = ModelType.STABLE_DIFFUSION,
                    version = "3.5",
                    size = totalSize,
                    downloadUrl = null,
                    isDownloaded = true,
                    localPath = modelDir.absolutePath,
                    quantization = ModelConfig.MODELS["sd35m"]?.quantization
                )

                // Update database
                val modelDao = database.diffusionModelDao()
                val existingModel = modelDao.getModelById(defaultModel.id)
                if (existingModel == null) {
                    Logger.d(TAG, "Inserting new model entry")
                    modelDao.insertModel(defaultModel)
                } else {
                    Logger.d(TAG, "Updating existing model entry")
                    modelDao.updateModel(defaultModel.copy(
                        isDownloaded = true,
                        localPath = modelDir.absolutePath,
                        size = totalSize
                    ))
                }

                Logger.d(TAG, "Model initialization completed successfully")
            } catch (e: Exception) {
                Logger.e(TAG, "Error initializing model", e)
                throw e
            }
        }
    }
}
