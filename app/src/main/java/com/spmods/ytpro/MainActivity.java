package com.spmods.ytpro;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private NativeSec nativeSec;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Initialize NativeSec
        nativeSec = new NativeSec();

        // Find TextView
        TextView sampleText = findViewById(R.id.sample_text);
        sampleText.setText(nativeSec.stringFromJNI());

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

        // Log all URLs
        Log.d(TAG, "YtPro URL: " + ytproUrl);
        Log.d(TAG, "Innertube JS: " + innertubeJs);
        Log.d(TAG, "CDN URL: " + cdnUrl);
        Log.d(TAG, "Innertube Script URL: " + innertubeScriptUrl);
        Log.d(TAG, "Bgplay JS: " + bgplayJs);
        Log.d(TAG, "Bgplay Script URL: " + bgplayScriptUrl);
        Log.d(TAG, "Script JS: " + scriptJs);
        Log.d(TAG, "Script JS URL: " + scriptJsUrl);
        Log.d(TAG, "YouTube Base URL: " + youtubeBaseUrl);
    }
}
