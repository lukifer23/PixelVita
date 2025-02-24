package com.example.androiddiffusion.repository

import com.example.androiddiffusion.data.DiffusionModel

interface ModelRepository {
    suspend fun getModels(): List<DiffusionModel>
    suspend fun downloadModel(model: DiffusionModel, onProgress: (Int) -> Unit)
    suspend fun deleteModel(model: DiffusionModel)
    suspend fun getModel(id: String): DiffusionModel?
    suspend fun updateModel(model: DiffusionModel)
} 