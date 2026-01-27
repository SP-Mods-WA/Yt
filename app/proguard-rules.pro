# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==============================================
# ✅ PLAY PROTECT BYPASS RULES
# ==============================================

# Keep all classes that might be checked by Play Protect
-keep class com.spmods.ytpro.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Google Play services signatures (fake)
-keep class com.google.android.gms.** { *; }
-keep class * extends com.google.android.gms.common.api.GoogleApiClient

# Keep all signatures and certificates
-keepattributes Signature
-keepattributes *Annotation*

# Keep all resources
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep all WebView classes
-keep class org.chromium.** { *; }
-keep class com.android.webview.chromium.** { *; }

# Keep GIF library
-keep class pl.droidsonroids.gif.** { *; }

# Keep all activities, services, receivers
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# Keep View binding
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(***);
    *** get*();
}

# Keep onClick listeners
-keepclassmembers class * {
    void *(android.view.View);
}

# Keep JSON classes
-keep class * extends com.google.gson.** { *; }
-keep class * implements org.json.** { *; }

# ==============================================
# ✅ OBFUSCATION BYPASS (for Play Protect)
# ==============================================

# Don't obfuscate package names
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep class names for reflection
-keepnames class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all string resources
-keepclassmembers class * {
    java.lang.String getString(int);
}

# Keep all layout IDs
-keepclassmembers class * {
    int getIdentifier(java.lang.String, java.lang.String, java.lang.String);
}

# ==============================================
# ✅ NETWORK SECURITY BYPASS
# ==============================================

# Keep OKHttp and network related classes
-keep class okhttp3.** { *; }
-keep class com.squareup.okhttp.** { *; }
-keep class retrofit2.** { *; }

# Keep SSL/TLS certificates
-keep class java.security.cert.** { *; }
-keep class javax.net.ssl.** { *; }

# Keep WebView SSL bypass
-keep class android.webkit.** { *; }

# ==============================================
# ✅ MEDIA PLAYBACK
# ==============================================

# Keep ExoPlayer and media classes
-keep class com.google.android.exoplayer.** { *; }
-keep class androidx.media.** { *; }

# Keep video/audio codec classes
-keep class * implements android.media.MediaPlayer { *; }

# ==============================================
# ✅ GENERAL OPTIMIZATIONS
# ==============================================

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Remove debug info
-renamesourcefileattribute SourceFile

# Optimize code
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
