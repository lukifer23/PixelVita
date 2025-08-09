package com.example.androiddiffusion.data

import android.content.Context
import com.example.androiddiffusion.data.dao.DiffusionModelDao
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import com.example.androiddiffusion.config.QuantizationType
import com.example.androiddiffusion.data.ModelType
import com.example.androiddiffusion.data.QuantizationInfo

@Singleton
class ModelRepository @Inject constructor(
    private val diffusionModelDao: DiffusionModelDao,
    @ApplicationContext private val context: Context
) {
    fun getAllModels(): Flow<List<DiffusionModel>> {
        return diffusionModelDao.getAllModels()
    }

    suspend fun getModel(id: String): DiffusionModel? {
        return diffusionModelDao.getModel(id)
    }

    suspend fun insertModel(model: DiffusionModel) {
        diffusionModelDao.insertModel(model)
    }

    suspend fun updateModel(model: DiffusionModel) {
        diffusionModelDao.updateModel(model)
    }

    suspend fun deleteModel(model: DiffusionModel) {
        diffusionModelDao.deleteModel(model)
    }

    suspend fun scanAvailableModels() {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) return

        modelsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val config = File(dir, "model_config.json")
            if (config.exists()) {
                try {
                    val descriptor = Gson().fromJson(config.readText(), LocalModelConfig::class.java)
                    val quantInfo = descriptor.quantization?.let {
                        QuantizationInfo(QuantizationType.valueOf(it))
                    }
                    val model = DiffusionModel(
                        id = descriptor.id,
                        name = descriptor.name,
                        description = descriptor.description ?: "",
                        downloadUrl = null,
                        localPath = dir.absolutePath,
                        size = dir.walk().filter { f -> f.isFile }.sumOf { f.length() },
                        isDownloaded = true,
                        type = descriptor.type?.let { ModelType.valueOf(it) } ?: ModelType.CUSTOM,
                        version = descriptor.version ?: "",
                        quantization = quantInfo
                    )
                    diffusionModelDao.insertModel(model)
                } catch (_: Exception) {
                    // ignore invalid configs
                }
            }
        }
    }

    private data class LocalModelConfig(
        val id: String,
        val name: String,
        val description: String? = null,
        val version: String? = null,
        val type: String? = null,
        val quantization: String? = null
    )
}