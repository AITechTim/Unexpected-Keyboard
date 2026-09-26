#include <jni.h>
#include "predictor_core.h"

using keyboard::Predictor;
static Predictor * ptr(jlong h) { return reinterpret_cast<Predictor *>(h); }
static std::string bytes(JNIEnv * env, jbyteArray a) {
    std::string s(env->GetArrayLength(a), '\0');
    env->GetByteArrayRegion(a, 0, s.size(), reinterpret_cast<jbyte *>(&s[0]));
    return s;
}
extern "C" {
JNIEXPORT jlong JNICALL Java_juloo_keyboard2_prediction_runtime_NativePredictor_create(JNIEnv *, jclass) {
    try { return reinterpret_cast<jlong>(new Predictor()); }
    catch (const std::exception &) { return 0; }
}
JNIEXPORT jboolean JNICALL Java_juloo_keyboard2_prediction_runtime_NativePredictor_load(JNIEnv * env, jclass, jlong h, jstring file) {
    const char * path = env->GetStringUTFChars(file, nullptr);
    bool ok = false;
    try { ok = ptr(h)->load(path); }
    catch (const std::exception &) {}
    env->ReleaseStringUTFChars(file, path);
    return ok;
}
JNIEXPORT jobjectArray JNICALL Java_juloo_keyboard2_prediction_runtime_NativePredictor_predict(JNIEnv * env, jclass, jlong h, jbyteArray context, jbyteArray prefix, jboolean phrase) {
    std::vector<std::string> words;
    try { words = ptr(h)->predict(bytes(env, context), bytes(env, prefix), phrase ? 500 : 250, phrase); }
    catch (const std::exception &) { ptr(h)->reset(); }
    jclass byte_array = env->FindClass("[B");
    jobjectArray result = env->NewObjectArray(words.size(), byte_array, nullptr);
    for (size_t i = 0; i < words.size(); ++i) {
        jbyteArray word = env->NewByteArray(words[i].size());
        env->SetByteArrayRegion(word, 0, words[i].size(), reinterpret_cast<const jbyte *>(words[i].data()));
        env->SetObjectArrayElement(result, i, word);
        env->DeleteLocalRef(word);
    }
    return result;
}
JNIEXPORT void JNICALL Java_juloo_keyboard2_prediction_runtime_NativePredictor_cancel(JNIEnv *, jclass, jlong h) { ++ptr(h)->cancellation; }
JNIEXPORT void JNICALL Java_juloo_keyboard2_prediction_runtime_NativePredictor_reset(JNIEnv *, jclass, jlong h) { ptr(h)->reset(); }
JNIEXPORT void JNICALL Java_juloo_keyboard2_prediction_runtime_NativePredictor_destroy(JNIEnv *, jclass, jlong h) { delete ptr(h); }
}
