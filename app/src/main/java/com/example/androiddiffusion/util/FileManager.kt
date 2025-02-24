package com.example.androiddiffusion.util

import android.content.Context
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    private val context: Context
) {
    fun saveModelFile(responseBody: ResponseBody, modelId: String, onProgress: (Int) -> Unit): String {
        val modelDir = File(context.filesDir, "models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        val file = File(modelDir, "$modelId.onnx")
        val totalBytes = responseBody.contentLength()
        var loadedBytes = 0L

        responseBody.byteStream().use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    loadedBytes += bytes
                    val progress = ((loadedBytes.toFloat() / totalBytes) * 100).toInt()
                    onProgress(progress)
                    bytes = inputStream.read(buffer)
                }
            }
        }

        return file.absolutePath
    }

    fun deleteFile(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }

    fun getContext(): Context {
        return context
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192
    }
} 