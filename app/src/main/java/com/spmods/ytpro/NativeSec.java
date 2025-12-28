package com.spmods.ytpro;

public class NativeSec {
    
    // Load native library
    static {
        System.loadLibrary("ytsec");
    }
    
    // Native method declarations
    public native String stringFromJNI();
    public native String getYtproUrl();
    public native String getInnertubeJs();
    public native String getCdnUrl();
    public native String getInnertubeScriptUrl();
    public native String getBgplayJs();
    public native String getBgplayScriptUrl();
    public native String getScriptJs();
    public native String getScriptJsUrl();
    public native String getYoutubeBaseUrl();
}
