#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "YtSec"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Get YouTube CDN base URL
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getYtproUrl(JNIEnv* env, jobject) {
    return env->NewStringUTF("youtube.com/ytpro_cdn/npm/ytpro");
}

// Get innertube.js filename
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getInnertubeJs(JNIEnv* env, jobject) {
    return env->NewStringUTF("innertube.js");
}

// Get CDN jsdelivr URL
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getCdnUrl(JNIEnv* env, jobject) {
    return env->NewStringUTF("https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/");
}

// Get innertube script full URL
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getInnertubeScriptUrl(JNIEnv* env, jobject) {
    return env->NewStringUTF("https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/innertube.js");
}

// Get bgplay.js filename
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getBgplayJs(JNIEnv* env, jobject) {
    return env->NewStringUTF("bgplay.js");
}

// Get bgplay script full URL
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getBgplayScriptUrl(JNIEnv* env, jobject) {
    return env->NewStringUTF("https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/bgplay.js");
}

// Get script.js filename
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getScriptJs(JNIEnv* env, jobject) {
    return env->NewStringUTF("script.js");
}

// Get script.js full URL
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getScriptJsUrl(JNIEnv* env, jobject) {
    return env->NewStringUTF("https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/script.js");
}

// Get YouTube base URL (last one from your code)
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_getYoutubeBaseUrl(JNIEnv* env, jobject) {
    return env->NewStringUTF("youtube.com/ytpro_cdn/npm/ytpro/");
}

// Original stringFromJNI method
extern "C" JNIEXPORT jstring JNICALL
Java_com_spmods_ytpro_NativeSec_stringFromJNI(JNIEnv* env, jobject) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
