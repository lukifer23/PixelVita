package com.example.androiddiffusion.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.androiddiffusion.config.QuantizationInfo
import com.example.androiddiffusion.config.QuantizationType
import com.example.androiddiffusion.data.ModelType

@Entity(tableName = "diffusion_models")
data class DiffusionModel(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String?,
    val localPath: String?,
    val size: Long,
    val isDownloaded: Boolean = false,
    val type: ModelType = ModelType.STABLE_DIFFUSION,
    val version: String,
    val quantization: QuantizationInfo? = null,
    val checksum: String = ""
)

data class QuantizationInfo(
    val type: QuantizationType,
    val originalSize: Long = 0L
) 