#include <jni.h>
#include <string>

// 🔐 XOR Decryption
std::string xorDecrypt(const std::string& encrypted, const std::string& key) {
    std::string decrypted;
    size_t keyLen = key.length();
    
    for (size_t i = 0; i < encrypted.length(); i++) {
        decrypted += encrypted[i] ^ key[i % keyLen];
    }
    
    return decrypted;
}

// 🔐 Encrypted URLs (Raw bytes)
const std::string ENC_YOUTUBE_BASE = "\x16\x13\x19\x04\x04\x1e\x11\x15\x02\x11\x1c\x0b\x07\x16\x1e\x15\x17\x14\x1f\x02\x1e\x1d\x1b\x0d\x06\x16\x0f";
const std::string ENC_INNERTUBE = "\x16\x13\x19\x04\x04\x1e\x11\x15\x02\x11\x1c\x08\x1e\x10\x1e\x0b\x16\x19\x07\x0f\x10\x0f\x1a\x1b\x0e\x03\x1e\x12\x15\x10\x1c\x1a\x1c\x02\x1e\x1f\x12\x1f\x1a\x1b\x06\x10\x0f\x1d\x18\x0f\x19\x1e\x1c\x17\x0f\x1c\x0d\x1e\x1c\x15\x11\x16\x0f\x10\x02\x18\x16\x19\x16\x1c\x1f\x02\x1b\x1e\x15";
const std::string ENC_BGPLAY = "\x16\x13\x19\x04\x04\x1e\x11\x15\x02\x11\x1c\x08\x1e\x10\x1e\x0b\x16\x19\x07\x0f\x10\x0f\x1a\x1b\x0e\x03\x1e\x12\x15\x10\x1c\x1a\x1c\x02\x1e\x1f\x12\x1f\x1a\x1b\x06\x10\x0f\x1d\x18\x0f\x19\x1e\x1c\x17\x0f\x1c\x0d\x1e\x1c\x15\x11\x16\x0f\x0e\x06\x1a\x16\x1a\x16\x0f\x1f\x02\x1b\x1e\x15";
const std::string ENC_SCRIPT = "\x16\x13\x19\x04\x04\x1e\x11\x15\x02\x11\x1c\x08\x1e\x10\x1e\x0b\x16\x19\x07\x0f\x10\x0f\x1a\x1b\x0e\x03\x1e\x12\x15\x10\x1c\x1a\x1c\x02\x1e\x1f\x12\x1f\x1a\x1b\x06\x10\x0f\x1d\x18\x0f\x19\x1e\x1c\x17\x0f\x1c\x0d\x1e\x1c\x15\x11\x16\x0f\x10\x1b\x1a\x16\x1a\x16\x0f\x1f\x02\x1b\x1e";
const std::string ENC_CDN_BASE = "\x1a\x0d\x06\x16\x1e\x07\x1a\x0b\x0f\x12\x19\x07\x0f\x10\x0f\x1a\x1b\x0e\x03\x1e\x12\x15\x10\x1c\x1a\x1c\x02\x1e\x1f\x12\x1f\x1a\x1b\x06\x10\x0f\x1d\x18\x0f\x19\x1e\x1c\x17\x0f\x1c\x0d\x1e\x1c\x15\x11\x16\x0f";
const std::string ENC_ESM = "\x1a\x1b\x0f\x16\x0f\x1d\x11";
const std::string ENC_API_URL = "\x16\x13\x19\x04\x04\x1e\x11\x15\x02\x11\x1c\x1a\x1a\x12\x1e\x06\x11\x1b\x0e\x03\x1e\x1d\x15\x1a\x1c\x18\x1c\x02\x1e\x1f\x12\x1f\x1a\x1b\x1d\x10\x1c\x1c\x1e\x11\x1b\x1e\x1b\x16\x1c\x1f\x15\x11\x16\x1a\x1e\x1f\x05";

const std::string SECRET_KEY = "spmods";

// 🔓 Native Methods
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getYouTubeUrl(JNIEnv* env, jobject) {
    std::string decrypted = xorDecrypt(ENC_YOUTUBE_BASE, SECRET_KEY);
    return env->NewStringUTF(decrypted.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getInnerTubeUrl(JNIEnv* env, jobject) {
    std::string decrypted = xorDecrypt(ENC_INNERTUBE, SECRET_KEY);
    return env->NewStringUTF(decrypted.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getBgPlayUrl(JNIEnv* env, jobject) {
    std::string decrypted = xorDecrypt(ENC_BGPLAY, SECRET_KEY);
    return env->NewStringUTF(decrypted.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getScriptUrl(JNIEnv* env, jobject) {
    std::string decrypted = xorDecrypt(ENC_SCRIPT, SECRET_KEY);
    return env->NewStringUTF(decrypted.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getCdnBase(JNIEnv* env, jobject) {
    std::string decrypted = xorDecrypt(ENC_CDN_BASE, SECRET_KEY);
    return env->NewStringUTF(decrypted.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getEsmDomain(JNIEnv* env, jobject) {
    std::string decrypted = xorDecrypt(ENC_ESM, SECRET_KEY);
    return env->NewStringUTF(decrypted.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getApiUrl(JNIEnv* env, jobject) {
    std::string decrypted = xorDecrypt(ENC_API_URL, SECRET_KEY);
    return env->NewStringUTF(decrypted.c_str());
}
