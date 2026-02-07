package com.spmods.ytpro;

import android.util.Base64;
import android.util.Log;

public class EncryptionUtil {
    private static final String TAG = "EncryptionUtil";
    
    // 🔐 Secret Key (Python script එකේ තියන එකම!)
    private static final String SECRET_KEY = "YTProSecretKey2025!@#$%^&*()";
    
    /**
     * Decrypt XOR encrypted + Base64 encoded data
     * @param encrypted Base64 encoded string
     * @return Decrypted string
     */
    public static String decrypt(String encrypted) {
        try {
            // Step 1: Decode Base64
            byte[] data = Base64.decode(encrypted, Base64.DEFAULT);
            
            // Step 2: Get secret key bytes
            byte[] key = SECRET_KEY.getBytes("UTF-8");
            
            // Step 3: XOR decrypt
            for (int i = 0; i < data.length; i++) {
                data[i] ^= key[i % key.length];
            }
            
            // Step 4: Convert to string
            String result = new String(data, "UTF-8");
            
            Log.d(TAG, "✅ Decrypted " + data.length + " bytes");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Decryption failed: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
}
