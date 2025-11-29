plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
}

android {
    namespace = "com.ailive"
    compileSdk = 35
    // ndkVersion = "26.3.11579264" // Temporarily disabled for syntax testing

    defaultConfig {
        applicationId = "com.ailive"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // NDK and native build temporarily disabled for syntax testing
        // ndk { abiFilters += "arm64-v8a" }
        // externalNativeBuild { cmake { ... } }
    }

    // Temporarily simplified build flavors for testing
    flavorDimensions += "acceleration"
    productFlavors {
        create("gpu") {
            dimension = "acceleration"
            applicationIdSuffix = ".gpu"
            versionNameSuffix = "-GPU"
            buildConfigField("boolean", "GPU_ENABLED", "true")
            buildConfigField("String", "BUILD_VARIANT", "GPU (Testing)")
        }
        create("cpu") {
            dimension = "acceleration"
            versionNameSuffix = "-CPU"
            buildConfigField("boolean", "GPU_ENABLED", "false")
            buildConfigField("String", "BUILD_VARIANT", "CPU (Testing)")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Lifecycle - REQUIRED for lifecycleScope
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // TensorFlow Lite - For Whisper (STT) and MobileNetV3 (Vision)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // ONNX Runtime for BGE embeddings
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")

    // CameraX for camera functionality
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Data Visualization
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Room Database for persistent memory
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Jetpack Compose BOM (Bill of Materials)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Material Design 3
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose Foundation
    implementation("androidx.compose.foundation:foundation")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Web Search Integration
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // Additional utilities
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.yaml:snakeyaml:2.2")

    // Test utilities
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.google.truth:truth:1.4.2")
}