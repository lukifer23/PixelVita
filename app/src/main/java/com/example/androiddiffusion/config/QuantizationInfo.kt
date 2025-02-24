package com.example.androiddiffusion.config

import com.example.androiddiffusion.config.QuantizationType

data class QuantizationInfo(
    val type: QuantizationType,
    val originalSize: Long = 0L
) 