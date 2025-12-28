package com.spmods.ytpro;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import com.spmods.ytpro.databinding.MainBinding;

public class MainActivity extends AppCompatActivity {

    private MainBinding binding;
    private NativeSec nativeSec;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize NativeSec
        nativeSec = new NativeSec();

        // Example TextView from native
        binding.sampleText.setText(nativeSec.stringFromJNI());

        // Get all URLs from native
        String ytproUrl = nativeSec.getYtproUrl();
        String innertubeJs = nativeSec.getInnertubeJs();
        String cdnUrl = nativeSec.getCdnUrl();
        String innertubeScriptUrl = nativeSec.getInnertubeScriptUrl();
        String bgplayJs = nativeSec.getBgplayJs();
        String bgplayScriptUrl = nativeSec.getBgplayScriptUrl();
        String scriptJs = nativeSec.getScriptJs();
        String scriptJsUrl = nativeSec.getScriptJsUrl();
        String youtubeBaseUrl = nativeSec.getYoutubeBaseUrl();

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
        // webView.loadUrl(innertubeScriptUrl);
        // checkUrl(ytproUrl, innertubeJs);
    }

    // Example method showing how to use native URLs
    private void checkUrl(String baseUrl, String scriptName) {
        if (baseUrl.contains(scriptName)) {
            Log.d(TAG, "URL check passed");
        }
    }
}
