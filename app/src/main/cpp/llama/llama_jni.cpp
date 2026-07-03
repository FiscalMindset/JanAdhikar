// JNI bridge over llama.cpp — runs a local GGUF chat model (Qwen 2.5 1.5B) on
// device, 100% offline. Non-streaming: generate() returns the full completion.
// Mirrors the whisper JNI: no ggml symbols leak past this surface.
#include <jni.h>
#include <android/log.h>
#include "llama.h"
#include <string>
#include <vector>

#define LOG_TAG "JanadhikarLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
struct LlamaHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_sampler *smpl = nullptr;
};
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_janadhikar_llm_LlamaBridge_nativeLoad(
        JNIEnv *env, jobject, jstring jpath, jint nCtx, jint nThreads) {
    static bool backendReady = false;
    if (!backendReady) { llama_backend_init(); backendReady = true; }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU only — reliable across phones
    llama_model *model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) { LOGE("model load failed"); return 0; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) nCtx;
    cp.n_threads = nThreads;
    cp.n_threads_batch = nThreads;
    cp.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED; // faster attention on ARM
    llama_context *ctx = llama_init_from_model(model, cp);
    if (!ctx) { LOGE("ctx init failed"); llama_model_free(model); return 0; }

    auto *h = new LlamaHandle();
    h->model = model;
    h->ctx = ctx;
    h->vocab = llama_model_get_vocab(model);
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    // Low-temperature sampling: coherent, near-deterministic, still natural.
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    h->smpl = smpl;
    LOGI("model loaded (n_ctx=%d, threads=%d)", nCtx, nThreads);
    return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_janadhikar_llm_LlamaBridge_nativeGenerate(
        JNIEnv *env, jobject, jlong handle, jstring jprompt, jint maxTokens) {
    auto *h = reinterpret_cast<LlamaHandle *>(handle);
    if (!h) return env->NewStringUTF("");

    const char *cprompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string prompt(cprompt);
    env->ReleaseStringUTFChars(jprompt, cprompt);

    // Fresh generation → wipe the KV cache from any previous call.
    llama_memory_clear(llama_get_memory(h->ctx), true);

    const int n_prompt = -llama_tokenize(h->vocab, prompt.c_str(), (int32_t) prompt.size(),
                                         nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(h->vocab, prompt.c_str(), (int32_t) prompt.size(),
                       tokens.data(), (int32_t) tokens.size(), true, true) < 0) {
        LOGE("tokenize failed");
        return env->NewStringUTF("");
    }

    std::string result;
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    for (int i = 0; i < maxTokens; i++) {
        if (llama_decode(h->ctx, batch) != 0) { LOGE("decode failed"); break; }
        llama_token tok = llama_sampler_sample(h->smpl, h->ctx, -1);
        if (llama_vocab_is_eog(h->vocab, tok)) break;
        char piece[256];
        int n = llama_token_to_piece(h->vocab, tok, piece, sizeof(piece), 0, true);
        if (n < 0) break;
        result.append(piece, n);
        batch = llama_batch_get_one(&tok, 1);
    }
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_janadhikar_llm_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    auto *h = reinterpret_cast<LlamaHandle *>(handle);
    if (!h) return;
    if (h->smpl) llama_sampler_free(h->smpl);
    if (h->ctx) llama_free(h->ctx);
    if (h->model) llama_model_free(h->model);
    delete h;
}
