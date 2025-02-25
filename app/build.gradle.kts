plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
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
        kotlinCompilerExtensionVersion = "1.5.3"
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
    val roomVersion = "2.6.1"
    val composeVersion = "1.5.4"
    val lifecycleVersion = "2.7.0"
    val hiltVersion = "2.48"
    
    // Core Library Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // Kotlin Standard Library and Core Dependencies
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.22"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Dagger Hilt
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-android-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Android Core Dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.multidex:multidex:2.0.1")

    // Compose Dependencies
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ONNX Runtime
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")

    // Other Dependencies
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")
    implementation("com.google.accompanist:accompanist-navigation-animation:0.32.0")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(platform("io.coil-kt:coil-bom:2.5.0"))
    implementation("io.coil-kt:coil")
    implementation("io.coil-kt:coil-compose")
    
    // Testing Dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
