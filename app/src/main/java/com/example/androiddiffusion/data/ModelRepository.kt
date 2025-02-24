package com.example.androiddiffusion.data

import com.example.androiddiffusion.data.dao.DiffusionModelDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val diffusionModelDao: DiffusionModelDao
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
} 