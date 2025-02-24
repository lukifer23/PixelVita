package com.example.androiddiffusion.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.core.graphics.scale
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.example.androiddiffusion.config.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    const val MODEL_INPUT_SIZE = 512
    
    suspend fun loadAndResizeImage(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val imageLoader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(Size(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE))
            .allowHardware(false) // Required for pixel manipulation
            .build()
            
        val result = imageLoader.execute(request)
        (result.drawable as? BitmapDrawable)?.bitmap 
            ?: throw IllegalStateException("Failed to load image")
    }
    
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (bitmap.width == targetWidth && bitmap.height == targetHeight) {
            return bitmap
        }
        
        val width = bitmap.width
        val height = bitmap.height
        
        val scaleWidth = targetWidth.toFloat() / width
        val scaleHeight = targetHeight.toFloat() / height
        val scale = scaleWidth.coerceAtMost(scaleHeight)
        
        val matrix = Matrix().apply {
            postScale(scale, scale)
        }
        
        val resized = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            width,
            height,
            matrix,
            true
        )
        
        if (resized != bitmap) {
            bitmap.recycle()
        }
        
        return resized
    }
    
    fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return FloatArray(pixels.size * 3) { i ->
            val pixel = pixels[i / 3]
            when (i % 3) {
                0 -> ((pixel shr 16 and 0xFF) / 255f) * 2f - 1f // R
                1 -> ((pixel shr 8 and 0xFF) / 255f) * 2f - 1f  // G
                2 -> ((pixel and 0xFF) / 255f) * 2f - 1f        // B
                else -> throw IllegalStateException()
            }
        }
    }
    
    fun floatArrayToBitmap(floatArray: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = ((floatArray[i * 3] + 1f) / 2f * 255f).toInt().coerceIn(0, 255)
            val g = ((floatArray[i * 3 + 1] + 1f) / 2f * 255f).toInt().coerceIn(0, 255)
            val b = ((floatArray[i * 3 + 2] + 1f) / 2f * 255f).toInt().coerceIn(0, 255)
            pixels[i] = 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }
        
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
    
    suspend fun saveBitmap(bitmap: Bitmap, file: File) = withContext(Dispatchers.IO) {
        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            FileOutputStream(file).use { fos ->
                fos.write(bos.toByteArray())
                fos.flush()
            }
        }
    }
    
    fun recycleBitmapQuietly(bitmap: Bitmap?) {
        try {
            bitmap?.recycle()
        } catch (e: Exception) {
            // Ignore recycling errors
        }
    }
    
    inline fun <T> withBitmap(bitmap: Bitmap, block: (Bitmap) -> T): T {
        try {
            return block(bitmap)
        } finally {
            recycleBitmapQuietly(bitmap)
        }
    }

    fun processImage(bitmap: Bitmap): Bitmap {
        val targetSize = ModelConfig.InferenceSettings.DEFAULT_IMAGE_SIZE
        return if (bitmap.width != targetSize || bitmap.height != targetSize) {
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        } else {
            bitmap
        }
    }

    fun normalizeImage(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        return FloatArray(3 * bitmap.width * bitmap.height).apply {
            var index = 0
            pixels.forEach { pixel ->
                this[index++] = Color.red(pixel) / 127.5f - 1f
                this[index++] = Color.green(pixel) / 127.5f - 1f
                this[index++] = Color.blue(pixel) / 127.5f - 1f
            }
        }
    }

    fun denormalizeImage(floatArray: FloatArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        
        var index = 0
        for (i in pixels.indices) {
            val r = ((floatArray[index++] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((floatArray[index++] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((floatArray[index++] + 1f) * 127.5f).toInt().coerceIn(0, 255)
            pixels[i] = Color.rgb(r, g, b)
        }
        
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    fun cropImage(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }
} 