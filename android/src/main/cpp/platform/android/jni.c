#include "warp.h"
#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>

// JNI implementation for WarpNative Java class

static warp_context_t* g_warp_ctx = NULL;
static JavaVM* g_jvm = NULL;
static jobject g_warp_native_obj = NULL;
static jclass g_warp_native_class = NULL;

// Method IDs
static jmethodID mid_on_packet_received = NULL;
static jmethodID mid_on_handshake_complete = NULL;
static jmethodID mid_on_state_changed = NULL;
static jmethodID mid_on_error = NULL;

// JNI_OnLoad
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Find WarpNative class
    jclass clazz = (*env)->FindClass(env, "com/troubadour/warp/WarpNative");
    if (!clazz) {
        __android_log_print(ANDROID_LOG_ERROR, "WarpNative", "Failed to find WarpNative class");
        return JNI_ERR;
    }
    g_warp_native_class = (jclass)(*env)->NewGlobalRef(env, clazz);

    // Get method IDs
    mid_on_packet_received = (*env)->GetStaticMethodID(env, clazz, "onPacketReceived",
                                                       "([BII)V");
    mid_on_handshake_complete = (*env)->GetStaticMethodID(env, clazz, "onHandshakeComplete",
                                                          "(Z)V");
    mid_on_state_changed = (*env)->GetStaticMethodID(env, clazz, "onStateChanged",
                                                     "(I)V");
    mid_on_error = (*env)->GetStaticMethodID(env, clazz, "onError",
                                             "(ILjava/lang/String;)V");

    if (!mid_on_packet_received || !mid_on_handshake_complete ||
        !mid_on_state_changed || !mid_on_error) {
        __android_log_print(ANDROID_LOG_ERROR, "WarpNative", "Failed to get method IDs");
        return JNI_ERR;
    }

    __android_log_print(ANDROID_LOG_INFO, "WarpNative", "JNI loaded successfully");
    return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        if (g_warp_native_class) {
            (*env)->DeleteGlobalRef(env, g_warp_native_obj);
            (*env)->DeleteGlobalRef(env, g_warp_native_class);
        }
    }
}

// Helper: Get JNIEnv for current thread
static JNIEnv* get_jni_env() {
    JNIEnv* env;
    int status = (*g_jvm)->GetEnv(g_jvm, (void**)&env, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) != JNI_OK) {
            return NULL;
        }
    }
    return env;
}

// Native method implementations

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_warpInit
(JNIEnv* env, jclass clazz, jstring endpoint, jint port,
 jstring privateKey, jstring publicKey,
 jstring presharedKey,
 jstring reserved1, jstring reserved2, jstring reserved3,
 jboolean enableObfuscation) {

    if (!g_warp_ctx) {
        g_warp_ctx = calloc(1, sizeof(warp_context_t));
        if (!g_warp_ctx) return WARP_ERROR_INIT;
        warp_init(g_warp_ctx);
    }

    // Parse strings
    const char* endpoint_str = (*env)->GetStringUTFChars(env, endpoint, NULL);
    const char* private_key_str = (*env)->GetStringUTFChars(env, privateKey, NULL);
    const char* public_key_str = (*env)->GetStringUTFChars(env, publicKey, NULL);
    const char* preshared_str = presharedKey ? (*env)->GetStringUTFChars(env, presharedKey, NULL) : NULL;
    const char* r1_str = reserved1 ? (*env)->GetStringUTFChars(env, reserved1, NULL) : NULL;
    const char* r2_str = reserved2 ? (*env)->GetStringUTFChars(env, reserved2, NULL) : NULL;
    const char* r3_str = reserved3 ? (*env)->GetStringUTFChars(env, reserved3, NULL) : NULL;

    // Parse keys from base64
    // In real implementation, would decode base64
    // For now, just copy
    uint8_t private_key[32] = {0};
    uint8_t public_key[32] = {0};
    uint8_t preshared_key[32] = {0};
    amnezia_obfs_params_t obfs_params = {0};

    int ret = warp_set_endpoint(g_warp_ctx, endpoint_str, (uint16_t)port);
    if (ret == WARP_OK) {
        ret = warp_set_private_key(g_warp_ctx, g_warp_ctx->local_private);
    }
    if (ret == WARP_OK) {
        ret = warp_set_peer_public_key(g_warp_ctx, g_warp_ctx->peer_public);
    }
    if (ret == WARP_OK && preshared_key) {
        ret = warp_set_preshared_key(g_warp_ctx, preshared_key);
    }
    if (ret == WARP_OK && enableObfuscation) {
        amnezia_obfs_params_t params = {0};
        // Parse obfuscation params from strings
        // ...
        ret = warp_set_obfuscation(g_warp_ctx, true, &params);
    }

    // Release strings
    (*env)->ReleaseStringUTFChars(env, endpoint, endpoint_str);
    (*env)->ReleaseStringUTFChars(env, privateKey, private_key_str);
    (*env)->ReleaseStringUTFChars(env, publicKey, public_key_str);
    if (preshared_str) (*env)->ReleaseStringUTFChars(env, presharedKey, preshared_str);
    if (r1_str) (*env)->ReleaseStringUTFChars(env, reserved1, r1_str);
    if (r2_str) (*env)->ReleaseStringUTFChars(env, reserved2, r2_str);
    if (r3_str) (*env)->ReleaseStringUTFChars(env, reserved3, r3_str);

    return ret;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_warpStart
(JNIEnv* env, jclass clazz) {
    if (!g_warp_ctx) return WARP_ERROR_INIT;
    return warp_start(g_warp_ctx);
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_warpStop
(JNIEnv* env, jclass clazz) {
    if (!g_warp_ctx) return WARP_OK;
    return warp_stop(g_warp_ctx);
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_warpSetAllowedIPs
(JNIEnv* env, jclass clazz, jobjectArray allowedIPs) {
    // Parse string array
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_warpSetDNS
(JNIEnv* env, jclass clazz, jobjectArray dnsServers) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_warpSetMTU
(JNIEnv* env, jclass clazz, jint mtu) {
    if (!g_warp_ctx) return WARP_ERROR_CONFIG;
    return warp_set_mtu(g_warp_ctx, (uint32_t)mtu);
}

JNIEXPORT jobject JNICALL Java_com_troubadour_warp_WarpNative_getStats
(JNIEnv* env, jclass clazz) {
    // Create WarpStats object and populate
    jclass statsClass = (*env)->FindClass(env, "com/troubadour/warp/WarpManager$WarpStats");
    if (!statsClass) return NULL;

    jmethodID constructor = (*env)->GetMethodID(env, statsClass, "<init>", "(JJJJ)V");
    if (!constructor) return NULL;

    warp_stats_t stats;
    warp_get_stats(g_warp_ctx, &stats);

    return (*env)->NewObject(env, statsClass, constructor,
                             (jlong)stats.bytes_sent,
                             (jlong)stats.bytes_received,
                             (jlong)stats.packets_sent,
                             (jlong)stats.packets_received);
}

JNIEXPORT jstring JNICALL Java_com_troubadour_warp_WarpNative_getVersion
(JNIEnv* env, jclass clazz) {
    return (*env)->NewStringUTF(env, warp_get_version());
}

// Key generation
JNIEXPORT jstring JNICALL Java_com_troubadour_warp_WarpNative_generatePrivateKey
(JNIEnv* env, jclass clazz) {
    uint8_t private_key[32];
    warp_generate_keypair(NULL, NULL); // Would generate
    // Encode to base64
    return (*env)->NewStringUTF(env, "base64_private_key_placeholder");
}

JNIEXPORT jstring JNICALL Java_com_troubadour_warp_WarpNative_derivePublicKey
(JNIEnv* env, jclass clazz, jstring privateKey) {
    return (*env)->NewStringUTF(env, "base64_public_key_placeholder");
}

JNIEXPORT jstring JNICALL Java_com_troubadour_warp_WarpNative_generatePresharedKey
(JNIEnv* env, jclass clazz) {
    return (*env)->NewStringUTF(env, "base64_preshared_key_placeholder");
}

// Handshake
JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_warpHandshake
(JNIEnv* env, jclass clazz) {
    if (!g_warp_ctx) return WARP_ERROR_INIT;
    return warp_handshake(g_warp_ctx);
}

// Crypto operations
JNIEXPORT jbyteArray JNICALL Java_com_troubadour_warp_WarpNative_cryptoCurve25519
(JNIEnv* env, jclass clazz, jbyteArray privateKey, jbyteArray basePoint) {
    // Implementation
    return (*env)->NewByteArray(env, 32);
}

JNIEXPORT jbyteArray JNICALL Java_com_troubadour_warp_WarpNative_cryptoChaCha20Poly1305Encrypt
(JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce,
 jbyteArray plaintext, jbyteArray aad) {
    return (*env)->NewByteArray(env, 0);
}

JNIEXPORT jbyteArray JNICALL Java_com_troubadour_warp_WarpNative_cryptoChaCha20Poly1305Decrypt
(JNIEnv* env, jclass clazz, jbyteArray key, jbyteArray nonce,
 jbyteArray ciphertext, jbyteArray aad) {
    return (*env)->NewByteArray(env, 0);
}

JNIEXPORT jbyteArray JNICALL Java_com_troubadour_warp_WarpNative_cryptoBlake2s
(JNIEnv* env, jclass clazz, jbyteArray input, jbyteArray key) {
    return (*env)->NewByteArray(env, 32);
}

// Configuration
JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setEndpoint
(JNIEnv* env, jclass clazz, jstring endpoint, jint port) {
    if (!g_warp_ctx) return WARP_ERROR_CONFIG;
    const char* ep = (*env)->GetStringUTFChars(env, endpoint, NULL);
    int ret = warp_set_endpoint(g_warp_ctx, ep, (uint16_t)port);
    (*env)->ReleaseStringUTFChars(env, endpoint, ep);
    return ret;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setPrivateKey
(JNIEnv* env, jclass clazz, jbyteArray privateKey) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setPublicKey
(JNIEnv* env, jclass clazz, jbyteArray publicKey) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setPresharedKey
(JNIEnv* env, jclass clazz, jbyteArray presharedKey) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setObfuscationEnabled
(JNIEnv* env, jclass clazz, jboolean enabled) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setObfuscationParams
(JNIEnv* env, jclass clazz, jbyteArray Jc, jbyte Jmin, jbyteArray Jmax,
 jbyteArray S, jbyteArray H, jbyteArray I) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setAllowedIPs
(JNIEnv* env, jclass clazz, jobjectArray allowedIPs) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setDNS
(JNIEnv* env, jclass clazz, jobjectArray dnsServers) {
    return WARP_OK;
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setMTU
(JNIEnv* env, jclass clazz, jint mtu) {
    if (!g_warp_ctx) return WARP_ERROR_CONFIG;
    return warp_set_mtu(g_warp_ctx, (uint32_t)mtu);
}

JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_setKeepalive
(JNIEnv* env, jclass clazz, jint seconds) {
    return WARP_OK;
}

// Stats
JNIEXPORT jlong JNICALL Java_com_troubadour_warp_WarpNative_getBytesSent
(JNIEnv* env, jclass clazz) {
    warp_stats_t stats;
    warp_get_stats(g_warp_ctx, &stats);
    return (jlong)stats.bytes_sent;
}

JNIEXPORT jlong JNICALL Java_com_troubadour_warp_WarpNative_getBytesReceived
(JNIEnv* env, jclass clazz) {
    warp_stats_t stats;
    warp_get_stats(g_warp_ctx, &stats);
    return (jlong)stats.bytes_received;
}

JNIEXPORT jlong JNICALL Java_com_troubadour_warp_WarpNative_getPacketsSent
(JNIEnv* env, jclass clazz) {
    warp_stats_t stats;
    warp_get_stats(g_warp_ctx, &stats);
    return (jlong)stats.packets_sent;
}

JNIEXPORT jlong JNICALL Java_com_troubadour_warp_WarpNative_getPacketsReceived
(JNIEnv* env, jclass clazz) {
    warp_stats_t stats;
    warp_get_stats(g_warp_ctx, &stats);
    return (jlong)stats.packets_received;
}

// State
JNIEXPORT jint JNICALL Java_com_troubadour_warp_WarpNative_getState
(JNIEnv* env, jclass clazz) {
    if (!g_warp_ctx) return WARP_STATE_STOPPED;
    return g_warp_ctx->state;
}

JNIEXPORT jstring JNICALL Java_com_troubadour_warp_WarpNative_getLastError
(JNIEnv* env, jclass clazz) {
    if (!g_warp_ctx) return (*env)->NewStringUTF(env, "Not initialized");
    return (*env)->NewStringUTF(env, g_warp_ctx->last_error);
}

// Callbacks from native to Java
void WarpNative_onPacketReceived(JNIEnv* env, const uint8_t* packet, int offset, int length) {
    if (!mid_on_packet_received) return;
    jbyteArray array = (*env)->NewByteArray(env, length);
    (*env)->SetByteArrayRegion(env, array, 0, length, (jbyte*)(packet + offset));
    (*env)->CallStaticVoidMethod(env, g_warp_native_class, mid_on_packet_received, array, offset, length);
    (*env)->DeleteLocalRef(env, array);
}

void WarpNative_onHandshakeComplete(JNIEnv* env, jboolean success) {
    if (mid_on_handshake_complete) {
        (*env)->CallStaticVoidMethod(env, g_warp_native_class, mid_on_handshake_complete, success);
    }
}

void WarpNative_onStateChanged(JNIEnv* env, jint state) {
    if (mid_on_state_changed) {
        (*env)->CallStaticVoidMethod(env, g_warp_native_class, mid_on_state_changed, state);
    }
}

void WarpNative_onError(JNIEnv* env, jint errorCode, const char* message) {
    if (mid_on_error) {
        jstring msg = (*env)->NewStringUTF(env, message);
        (*env)->CallStaticVoidMethod(env, g_warp_native_class, mid_on_error, errorCode, msg);
        (*env)->DeleteLocalRef(env, msg);
    }
}

// Helper to get JNIEnv
JNIEnv* warp_get_jni_env() {
    return get_jni_env();
}