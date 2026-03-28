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
#include "common.h"

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
extern "C" JNIEXPORT jlong JNICALL
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
extern "C" JNIEXPORT jlong JNICALL
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
extern "C" JNIEXPORT jstring JNICALL
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
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("[error: decode failed]");
    }

    /* Greedy sampling loop. */
    std::string result;
    const llama_token eos = llama_vocab_eos(vocab);
    int n_decoded = 0;

    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    while (n_decoded < max_tokens) {
        llama_token new_token = llama_sampler_sample(sampler, ctx, batch.n_tokens - 1);

        if (llama_vocab_is_eog(vocab, new_token)) break;

        /* Convert token to text. */
        char buf[256];
        int len = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
        }

        /* Prepare next batch with the generated token. */
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token, n_prompt + n_decoded, {0}, true);

        if (llama_decode(ctx, batch) != 0) break;
        n_decoded++;
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);
    llama_kv_cache_clear(ctx);

    LOGI("Generated %d tokens", n_decoded);
    return env->NewStringUTF(result.c_str());
}

/* ------------------------------------------------------------------ */
/* freeContext                                                          */
/* ------------------------------------------------------------------ */
extern "C" JNIEXPORT void JNICALL
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
extern "C" JNIEXPORT void JNICALL
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
