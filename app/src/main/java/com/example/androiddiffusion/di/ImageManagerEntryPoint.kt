package com.example.androiddiffusion.di

import com.example.androiddiffusion.util.ImageManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageManagerEntryPoint {
    fun imageManager(): ImageManager
} 