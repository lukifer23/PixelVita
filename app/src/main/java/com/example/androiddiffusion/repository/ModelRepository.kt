package com.example.androiddiffusion.repository

import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.dao.ModelDao
import com.example.androiddiffusion.data.ModelType
import com.example.androiddiffusion.config.ModelConfig
import com.example.androiddiffusion.network.ModelApi
import com.example.androiddiffusion.util.FileManager
import com.example.androiddiffusion.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException

@Singleton
class ModelRepository @Inject constructor(
    private val modelDao: ModelDao,
    private val modelApi: ModelApi,
    private val fileManager: FileManager
) {

    companion object {
        private const val COMPONENT = "ModelRepository"
        private const val ASSET_MODEL_PATH = "models/sd35_medium_optimized"
    }

    suspend fun getModels(): List<DiffusionModel> = withContext(Dispatchers.IO) {
        try {
            var models = modelDao.getAllModels()
            
            // If no models exist, initialize with pre-installed model
            if (models.isEmpty()) {
                Logger.d(COMPONENT, "No models found, initializing pre-installed model")
                initializePreInstalledModel()
                models = modelDao.getAllModels()
            }

            // Verify pre-installed model files exist
            models.forEach { model ->
                if (model.localPath?.startsWith(ASSET_MODEL_PATH) == true) {
                    verifyAssetModelFiles(model)
                }
            }

            Logger.d(COMPONENT, "Returning ${models.size} models")
            models
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error getting models", e)
            throw e
        }
    }

    private suspend fun initializePreInstalledModel() {
        try {
            val preInstalledModel = DiffusionModel(
                id = "sd35m",
                name = "Stable Diffusion 3.5 Medium",
                description = "Optimized INT8 quantized version of SD 3.5 Medium for efficient mobile inference",
                type = ModelType.STABLE_DIFFUSION,
                version = "3.5",
                size = calculateAssetModelSize(),
                downloadUrl = null,
                isDownloaded = true,
                localPath = ASSET_MODEL_PATH,
                quantization = ModelConfig.MODELS["sd35m_text_encoder"]?.quantization
            )
            modelDao.insertModel(preInstalledModel)
            Logger.d(COMPONENT, "Pre-installed model initialized: ${preInstalledModel.id}")
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Failed to initialize pre-installed model", e)
            throw e
        }
    }

    private suspend fun verifyAssetModelFiles(model: DiffusionModel) {
        val requiredFiles = listOf(
            "text_encoder.onnx",
            "unet.onnx",
            "vae_decoder.onnx",
            "model_config.json"
        )

        val context = fileManager.getContext()
        requiredFiles.forEach { fileName ->
            val assetPath = "${model.localPath}/$fileName"
            try {
                context.assets.open(assetPath).use { it.close() }
            } catch (e: Exception) {
                Logger.e(COMPONENT, "Missing required model file: $assetPath", e)
                throw IOException("Missing required model file: $fileName")
            }
        }
    }

    private suspend fun calculateAssetModelSize(): Long {
        val context = fileManager.getContext()
        var totalSize = 0L
        val files = context.assets.list(ASSET_MODEL_PATH) ?: return 0L
        
        files.forEach { fileName ->
            try {
                context.assets.open("$ASSET_MODEL_PATH/$fileName").use { input ->
                    totalSize += input.available().toLong()
                }
            } catch (e: Exception) {
                Logger.e(COMPONENT, "Error calculating size for $fileName", e)
            }
        }
        return totalSize
    }

    suspend fun downloadModel(model: DiffusionModel, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val downloadUrl = model.downloadUrl ?: throw IllegalStateException("Model has no download URL")
            val response = modelApi.downloadModel(downloadUrl)
            val localPath = fileManager.saveModelFile(response.body()!!, model.id) { progress ->
                onProgress(progress)
            }
            val updatedModel = model.copy(
                localPath = localPath,
                isDownloaded = true
            )
            modelDao.updateModel(updatedModel)
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun deleteModel(model: DiffusionModel) = withContext(Dispatchers.IO) {
        model.localPath?.let { path ->
            fileManager.deleteFile(path)
        }
        val updatedModel = model.copy(
            localPath = null,
            isDownloaded = false
        )
        modelDao.updateModel(updatedModel)
    }

    suspend fun getModel(id: String): DiffusionModel? = withContext(Dispatchers.IO) {
        modelDao.getModelById(id)
    }

    suspend fun updateModel(model: DiffusionModel) = withContext(Dispatchers.IO) {
        modelDao.updateModel(model)
    }
}
