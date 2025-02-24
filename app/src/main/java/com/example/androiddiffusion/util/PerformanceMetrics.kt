package com.example.androiddiffusion.util

data class PerformanceMetrics(
    val inferenceTime: Double = 0.0,
    val memoryUsage: Long = 0L,
    val cpuUsage: Float = 0f,
    val gpuUsage: Float = 0f,
    val temperature: Float = 0f,
    
    val metrics: Map<String, Any> = mapOf(
        "inferenceTime" to inferenceTime,
        "memoryUsage" to memoryUsage,
        "cpuUsage" to cpuUsage,
        "gpuUsage" to gpuUsage,
        "temperature" to temperature
    )
) {
    fun toMap(): Map<String, Any> = metrics

    companion object {
        fun fromMap(map: Map<String, Any>): PerformanceMetrics = PerformanceMetrics(
            inferenceTime = (map["inferenceTime"] as? Number)?.toDouble() ?: 0.0,
            memoryUsage = (map["memoryUsage"] as? Number)?.toLong() ?: 0L,
            cpuUsage = (map["cpuUsage"] as? Number)?.toFloat() ?: 0f,
            gpuUsage = (map["gpuUsage"] as? Number)?.toFloat() ?: 0f,
            temperature = (map["temperature"] as? Number)?.toFloat() ?: 0f
        )
    }
}

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val sdkVersion: Int,
    val cpuArchitecture: String,
    val gpuVendor: String?,
    val gpuRenderer: String?,
    val totalMemoryMB: Long,
    val numberOfCores: Int,
    val supportedABIs: List<String>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "manufacturer" to manufacturer,
        "model" to model,
        "sdk_version" to sdkVersion,
        "cpu_architecture" to cpuArchitecture,
        "gpu_vendor" to (gpuVendor ?: ""),
        "gpu_renderer" to (gpuRenderer ?: ""),
        "total_memory_mb" to totalMemoryMB,
        "number_of_cores" to numberOfCores,
        "supported_abis" to supportedABIs
    )
    
    companion object {
        fun fromMap(map: Map<String, Any>): DeviceInfo {
            return DeviceInfo(
                manufacturer = map["manufacturer"] as? String ?: "",
                model = map["model"] as? String ?: "",
                sdkVersion = (map["sdk_version"] as? Number)?.toInt() ?: 0,
                cpuArchitecture = map["cpu_architecture"] as? String ?: "",
                gpuVendor = map["gpu_vendor"] as? String,
                gpuRenderer = map["gpu_renderer"] as? String,
                totalMemoryMB = (map["total_memory_mb"] as? Number)?.toLong() ?: 0L,
                numberOfCores = (map["number_of_cores"] as? Number)?.toInt() ?: 0,
                supportedABIs = (map["supported_abis"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        }
    }
} 