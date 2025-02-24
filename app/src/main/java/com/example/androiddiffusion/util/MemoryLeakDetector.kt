package com.example.androiddiffusion.util

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryLeakDetector @Inject constructor(
    private val logger: Logger
) {
    companion object {
        private const val TAG = "MemoryLeakDetector"
        private const val CHECK_INTERVAL_MS = 30_000L // 30 seconds
    }

    private val trackedObjects = ConcurrentHashMap<String, TrackedObject>()
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false

    private data class TrackedObject(
        val weakRef: WeakReference<Any>,
        val className: String,
        val creationTime: Long,
        var lastSeenTime: Long,
        var expectedLifetime: Long
    )

    fun startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true
            scheduleCheck()
            Logger.d(TAG, "Memory leak monitoring started")
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            isMonitoring = false
            handler.removeCallbacksAndMessages(null)
            trackedObjects.clear()
            Logger.d(TAG, "Memory leak monitoring stopped")
        }
    }

    fun trackObject(obj: Any, id: String, expectedLifetimeMs: Long = CHECK_INTERVAL_MS * 2) {
        val className = obj.javaClass.simpleName
        trackedObjects[id] = TrackedObject(
            weakRef = WeakReference(obj),
            className = className,
            creationTime = System.currentTimeMillis(),
            lastSeenTime = System.currentTimeMillis(),
            expectedLifetime = expectedLifetimeMs
        )
        Logger.d(TAG, "Started tracking object: $className (ID: $id)")
    }

    fun stopTrackingObject(id: String) {
        trackedObjects.remove(id)?.let { tracked ->
            Logger.d(TAG, "Stopped tracking object: ${tracked.className} (ID: $id)")
        }
    }

    fun updateObjectLastSeen(id: String) {
        trackedObjects[id]?.let { tracked ->
            tracked.lastSeenTime = System.currentTimeMillis()
        }
    }

    private fun scheduleCheck() {
        if (!isMonitoring) return

        handler.postDelayed({
            checkForLeaks()
            scheduleCheck()
        }, CHECK_INTERVAL_MS)
    }

    private fun checkForLeaks() {
        val currentTime = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()

        trackedObjects.forEach { (id, tracked) ->
            val obj = tracked.weakRef.get()
            if (obj == null) {
                // Object has been garbage collected
                toRemove.add(id)
                Logger.d(TAG, "Object collected: ${tracked.className} (ID: $id)")
            } else {
                val lifetime = currentTime - tracked.creationTime
                if (lifetime > tracked.expectedLifetime) {
                    // Potential memory leak
                    Logger.w(TAG, "Potential memory leak detected:")
                    Logger.w(TAG, "- Object: ${tracked.className}")
                    Logger.w(TAG, "- ID: $id")
                    Logger.w(TAG, "- Lifetime: ${lifetime / 1000}s")
                    Logger.w(TAG, "- Expected lifetime: ${tracked.expectedLifetime / 1000}s")
                    Logger.w(TAG, "- Last seen: ${(currentTime - tracked.lastSeenTime) / 1000}s ago")
                }
            }
        }

        toRemove.forEach { trackedObjects.remove(it) }
    }
} 