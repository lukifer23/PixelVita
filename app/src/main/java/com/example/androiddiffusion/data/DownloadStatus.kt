package com.example.androiddiffusion.data

sealed class DownloadStatus {
    data class Downloading(val progress: Int) : DownloadStatus()
    object Completed : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
} 