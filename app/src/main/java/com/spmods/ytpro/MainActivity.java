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
// Log all URLs (for testing)
        Log.d(TAG, "YtPro URL: " + ytproUrl);
        Log.d(TAG, "Innertube JS: " + innertubeJs);
        Log.d(TAG, "CDN URL: " + cdnUrl);
        Log.d(TAG, "Innertube Script URL: " + innertubeScriptUrl);
        Log.d(TAG, "Bgplay JS: " + bgplayJs);
        Log.d(TAG, "Bgplay Script URL: " + bgplayScriptUrl);
        Log.d(TAG, "Script JS: " + scriptJs);
        Log.d(TAG, "Script JS URL: " + scriptJsUrl);
        Log.d(TAG, "YouTube Base URL: " + youtubeBaseUrl);

        // Use these URLs in your WebView or wherever needed
        // Example:
         webView.loadUrl(innertubeScriptUrl);
         checkUrl(ytproUrl, innertubeJs);
    }

    // Example method showing how to use native URLs
    private void checkUrl(String baseUrl, String scriptName) {
        if (baseUrl.contains(scriptName)) {
            Log.d(TAG, "URL check passed");
        }
    }
}
