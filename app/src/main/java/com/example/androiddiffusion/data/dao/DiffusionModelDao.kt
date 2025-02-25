package com.example.androiddiffusion.data.dao

import androidx.room.*
import com.example.androiddiffusion.data.DiffusionModel
import kotlinx.coroutines.flow.Flow

@Dao
interface DiffusionModelDao {
    @Query("SELECT * FROM diffusion_models")
    fun getAllModels(): Flow<List<DiffusionModel>>

    @Query("SELECT * FROM diffusion_models WHERE id = :id")
    suspend fun getModel(id: String): DiffusionModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: DiffusionModel)

    @Update
    suspend fun updateModel(model: DiffusionModel)

    @Delete
    suspend fun deleteModel(model: DiffusionModel)
}
