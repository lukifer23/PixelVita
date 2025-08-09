plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// Force Java 17 toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Extract ONNX Runtime native libraries
tasks.register<Copy>("extractOnnxRuntimeLibs") {
    doLast {
        val onnxRuntimeDep = project.configurations
            .findByName("implementation")
            ?.resolvedConfiguration
            ?.firstLevelModuleDependencies
            ?.find { dep -> 
                dep.moduleGroup == "com.microsoft.onnxruntime" && 
                dep.moduleName == "onnxruntime-android" 
            }
        
        val aarFile = onnxRuntimeDep?.moduleArtifacts?.firstOrNull()?.file
        
        if (aarFile != null && aarFile.exists()) {
            copy {
                from(zipTree(aarFile)) {
                    include("jni/**/*.so")
                    eachFile {
                        // Preserve ABI directory structure
                        val segments = relativePath.segments
                        if (segments.size >= 3) {
                            val abi = segments[1]  // e.g., "arm64-v8a"
                            val filename = segments.last()  // e.g., "libonnxruntime.so"
                            relativePath = RelativePath(true, abi, filename)
                        }
                    }
                }
                into("${project.projectDir}/src/main/jniLibs")
            }
        } else {
            logger.warn("ONNX Runtime AAR file not found")
        }
    }
}

tasks.named("preBuild") {
    dependsOn("extractOnnxRuntimeLibs")
}

android {
    namespace = "com.example.androiddiffusion"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.androiddiffusion"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Room schema export directory
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }

        // Configure memory settings
        manifestPlaceholders.putAll(mapOf(
            "maxHeapSize" to "8192",
            "vmHeapSize" to "4096",
            "heapGrowthLimit" to "4096",
            "isLargeHeap" to "true",
            "isHardwareAccelerated" to "true"
        ))

        // Enable multidex
        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "android"
            keyAlias = "androiddiffusion"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG", "false")
            buildConfigField("String", "VERSION_NAME", "\"${defaultConfig.versionName}\"")
            buildConfigField("int", "VERSION_CODE", "${defaultConfig.versionCode}")
            buildConfigField("boolean", "ENABLE_MEMORY_OPTIMIZATIONS", "true")
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
            multiDexEnabled = true
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField("boolean", "DEBUG", "true")
            buildConfigField("String", "VERSION_NAME", "\"${defaultConfig.versionName}\"")
            buildConfigField("int", "VERSION_CODE", "${defaultConfig.versionCode}")
            buildConfigField("boolean", "ENABLE_MEMORY_OPTIMIZATIONS", "true")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            buildConfigField("String", "LOG_LEVEL", "\"VERBOSE\"")
            resValue("string", "app_name", "Android Diffusion (Debug)")
            multiDexEnabled = true
        }
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
        prefab = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi",
            "-Xjvm-default=all",
            "-Xskip-prerelease-check"
        )
        apiVersion = "1.9"
        languageVersion = "1.9"
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/previous-compilation-data.bin",
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += listOf(
                "**/libonnxruntime.so",
                "**/libmemory_manager.so",
                "**/libnative_optimizations.so",
                "**/libc++_shared.so"
            )
            keepDebugSymbols += listOf(
                "**/*.so"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Resource configuration
    androidResources {
        noCompress += listOf("onnx", "json", "pt", "bin")
    }

    // Configure lint
    lint {
        disable += listOf(
            "LargeAsset",
            "SoonBlockedPrivateApi",
            "UnusedResources",
            "ObsoleteLintCustomCheck",
            "UnsafeOptInUsageError"
        )
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
            }
            jniLibs {
                srcDirs("src/main/jniLibs")
            }
        }
    }
}

dependencies {
    // Core Library Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Kotlin Standard Library and Core Dependencies
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib.common)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Android Core Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.multidex)

    // Compose Dependencies
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ONNX Runtime
    implementation(libs.onnxruntime.android)

    // Other Dependencies
    implementation(libs.google.android.material)
    implementation(libs.accompanist.permissions)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.navigation.animation)
    implementation(libs.google.play.services.base)
    implementation(libs.google.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Testing Dependencies
    testImplementation(libs.junit4)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
