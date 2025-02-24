package com.example.androiddiffusion.data.db.converters

import androidx.room.TypeConverter
import com.example.androiddiffusion.config.QuantizationInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class QuantizationInfoConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromQuantizationInfo(value: QuantizationInfo?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toQuantizationInfo(value: String?): QuantizationInfo? {
        return value?.let {
            val type = object : TypeToken<QuantizationInfo>() {}.type
            gson.fromJson(it, type)
        }
    }
} 