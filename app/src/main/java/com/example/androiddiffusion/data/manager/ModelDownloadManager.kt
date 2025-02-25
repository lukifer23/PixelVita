package com.example.androiddiffusion.data.manager

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.androiddiffusion.R
import com.example.androiddiffusion.DiffusionApplication
import com.example.androiddiffusion.util.DownloadException
import com.example.androiddiffusion.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

sealed class DownloadError : Exception() {
    data class NetworkError(override val message: String, override val cause: Throwable? = null) : DownloadError()
    data class StorageError(override val message: String, override val cause: Throwable? = null) : DownloadError()
    data class ValidationError(override val message: String, override val cause: Throwable? = null) : DownloadError()
    data class TimeoutError(override val message: String, override val cause: Throwable? = null) : DownloadError()
    data class InvalidModelError(override val message: String, override val cause: Throwable? = null) : DownloadError()
}

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger,
    private val connectivityManager: ConnectivityManager
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)  // No timeout for the entire call
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            
            // Create a new request with browser-like headers
            val newRequest = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")  // Prevent compression
                .header("Connection", "keep-alive")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Range", "bytes=0-")  // Add range header to support resuming
                .build()
            
            Log.d(TAG, "Making request to: ${newRequest.url}")
            Log.d(TAG, "Request headers: ${newRequest.headers}")
            
            val response = chain.proceed(newRequest)
            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response headers: ${response.headers}")
            
            // If we get a redirect, log the new location
            response.header("Location")?.let { location ->
                Log.d(TAG, "Redirect location: $location")
            }
            
            response
        }
        .build()
    
    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val NOTIFICATION_CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1
        private const val BUFFER_SIZE = 8192
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY = 2000L
        private const val MAX_RETRY_DELAY = 30000L
        private const val MAX_DOWNLOAD_DURATION = 120L * 60L * 1000L // 120 minutes
        private const val DOWNLOAD_TIMEOUT = 10L * 60L * 1000L // 10 minutes
        private const val PROGRESS_UPDATE_INTERVAL = 500L // 500ms
        private const val SYNC_INTERVAL = 10L * 1024L * 1024L // Sync every 10MB
        private const val TEMP_DIR = "temp_downloads"
        private const val COMPONENT = "ModelDownloadManager"
        private const val REQUEST_CODE_STORAGE_PERMISSION = 1001
        private fun log(message: String) = Logger.d(COMPONENT, message)
    }
    
    private val tempDir: File = context.cacheDir
    
    private fun getTempFile(modelId: String): File {
        // Clean any stale temp files
        tempDir.listFiles()?.forEach { file ->
            if (file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                file.delete()
            }
        }
        return File(tempDir, "${modelId}.tmp")
    }
    
    private fun ensureStorageAvailable(requiredBytes: Long): Boolean {
        val stat = StatFs(tempDir.path)
        val availableBytes = stat.availableBytes
        if (availableBytes < requiredBytes + 100 * 1024 * 1024) { // Add 100MB buffer
            throw DownloadError.StorageError("Insufficient storage space. Need ${requiredBytes / 1024 / 1024}MB, have ${availableBytes / 1024 / 1024}MB")
        }
        return true
    }
    
    private var currentActivity: Activity? = null
    private var permissionResultLauncher: ActivityResultLauncher<Intent>? = null

    fun setActivity(activity: Activity) {
        currentActivity = activity
    }

    fun setPermissionResultLauncher(launcher: ActivityResultLauncher<Intent>) {
        permissionResultLauncher = launcher
    }
    
    init {
        createNotificationChannel()
        // Only request notification permission on Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentActivity?.let { activity ->
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_STORAGE_PERMISSION)
                }
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    private fun calculateChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { input ->
            var bytes = input.read(buffer)
            while (bytes >= 0) {
                digest.update(buffer, 0, bytes)
                bytes = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun calculateBackoff(retryCount: Int): Long {
        return min(INITIAL_RETRY_DELAY * (1L shl retryCount), MAX_RETRY_DELAY)
    }
    
    private suspend fun downloadWithRetry(
        model: DiffusionModel,
        tempFile: File,
        notification: NotificationCompat.Builder,
        progressCallback: suspend (Float) -> Unit
    ): Flow<DownloadStatus> = flow {
        if (model.downloadUrl == null) {
            throw IllegalStateException("This model does not support downloading")
        }

        var retryCount = 0
        val startTime = System.currentTimeMillis()
        var lastProgressUpdate = 0L
        
        while (retryCount < MAX_RETRIES) {
            try {
                if (System.currentTimeMillis() - startTime > MAX_DOWNLOAD_DURATION) {
                    throw DownloadError.TimeoutError("Download exceeded maximum duration of 120 minutes")
                }

                if (!isNetworkAvailable()) {
                    throw DownloadError.NetworkError("No network connection available")
                }

                val downloadUrl = model.downloadUrl ?: throw DownloadError.InvalidModelError("Download URL is missing")

                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(downloadUrl)
                        .addHeader("User-Agent", "AndroidDiffusion/1.0")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    
                    if (!response.isSuccessful) {
                        val errorMsg = when (response.code) {
                            404 -> "Model file not found on server (404)"
                            403 -> "Access denied (403)"
                            else -> "Download failed with code: ${response.code}"
                        }
                        throw IOException(errorMsg)
                    }
                    
                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    
                    if (model.size > 0L && contentLength > 0L && contentLength != model.size) {
                        throw IOException("Model size mismatch. Expected: ${model.size}, Actual: $contentLength")
                    }
                    
                    var bytesDownloaded = 0L
                    
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes = input.read(buffer)
                            
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesDownloaded += bytes
                                
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                                    val progress = if (contentLength > 0L) {
                                        bytesDownloaded.toFloat() / contentLength.toFloat()
                                    } else 0f
                                    
                                    lastProgressUpdate = currentTime
                                    withContext(Dispatchers.Main) {
                                        progressCallback(progress)
                                        updateNotification(notification, model.name, (progress * 100).toInt())
                                    }
                                    emit(DownloadStatus.Downloading((progress * 100).toInt()))
                                }
                                
                                bytes = input.read(buffer)
                            }
                        }
                    }

                    // Validate file size
                    if (model.size > 0L && tempFile.length() < model.size) {
                        throw IOException("Downloaded file size is too small. Expected at least: ${model.size}, Got: ${tempFile.length()}")
                    }
                    
                    // Validate checksum if available
                    if (model.checksum.isNotEmpty()) {
                        val downloadedChecksum = calculateChecksum(tempFile)
                        if (downloadedChecksum != model.checksum) {
                            throw IOException("Checksum verification failed")
                        }
                    }
                    
                    // Move temp file to final location
                    val modelDir = File(context.getExternalFilesDir(null), "models").apply {
                        mkdirs()
                    }
                    
                    // Create model-specific directory
                    val baseModelId = model.id.split("_").take(3).joinToString("_") // Get base model ID (e.g., "sd35_medium_turbo")
                    val modelTypeDir = File(modelDir, baseModelId).apply {
                        mkdirs()
                    }
                    
                    // Determine the final filename based on model component
                    val finalFilename = when {
                        model.id.endsWith("_unet") -> "unet.onnx"
                        model.id.endsWith("_vae") -> "vae_decoder.onnx"
                        else -> "text_encoder.onnx"
                    }
                    
                    val finalFile = File(modelTypeDir, finalFilename)
                    
                    if (!tempFile.renameTo(finalFile)) {
                        throw IOException("Failed to save downloaded model")
                    }
                    
                    // Update model in database
                    withContext(Dispatchers.IO) {
                        val updatedModel = model.copy(
                            isDownloaded = true,
                            localPath = finalFile.absolutePath
                        )
                        DiffusionApplication.getDatabase().diffusionModelDao().updateModel(updatedModel)
                    }
                    
                    withContext(Dispatchers.Main) {
                        completeNotification(notification, model.name)
                    }
                    emit(DownloadStatus.Completed)
                }
                break
                
            } catch (e: Exception) {
                val error = when (e) {
                    is TimeoutCancellationException -> 
                        DownloadError.TimeoutError("Download timed out", e)
                    is IOException -> when {
                        e.message?.contains("ENOSPC") == true -> 
                            DownloadError.StorageError("Insufficient storage space", e)
                        e.message?.contains("permission") == true -> 
                            DownloadError.StorageError("Storage permission denied", e)
                        else -> DownloadError.NetworkError("Network error: ${e.message}", e)
                    }
                    is SecurityException -> 
                        DownloadError.StorageError("Storage permission denied", e)
                    else -> e
                }

                retryCount++
                if (retryCount < MAX_RETRIES) {
                    val delayTime = calculateBackoff(retryCount)
                    log("Download attempt $retryCount failed: ${e.message}. Retrying in ${delayTime}ms")
                    withContext(Dispatchers.Default) {
                        kotlinx.coroutines.delay(delayTime)
                    }
                    emit(DownloadStatus.Downloading(-1))
                } else {
                    log("Download failed after $MAX_RETRIES attempts")
                    withContext(Dispatchers.Main) {
                        errorNotification(notification, model.name)
                    }
                    throw error
                }
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private fun hasStoragePermission(): Boolean {
        // On Android 10+ (Q), we don't need external storage permission for app-specific files
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || 
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }
    
    private suspend fun validateDownloadSize(url: String, expectedSize: Long): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .head()  // Use HEAD request to get content length without downloading
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to validate file size: ${response.code}")
        }
        
        response.header("Content-Length")?.toLongOrNull() ?: -1L
    }
    
    private fun parseUrlExpiry(url: String): Long {
        return try {
            if (!url.contains("Expires=")) return 0L
            val expiryPart = url.split("Expires=").getOrNull(1) ?: return 0L
            val expiresParam = expiryPart.split("&").firstOrNull() ?: return 0L
            expiresParam.toLongOrNull()?.times(1000) ?: 0L // Convert to milliseconds
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse URL expiry time", e)
            0L
        }
    }

    private fun isUrlExpired(url: String?): Boolean {
        if (url == null) return false
        return try {
            if (!url.contains("Expires=")) return false
            val expiryPart = url.split("Expires=").getOrNull(1) ?: return false
            val expiresParam = expiryPart.split("&").firstOrNull() ?: return false
            val expiryTime = expiresParam.toLongOrNull()?.times(1000) ?: return false
            System.currentTimeMillis() > expiryTime
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse URL expiry time", e)
            false
        }
    }

    private suspend fun moveFileToFinal(tempFile: File, finalFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ensure parent directories exist
            finalFile.parentFile?.mkdirs()

            // Verify temp file exists and has content
            if (!tempFile.exists()) {
                Log.e(TAG, "Temp file missing before move: ${tempFile.absolutePath}")
                return@withContext false
            }

            val tempSize = tempFile.length()
            if (tempSize == 0L) {
                Log.e(TAG, "Temp file is empty before move: ${tempFile.absolutePath}")
                return@withContext false
            }

            try {
                // Try direct rename first
                if (tempFile.renameTo(finalFile)) {
                    // Verify the move
                    if (!finalFile.exists()) {
                        Log.e(TAG, "Final file missing after rename")
                        return@withContext false
                    }
                    
                    if (finalFile.length() != tempSize) {
                        Log.e(TAG, "Size mismatch after rename. Expected: $tempSize, Got: ${finalFile.length()}")
                        return@withContext false
                    }
                    
                    return@withContext true
                }
                
                Log.w(TAG, "Direct rename failed, trying copy-delete")
                
                // Fallback to copy-delete
                tempFile.inputStream().use { input ->
                    finalFile.outputStream().use { output ->
                        input.copyTo(output)
                        output.fd.sync() // Ensure write is complete
                    }
                }
                
                // Verify copy
                if (!finalFile.exists() || finalFile.length() != tempSize) {
                    Log.e(TAG, "Verification failed after copy. Expected size: $tempSize, Got: ${finalFile.length()}")
                    return@withContext false
                }
                
                // Delete temp file only if copy succeeded
                tempFile.delete()
                return@withContext true
                
            } catch (e: Exception) {
                Log.e(TAG, "File move operation failed", e)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "File move operation failed", e)
            return@withContext false
        }
    }

    private suspend fun downloadModel(model: DiffusionModel): Flow<DownloadStatus> = flow {
        val downloadUrl = model.downloadUrl
        if (downloadUrl == null) {
            emit(DownloadStatus.Error("Download URL is missing"))
            return@flow
        }
        
        val tempFile = getTempFile(model.id)
        val notification = createNotification(model.name)
        
        try {
            Logger.d(COMPONENT, "Starting download process")
            Logger.d(COMPONENT, "Download URL: $downloadUrl")
            Logger.d(COMPONENT, "Expected size: ${model.size} bytes")
            Logger.d(COMPONENT, "Temp file location: ${tempFile.absolutePath}")

            if (!isNetworkAvailable()) {
                emit(DownloadStatus.Error("No network connection available"))
                return@flow
            }

            if (isUrlExpired(downloadUrl)) {
                throw DownloadError.TimeoutError("Download URL has expired")
            }

            emit(DownloadStatus.Downloading(0))
            
            withContext(Dispatchers.IO) {
                // Download to temp file with enhanced progress tracking
                downloadWithProgress(
                    url = downloadUrl,
                    tempFile = tempFile,
                    expectedSize = model.size
                ) { progress ->
                    Logger.d(COMPONENT, "Download progress: $progress%")
                    updateNotification(notification, model.name, progress.toInt())
                    emit(DownloadStatus.Downloading(progress.toInt()))
                }
                
                // Prepare final location with detailed logging
                val modelDir = File(context.getExternalFilesDir(null), "models")
                Logger.d(COMPONENT, "Preparing model directory: ${modelDir.absolutePath}")
                
                // Create model-specific directory based on base model ID
                val baseModelId = model.id.split("_").take(3).joinToString("_") // e.g., "sd35_medium"
                val modelTypeDir = File(modelDir, baseModelId).apply {
                    if (!exists() && !mkdirs()) {
                        throw IOException("Failed to create model directory: $absolutePath")
                    }
                }
                Logger.d(COMPONENT, "Created model type directory: ${modelTypeDir.absolutePath}")
                
                // Determine the final filename based on model component
                val finalFilename = when {
                    model.id.endsWith("_unet") -> "unet.onnx"
                    model.id.endsWith("_vae") -> "vae_decoder.onnx"
                    else -> "text_encoder.onnx"
                }
                
                val finalFile = File(modelTypeDir, finalFilename)
                Logger.d(COMPONENT, "Moving downloaded file to: ${finalFile.absolutePath}")
                
                // Move file to final location with verification
                if (!moveFileToFinal(tempFile, finalFile)) {
                    throw IOException("Failed to move downloaded model to final location")
                }
                
                // Update model in database
                val updatedModel = model.copy(
                    isDownloaded = true,
                    localPath = finalFile.absolutePath
                )
                Logger.d(COMPONENT, "Updating database entry for model: ${model.id}")
                DiffusionApplication.getDatabase().diffusionModelDao().updateModel(updatedModel)
            }
            
            Logger.d(COMPONENT, "Download completed successfully for model: ${model.id}")
            withContext(Dispatchers.Main) {
                completeNotification(notification, model.name)
            }
            emit(DownloadStatus.Completed)
            
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Download failed for model ${model.id}: ${e.message}", e)
            withContext(Dispatchers.Main) {
                errorNotification(notification, model.name)
            }
            // Clean up temp file if it exists
            if (tempFile.exists()) {
                Logger.d(COMPONENT, "Cleaning up temp file: ${tempFile.absolutePath}")
                tempFile.delete()
            }
            throw when (e) {
                is IOException -> DownloadError.StorageError(e.message ?: "Storage operation failed", e)
                is SecurityException -> DownloadError.StorageError("Storage permission denied", e)
                else -> DownloadError.NetworkError(e.message ?: "Download failed", e)
            }
        }
    }.flowOn(Dispatchers.IO)
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of model downloads"
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(modelName: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading $modelName")
            .setProgress(100, 0, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
    }
    
    private fun updateNotification(notification: NotificationCompat.Builder, modelName: String, progress: Int) {
        notification.setContentTitle("Downloading $modelName")
            .setProgress(100, progress, false)
            .setContentText("$progress%")
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }
    
    private fun completeNotification(notification: NotificationCompat.Builder, modelName: String) {
        notification.setContentTitle("Download Complete")
            .setContentText("$modelName has been downloaded")
            .setProgress(0, 0, false)
            .setOngoing(false)
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }
    
    private fun errorNotification(
        notification: NotificationCompat.Builder,
        modelName: String
    ) {
        notification.setContentTitle("Download Failed")
            .setContentText("Failed to download $modelName")
            .setProgress(0, 0, false)
            .setOngoing(false)
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }

    private suspend fun downloadWithProgress(
        url: String,
        tempFile: File,
        expectedSize: Long,
        onProgress: suspend (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        var totalBytesRead = 0L
        var lastLoggedProgress = 0f
        var fileOutput: FileOutputStream? = null
        var bufferedOutput: BufferedOutputStream? = null
        var downloadSuccessful = false

        try {
            // Ensure parent directory exists and is writable
            tempFile.parentFile?.apply {
                if (!exists()) {
                    Logger.d(COMPONENT, "Creating parent directory: $absolutePath")
                    if (!mkdirs()) {
                        throw IOException("Failed to create parent directory: $absolutePath")
                    }
                }
                if (!canWrite()) {
                    throw IOException("Cannot write to parent directory: $absolutePath")
                }
            }

            // Delete existing temp file if it exists
            if (tempFile.exists()) {
                Logger.d(COMPONENT, "Deleting existing temp file: ${tempFile.absolutePath}")
                if (!tempFile.delete()) {
                    throw IOException("Failed to delete existing temp file: ${tempFile.absolutePath}")
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response code: ${response.code}")
                }

                response.body?.let { body ->
                    val contentLength = body.contentLength()
                    Logger.d(COMPONENT, "Content length from server: $contentLength bytes")
                    Logger.d(COMPONENT, "Writing to temp file: ${tempFile.absolutePath}")

                    // Create new file with explicit permissions
                    if (!tempFile.createNewFile()) {
                        throw IOException("Failed to create new temp file: ${tempFile.absolutePath}")
                    }

                    // Set file permissions to ensure writability
                    tempFile.setWritable(true, true)
                    tempFile.setReadable(true, true)

                    fileOutput = FileOutputStream(tempFile)
                    bufferedOutput = BufferedOutputStream(fileOutput, BUFFER_SIZE)

                    body.byteStream().buffered(BUFFER_SIZE).use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var lastSyncTime = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (bytesRead > 0) {
                                bufferedOutput!!.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                // Calculate progress based on the larger of expected and actual size
                                val actualSize = if (contentLength > 0) contentLength else expectedSize
                                val targetSize = maxOf(expectedSize, actualSize)
                                val progress = (totalBytesRead.toFloat() / targetSize) * 100
                                
                                if (progress - lastLoggedProgress >= 1f || progress >= 100f) {
                                    lastLoggedProgress = progress.coerceAtMost(100f)
                                    Logger.d(COMPONENT, "Download progress: ${lastLoggedProgress}% ($totalBytesRead/$targetSize bytes)")
                                    withContext(Dispatchers.Main) {
                                        onProgress(lastLoggedProgress)
                                    }
                                }

                                // Periodically sync to disk
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastSyncTime >= 5000) {
                                    bufferedOutput!!.flush()
                                    fileOutput!!.fd.sync()
                                    lastSyncTime = currentTime
                                    
                                    // Verify file still exists and is growing
                                    if (!tempFile.exists()) {
                                        throw IOException("Temp file disappeared during download: ${tempFile.absolutePath}")
                                    }
                                    
                                    val currentSize = tempFile.length()
                                    if (currentSize < totalBytesRead) {
                                        throw IOException("File size mismatch during download. Written: $totalBytesRead, File size: $currentSize")
                                    }
                                }
                            }
                        }

                        // Final flush and sync
                        bufferedOutput!!.flush()
                        fileOutput!!.fd.sync()

                        // Verify the final file
                        if (!tempFile.exists()) {
                            throw IOException("File does not exist after download: ${tempFile.absolutePath}")
                        }

                        val finalSize = tempFile.length()
                        Logger.d(COMPONENT, "Download completed. Final size: $finalSize bytes")

                        if (finalSize == 0L) {
                            throw IOException("Downloaded file is empty: ${tempFile.absolutePath}")
                        }

                        // Ensure file is fully synced to disk
                        fileOutput!!.fd.sync()
                        
                        // Double check file integrity
                        if (tempFile.exists() && tempFile.length() >= expectedSize) {
                            downloadSuccessful = true
                            Logger.d(COMPONENT, "Download completed successfully")
                        } else {
                            throw IOException("Final file verification failed")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(COMPONENT, "Error during download: ${e.message}", e)
            try {
                bufferedOutput?.close()
                fileOutput?.close()
            } catch (closeException: Exception) {
                Logger.e(COMPONENT, "Error closing streams: ${closeException.message}", closeException)
            }
            throw e
        } finally {
            try {
                bufferedOutput?.close()
                fileOutput?.close()
            } catch (closeException: Exception) {
                Logger.e(COMPONENT, "Error closing streams: ${closeException.message}", closeException)
            }
            
            // Only delete the temp file if the download was not successful
            if (!downloadSuccessful && tempFile.exists()) {
                Logger.d(COMPONENT, "Cleaning up temp file after failed download: ${tempFile.absolutePath}")
                tempFile.delete()
            }
        }
    }

    private fun getStorageInfo(): String {
        val stat = StatFs(context.getExternalFilesDir(null)?.path ?: return "Storage info unavailable")
        val availableBytes = stat.availableBytes
        val totalBytes = stat.totalBytes
        return "Available: ${formatFileSize(availableBytes)}, Total: ${formatFileSize(totalBytes)}"
    }

    private fun formatFileSize(size: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${df.format(size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024.0))} MB"
            else -> "${df.format(size / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    private fun requestStoragePermission() {
        currentActivity?.let { activity ->
            // On Android 11+ (R), we need to request all files access permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) return
                
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                
                permissionResultLauncher?.launch(intent) ?: run {
                    activity.startActivityForResult(intent, REQUEST_CODE_STORAGE_PERMISSION)
                }
            } else {
                // For Android 10 and below, request traditional storage permission
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_STORAGE_PERMISSION
                )
            }
        } ?: throw DownloadException("Activity reference not available for permission request")
    }

    private fun getModelDownloadRequest(model: DiffusionModel): DownloadManager.Request {
        val downloadUrl = model.downloadUrl ?: throw IllegalStateException("This model does not support downloading")
        return DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("Downloading ${model.name}")
            setDescription("Downloading model file...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "${model.id}.onnx")
        }
    }

    private fun getModelDownloadPath(model: DiffusionModel): String {
        val downloadUrl = model.downloadUrl ?: throw IllegalStateException("This model does not support downloading")
        return Uri.parse(downloadUrl).lastPathSegment ?: "${model.id}.onnx"
    }

    private fun getModelDownloadFileName(model: DiffusionModel): String {
        val downloadUrl = model.downloadUrl ?: throw IllegalStateException("This model does not support downloading")
        return Uri.parse(downloadUrl).lastPathSegment ?: "${model.id}.onnx"
    }

    private fun getModelPath(model: DiffusionModel): String {
        return model.localPath ?: throw IllegalStateException("Model path not set")
    }

    private fun verifyModelFile(model: DiffusionModel): Boolean {
        val modelFile = File(getModelPath(model))
        return modelFile.exists() && modelFile.length() > 0
    }
}
