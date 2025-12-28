# ========================================
# 🛡️ ADVANCED PLAY PROTECT BYPASS CONFIG
# ========================================

# ========================================
# BASIC OBFUSCATION
# ========================================

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-dontpreverify
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# Class names obfuscate කරන්න
-repackageclasses 'o'
-allowaccessmodification
-flattenpackagehierarchy

# ========================================
# REMOVE ALL DEBUG & LOGGING
# ========================================

# All logging completely remove කරන්න
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
    public static int wtf(...);
}

# printStackTrace remove කරන්න
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# Debug class references remove කරන්න
-assumenosideeffects class * {
    public void debug(...);
    public void log(...);
}

# ========================================
# 🎯 PLAY PROTECT BYPASS - CORE RULES
# ========================================

# Google Play Services Detection Bypass
-assumenosideeffects class com.google.android.gms.** {
    public *;
}

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Package Manager Signature Checks Remove
-assumenosideeffects class android.content.pm.PackageManager {
    public android.content.pm.PackageInfo getPackageInfo(...);
    public android.content.pm.Signature[] getPackageSignatures(...);
    public android.content.pm.PackageInfo getPackageArchiveInfo(...);
}

# Application Signature Verification Bypass
-assumenosideeffects class android.content.pm.PackageInfo {
    public android.content.pm.Signature[] signatures;
}

# Play Store References Remove
-assumenosideeffects class com.android.vending.** {
    *;
}

-dontwarn com.android.vending.**

# SafetyNet/Play Integrity Bypass
-assumenosideeffects class com.google.android.gms.safetynet.** {
    *;
}

-keep class com.google.android.gms.safetynet.** { *; }
-dontwarn com.google.android.gms.safetynet.**

# Anti-Tampering Detection Remove
-assumenosideeffects class * {
    public void checkSignature(...);
    public boolean verifySignature(...);
    public boolean isAppSigned(...);
    public boolean validateSignature(...);
}

# ========================================
# HIDE APP INFORMATION
# ========================================

# Build.VERSION references obfuscate කරන්න
-assumenosideeffects class android.os.Build {
    public static final java.lang.String MANUFACTURER;
    public static final java.lang.String BRAND;
    public static final java.lang.String MODEL;
    public static final java.lang.String DEVICE;
}

# System properties hide කරන්න
-assumenosideeffects class java.lang.System {
    public static java.lang.String getProperty(...);
}

# ========================================
# STRING ENCRYPTION & OBFUSCATION
# ========================================

# All strings obfuscate කරන්න
-adaptclassstrings
-adaptresourcefilenames
-adaptresourcefilecontents

# ========================================
# KEEP YOUR APP CLASSES (But Obfuscated)
# ========================================

# Main package keep කරන්න (but names obfuscate වෙයි)
-keep,allowobfuscation class com.spmods.ytpro.** { *; }

# Activities, Services keep කරන්න
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# ========================================
# WEBVIEW SUPPORT
# ========================================

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes JavascriptInterface
-keepattributes *Annotation*

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

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# JNI calls obfuscate කරන්න
-keepclassmembers class * {
    native <methods>;
}

# ========================================
# PARCELABLE
# ========================================

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ========================================
# ENUMS
# ========================================

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========================================
# R CLASS & RESOURCES
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
# REFLECTION SUPPORT
# ========================================

-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ========================================
# REMOVE SOURCE ATTRIBUTES
# ========================================

-renamesourcefileattribute SourceFile

# Line numbers remove කරන්න (debugging hard කරන්න)
# -keepattributes SourceFile,LineNumberTable  # Comment out කරලා තියන්න

# ========================================
# CUSTOM OBFUSCATION
# ========================================

# Dictionary for obfuscated names (Optional)
# -obfuscationdictionary obfuscation-dictionary.txt
# -classobfuscationdictionary obfuscation-dictionary.txt
# -packageobfuscationdictionary obfuscation-dictionary.txt

# ========================================
# THIRD-PARTY LIBRARIES (If Used)
# ========================================

# Gson
-keepattributes Signature
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# ========================================
# WARNINGS SUPPRESS
# ========================================

-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ========================================
# ADDITIONAL SECURITY
# ========================================

# Remove metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Print mapping file (for debugging if needed)
-printmapping mapping.txt
-printseeds seeds.txt
-printusage usage.txt

# ========================================
# END OF CONFIGURATION
# ========================================
