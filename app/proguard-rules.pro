# JNI/Native code protection
-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep JavaScriptInterface methods
-keepclassmembers class com.spmods.ytpro.MainActivity$WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep native methods
-keepclasseswithmembers class com.spmods.ytpro.MainActivity {
    native <methods>;
}

# Keep all classes in package (for debugging, remove for release)
-keep class com.spmods.ytpro.** { *; }

# WebView JavaScript Interface
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *;
}

# Keep application class
-keep public class com.spmods.ytpro.MainActivity {
    public *;
}

# Keep JavaScript interface
-keep public class com.spmods.ytpro.MainActivity$WebAppInterface {
    public *;
}

# Remove logging in release (Optional)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
