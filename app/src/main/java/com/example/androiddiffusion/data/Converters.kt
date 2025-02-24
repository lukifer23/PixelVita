package com.example.androiddiffusion.data

import androidx.room.TypeConverter
import com.example.androiddiffusion.config.QuantizationInfo
import com.example.androiddiffusion.config.QuantizationType
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.example.androiddiffusion.data.ModelType

class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromModelType(value: ModelType): String = value.name
    
    @TypeConverter
    fun toModelType(value: String): ModelType = enumValueOf(value)
    
    @TypeConverter
    fun fromQuantizationInfo(value: QuantizationInfo?): String? {
        return value?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toQuantizationInfo(value: String?): QuantizationInfo? {
        return value?.let {
            try {
                val jsonObject = gson.fromJson(it, JsonObject::class.java)
                val type = jsonObject.get("type")?.asString?.let { typeName ->
                    try {
                        enumValueOf<QuantizationType>(typeName)
                    } catch (e: IllegalArgumentException) {
                        QuantizationType.NONE
                    }
                } ?: QuantizationType.NONE
                
                val originalSize = jsonObject.get("originalSize")?.asLong ?: 0L
                
                QuantizationInfo(type = type, originalSize = originalSize)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    @TypeConverter
    fun fromQuantizationType(value: QuantizationType): String = value.name
    
    @TypeConverter
    fun toQuantizationType(value: String): QuantizationType = enumValueOf(value)
} 