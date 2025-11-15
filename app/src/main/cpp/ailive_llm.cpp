/**
 * ailive_llm.cpp - JNI Bridge for llama.cpp in AILive
 *
 * Based on SmolChat architecture (541 GitHub stars)
 * Provides Java/Kotlin ↔ C++ bridge for GGUF model inference
 *
 * This version contains critical fixes for tokenization, state management,
 * and sampling to resolve issues with token production and response coherence.
 *
 * @author AILive Team (with fixes by Gemini)
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

// Forward declaration
static std::string llama_decode_and_generate(const std::string& prompt_str, int max_tokens);

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

    if (g_model != nullptr) {
        LOGI("Model already loaded. Freeing old model first.");
        Java_com_ailive_ai_llm_LLMBridge_nativeFreeModel(env, thiz);
    }

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", path);
    LOGI("Context size: %d", n_ctx);

    try {
        llama_backend_init(false); // num_threads = 0 (auto)

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 99; // Offload as much as possible

        g_model = llama_load_model_from_file(path, model_params);
        if (g_model == nullptr) {
            LOGE("Failed to load model from %s", path);
            env->ReleaseStringUTFChars(model_path, path);
            return JNI_FALSE;
        }

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = n_ctx > 0 ? n_ctx : 2048;
        ctx_params.n_threads = 4;
        ctx_params.n_batch = 512;

        g_ctx = llama_new_context_with_model(g_model, ctx_params);
        if (g_ctx == nullptr) {
            LOGE("Failed to create context");
            llama_free_model(g_model);
            g_model = nullptr;
            env->ReleaseStringUTFChars(model_path, path);
            return JNI_FALSE;
        }

        LOGI("✅ Model loaded successfully!");
        LOGI("   Context size: %d", llama_n_ctx(g_ctx));

        env->ReleaseStringUTFChars(model_path, path);
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Exception during model loading: %s", e.what());
        if (path != nullptr) env->ReleaseStringUTFChars(model_path, path);
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
        LOGE("Model not loaded, cannot generate.");
        return env->NewStringUTF("");
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    std::string result = llama_decode_and_generate(prompt_cstr, max_tokens);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    return env->NewStringUTF(result.c_str());
}

/**
 * Free model resources
 */
JNIEXPORT void JNICALL
Java_com_ailive_ai_llm_LLMBridge_nativeFreeModel(JNIEnv* env, jobject thiz) {
    LOGI("Freeing model resources...");

    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }

    if (g_model != nullptr) {
        llama_free_model(g_model);
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


/**
 * Main generation function using the corrected llama.cpp workflow.
 */
static std::string llama_decode_and_generate(const std::string& prompt_str, int max_tokens) {
    LOGI("🔍 Generating response for: %.80s...", prompt_str.c_str());

    // Clear the KV cache from previous runs
    llama_kv_cache_clear(g_ctx);

    // Tokenize the prompt
    std::vector<llama_token> prompt_tokens;
    prompt_tokens.reserve(prompt_str.length() + 1); // Reserve space
    int n_prompt_tokens = llama_tokenize(g_model, prompt_str.c_str(), prompt_str.length(), prompt_tokens.data(), prompt_tokens.capacity(), true, false);
    if (n_prompt_tokens < 0) {
        LOGE("Failed to tokenize prompt (buffer too small). Required size: %d", -n_prompt_tokens);
        // Resize and try again
        prompt_tokens.resize(-n_prompt_tokens);
        n_prompt_tokens = llama_tokenize(g_model, prompt_str.c_str(), prompt_str.length(), prompt_tokens.data(), prompt_tokens.size(), true, false);
    } else {
        prompt_tokens.resize(n_prompt_tokens);
    }

    if (n_prompt_tokens <= 0) {
        LOGE("Tokenization resulted in 0 or negative tokens.");
        return "[ERROR: Tokenization failed]";
    }
    LOGI("Tokenized prompt into %d tokens.", n_prompt_tokens);

    // --- Process Prompt ---
    llama_batch batch = llama_batch_init(n_prompt_tokens, 0, 1);
    for (int i = 0; i < n_prompt_tokens; ++i) {
        llama_batch_add(batch, prompt_tokens[i], i, {0}, true);
    }
    batch.logits[batch.n_tokens - 1] = 1; // Request logit for the last token

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode prompt.");
        llama_batch_free(batch);
        return "[ERROR: Prompt decoding failed]";
    }
    LOGI("Prompt decoded successfully.");

    // --- Generate Response ---
    std::string result_str;
    int n_current = n_prompt_tokens;

    while (n_current < max_tokens) {
        // Sample the next token
        auto* logits = llama_get_logits_ith(g_ctx, batch.n_tokens - 1);
        
        llama_token_data_array candidates;
        candidates.data = new llama_token_data[llama_n_vocab(g_model)];
        candidates.size = llama_n_vocab(g_model);
        for (int token_id = 0; token_id < candidates.size; ++token_id) {
            candidates.data[token_id].id = token_id;
            candidates.data[token_id].logit = logits[token_id];
            candidates.data[token_id].p = 0.0f;
        }

        llama_token_data_array cur_p = { candidates.data, candidates.size, false };

        // Apply penalties
        llama_sample_repetition_penalties(g_ctx, &cur_p, prompt_tokens.data(), prompt_tokens.size(), 1.1f, 64, 1.0f);
        llama_sample_top_k(g_ctx, &cur_p, 40, 1);
        llama_sample_min_p(g_ctx, &cur_p, 0.05f, 1);
        llama_sample_top_p(g_ctx, &cur_p, 0.95f, 1);
        llama_sample_temp(g_ctx, &cur_p, 0.8f);
        
        llama_token new_token_id = llama_sample_token(g_ctx, &cur_p);
        delete[] candidates.data;

        // Check for End-of-Sequence
        if (new_token_id == llama_token_eos(g_model)) {
            LOGI("End of generation (EOS token).");
            break;
        }

        // Append token to result string
        result_str += llama_token_to_piece(g_ctx, new_token_id);

        // Prepare for next iteration
        llama_batch_free(batch);
        batch = llama_batch_init(1, 0, 1);
        llama_batch_add(batch, new_token_id, n_current, {0}, true);
        
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Failed to decode token %d", new_token_id);
            break;
        }

        n_current++;
    }

    llama_batch_free(batch);
    LOGI("✨ Generated %zu tokens: %.80s...", result_str.length(), result_str.c_str());
    return result_str;
}
