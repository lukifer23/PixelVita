package com.example.androiddiffusion.util

import android.app.Activity
import android.app.Application
import android.os.Bundle

class ActivityLifecycleCallback(
    private val onTrimMemory: (Int) -> Unit = {},
    private val onLowMemory: () -> Unit = {}
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (activity.isFinishing) {
            System.gc()
        }
    }

    fun onTrimMemory(level: Int) {
        onTrimMemory.invoke(level)
    }

    fun onLowMemory() {
        onLowMemory.invoke()
    }
} 