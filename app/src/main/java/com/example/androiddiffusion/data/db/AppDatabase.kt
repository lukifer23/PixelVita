package com.example.androiddiffusion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.data.dao.ModelDao
import com.example.androiddiffusion.data.db.converters.QuantizationInfoConverter

@Database(
    entities = [DiffusionModel::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(QuantizationInfoConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
} 