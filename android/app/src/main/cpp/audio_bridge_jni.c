#include "audio_engine.h"

#include <jni.h>

static AudioEngine g_engine = {0};

JNIEXPORT jboolean JNICALL
Java_com_audiobridge_AudioBridgeService_nativeStart(
    JNIEnv *env, jobject thiz, jstring server_ip,
    jint mic_port, jint spk_port, jint sample_rate, jint frame_size) {

    (void)thiz;
    if (g_engine.running) engine_stop(&g_engine);

    const char *ip = (*env)->GetStringUTFChars(env, server_ip, NULL);
    if (!ip) return JNI_FALSE;

    jboolean ok = engine_start(&g_engine, ip, (int)mic_port, (int)spk_port,
                                (int)sample_rate, (int)frame_size)
                  ? JNI_TRUE : JNI_FALSE;

    (*env)->ReleaseStringUTFChars(env, server_ip, ip);
    return ok;
}

JNIEXPORT void JNICALL
Java_com_audiobridge_AudioBridgeService_nativeStop(
    JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    if (g_engine.running) engine_stop(&g_engine);
}

JNIEXPORT jfloat JNICALL
Java_com_audiobridge_AudioBridgeService_nativeGetMicLevel(
    JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return g_engine.mic_level;
}

JNIEXPORT jfloat JNICALL
Java_com_audiobridge_AudioBridgeService_nativeGetSpkLevel(
    JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return g_engine.spk_level;
}

JNIEXPORT jlong JNICALL
Java_com_audiobridge_AudioBridgeService_nativeGetTxBytes(
    JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return (jlong)g_engine.tx_bytes;
}

JNIEXPORT jlong JNICALL
Java_com_audiobridge_AudioBridgeService_nativeGetRxBytes(
    JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return (jlong)g_engine.rx_bytes;
}

JNIEXPORT jboolean JNICALL
Java_com_audiobridge_AudioBridgeService_nativeIsRunning(
    JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return g_engine.running ? JNI_TRUE : JNI_FALSE;
}
