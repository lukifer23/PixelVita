package com.example.androiddiffusion.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.room.Room
import com.example.androiddiffusion.data.AppDatabase
import com.example.androiddiffusion.data.dao.DiffusionModelDao
import com.example.androiddiffusion.data.ModelRepository
import com.example.androiddiffusion.data.manager.ModelDownloadManager
import com.example.androiddiffusion.config.MemoryManager
import com.example.androiddiffusion.util.NativeMemoryManager
import com.example.androiddiffusion.ml.diffusers.DiffusersPipeline
import com.example.androiddiffusion.ml.diffusers.TextTokenizer
import com.example.androiddiffusion.util.ImageManager
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.util.PermissionHelper
import com.example.androiddiffusion.util.MemoryLeakDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }
    
    @Provides
    @Singleton
    fun provideLogger(): Logger {
        return Logger()
    }
    
    @Provides
    @Singleton
    fun provideMemoryManager(
        @ApplicationContext context: Context
    ): MemoryManager {
        return MemoryManager(context)
    }

    @Provides
    @Singleton
    fun provideNativeMemoryManager(
        @ApplicationContext context: Context
    ): NativeMemoryManager {
        return NativeMemoryManager(context)
    }
    
    @Provides
    @Singleton
    fun provideTextTokenizer(
        @ApplicationContext context: Context
    ): TextTokenizer {
        return TextTokenizer(context)
    }
    
    @Provides
    @Singleton
    fun provideDiffusersPipeline(
        @ApplicationContext context: Context,
        memoryManager: MemoryManager,
        nativeMemoryManager: NativeMemoryManager,
        tokenizer: TextTokenizer
    ): DiffusersPipeline {
        return DiffusersPipeline(
            context = context,
            memoryManager = memoryManager,
            nativeMemoryManager = nativeMemoryManager,
            tokenizer = tokenizer
        )
    }
    
    @Provides
    @Singleton
    fun providePermissionHelper(
        @ApplicationContext context: Context
    ): PermissionHelper {
        return PermissionHelper(context)
    }
    
    @Provides
    @Singleton
    fun provideModelDownloadManager(
        @ApplicationContext context: Context,
        logger: Logger,
        connectivityManager: ConnectivityManager
    ): ModelDownloadManager {
        return ModelDownloadManager(context, logger, connectivityManager)
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(
        @ApplicationContext context: Context
    ): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideMemoryLeakDetector(
        logger: Logger
    ): MemoryLeakDetector {
        return MemoryLeakDetector(logger)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "diffusion_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDiffusionModelDao(database: AppDatabase): DiffusionModelDao {
        return database.diffusionModelDao()
    }

    @Provides
    @Singleton
    fun provideModelRepository(
        diffusionModelDao: DiffusionModelDao
    ): ModelRepository {
        return ModelRepository(diffusionModelDao)
    }

    @Provides
    @Singleton
    fun provideImageManager(
        @ApplicationContext context: Context
    ): ImageManager {
        return ImageManager(context)
    }
} 