#include <jni.h>
#include <string>
#include <android/log.h>

// Generated headers (Python script එකෙන් create වෙනවා)
#include "scripts/script.h"
#include "scripts/bgplay.h"
#include "scripts/innertube.h"

#define TAG "YTPro-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

/**
 * Load script.js
 */
JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeScriptLoader_getScript(
    JNIEnv* env,
    jobject /* this */) {
    
    LOGI("📄 Loading script.js from native library");
    
    try {
        std::string content(ytpro::script_data);
        LOGI("✅ Loaded script.js: %zu bytes", content.size());
        return env->NewStringUTF(content.c_str());
    } catch (const std::exception& e) {
        LOGE("❌ Exception loading script.js: %s", e.what());
        return env->NewStringUTF("");
    }
}

/**
 * Load bgplay.js
 */
JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeScriptLoader_getBgPlay(
    JNIEnv* env,
    jobject /* this */) {
    
    LOGI("📄 Loading bgplay.js from native library");
    
    try {
        std::string content(ytpro::bgplay_data);
        LOGI("✅ Loaded bgplay.js: %zu bytes", content.size());
        return env->NewStringUTF(content.c_str());
    } catch (const std::exception& e) {
        LOGE("❌ Exception loading bgplay.js: %s", e.what());
        return env->NewStringUTF("");
    }
}

/**
 * Load innertube.js
 */
JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeScriptLoader_getInnertube(
    JNIEnv* env,
    jobject /* this */) {
    
    LOGI("📄 Loading innertube.js from native library");
    
    try {
        std::string content(ytpro::innertube_data);
        LOGI("✅ Loaded innertube.js: %zu bytes", content.size());
        return env->NewStringUTF(content.c_str());
    } catch (const std::exception& e) {
        LOGE("❌ Exception loading innertube.js: %s", e.what());
        return env->NewStringUTF("");
    }
}

} // extern "C"
