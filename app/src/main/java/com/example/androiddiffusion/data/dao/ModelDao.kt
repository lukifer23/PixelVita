package com.example.androiddiffusion.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.androiddiffusion.data.DiffusionModel

@Dao
interface ModelDao {
    @Query("SELECT * FROM diffusion_models")
    suspend fun getAllModels(): List<DiffusionModel>

    @Query("SELECT * FROM diffusion_models WHERE id = :id")
    suspend fun getModelById(id: String): DiffusionModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: DiffusionModel)

    @Update
    suspend fun updateModel(model: DiffusionModel)

    @Delete
    suspend fun deleteModel(model: DiffusionModel)
} 