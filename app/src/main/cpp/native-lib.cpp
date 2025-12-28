#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "YTSec-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// XOR encryption key (hidden in native code)
static const char ENCRYPTION_KEY[] = "YTPro2025SecureKey";
static const int KEY_LENGTH = 18;

/**
 * Simple XOR encryption/decryption
 * XOR is symmetric - same function for encrypt and decrypt
 */
std::string xorCrypt(const std::string& input) {
    std::string output = input;
    
    for (size_t i = 0; i < input.length(); i++) {
        output[i] = input[i] ^ ENCRYPTION_KEY[i % KEY_LENGTH];
    }
    
    return output;
}

/**
 * JNI Native Method: Encrypt
 * Called from Java: NativeSec.enc(String)
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_enc(JNIEnv *env, jclass clazz, jstring data) {
    
    // Null check
    if (data == nullptr) {
        LOGE("Encrypt: null input received");
        return env->NewStringUTF("");
    }
    
    // Get input string
    const char* input = env->GetStringUTFChars(data, nullptr);
    if (input == nullptr) {
        LOGE("Encrypt: failed to get string chars");
        return env->NewStringUTF("");
    }
    
    // Encrypt
    std::string encrypted = xorCrypt(std::string(input));
    
    // Release input
    env->ReleaseStringUTFChars(data, input);
    
    LOGI("✅ Encrypted: %zu bytes", encrypted.length());
    
    // Return encrypted string
    return env->NewStringUTF(encrypted.c_str());
}

/**
 * JNI Native Method: Decrypt
 * Called from Java: NativeSec.dec(String)
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_dec(JNIEnv *env, jclass clazz, jstring data) {
    
    // Null check
    if (data == nullptr) {
        LOGE("Decrypt: null input received");
        return env->NewStringUTF("");
    }
    
    // Get input string
    const char* input = env->GetStringUTFChars(data, nullptr);
    if (input == nullptr) {
        LOGE("Decrypt: failed to get string chars");
        return env->NewStringUTF("");
    }
    
    // Decrypt (XOR is symmetric, same function)
    std::string decrypted = xorCrypt(std::string(input));
    
    // Release input
    env->ReleaseStringUTFChars(data, input);
    
    LOGI("✅ Decrypted: %zu bytes", decrypted.length());
    
    // Return decrypted string
    return env->NewStringUTF(decrypted.c_str());
}
