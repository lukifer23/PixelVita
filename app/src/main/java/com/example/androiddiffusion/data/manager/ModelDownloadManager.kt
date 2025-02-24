package com.example.androiddiffusion.data.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.androiddiffusion.data.DiffusionModel
import com.example.androiddiffusion.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    private val context: Context,
    private val logger: Logger,
    private val connectivityManager: ConnectivityManager
) {
    private val client = OkHttpClient.Builder()
        .build()

    suspend fun downloadModel(model: DiffusionModel, onProgress: suspend (Int) -> Unit) {
        if (!isNetworkAvailable()) {
            throw IOException("No network connection available")
        }

        val downloadUrl = model.downloadUrl ?: throw IllegalStateException("Download URL is missing")
        val tempFile = File(context.cacheDir, "${model.id}.tmp")

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download failed with code: ${response.code}")
                    }

                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()
                    var bytesDownloaded = 0L

                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytes = input.read(buffer)

                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytesDownloaded += bytes

                                val progress = if (contentLength > 0L) {
                                    (bytesDownloaded * 100 / contentLength).toInt()
                                } else 0

                                onProgress(progress)
                                bytes = input.read(buffer)
                            }
                        }
                    }
                }
            }

            // Move temp file to final location
            val modelDir = File(context.getExternalFilesDir(null), "models/${model.id}")
            modelDir.mkdirs()

            val finalFile = File(modelDir, "model.onnx")
            if (!tempFile.renameTo(finalFile)) {
                throw IOException("Failed to save downloaded model")
            }

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
} 