/**
 * JNI bridge between Kotlin (LlamaCpp.kt) and llama.cpp C library.
 *
 * Provides model loading, context creation, text generation, and resource
 * cleanup.  All heavy work runs on background threads managed by
 * Kotlin coroutines; this code must be re-entrant but not thread-safe
 * within a single context handle.
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "llama.h"

#ifndef JNICALL
#define JNICALL
#endif

#ifndef JNIEXPORT
#define JNIEXPORT __attribute__((visibility("default")))
#endif

#define TAG "LlamaCppJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* ------------------------------------------------------------------ */
/* Helper: convert jstring → std::string                              */
/* ------------------------------------------------------------------ */
static std::string jstring_to_string(JNIEnv *env, jstring jstr) {
    if (!jstr) return "";
    const char *chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

/* ------------------------------------------------------------------ */
/* loadModel                                                          */
/* ------------------------------------------------------------------ */
extern "C"
JNIEXPORT jlong JNICALL
Java_com_stocksense_app_engine_LlamaCpp_loadModel(
        JNIEnv *env,
        jclass  /* clazz */,
        jstring jpath,
        jint    n_gpu_layers) {

    llama_backend_init();

    std::string path = jstring_to_string(env, jpath);
    LOGI("Loading model: %s  gpu_layers=%d", path.c_str(), n_gpu_layers);

    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = static_cast<int>(n_gpu_layers);

    llama_model *model = llama_model_load_from_file(path.c_str(), params);
    if (!model) {
        LOGE("Failed to load model from %s", path.c_str());
        return 0;
    }
    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(model);
}

/* ------------------------------------------------------------------ */
/* createContext                                                       */
/* ------------------------------------------------------------------ */
extern "C"
JNIEXPORT jlong JNICALL
Java_com_stocksense_app_engine_LlamaCpp_createContext(
        JNIEnv */* env */,
        jclass  /* clazz */,
        jlong   model_handle,
        jint    context_size) {

    auto *model = reinterpret_cast<llama_model *>(model_handle);
    if (!model) return 0;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx   = static_cast<uint32_t>(context_size);
    ctx_params.n_batch = static_cast<uint32_t>(context_size);

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context (n_ctx=%d)", context_size);
        return 0;
    }
    LOGI("Context created (n_ctx=%d)", context_size);
    return reinterpret_cast<jlong>(ctx);
}

/* ------------------------------------------------------------------ */
/* runInference                                                        */
/* ------------------------------------------------------------------ */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_stocksense_app_engine_LlamaCpp_runInference(
        JNIEnv *env,
        jclass  /* clazz */,
        jlong   ctx_handle,
        jstring jprompt,
        jint    max_tokens) {

    auto *ctx = reinterpret_cast<llama_context *>(ctx_handle);
    if (!ctx) {
        return env->NewStringUTF("[error: null context]");
    }

    // Clear model memory before each new inference so previous calls don't
    // corrupt positions and cause context-overflow on repeated use.
    // llama.cpp replaced llama_kv_cache_clear() with the generic memory API.
    if (auto *memory = llama_get_memory(ctx)) {
        llama_memory_clear(memory, true);
    }

    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);
    std::string prompt = jstring_to_string(env, jprompt);

    /* Tokenize the prompt. */
    int n_prompt = prompt.size() + 16;
    std::vector<llama_token> tokens(n_prompt);
    n_prompt = llama_tokenize(vocab, prompt.c_str(),
                              static_cast<int32_t>(prompt.size()),
                              tokens.data(),
                              static_cast<int32_t>(tokens.size()),
                              /* add_special */ true,
                              /* parse_special */ true);
    if (n_prompt < 0) {
        tokens.resize(-n_prompt);
        n_prompt = llama_tokenize(vocab, prompt.c_str(),
                                  static_cast<int32_t>(prompt.size()),
                                  tokens.data(),
                                  static_cast<int32_t>(tokens.size()),
                                  true, true);
    }
    tokens.resize(n_prompt);

    /* Create a batch and evaluate. */
    llama_batch batch = llama_batch_init(n_prompt + max_tokens, 0, 1);

    for (int i = 0; i < n_prompt; i++) {
        batch.token[i] = tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }
    batch.n_tokens = n_prompt;
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("[error: decode failed]");
    }

    /* Greedy sampling loop.
     * After the prompt decode only the last-position logits are enabled.
     * Use llama_get_logits_ith(ctx, -1) which always returns the logits for
     * the LAST enabled row — works for both the initial prompt-end token and
     * each single-token generation step.                                     */
    std::string result;
    int n_decoded = 0;
    int cur_pos = n_prompt;  // track absolute position in sequence
    while (n_decoded < max_tokens) {
        // -1 index = last enabled logit row (safe after any decode that enables exactly one row).
        float *logits = llama_get_logits_ith(ctx, -1);
        if (!logits) break;

        int vocab_size = llama_vocab_n_tokens(vocab);
        int max_id = 0;
        float max_logit = logits[0];
        for (int i = 1; i < vocab_size; ++i) {
            if (logits[i] > max_logit) {
                max_logit = logits[i];
                max_id = i;
            }
        }
        llama_token new_token = max_id;
        if (new_token == llama_vocab_eos(vocab)) break;

        // Convert token to text
        char buf[256];
        int len = llama_token_to_piece(vocab, new_token, buf, sizeof(buf),
                                       /* lstrip */ 0, /* special */ false);
        if (len > 0) {
            result.append(buf, len);
        }

        // Prepare next batch with the generated token
        batch.token[0] = new_token;
        batch.pos[0] = cur_pos;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        batch.n_tokens = 1;
        cur_pos++;

        if (llama_decode(ctx, batch) != 0) break;
        n_decoded++;
    }

    llama_batch_free(batch);

    LOGI("Generated %d tokens", n_decoded);
    return env->NewStringUTF(result.c_str());
}

/* ------------------------------------------------------------------ */
/* freeContext                                                          */
/* ------------------------------------------------------------------ */
extern "C"
JNIEXPORT void JNICALL
Java_com_stocksense_app_engine_LlamaCpp_freeContext(
        JNIEnv */* env */,
        jclass  /* clazz */,
        jlong   ctx_handle) {

    auto *ctx = reinterpret_cast<llama_context *>(ctx_handle);
    if (ctx) {
        llama_free(ctx);
        LOGI("Context freed");
    }
}

/* ------------------------------------------------------------------ */
/* freeModel                                                           */
/* ------------------------------------------------------------------ */
extern "C"
JNIEXPORT void JNICALL
Java_com_stocksense_app_engine_LlamaCpp_freeModel(
        JNIEnv */* env */,
        jclass  /* clazz */,
        jlong   model_handle) {

    auto *model = reinterpret_cast<llama_model *>(model_handle);
    if (model) {
        llama_model_free(model);
        llama_backend_free();
        LOGI("Model freed");
    }
}
