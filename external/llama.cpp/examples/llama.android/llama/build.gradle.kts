plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "android.llama.cpp"
    compileSdk = 34

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // Add NDK properties if wanted, e.g.
            // abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DLLAMA_CURL=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
                arguments += "-DCMAKE_BUILD_TYPE=Release"

                // ✨ OpenCL GPU Acceleration for Adreno 750 (v1.1)
                // Three ways to enable GPU:
                // 1. Build GPU variant: ./gradlew assembleGpuDebug
                // 2. Set env var: ENABLE_GPU=true ./gradlew assembleDebug
                // 3. Set gradle property: ./gradlew assembleDebug -PENABLE_GPU=true
                val enableGpuEnv = System.getenv("ENABLE_GPU")?.toBoolean() ?: false
                val enableGpuProp = project.findProperty("ENABLE_GPU")?.toString()?.toBoolean() ?: false
                val enableGpu = enableGpuEnv || enableGpuProp

                if (enableGpu) {
                    arguments += "-DGGML_OPENCL=ON"
                    arguments += "-DGGML_OPENCL_EMBED_KERNELS=ON"
                    arguments += "-DGGML_OPENCL_USE_ADRENO_KERNELS=ON"
                    println("✨ GPU Acceleration: ENABLED (OpenCL for Adreno 750)")
                } else {
                    println("ℹ️  GPU Acceleration: DISABLED (build 'gpu' variant or set ENABLE_GPU=true)")
                }

                cppFlags += listOf()
                arguments += listOf()

                cppFlags("")
            }
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
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
