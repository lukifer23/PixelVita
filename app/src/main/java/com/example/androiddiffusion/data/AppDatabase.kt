package com.example.androiddiffusion.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.androiddiffusion.data.dao.DiffusionModelDao

@Database(
    entities = [DiffusionModel::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diffusionModelDao(): DiffusionModelDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the new checksum column with a default empty string
                db.execSQL(
                    "ALTER TABLE diffusion_models ADD COLUMN checksum TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
} 