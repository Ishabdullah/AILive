/**
 * ailive_llm.cpp - JNI Bridge for llama.cpp in AILive
 *
 * Based on SmolChat architecture (541 GitHub stars)
 * Provides Java/Kotlin ↔ C++ bridge for GGUF model inference
 *
 * @author AILive Team
 * @since Phase 7.9 - GGUF Support
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "AILive-LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global context (one model at a time)
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_sampler* g_sampler = nullptr;

extern "C" {

/**
 * Load GGUF model from file path
 *
 * @param env JNI environment
 * @param thiz Java object reference
 * @param model_path Path to .gguf model file
 * @param n_ctx Context size (default 2048)
 * @return true if successful, false otherwise
 */
JNIEXPORT jboolean JNICALL
Java_com_ailive_ai_llm_LLMBridge_nativeLoadModel(
        JNIEnv* env,
        jobject thiz,
        jstring model_path,
        jint n_ctx) {

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", path);
    LOGI("Context size: %d", n_ctx);

    try {
        // Initialize llama backend
        llama_backend_init();

        // Model parameters
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 99; // Offload as much as possible to GPU

        // Load model (new API)
        g_model = llama_model_load_from_file(path, model_params);
        if (g_model == nullptr) {
            LOGE("Failed to load model from %s", path);
            env->ReleaseStringUTFChars(model_path, path);
            return JNI_FALSE;
        }

        // Context parameters
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = n_ctx;
        ctx_params.n_threads = 4;  // 4 CPU threads
        ctx_params.n_batch = 512;

        // Create context (new API)
        g_ctx = llama_init_from_model(g_model, ctx_params);
        if (g_ctx == nullptr) {
            LOGE("Failed to create context");
            llama_model_free(g_model);
            g_model = nullptr;
            env->ReleaseStringUTFChars(model_path, path);
            return JNI_FALSE;
        }

        // Create sampler
        llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
        g_sampler = llama_sampler_chain_init(sampler_params);

        // Add temperature sampling
        llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.9f));

        // Add top-p sampling
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.9f, 1));

        LOGI("✅ Model loaded successfully!");
        LOGI("   Context size: %d", llama_n_ctx(g_ctx));

        env->ReleaseStringUTFChars(model_path, path);
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Exception during model loading: %s", e.what());
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }
}

/**
 * Generate text completion
 *
 * @param env JNI environment
 * @param thiz Java object reference
 * @param prompt Input text prompt
 * @param max_tokens Maximum tokens to generate
 * @return Generated text
 */
JNIEXPORT jstring JNICALL
Java_com_ailive_ai_llm_LLMBridge_nativeGenerate(
        JNIEnv* env,
        jobject thiz,
        jstring prompt,
        jint max_tokens) {

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("Model not loaded");
        return env->NewStringUTF("");
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("🔍 Generating response for: %.50s...", prompt_cstr);

    try {
        // Get model vocab for tokenization
        const llama_vocab* vocab = llama_model_get_vocab(g_model);

        // Tokenize prompt (new API)
        std::vector<llama_token> tokens;
        const int n_prompt_tokens = -llama_tokenize(
            vocab,
            prompt_cstr,
            strlen(prompt_cstr),
            nullptr,
            0,
            true,  // add_bos
            false  // special
        );

        tokens.resize(n_prompt_tokens);
        llama_tokenize(
            vocab,
            prompt_cstr,
            strlen(prompt_cstr),
            tokens.data(),
            tokens.size(),
            true,
            false
        );

        LOGI("Tokenized: %zu tokens", tokens.size());

        // Prepare batch
        llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());

        // Decode prompt
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Failed to decode prompt");
            env->ReleaseStringUTFChars(prompt, prompt_cstr);
            return env->NewStringUTF("");
        }

        // Generate tokens
        std::string result;
        int n_generated = 0;

        while (n_generated < max_tokens) {
            // Sample next token
            llama_token token = llama_sampler_sample(g_sampler, g_ctx, -1);

            // Get vocab for token operations
            const llama_vocab* vocab = llama_model_get_vocab(g_model);

            // Check for EOS (new API)
            if (llama_vocab_is_eog(vocab, token)) {
                LOGI("End of generation (EOS token)");
                break;
            }

            // Convert token to text (new API)
            char buf[256];
            int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
            if (n > 0) {
                result.append(buf, n);
            }

            // Prepare next iteration
            batch = llama_batch_get_one(&token, 1);
            if (llama_decode(g_ctx, batch) != 0) {
                LOGE("Failed to decode token %d", token);
                break;
            }

            n_generated++;
        }

        LOGI("✨ Generated %d tokens: %.50s...", n_generated, result.c_str());

        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF(result.c_str());

    } catch (const std::exception& e) {
        LOGE("Exception during generation: %s", e.what());
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("");
    }
}

/**
 * Free model resources
 */
JNIEXPORT void JNICALL
Java_com_ailive_ai_llm_LLMBridge_nativeFreeModel(JNIEnv* env, jobject thiz) {
    LOGI("Freeing model resources...");

    if (g_sampler != nullptr) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_free();
    LOGI("✅ Resources freed");
}

/**
 * Check if model is loaded
 */
JNIEXPORT jboolean JNICALL
Java_com_ailive_ai_llm_LLMBridge_nativeIsLoaded(JNIEnv* env, jobject thiz) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
