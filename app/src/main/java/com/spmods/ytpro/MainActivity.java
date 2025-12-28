package com.spmods.ytpro;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private NativeSec nativeSec;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Initialize NativeSec
        nativeSec = new NativeSec();

        // Log native values (no TextView needed)
        Log.d(TAG, "Native String: " + nativeSec.stringFromJNI());
        Log.d(TAG, "YtPro URL: " + nativeSec.getYtproUrl());
        Log.d(TAG, "CDN URL: " + nativeSec.getCdnUrl());
        
        // Your WebView or other UI setup here...
    }
}
