# ✅ Advanced ProGuard Configuration for Play Protect Bypass Attempt

# ========================================
# BASIC OBFUSCATION
# ========================================

# Class names obfuscate කරන්න
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ========================================
# REMOVE DEBUG INFO
# ========================================

# සියලුම logging remove කරන්න
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# printStackTrace remove කරන්න
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# ========================================
# KEEP YOUR APP CLASSES
# ========================================

# Main app package keep කරන්න (but obfuscate)
-keep,allowobfuscation class com.spmods.vidpro.** { *; }

# Activities keep කරන්න
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ========================================
# WEBVIEW SUPPORT
# ========================================

# WebView JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes JavascriptInterface
-keepattributes *Annotation*

# WebView classes
-keep public class android.webkit.** { *; }
-dontwarn android.webkit.**

-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# ========================================
# SERIALIZATION
# ========================================

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========================================
# NATIVE METHODS
# ========================================

-keepclasseswithmembernames class * {
    native <methods>;
}

# ========================================
# ENUMS
# ========================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================================
# PARCELABLE
# ========================================

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ========================================
# R CLASS
# ========================================

-keepclassmembers class **.R$* {
    public static <fields>;
}

-keep class **.R$*

# ========================================
# VIEW CONSTRUCTORS
# ========================================

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ========================================
# ONCLICK METHODS
# ========================================

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# ========================================
# REFLECTION
# ========================================

-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ========================================
# REMOVE ATTRIBUTES (Make reverse engineering harder)
# ========================================

-printmapping mapping.txt
-renamesourcefileattribute SourceFile

# Line numbers remove කරන්න (makes debugging harder)
# -keepattributes SourceFile,LineNumberTable

# ========================================
# OPTIMIZATION
# ========================================

-optimizationpasses 5
-dontpreverify
-verbose

# ========================================
# THIRD-PARTY LIBRARIES
# ========================================

# Gson (if used)
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# OkHttp (if used)
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ========================================
# CUSTOM OBFUSCATION
# ========================================

# Class member names obfuscate කරන්න
-keepclassmembernames class * {
    <methods>;
    <fields>;
}

# Package structure flatten කරන්න
-flattenpackagehierarchy

# Unused code remove කරන්න
-dontshrink

# ========================================
# WARNINGS SUPPRESS
# ========================================

-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ========================================
# ADDITIONAL SECURITY
# ========================================

# String encryption (basic level)
-adaptclassstrings

# Resource obfuscation
-adaptresourcefilenames

# Resource file contents obfuscation
-adaptresourcefilecontents
