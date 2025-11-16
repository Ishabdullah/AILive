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
#include <mutex>
#include <android/log.h>
#include "llama.h"
// #include "llama_image.h" // TODO: Not available in current llama.cpp - vision features temporarily disabled

#define LOG_TAG "AILive-LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Global context (one model at a time)
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

// CRITICAL FIX: Add mutex for thread safety
// Model initialized on one thread, but generation called from another
static std::mutex g_llama_mutex;

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

    if (g_model != nullptr || g_ctx != nullptr) {
        LOGI("Model already loaded. Freeing old model first.");
        if (g_ctx != nullptr) {
            llama_free(g_ctx);
            g_ctx = nullptr;
        }
        if (g_model != nullptr) {
            llama_model_free(g_model);
            g_model = nullptr;
        }
    }

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", path);
    LOGI("Context size: %d", n_ctx);

    try {
        llama_backend_init(); // Initialize backend

        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 99; // Offload as much as possible

        g_model = llama_model_load_from_file(path, model_params);
        if (g_model == nullptr) {
            LOGE("Failed to load model from %s", path);
            env->ReleaseStringUTFChars(model_path, path);
            return JNI_FALSE;
        }

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = n_ctx > 0 ? n_ctx : 2048;
        ctx_params.n_threads = 4;
        ctx_params.n_batch = 512;

        g_ctx = llama_init_from_model(g_model, ctx_params);
        if (g_ctx == nullptr) {
            LOGE("Failed to create context");
            llama_model_free(g_model);
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

    // CRITICAL FIX: Lock mutex to prevent concurrent access
    // Model initialized on one thread, generation called from another
    std::lock_guard<std::mutex> lock(g_llama_mutex);

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("Model not loaded, cannot generate.");
        return env->NewStringUTF("");
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);

    // CRITICAL FIX: Validate prompt before processing
    if (prompt_cstr == nullptr) {
        LOGE("❌ Prompt is null!");
        return env->NewStringUTF("[ERROR: Null prompt]");
    }

    size_t prompt_len = strlen(prompt_cstr);
    if (prompt_len == 0) {
        LOGE("❌ Prompt is empty!");
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("[ERROR: Empty prompt]");
    }

    if (prompt_len > 16000) {
        LOGW("⚠️ Prompt very long (%zu bytes), may cause issues", prompt_len);
    }

    LOGI("📝 Received prompt: %zu bytes, max_tokens=%d", prompt_len, max_tokens);
    LOGI("   Thread safety: LOCKED (mutex acquired)");

    std::string result;
    try {
        result = llama_decode_and_generate(prompt_cstr, max_tokens);
    } catch (const std::exception& e) {
        LOGE("❌ Exception during generation: %s", e.what());
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("[ERROR: Generation failed]");
    } catch (...) {
        LOGE("❌ Unknown exception during generation");
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("[ERROR: Unknown error]");
    }

    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    LOGI("✅ Generation complete: %zu bytes", result.length());
    LOGI("   Thread safety: UNLOCKED (mutex releasing)");
    return env->NewStringUTF(result.c_str());
}

/**
 * Generate text completion with image input (multimodal).
 *
 * @param env JNI environment
 * @param thiz Java object reference
 * @param prompt Input text prompt
 * @param image_bytes Raw image data (e.g., JPEG, PNG)
 * @param max_tokens Maximum tokens to generate
 * @return Generated text
 */
JNIEXPORT jstring JNICALL
Java_com_ailive_ai_llm_LLMBridge_nativeGenerateWithImage(
        JNIEnv* env,
        jobject thiz,
        jstring prompt,
        jbyteArray image_bytes,
        jint max_tokens) {

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("Model not loaded, cannot generate with image.");
        return env->NewStringUTF("");
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    jbyte* image_data = env->GetByteArrayElements(image_bytes, nullptr);
    jsize image_len = env->GetArrayLength(image_bytes);

    std::vector<uint8_t> image_vec(image_data, image_data + image_len);

    // TODO: Vision features temporarily disabled - llama_image API not available in current llama.cpp
    std::string result = "[ERROR: Vision/multimodal features not available in this build]";
    // std::string result = llama_decode_and_generate_multimodal(prompt_cstr, image_vec, max_tokens);

    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    env->ReleaseByteArrayElements(image_bytes, image_data, JNI_ABORT);

    return env->NewStringUTF(result.c_str());
}


/**
 * Generate an embedding vector for a given prompt.
 *
 * @param env JNI environment
 * @param thiz Java object reference
 * @param prompt Input text prompt
 * @return A float array representing the embedding, or null on failure.
 */
JNIEXPORT jfloatArray JNICALL
Java_com_ailive_ai_llm_LLMBridge_nativeGenerateEmbedding(
        JNIEnv* env,
        jobject thiz,
        jstring prompt) {

    if (g_model == nullptr || g_ctx == nullptr) {
        LOGE("Model not loaded, cannot generate embedding.");
        return nullptr;
    }

    const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("🧠 Generating embedding for: %.80s...", prompt_cstr);

    // Note: KV cache clearing function varies by llama.cpp version
    // Skipping cache clear - will naturally overwrite with new tokens

    // Tokenize the prompt
    std::vector<llama_token> tokens;
    tokens.resize(strlen(prompt_cstr) + 1); // Actually allocate memory
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int n_tokens = llama_tokenize(vocab, prompt_cstr, strlen(prompt_cstr), tokens.data(), tokens.size(), true, false);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, prompt_cstr, strlen(prompt_cstr), tokens.data(), tokens.size(), true, false);
    } else {
        tokens.resize(n_tokens);
    }

    if (n_tokens <= 0) {
        LOGE("Embedding tokenization failed.");
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return nullptr;
    }

    // Create a batch for the prompt
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = 0; // Logits not needed for embedding
    }

    // Decode the prompt to update the context
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("llama_decode failed for embedding");
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return nullptr;
    }

    // Get the embedding for the last token
    const int n_embd = llama_model_n_embd(g_model);
    const float* embedding = llama_get_embeddings_ith(g_ctx, n_tokens - 1);

    if (embedding == nullptr) {
        LOGE("Failed to get embeddings.");
        llama_batch_free(batch);
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return nullptr;
    }

    // Create and return the float array
    jfloatArray result = env->NewFloatArray(n_embd);
    if (result == nullptr) {
        LOGE("Failed to create new float array.");
    } else {
        env->SetFloatArrayRegion(result, 0, n_embd, embedding);
    }

    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    LOGI("✅ Embedding generated successfully.");
    return result;
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


/**
 * Main generation function using the corrected llama.cpp workflow.
 *
 * CRITICAL FIXES:
 * - Clear KV cache before each generation (prevents stale state crashes)
 * - Clear sequence 0 to reset generation state
 * - Thread-safe (called under mutex lock)
 */
static std::string llama_decode_and_generate(const std::string& prompt_str, int max_tokens) {
    LOGI("🔍 Generating response for: %.80s...", prompt_str.c_str());

    // CRITICAL FIX: Clear KV cache before generation
    // Previous generations leave stale state that causes crashes
    // Clear sequence 0 (default sequence for single-user chat)
    LOGI("🧹 Clearing KV cache for fresh generation...");
    llama_kv_cache_clear(g_ctx);
    LOGI("✅ KV cache cleared");

    // Tokenize the prompt
    std::vector<llama_token> prompt_tokens;
    prompt_tokens.resize(prompt_str.length() + 1); // Actually allocate memory
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int n_prompt_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), prompt_tokens.data(), prompt_tokens.size(), true, false);
    if (n_prompt_tokens < 0) {
        LOGE("Failed to tokenize prompt (buffer too small). Required size: %d", -n_prompt_tokens);
        // Resize and try again
        prompt_tokens.resize(-n_prompt_tokens);
        n_prompt_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), prompt_tokens.data(), prompt_tokens.size(), true, false);
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
    batch.n_tokens = n_prompt_tokens;
    for (int i = 0; i < n_prompt_tokens; ++i) {
        batch.token[i] = prompt_tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = (i == n_prompt_tokens - 1) ? 1 : 0; // Request logit only for last token
    }

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
        // Sample the next token using the new sampler API
        auto* logits = llama_get_logits_ith(g_ctx, batch.n_tokens - 1);

        // CRITICAL FIX: Validate logits pointer
        if (logits == nullptr) {
            LOGE("❌ Failed to get logits from context (returned null)");
            llama_batch_free(batch);
            return "[ERROR: Logits retrieval failed - context may be corrupted]";
        }

        const llama_vocab* vocab = llama_model_get_vocab(g_model);
        const int n_vocab = llama_vocab_n_tokens(vocab);

        llama_token_data_array candidates;
        candidates.data = new llama_token_data[n_vocab];
        candidates.size = n_vocab;
        for (int token_id = 0; token_id < candidates.size; ++token_id) {
            candidates.data[token_id].id = token_id;
            candidates.data[token_id].logit = logits[token_id];
            candidates.data[token_id].p = 0.0f;
        }

        llama_token_data_array cur_p = { candidates.data, candidates.size, -1 };

        // Create a sampler chain for this token
        llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
        llama_sampler* sampler_chain = llama_sampler_chain_init(chain_params);

        // Add samplers to the chain
        llama_sampler_chain_add(sampler_chain, llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f)); // repetition penalty
        llama_sampler_chain_add(sampler_chain, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler_chain, llama_sampler_init_min_p(0.05f, 1));
        llama_sampler_chain_add(sampler_chain, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(sampler_chain, llama_sampler_init_temp(0.8f));
        llama_sampler_chain_add(sampler_chain, llama_sampler_init_dist(0)); // greedy sampling

        // Apply the sampler chain
        llama_sampler_apply(sampler_chain, &cur_p);

        llama_token new_token_id = cur_p.data[cur_p.selected].id;

        llama_sampler_free(sampler_chain);
        delete[] candidates.data;

        // Check for End-of-Sequence
        if (new_token_id == llama_vocab_eos(vocab)) {
            LOGI("End of generation (EOS token).");
            break;
        }

        // Append token to result string
        char piece_buf[256];
        int piece_len = llama_token_to_piece(vocab, new_token_id, piece_buf, sizeof(piece_buf), 0, false);
        if (piece_len > 0) {
            result_str.append(piece_buf, std::min(piece_len, (int)sizeof(piece_buf)));
        }

        // Prepare for next iteration
        llama_batch_free(batch);
        batch = llama_batch_init(1, 0, 1);
        batch.n_tokens = 1;
        batch.token[0] = new_token_id;
        batch.pos[0] = n_current;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        
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

#if 0 // TODO: Vision features disabled - llama_image API not available in current llama.cpp
/**
 * Main generation function using the corrected llama.cpp workflow for multimodal input.
 */
static std::string llama_decode_and_generate_multimodal(const std::string& prompt_str, const std::vector<uint8_t>& image_bytes, int max_tokens) {
    LOGI("🖼️ Generating response for multimodal input (prompt: %.80s..., image size: %zu bytes)...", prompt_str.c_str(), image_bytes.size());

    // Clear the KV cache from previous runs
    llama_kv_cache_clear(g_ctx);

    // 1. Create image embed
    llama_image_embed* image_embed = llama_image_embed_make_with_bytes(g_ctx, g_model, image_bytes.data(), image_bytes.size());
    if (image_embed == nullptr) {
        LOGE("Failed to create image embed from bytes.");
        return "[ERROR: Image embedding failed]";
    }
    LOGI("Image embed created. N_image_tokens: %d", llama_image_embed_n_tokens(image_embed));

    // 2. Tokenize prompt with image embed
    std::vector<llama_token> prompt_tokens;
    prompt_tokens.reserve(prompt_str.length() + llama_image_embed_n_tokens(image_embed) + 1); // Reserve space

    // Add BOS token
    prompt_tokens.push_back(llama_token_bos(g_model));

    // Add image tokens
    llama_image_embed_tokenize_to_vector(g_model, image_embed, prompt_tokens);

    // Add text tokens
    int n_text_tokens = llama_tokenize(g_model, prompt_str.c_str(), prompt_str.length(), nullptr, 0, false, false);
    if (n_text_tokens < 0) {
        LOGE("Failed to tokenize text prompt for multimodal (buffer too small). Required size: %d", -n_text_tokens);
        // Resize and try again
        std::vector<llama_token> text_tokens_temp;
        text_tokens_temp.resize(-n_text_tokens);
        n_text_tokens = llama_tokenize(g_model, prompt_str.c_str(), prompt_str.length(), text_tokens_temp.data(), text_tokens_temp.size(), false, false);
        prompt_tokens.insert(prompt_tokens.end(), text_tokens_temp.begin(), text_tokens_temp.end());
    } else {
        std::vector<llama_token> text_tokens_temp;
        text_tokens_temp.resize(n_text_tokens);
        llama_tokenize(g_model, prompt_str.c_str(), prompt_str.length(), text_tokens_temp.data(), text_tokens_temp.size(), false, false);
        prompt_tokens.insert(prompt_tokens.end(), text_tokens_temp.begin(), text_tokens_temp.end());
    }

    // Add EOS token if not already present
    if (prompt_tokens.empty() || prompt_tokens.back() != llama_token_eos(g_model)) {
        prompt_tokens.push_back(llama_token_eos(g_model));
    }

    llama_image_embed_free(image_embed); // Free image embed after tokenization

    if (prompt_tokens.empty()) {
        LOGE("Multimodal tokenization resulted in 0 tokens.");
        return "[ERROR: Multimodal tokenization failed]";
    }
    LOGI("Tokenized multimodal prompt into %zu tokens.", prompt_tokens.size());

    // --- Process Prompt ---
    llama_batch batch = llama_batch_init(prompt_tokens.size(), 0, 1);
    for (size_t i = 0; i < prompt_tokens.size(); ++i) {
        llama_batch_add(batch, prompt_tokens[i], i, {0}, true);
    }
    batch.logits[batch.n_tokens - 1] = 1; // Request logit for the last token

    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode multimodal prompt.");
        llama_batch_free(batch);
        return "[ERROR: Multimodal prompt decoding failed]";
    }
    LOGI("Multimodal prompt decoded successfully.");

    // --- Generate Response ---
    std::string result_str;
    int n_current = prompt_tokens.size();

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
#endif // Vision features disabled
