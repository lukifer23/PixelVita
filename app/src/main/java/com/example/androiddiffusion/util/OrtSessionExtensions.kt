package com.example.androiddiffusion.util

import ai.onnxruntime.OrtSession

// These extension functions act as no-ops if the underlying ONNX Runtime does not provide them.
// In a production environment, you would use the actual API if available.

fun OrtSession.SessionOptions.setMemoryPatternOptimization(enabled: Boolean) {
    // No-op implementation
}

fun OrtSession.SessionOptions.setOptimizationLevel(optLevel: String) {
    // No-op implementation; 'optLevel' can be "BASIC", "ADVANCED", etc.
}

fun OrtSession.SessionOptions.addConfigEntry(key: String, value: String) {
    // No-op implementation
} 