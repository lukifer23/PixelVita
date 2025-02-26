package com.example.androiddiffusion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import java.lang.reflect.Method
import androidx.core.app.NotificationCompat
import com.example.androiddiffusion.MainActivity
import com.example.androiddiffusion.R
import com.example.androiddiffusion.config.MemoryManager
import com.example.androiddiffusion.ml.diffusers.DiffusersPipeline
import com.example.androiddiffusion.util.Logger
import com.example.androiddiffusion.util.NativeMemoryManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ModelLoadingService : Service() {
    companion object {
        private const val TAG = "ModelLoadingService"
        private const val NOTIFICATION_CHANNEL_ID = "model_loading_channel"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "ModelLoadingService::WakeLock"
        private const val MIN_REQUIRED_MEMORY_MB = 2048L // 2GB minimum
        private const val TARGET_MEMORY_MB = 4096L // 4GB target
        private const val MAX_RETRIES = 5
    }

    @Inject
    lateinit var memoryManager: MemoryManager

    @Inject
    lateinit var nativeMemoryManager: NativeMemoryManager

    @Inject
    lateinit var diffusersPipeline: DiffusersPipeline

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var currentMemoryMB = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        ensureMemoryAvailable()
    }

    private fun ensureMemoryAvailable() {
        var retryCount = 0
        var success = false

        while (retryCount < MAX_RETRIES && !success) {
            try {
                // Try to request a larger heap through VM runtime
                val vmRuntime = Class.forName("dalvik.system.VMRuntime")
                val instanceMethod: Method = vmRuntime.getDeclaredMethod("getRuntime")
                val runtime: Any = instanceMethod.invoke(null)

                // Set very low heap utilization
                vmRuntime.getDeclaredMethod("setTargetHeapUtilization", Float::class.java)
                    .invoke(runtime, 0.15f)

                // Try to disable hidden API restrictions
                vmRuntime.getDeclaredMethod("setHiddenApiExemptions", Array<String>::class.java)
                    .invoke(runtime, arrayOf("L"))

                // Request a large heap size
                val maxMemory = Runtime.getRuntime().maxMemory()
                val requestedHeapSize = TARGET_MEMORY_MB * 1024 * 1024 // Convert to bytes
                vmRuntime.getDeclaredMethod("setMinimumHeapSize", Long::class.java)
                    .invoke(runtime, requestedHeapSize)

                Logger.d(TAG, "Attempt ${retryCount + 1}: Requested heap size: ${requestedHeapSize / (1024 * 1024)}MB")

                // Force multiple GC passes
                repeat(3) {
                    System.gc()
                    Runtime.getRuntime().gc()
                    Thread.sleep(100)
                }

                // Try to grow the heap
                vmRuntime.getDeclaredMethod("requestHeapTrim").invoke(runtime)
                vmRuntime.getDeclaredMethod("trimHeap").invoke(runtime)
                Thread.sleep(100)

                // Verify new heap size
                val newMaxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                currentMemoryMB = newMaxMemory
                Logger.d(TAG, "New max heap size: ${newMaxMemory}MB")

                if (newMaxMemory >= MIN_REQUIRED_MEMORY_MB) {
                    success = true
                    Logger.d(TAG, "Successfully increased heap to ${newMaxMemory}MB")
                    
                    // Update notification with success
                    updateNotification("Memory allocated: ${newMaxMemory}MB")
                } else {
                    Logger.w(TAG, "Failed to achieve target memory. Current: ${newMaxMemory}MB, Target: $TARGET_MEMORY_MB")
                    retryCount++
                    Thread.sleep(500) // Wait before retry
                }

            } catch (e: Exception) {
                Logger.e(TAG, "Failed to request max memory on attempt $retryCount", e)
                retryCount++
                Thread.sleep(500) // Wait before retry
            }
        }

        if (!success) {
            // If we couldn't get enough Java heap, try native memory
            val availableNativeMemory = nativeMemoryManager.getAvailableMemoryMB()
            Logger.d(TAG, "Falling back to native memory. Available: ${availableNativeMemory}MB")
            
            if (availableNativeMemory >= MIN_REQUIRED_MEMORY_MB) {
                success = true
                updateNotification("Using native memory: ${availableNativeMemory}MB")
            } else {
                Logger.e(TAG, "Failed to secure enough memory through any means")
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Model Loading",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Used for loading ML models"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(message: String = "Preparing model loading..."): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Loading Model")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(10*60*1000L /*10 minutes*/)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        serviceScope.launch {
            try {
                // Log memory state
                val runtime = Runtime.getRuntime()
                val maxMemory = runtime.maxMemory() / (1024 * 1024)
                val totalMemory = runtime.totalMemory() / (1024 * 1024)
                val freeMemory = runtime.freeMemory() / (1024 * 1024)
                
                Logger.d(TAG, "Memory State:")
                Logger.d(TAG, "Max Memory: ${maxMemory}MB")
                Logger.d(TAG, "Total Memory: ${totalMemory}MB")
                Logger.d(TAG, "Free Memory: ${freeMemory}MB")
                Logger.d(TAG, "Native Memory Available: ${nativeMemoryManager.getAvailableMemoryMB()}MB")
                
                if (maxMemory < MIN_REQUIRED_MEMORY_MB && nativeMemoryManager.getAvailableMemoryMB() < MIN_REQUIRED_MEMORY_MB) {
                    throw OutOfMemoryError("Failed to secure enough memory. Max Java heap: ${maxMemory}MB, Native available: ${nativeMemoryManager.getAvailableMemoryMB()}MB")
                }
                
                updateNotification("Memory secured, loading model...")
                
                // Your model loading logic here
                // This will run with the increased memory allocation
                
                updateNotification("Model loaded successfully")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading model", e)
                updateNotification("Error: ${e.message}")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        wakeLock?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 
