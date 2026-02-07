package com.spmods.ytpro;

import android.util.Log;

public class NativeScriptLoader {
    
    private static final String TAG = "NativeScriptLoader";
    private static boolean libraryLoaded = false;
    
    // Load native library
    static {
        try {
            System.loadLibrary("ytpro-native");
            libraryLoaded = true;
            Log.d(TAG, "✅ Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            libraryLoaded = false;
            Log.e(TAG, "❌ Failed to load native library: " + e.getMessage());
        }
    }
    
    // Native methods (C++ functions)
    private native String getScript();
    private native String getBgPlay();
    private native String getInnertube();
    
    /**
     * Check if native library is loaded
     */
    public boolean isLibraryLoaded() {
        return libraryLoaded;
    }
    
    /**
     * Load script by filename
     */
    public String loadScript(String filename) {
        if (!libraryLoaded) {
            Log.e(TAG, "❌ Native library not loaded!");
            return "";
        }
        
        try {
            String content = "";
            
            if (filename.equals("script.js")) {
                content = getScript();
            } else if (filename.equals("bgplay.js")) {
                content = getBgPlay();
            } else if (filename.equals("innertube.js")) {
                content = getInnertube();
            } else {
                Log.e(TAG, "❌ Unknown script: " + filename);
                return "";
            }
            
            if (content.isEmpty()) {
                Log.e(TAG, "❌ Empty content for: " + filename);
                return "";
            }
            
            Log.d(TAG, "✅ Loaded: " + filename + " (" + content.length() + " bytes)");
            return content;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading " + filename + ": " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
}
