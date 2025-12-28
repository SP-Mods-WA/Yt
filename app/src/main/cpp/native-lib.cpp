#include <jni.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include <ctime>
#include <sstream>
#include <iomanip>

#define LOG_TAG "YTProEncrypt"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// XOR encryption key (Change this for production!)
static const char ENCRYPTION_KEY[] = "YTProSecureKey@2024SPMods!";
static const int KEY_LENGTH = sizeof(ENCRYPTION_KEY) - 1;

// Simple XOR encryption/decryption
std::string xorEncryptDecrypt(const std::string& input) {
    std::string output = input;
    
    for (size_t i = 0; i < input.size(); ++i) {
        output[i] = input[i] ^ ENCRYPTION_KEY[i % KEY_LENGTH];
    }
    return output;
}

// Base64 encoding (simple implementation)
std::string base64Encode(const std::string& input) {
    static const char* base64_chars = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "abcdefghijklmnopqrstuvwxyz"
        "0123456789+/";
    
    std::string ret;
    int i = 0;
    int j = 0;
    unsigned char char_array_3[3];
    unsigned char char_array_4[4];
    size_t in_len = input.size();
    const char* bytes_to_encode = input.c_str();
    
    while (in_len--) {
        char_array_3[i++] = *(bytes_to_encode++);
        if (i == 3) {
            char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
            char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
            char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
            char_array_4[3] = char_array_3[2] & 0x3f;
            
            for(i = 0; i < 4; i++)
                ret += base64_chars[char_array_4[i]];
            i = 0;
        }
    }
    
    if (i) {
        for(j = i; j < 3; j++)
            char_array_3[j] = '\0';
        
        char_array_4[0] = (char_array_3[0] & 0xfc) >> 2;
        char_array_4[1] = ((char_array_3[0] & 0x03) << 4) + ((char_array_3[1] & 0xf0) >> 4);
        char_array_4[2] = ((char_array_3[1] & 0x0f) << 2) + ((char_array_3[2] & 0xc0) >> 6);
        char_array_4[3] = char_array_3[2] & 0x3f;
        
        for (j = 0; j < i + 1; j++)
            ret += base64_chars[char_array_4[j]];
        
        while(i++ < 3)
            ret += '=';
    }
    
    return ret;
}

// Base64 decoding
std::string base64Decode(const std::string& input) {
    static const char* base64_chars = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "abcdefghijklmnopqrstuvwxyz"
        "0123456789+/";
    
    int in_len = input.size();
    int i = 0;
    int j = 0;
    int in_ = 0;
    unsigned char char_array_4[4], char_array_3[3];
    std::string ret;
    
    while (in_len-- && (input[in_] != '=')) {
        char_array_4[i++] = input[in_]; in_++;
        if (i == 4) {
            for (i = 0; i < 4; i++)
                char_array_4[i] = strchr(base64_chars, char_array_4[i]) - base64_chars;
            
            char_array_3[0] = (char_array_4[0] << 2) + ((char_array_4[1] & 0x30) >> 4);
            char_array_3[1] = ((char_array_4[1] & 0xf) << 4) + ((char_array_4[2] & 0x3c) >> 2);
            char_array_3[2] = ((char_array_4[2] & 0x3) << 6) + char_array_4[3];
            
            for (i = 0; i < 3; i++)
                ret += char_array_3[i];
            i = 0;
        }
    }
    
    if (i) {
        for (j = i; j < 4; j++)
            char_array_4[j] = 0;
        
        for (j = 0; j < 4; j++)
            char_array_4[j] = strchr(base64_chars, char_array_4[j]) - base64_chars;
        
        char_array_3[0] = (char_array_4[0] << 2) + ((char_array_4[1] & 0x30) >> 4);
        char_array_3[1] = ((char_array_4[1] & 0xf) << 4) + ((char_array_4[2] & 0x3c) >> 2);
        char_array_3[2] = ((char_array_4[2] & 0x3) << 6) + char_array_4[3];
        
        for (j = 0; j < i - 1; j++)
            ret += char_array_3[j];
    }
    
    return ret;
}

// Encrypt string for Java
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_encryptStringNative(
        JNIEnv* env,
        jobject /* this */,
        jstring input) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    if (inputStr == nullptr) {
        return nullptr;
    }
    
    std::string encrypted = xorEncryptDecrypt(std::string(inputStr));
    std::string base64Encoded = base64Encode(encrypted);
    
    env->ReleaseStringUTFChars(input, inputStr);
    
    return env->NewStringUTF(base64Encoded.c_str());
}

// Decrypt string for Java
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_decryptStringNative(
        JNIEnv* env,
        jobject /* this */,
        jstring input) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    if (inputStr == nullptr) {
        return nullptr;
    }
    
    std::string base64Decoded = base64Decode(std::string(inputStr));
    std::string decrypted = xorEncryptDecrypt(base64Decoded);
    
    env->ReleaseStringUTFChars(input, inputStr);
    
    return env->NewStringUTF(decrypted.c_str());
}

// Encrypted URL validation
extern "C" JNIEXPORT jboolean JNICALL
Java_com_spmods_ytpro_MainActivity_isValidEncryptedUrl(
        JNIEnv* env,
        jobject /* this */,
        jstring encryptedUrl) {
    const char* url = env->GetStringUTFChars(encryptedUrl, nullptr);
    if (url == nullptr) {
        return JNI_FALSE;
    }
    
    std::string decryptedUrl = xorEncryptDecrypt(base64Decode(std::string(url)));
    env->ReleaseStringUTFChars(encryptedUrl, url);
    
    bool isValid = (decryptedUrl.find("youtube.com") != std::string::npos || 
                    decryptedUrl.find("youtu.be") != std::string::npos ||
                    decryptedUrl.find("http") != std::string::npos);
    
    return isValid ? JNI_TRUE : JNI_FALSE;
}

// Get encrypted API endpoints
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getEncryptedEndpoint(
        JNIEnv* env,
        jobject /* this */,
        jint endpointType) {
    
    std::string endpoint;
    
    switch(endpointType) {
        case 0: // Script endpoint
            endpoint = "https://youtube.com/ytpro_cdn/npm/ytpro/script.js";
            break;
        case 1: // BG Play endpoint
            endpoint = "https://youtube.com/ytpro_cdn/npm/ytpro/bgplay.js";
            break;
        case 2: // InnerTube endpoint
            endpoint = "https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js";
            break;
        case 3: // CDN endpoint
            endpoint = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/";
            break;
        default:
            endpoint = "https://m.youtube.com";
    }
    
    std::string encrypted = base64Encode(xorEncryptDecrypt(endpoint));
    return env->NewStringUTF(encrypted.c_str());
}

// Secure cookie encryption
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_encryptCookies(
        JNIEnv* env,
        jobject /* this */,
        jstring cookies) {
    const char* cookieStr = env->GetStringUTFChars(cookies, nullptr);
    if (cookieStr == nullptr) {
        return nullptr;
    }
    
    std::string cookieWithTime = std::string(cookieStr) + "|" + std::to_string(time(nullptr));
    std::string encrypted = base64Encode(xorEncryptDecrypt(cookieWithTime));
    
    env->ReleaseStringUTFChars(cookies, cookieStr);
    return env->NewStringUTF(encrypted.c_str());
}

// Generate secure hash for download verification
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_generateDownloadHash(
        JNIEnv* env,
        jobject /* this */,
        jstring videoId,
        jstring quality) {
    const char* videoIdStr = env->GetStringUTFChars(videoId, nullptr);
    const char* qualityStr = env->GetStringUTFChars(quality, nullptr);
    
    if (videoIdStr == nullptr || qualityStr == nullptr) {
        if (videoIdStr) env->ReleaseStringUTFChars(videoId, videoIdStr);
        if (qualityStr) env->ReleaseStringUTFChars(quality, qualityStr);
        return nullptr;
    }
    
    std::string combined = std::string(videoIdStr) + "|" + 
                          std::string(qualityStr) + "|" + 
                          ENCRYPTION_KEY + "|" +
                          std::to_string(time(nullptr) / 3600);
    
    std::string hash = base64Encode(xorEncryptDecrypt(combined));
    
    env->ReleaseStringUTFChars(videoId, videoIdStr);
    env->ReleaseStringUTFChars(quality, qualityStr);
    
    return env->NewStringUTF(hash.c_str());
}

// Get secure key
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_getSecureKey(
        JNIEnv* env,
        jobject /* this */,
        jint keyType) {
    
    std::string key;
    if (keyType == 0) {
        key = ENCRYPTION_KEY;
    } else {
        key = "SecureKey" + std::to_string(time(nullptr));
    }
    
    std::string encrypted = base64Encode(xorEncryptDecrypt(key));
    return env->NewStringUTF(encrypted.c_str());
}

// Encrypt request data
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_encryptRequestData(
        JNIEnv* env,
        jobject /* this */,
        jstring data) {
    const char* dataStr = env->GetStringUTFChars(data, nullptr);
    if (dataStr == nullptr) {
        return nullptr;
    }
    
    std::string withTimestamp = std::string(dataStr) + "|" + std::to_string(time(nullptr));
    std::string encrypted = base64Encode(xorEncryptDecrypt(withTimestamp));
    
    env->ReleaseStringUTFChars(data, dataStr);
    return env->NewStringUTF(encrypted.c_str());
}

// Decrypt response data
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_MainActivity_decryptResponseData(
        JNIEnv* env,
        jobject /* this */,
        jstring data) {
    const char* dataStr = env->GetStringUTFChars(data, nullptr);
    if (dataStr == nullptr) {
        return nullptr;
    }
    
    std::string decrypted = xorEncryptDecrypt(base64Decode(std::string(dataStr)));
    
    // Remove timestamp
    size_t pos = decrypted.find_last_of('|');
    if (pos != std::string::npos) {
        decrypted = decrypted.substr(0, pos);
    }
    
    env->ReleaseStringUTFChars(data, dataStr);
    return env->NewStringUTF(decrypted.c_str());
}

// Verify script integrity (simple check)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_spmods_ytpro_MainActivity_verifyScriptIntegrity(
        JNIEnv* env,
        jobject /* this */,
        jstring scriptContent,
        jstring expectedHash) {
    const char* content = env->GetStringUTFChars(scriptContent, nullptr);
    const char* hash = env->GetStringUTFChars(expectedHash, nullptr);
    
    if (content == nullptr || hash == nullptr) {
        if (content) env->ReleaseStringUTFChars(scriptContent, content);
        if (hash) env->ReleaseStringUTFChars(expectedHash, hash);
        return JNI_FALSE;
    }
    
    // Simple length-based integrity check
    size_t contentLength = strlen(content);
    std::string expected = base64Decode(std::string(hash));
    
    bool isValid = (contentLength > 100 && 
                    expected.find(std::to_string(contentLength)) != std::string::npos);
    
    env->ReleaseStringUTFChars(scriptContent, content);
    env->ReleaseStringUTFChars(expectedHash, hash);
    
    return isValid ? JNI_TRUE : JNI_FALSE;
}
