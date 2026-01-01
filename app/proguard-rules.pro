# ============================================================
# BASIC CONFIGURATION
# ============================================================

# Keep important Android classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Keep View bindings and click listeners
-keepclassmembers class * extends android.view.View {
   public <init>(android.content.Context);
   public <init>(android.content.Context, android.util.AttributeSet);
   public <init>(android.content.Context, android.util.AttributeSet, int);
   void set*(***);
   *** get*();
}

# ============================================================
# YOUR APP SPECIFIC RULES
# ============================================================

# Keep your package classes
-keep class com.spmods.ytpro.** { *; }
-keep class com.spmods.** { *; }

# Keep R classes
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# ============================================================
# ANDROIDX / SUPPORT LIBRARIES
# ============================================================

# AndroidX libraries
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ConstraintLayout
-keep class androidx.constraintlayout.** { *; }
-dontwarn androidx.constraintlayout.**

# ============================================================
# NATIVE METHODS
# ============================================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================
# SERIALIZATION / PARCELABLE
# ============================================================

# Keep Parcelable
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Serializable
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# ANNOTATIONS
# ============================================================

# Keep annotations
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ============================================================
# REFLECTION
# ============================================================

# For reflection usage
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ============================================================
# LINE NUMBERS FOR DEBUGGING
# ============================================================

# Keep line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# ============================================================
# WEBVIEW / JAVASCRIPT
# ============================================================

# If you use WebView with JavaScript
# -keepclassmembers class com.spmods.ytpro.JavaScriptInterface {
#    public *;
# }

# ============================================================
# CUSTOM RULES (ADD YOUR OWN HERE)
# ============================================================

# If you use Gson
# -keepattributes Signature
# -keepattributes *Annotation*
# -keep class sun.misc.Unsafe { *; }
# -keep class com.google.gson.** { *; }

# If you use OkHttp
# -keep class okhttp3.** { *; }
# -keep interface okhttp3.** { *; }
# -dontwarn okhttp3.**

# If you use Retrofit
# -keep class retrofit2.** { *; }
# -keepattributes Signature
# -keepattributes Exceptions

# ============================================================
# WARNINGS SUPPRESSION
# ============================================================

# Suppress warnings
-dontwarn android.support.**
-dontwarn com.google.**
-dontwarn org.apache.**
-dontwarn okio.**
