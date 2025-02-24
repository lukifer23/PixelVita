package com.example.androiddiffusion.data

import androidx.room.*

@Dao
interface DiffusionModelDao {
    @Query("SELECT * FROM diffusion_models")
    suspend fun getAllModels(): List<DiffusionModel>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: DiffusionModel)
    
    @Update
    suspend fun updateModel(model: DiffusionModel)
    
    @Delete
    suspend fun deleteModel(model: DiffusionModel)
    
    @Query("SELECT * FROM diffusion_models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): DiffusionModel?
} 