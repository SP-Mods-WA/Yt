package com.spmods.ytpro;

import android.util.Log;

/**
 * Native Security - JNI Wrapper for Encryption
 * Simple XOR-based encryption using C++
 */
public class NativeSec {
    
    private static final String TAG = "NativeSec";
    private static boolean isLoaded = false;
    
    static {
        try {
            System.loadLibrary("ytsec");
            isLoaded = true;
            Log.d(TAG, "✅ Native security library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "❌ Failed to load native library: " + e.getMessage());
            isLoaded = false;
        }
    }
    
    /**
     * Check if native library is loaded
     * @return true if loaded, false otherwise
     */
    public static boolean loaded() {
        return isLoaded;
    }
    
    /**
     * Encrypt string using native C++ implementation
     * @param data String to encrypt
     * @return Encrypted string
     */
    public static native String enc(String data);
    
    /**
     * Decrypt string using native C++ implementation
     * @param data Encrypted string to decrypt
     * @return Decrypted string
     */
    public static native String dec(String data);
}
