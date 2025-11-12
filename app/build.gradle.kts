plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.ailive"
    compileSdk = 35
    ndkVersion = "26.3.11579264"  // NDK for llama.cpp

    defaultConfig {
        applicationId = "com.ailive"
        minSdk = 33  // Android 13+ required for llama.cpp Android module
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NDK configuration disabled (ONNX-only mode - Phase 7.10)
        // Will be re-enabled when llama.cpp JNI is built
        // ndk {
        //     abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        // }

        // externalNativeBuild {
        //     cmake {
        //         cppFlags += "-std=c++17"
        //         arguments += listOf(
        //             "-DANDROID_STL=c++_shared",
        //             "-DLLAMA_BUILD_TESTS=OFF",
        //             "-DLLAMA_BUILD_EXAMPLES=OFF"
        //         )
        //         cFlags += listOf("-O3", "-march=armv8-a+dotprod+i8mm+bf16")
        //     }
        // }
    }

    // ✨ GPU/CPU Build Variants (v1.1)
    // Allows installing both GPU and CPU versions side-by-side
    flavorDimensions += "acceleration"
    productFlavors {
        create("gpu") {
            dimension = "acceleration"
            applicationIdSuffix = ".gpu"
            versionNameSuffix = "-GPU"

            // Set build config field to indicate GPU variant
            buildConfigField("boolean", "GPU_ENABLED", "true")
            buildConfigField("String", "BUILD_VARIANT", "\"GPU (OpenCL Adreno 750)\"")

            // CRITICAL: Pass GPU flag to llama.cpp module via gradle property
            // This triggers OpenCL compilation in the native library
            externalNativeBuild {
                cmake {
                    // Signal to llama module that this is a GPU build
                    // The llama module's build.gradle.kts checks for ENABLE_GPU property
                }
            }
        }

        create("cpu") {
            dimension = "acceleration"
            applicationIdSuffix = ".cpu"
            versionNameSuffix = "-CPU"

            buildConfigField("boolean", "GPU_ENABLED", "false")
            buildConfigField("String", "BUILD_VARIANT", "\"CPU Only\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Enable BuildConfig generation
    buildFeatures {
        buildConfig = true
    }

    // External native build disabled (ONNX-only mode - Phase 7.10)
    // Will be re-enabled when llama.cpp JNI is built
    // externalNativeBuild {
    //     cmake {
    //         path = file("src/main/cpp/CMakeLists.txt")
    //         version = "3.22.1"
    //     }
    // }

    // CRITICAL: Allow large ONNX model files (348MB) in assets
    // Without this, files >100MB are excluded from APK
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Don't compress ONNX files (they're already compressed)
    androidResources {
        noCompress += "onnx"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Lifecycle - REQUIRED for lifecycleScope
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // REMOVED (v1.1 Week 4 Cleanup): TensorFlow Lite dependencies
    // No longer needed - using llama.cpp for all inference
    // implementation("org.tensorflow:tensorflow-lite:2.14.0")
    // implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    // implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // llama.cpp for LLM inference (Phase 9.0 - Official Android module)
    // Official llama.cpp Android bindings from examples/llama.android
    // Supports GGUF models with native ARM64 libraries
    implementation(project(":llama"))

    // DEPRECATED: ONNX Runtime (ArgMax opset 13 not supported on Android)
    // implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    // DEPRECATED: Hugging Face Tokenizers (llama.cpp has built-in tokenizer)
    // implementation("ai.djl.huggingface:tokenizers:0.29.0")

    // CameraX for camera functionality
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Google Play Services Location for GPS and location awareness (v1.2)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Phase 6.2: Data Visualization
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Room Database for persistent memory (v1.3)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Web Search Integration (v1.4)
    // OkHttp - HTTP client with connection pooling, interceptors, TLS
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Retrofit - Type-safe REST client
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")

    // Moshi - JSON parsing (Kotlin-friendly with codegen)
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // Caffeine - High-performance in-memory cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // SnakeYAML - YAML configuration parsing
    implementation("org.yaml:snakeyaml:2.2")

    // MockWebServer - Testing HTTP clients
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Kotlin Test - Coroutine testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // Mockito - Mocking framework
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    // Truth - Fluent assertions
    testImplementation("com.google.truth:truth:1.4.2")
}
