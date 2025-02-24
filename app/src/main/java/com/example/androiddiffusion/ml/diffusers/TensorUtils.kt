package com.example.androiddiffusion.ml.diffusers

import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import java.nio.FloatBuffer
import kotlin.math.roundToInt

object TensorUtils {
    private const val TAG = "TensorUtils"

    fun createInputTensor(data: FloatArray, shape: LongArray): OnnxTensor {
        Log.d(TAG, "Creating input tensor with shape: ${shape.contentToString()}")
        return OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(data), shape)
    }

    fun createInputTensor(environment: OrtEnvironment, data: FloatArray, shape: LongArray): OnnxTensor {
        Log.d(TAG, "Creating input tensor with shape: ${shape.contentToString()}")
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), shape)
    }

    fun reuseOrCreateBuffer(existingBuffer: FloatArray?, size: Int): FloatArray {
        return when {
            existingBuffer == null -> FloatArray(size)
            existingBuffer.size >= size -> existingBuffer
            else -> FloatArray(size)
        }
    }

    fun logTensorInfo(tensor: OnnxTensor?, name: String) {
        tensor?.let {
            Log.d(TAG, "Tensor $name - Shape: ${it.info.shape.contentToString()}")
        }
    }

    fun releaseTensor(tensor: OnnxTensor?) {
        try {
            tensor?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing tensor", e)
        }
    }

    fun tensorToFloatArray(tensor: OnnxTensor): FloatArray {
        return tensor.floatBuffer.array()
    }

    fun floatArrayToBitmap(
        array: FloatArray,
        width: Int,
        height: Int,
        denormalize: Boolean = true
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var idx = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = array[idx]
                val g = array[idx + height * width]
                val b = array[idx + 2 * height * width]
                
                val rInt = if (denormalize) normalizeAndClamp(r) else r.toInt()
                val gInt = if (denormalize) normalizeAndClamp(g) else g.toInt()
                val bInt = if (denormalize) normalizeAndClamp(b) else b.toInt()
                
                bitmap.setPixel(x, y, Color.rgb(rInt, gInt, bInt))
                idx++
            }
        }
        
        return bitmap
    }

    private fun normalizeAndClamp(value: Float): Int {
        return ((value + 1f) * 127.5f).toInt().coerceIn(0, 255)
    }

    fun bitmapToFloatArray(bitmap: Bitmap, normalize: Boolean = true): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val floatArray = FloatArray(3 * height * width)
        var idx = 0
        
        for (c in 0 until 3) {
            for (h in 0 until height) {
                for (w in 0 until width) {
                    val pixel = pixels[h * width + w]
                    val value = when (c) {
                        0 -> (pixel shr 16 and 0xFF) // R
                        1 -> (pixel shr 8 and 0xFF)  // G
                        2 -> (pixel and 0xFF)        // B
                        else -> 0
                    }.toFloat()
                    
                    floatArray[idx++] = if (normalize) {
                        (value / 127.5f) - 1f
                    } else {
                        value
                    }
                }
            }
        }
        
        return floatArray
    }

    fun reshapeTensor(
        data: FloatArray,
        fromShape: LongArray,
        toShape: LongArray
    ): FloatArray {
        require(fromShape.reduce(Long::times) == toShape.reduce(Long::times)) {
            "Total elements must match when reshaping"
        }
        return data.clone()
    }

    fun padOrCropText(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength)
        } else {
            text.padEnd(maxLength)
        }
    }
} 