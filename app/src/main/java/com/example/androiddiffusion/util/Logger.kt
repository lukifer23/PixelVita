package com.example.androiddiffusion.util

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.androiddiffusion.BuildConfig
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Singleton
import java.io.BufferedWriter
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.ref.WeakReference
import javax.inject.Inject

@Singleton
class Logger @Inject constructor() {
    companion object {
        private const val TAG = "AndroidDiffusion"
        private var instance: Logger? = null
        
        fun d(component: String, message: String) {
            instance?.debug(component, message)
        }
        
        fun i(component: String, message: String) {
            instance?.info(component, message)
        }
        
        fun w(component: String, message: String) {
            instance?.warn(component, message)
        }
        
        fun e(component: String, message: String, throwable: Throwable? = null) {
            instance?.error(component, message, throwable)
        }
        
        fun memory(component: String) {
            instance?.logMemory(component)
        }
        
        fun setDebugEnabled(enabled: Boolean) {
            instance?.isDebugEnabled = enabled
        }
        
        fun initialize(context: Context) {
            instance?.initializeLogger(context)
        }
    }

    private var isDebugEnabled = true
    private var contextRef: WeakReference<Context>? = null
    private val logQueue = ConcurrentLinkedQueue<String>()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logWriter: BufferedWriter? = null
    private val logScope = MainScope()
    
    private fun initializeLogger(context: Context) {
        contextRef = WeakReference(context.applicationContext)
        setupLogFile()
        startLogWriter()
        instance = this
    }
    
    private fun setupLogFile() {
        contextRef?.get()?.let { ctx ->
            val logDir = File(ctx.getExternalFilesDir(null), "logs")
            logDir.mkdirs()
            
            // Clean old log files
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000) {
                    file.delete()
                }
            }
            
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            logFile = File(logDir, "diffusion_log_$timestamp.txt")
            
            try {
                logWriter = BufferedWriter(FileWriter(logFile, true))
                // Write header
                logWriter?.apply {
                    write("=== Diffusion App Log Started at ${dateFormat.format(Date())} ===\n")
                    write("Device: ${android.os.Build.MODEL}\n")
                    write("Android: ${android.os.Build.VERSION.RELEASE}\n")
                    write("App Version: ${BuildConfig.VERSION_NAME}\n")
                    write("===========================================\n\n")
                    flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create log writer", e)
            }
        }
    }
    
    private fun startLogWriter() {
        logScope.launch {
            while (isActive) {
                try {
                    val message = logQueue.poll()
                    if (message != null) {
                        logWriter?.apply {
                            write(message)
                            flush()
                        }
                    } else {
                        delay(100) // Wait a bit before checking again
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing to log file", e)
                }
            }
        }
    }
    
    private fun debug(component: String, message: String) {
        if (isDebugEnabled) {
            val logMessage = formatLog("DEBUG", component, message)
            Log.d(TAG, "[$component] $message")
            logQueue.offer(logMessage)
        }
    }
    
    private fun info(component: String, message: String) {
        val logMessage = formatLog("INFO", component, message)
        Log.i(TAG, "[$component] $message")
        logQueue.offer(logMessage)
    }
    
    private fun warn(component: String, message: String) {
        val logMessage = formatLog("WARN", component, message)
        Log.w(TAG, "[$component] $message")
        logQueue.offer(logMessage)
    }
    
    private fun error(component: String, message: String, throwable: Throwable? = null) {
        val logMessage = formatLog("ERROR", component, "$message\n${throwable?.stackTraceToString() ?: ""}")
        Log.e(TAG, "[$component] $message", throwable)
        logQueue.offer(logMessage)
        // Save to file if context is available
        contextRef?.get()?.let { ctx ->
            // Implement file logging here if needed
        }
    }
    
    private fun logMemory(component: String) {
        if (!isDebugEnabled) return
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        
        debug(component, "Memory Stats:")
        debug(component, "- Max Memory: ${maxMemory}MB")
        debug(component, "- Total Memory: ${totalMemory}MB")
        debug(component, "- Free Memory: ${freeMemory}MB")
        debug(component, "- Used Memory: ${usedMemory}MB")
        debug(component, "- Memory Usage: ${(usedMemory.toFloat() / maxMemory.toFloat() * 100).toInt()}%")
    }
    
    private fun formatLog(level: String, tag: String, message: String): String {
        return "${dateFormat.format(Date())} [$level] $tag: $message\n"
    }
    
    fun getLogFile(): File? = logFile
    
    fun clearLogs() {
        logQueue.clear()
        logWriter?.close()
        logFile?.delete()
        setupLogFile()
    }
    
    fun cleanup() {
        logScope.cancel()
        logWriter?.close()
        logWriter = null
    }
} 