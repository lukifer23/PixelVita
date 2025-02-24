package com.example.androiddiffusion.config

enum class QuantizationType {
    NONE,       // No quantization (FP32)
    INT8,       // 8-bit integer quantization
    INT4,       // 4-bit integer quantization
    INT2,       // 2-bit integer quantization
    DYNAMIC     // Dynamic quantization
} 