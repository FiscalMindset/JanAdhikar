// JNI bridge for whisper.cpp.
// Contract: exposes ONLY com.janadhikar.stt.WhisperBridge natives.
// No ggml/whisper symbol may escape this translation unit.
#include <jni.h>
#include <android/log.h>
#include <string>
#include "whisper.h"

#define LOG_TAG "JanadhikarWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Kotlin: companion object @JvmStatic → static native on the enclosing class.
JNIEXPORT jlong JNICALL
Java_com_janadhikar_stt_WhisperBridge_nativeInit(
        JNIEnv *env, jclass /*clazz*/, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, params);
    env->ReleaseStringUTFChars(model_path, path);
    if (ctx == nullptr) {
        LOGE("whisper init failed");
        return 0;
    }
    LOGI("whisper context initialised");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_janadhikar_stt_WhisperBridge_nativeRelease(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong ctx_ptr) {
    if (ctx_ptr != 0) {
        whisper_free(reinterpret_cast<whisper_context *>(ctx_ptr));
    }
}

// Full-window decode: the Kotlin side re-transcribes the growing PCM window
// (~every 2 s) and replaces its transcript wholesale. lang_code: "en" | "hi" | "auto".
JNIEXPORT jstring JNICALL
Java_com_janadhikar_stt_WhisperBridge_nativeTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong ctx_ptr,
        jfloatArray pcm, jstring lang_code) {
    auto *ctx = reinterpret_cast<whisper_context *>(ctx_ptr);
    if (ctx == nullptr) return env->NewStringUTF("");

    // Guard: whisper_full on a near-empty buffer can fault inside ggml.
    jsize guard_n = env->GetArrayLength(pcm);
    if (guard_n < WHISPER_SAMPLE_RATE / 2) return env->NewStringUTF("");

    const char *lang = env->GetStringUTFChars(lang_code, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang;                 // "auto" enables EN/HI detection
    params.translate = false;               // transcribe, never translate — the
                                            // LLM layer owns language, not STT
    params.no_context = true;               // each window decoded independently
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.n_threads = 4;

    jsize n_samples = env->GetArrayLength(pcm);
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    int rc = whisper_full(ctx, params, samples, static_cast<int>(n_samples));
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang_code, lang);

    if (rc != 0) {
        LOGE("whisper_full failed rc=%d", rc);
        return env->NewStringUTF("");
    }

    std::string transcript;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        transcript += whisper_full_get_segment_text(ctx, i);
    }
    return env->NewStringUTF(transcript.c_str());
}

} // extern "C"
