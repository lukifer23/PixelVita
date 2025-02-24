# Android Diffusion

A high-performance, on-device stable diffusion app for Android that runs entirely on the device without requiring cloud services.

## Features

- On-device image generation using Stable Diffusion
- Support for multiple optimized models (SD v3.5, Flux)
- INT4/INT8 quantization for efficient model execution
- Adaptive performance based on device capabilities
- Memory-efficient processing with automatic optimization
- Modern Material3 UI using Jetpack Compose
- Background model downloading with progress tracking
- Automatic model optimization for target devices

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material3
- **Architecture**: MVVM with Clean Architecture
- **Dependencies**:
  - ONNX Runtime
  - Room Database
  - Dagger Hilt
  - Kotlin Coroutines
  - OkHttp
  - Coil

## System Requirements

- Android 7.0 (API 24) or higher
- ARMv8 (arm64-v8a) or x86_64 processor
- Minimum 4GB RAM recommended
- 2GB+ free storage space

## Getting Started

1. Clone the repository
2. Open the project in Android Studio Electric Eel or newer
3. Sync project with Gradle files
4. Build and run the application

## Project Structure

```
app/src/main/java/com/example/androiddiffusion/
├── config/                 # Configuration classes
├── data/                  # Data layer (Room DB, Models)
├── di/                    # Dependency injection
├── ml/                    # ML processing components
├── ui/                    # UI components
├── util/                  # Utility classes
├── viewmodel/             # ViewModels
├── DiffusionApplication.kt
└── MainActivity.kt
```

## Key Components

### ML Processing
- `DiffusersPipeline`: Handles model loading and inference
- `TensorUtils`: Handles tensor operations and conversions
- `Scheduler`: Manages diffusion scheduling

### Data Management
- `ModelDownloadManager`: Manages model downloads
- `AppDatabase`: Room database for model information
- `DiffusionModel`: Entity representing a diffusion model

### Configuration
- `ModelConfig`: Model-specific configurations
- `SystemRequirements`: Device capability detection
- `QuantizationType`: Model quantization settings

## Building

The project uses Gradle with the following configurations:
- JDK 17
- Android Gradle Plugin 8.2.0
- Kotlin 1.9.22
- NDK 26.1.10909125

## Performance Optimization

The app includes several optimization features:
- Automatic model quantization
- Memory management
- Device-specific optimizations
- Background processing
- Caching mechanisms

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

[Insert License Information]

## Acknowledgments

- [ONNX Runtime](https://onnxruntime.ai/)
- [Stable Diffusion](https://stability.ai/)
- [Android Jetpack](https://developer.android.com/jetpack)

## Contact

[Insert Contact Information] 