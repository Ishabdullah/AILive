/**
 * ailive_audio.cpp - JNI Bridge for whisper.cpp in AILive
 *
 * Provides a bridge between Kotlin and the whisper.cpp library for
 * high-performance, on-device speech-to-text.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cerrno>
#include <cstring>
#include <android/log.h>
#include "whisper.h"

// Piper TTS is temporarily disabled due to ExternalProject incompatibility with Android NDK
// Will be re-enabled once we have pre-built piper libs for ARM64 Android
// #define ENABLE_PIPER

#ifdef ENABLE_PIPER
#include "piper.hpp"
#endif

#define LOG_TAG_AUDIO "AILive-Audio"
#define LOGI_AUDIO(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG_AUDIO, __VA_ARGS__)
#define LOGE_AUDIO(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_AUDIO, __VA_ARGS__)

// Global context for the Whisper model
static whisper_context* g_whisper_ctx = nullptr;

#ifdef ENABLE_PIPER
// Global context for the Piper model
static piper::Voice* g_piper_voice = nullptr;
static piper::PiperConfig g_piper_config;
#endif


extern "C" {

/**
 * Initializes the Whisper context from a model file.
 *
 * @param env JNI environment
 * @param thiz Java object reference
 * @param model_path Path to the .ggml Whisper model file.
 * @return true if successful, false otherwise.
 */
JNIEXPORT jboolean JNICALL
Java_com_ailive_audio_WhisperProcessor_nativeInit(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {

    if (g_whisper_ctx != nullptr) {
        LOGI_AUDIO("Whisper context already initialized. Releasing first.");
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
    }

    // CRITICAL: Validate input
    if (model_path == nullptr) {
        LOGE_AUDIO("❌ Model path is null!");
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    if (path == nullptr) {
        LOGE_AUDIO("❌ Failed to get UTF chars from model path!");
        return JNI_FALSE;
    }

    size_t path_len = strlen(path);
    if (path_len == 0) {
        LOGE_AUDIO("❌ Model path is empty!");
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    LOGI_AUDIO("🎤 Initializing Whisper model...");
    LOGI_AUDIO("   Path: %s", path);
    LOGI_AUDIO("   Path length: %zu bytes", path_len);

    // Check if file exists before attempting to load
    FILE* test = fopen(path, "rb");
    if (test == nullptr) {
        LOGE_AUDIO("❌ Model file does not exist or cannot be opened!");
        LOGE_AUDIO("   Path: %s", path);
        LOGE_AUDIO("   errno: %d (%s)", errno, strerror(errno));
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }
    fclose(test);
    LOGI_AUDIO("   ✓ File exists and is readable");

    whisper_context_params params = whisper_context_default_params();
    g_whisper_ctx = whisper_init_from_file_with_params(path, params);

    env->ReleaseStringUTFChars(model_path, path);

    if (g_whisper_ctx == nullptr) {
        LOGE_AUDIO("❌ Failed to initialize whisper context.");
        LOGE_AUDIO("   Possible causes:");
        LOGE_AUDIO("   - Wrong model format (expected .bin for whisper)");
        LOGE_AUDIO("   - Corrupted model file");
        LOGE_AUDIO("   - Incompatible whisper.cpp version");
        return JNI_FALSE;
    }

    LOGI_AUDIO("✅ Whisper context initialized successfully!");
    return JNI_TRUE;
}

/**
 * Transcribes a chunk of raw audio data.
 *
 * @param env JNI environment
 * @param thiz Java object reference
 * @param audio_data A float array of PCM audio data (16kHz, mono).
 * @return The transcribed text as a Java string.
 */
JNIEXPORT jstring JNICALL
Java_com_ailive_audio_WhisperProcessor_nativeProcess(
        JNIEnv* env,
        jobject thiz,
        jfloatArray audio_data) {

    if (g_whisper_ctx == nullptr) {
        LOGE_AUDIO("Whisper context not initialized. Cannot process audio.");
        return env->NewStringUTF("");
    }

    jsize len = env->GetArrayLength(audio_data);
    jfloat* audio_buf = env->GetFloatArrayElements(audio_data, nullptr);

    LOGI_AUDIO("Processing %d audio samples.", len);

    // Set up whisper parameters
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.print_realtime = false;
    params.language = "en"; // Set language to English

    // Run the model
    if (whisper_full(g_whisper_ctx, params, audio_buf, len) != 0) {
        LOGE_AUDIO("Failed to process audio with Whisper.");
        env->ReleaseFloatArrayElements(audio_data, audio_buf, JNI_ABORT);
        return env->NewStringUTF("");
    }

    // Get the transcribed text
    const int n_segments = whisper_full_n_segments(g_whisper_ctx);
    std::string result_text;
    for (int i = 0; i < n_segments; ++i) {
        const char* text = whisper_full_get_segment_text(g_whisper_ctx, i);
        result_text += text;
    }

    LOGI_AUDIO("Transcription result: %s", result_text.c_str());

    env->ReleaseFloatArrayElements(audio_data, audio_buf, 0);

    return env->NewStringUTF(result_text.c_str());
}

/**
 * Releases all resources used by the Whisper context.
 */
JNIEXPORT void JNICALL
Java_com_ailive_audio_WhisperProcessor_nativeRelease(
        JNIEnv* env,
        jobject thiz) {

    if (g_whisper_ctx != nullptr) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
        LOGI_AUDIO("✅ Whisper context released.");
    }
}


// --- Piper TTS JNI Functions ---
// Temporarily disabled - will use Android system TTS as fallback

#ifdef ENABLE_PIPER
/**
 * Initializes the Piper TTS voice from a model file.
 */
JNIEXPORT jboolean JNICALL
Java_com_ailive_audio_TTSManager_nativeInitPiper(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {

    if (g_piper_voice != nullptr) {
        LOGI_AUDIO("Piper voice already initialized. Releasing first.");
        delete g_piper_voice;
        g_piper_voice = nullptr;
    }

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI_AUDIO("Initializing Piper model from: %s", path);

    try {
        // Initialize piper (must be called before loading voice)
        piper::initialize(g_piper_config);

        g_piper_voice = new piper::Voice();

        // Construct config path (model_path + ".json")
        std::string model_path_str(path);
        std::string config_path = model_path_str + ".json";

        // Speaker ID (optional, set to nullopt for default)
        std::optional<piper::SpeakerId> speaker_id = std::nullopt;

        // Load voice with all required parameters
        piper::loadVoice(g_piper_config, model_path_str, config_path,
                        *g_piper_voice, speaker_id, false); // useCuda = false for Android
    } catch (const std::exception& e) {
        LOGE_AUDIO("Failed to load Piper voice: %s", e.what());
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    env->ReleaseStringUTFChars(model_path, path);
    LOGI_AUDIO("✅ Piper voice initialized successfully.");
    return JNI_TRUE;
}

/**
 * Synthesizes speech from text and returns raw audio data.
 */
JNIEXPORT jshortArray JNICALL
Java_com_ailive_audio_TTSManager_nativeSynthesize(
        JNIEnv* env,
        jobject thiz,
        jstring text) {

    if (g_piper_voice == nullptr) {
        LOGE_AUDIO("Piper voice not initialized.");
        return nullptr;
    }

    const char* text_cstr = env->GetStringUTFChars(text, nullptr);
    std::vector<int16_t> audio_buffer;
    piper::SynthesisResult result;

    try {
        // Empty audio callback (called after each synthesized audio chunk)
        auto audio_callback = [](){};

        piper::textToAudio(g_piper_config, *g_piper_voice, text_cstr,
                          audio_buffer, result, audio_callback);
    } catch (const std::exception& e) {
        LOGE_AUDIO("Piper synthesis failed: %s", e.what());
        env->ReleaseStringUTFChars(text, text_cstr);
        return nullptr;
    }

    LOGI_AUDIO("Synthesized %zu audio samples.", audio_buffer.size());

    jshortArray audio_array = env->NewShortArray(audio_buffer.size());
    env->SetShortArrayRegion(audio_array, 0, audio_buffer.size(), audio_buffer.data());

    env->ReleaseStringUTFChars(text, text_cstr);
    return audio_array;
}

/**
 * Releases all resources used by the Piper voice.
 */
JNIEXPORT void JNICALL
Java_com_ailive_audio_TTSManager_nativeReleasePiper(
        JNIEnv* env,
        jobject thiz) {

    if (g_piper_voice != nullptr) {
        delete g_piper_voice;
        g_piper_voice = nullptr;
        LOGI_AUDIO("✅ Piper voice released.");
    }
}
#else
// Stub implementations when Piper is disabled
JNIEXPORT jboolean JNICALL
Java_com_ailive_audio_TTSManager_nativeInitPiper(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {
    LOGI_AUDIO("Piper TTS disabled - using Android system TTS fallback");
    return JNI_FALSE;
}

JNIEXPORT jshortArray JNICALL
Java_com_ailive_audio_TTSManager_nativeSynthesize(
        JNIEnv* env,
        jobject thiz,
        jstring text) {
    LOGI_AUDIO("Piper TTS disabled - no native synthesis available");
    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_ailive_audio_TTSManager_nativeReleasePiper(
        JNIEnv* env,
        jobject thiz) {
    // No-op when Piper is disabled
}
#endif // ENABLE_PIPER


} // extern "C"
