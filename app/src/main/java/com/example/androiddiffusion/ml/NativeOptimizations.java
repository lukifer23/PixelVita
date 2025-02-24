package com.example.androiddiffusion.ml;

import android.util.Log;

/**
 * Native optimizations for the diffusion model.
 * This class provides JNI bindings to C++ code that handles performance-critical operations.
 */
public class NativeOptimizations {
    private static final String TAG = "NativeOptimizations";
    
    static {
        try {
            System.loadLibrary("native_optimizations");
            System.loadLibrary("intellisense_test");
            Log.i(TAG, "Native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native libraries", e);
        }
    }
    
    /**
     * Checks if the device is supported for optimized operations.
     * 
     * @return true if the device is supported
     */
    public native boolean isDeviceSupported();
    
    /**
     * Gets the optimal number of threads to use for computation.
     * 
     * @return the optimal number of threads
     */
    public native int getOptimalNumThreadsNative();
    
    /**
     * Tests if IntelliSense is properly configured.
     * This is only used for development purposes.
     * 
     * @return true if IntelliSense is working correctly
     */
    public native boolean testIntelliSense();
    
    /**
     * Gets the optimal number of threads to use for computation.
     * This method adds some Java-side logic on top of the native implementation.
     * 
     * @return the optimal number of threads
     */
    public int getOptimalNumThreads() {
        int threads = getOptimalNumThreadsNative();
        Log.d(TAG, "Optimal number of threads: " + threads);
        return Math.max(1, threads);
    }
} 