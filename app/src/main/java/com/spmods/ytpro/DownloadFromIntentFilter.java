package com.spmods.ytpro;

import android.os.Bundle;
import android.content.Intent;
import android.util.Log;

public class DownloadFromIntentFilter extends MainActivity {

    private static final String TAG = "DownloadFromIntentFilter";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Handle incoming intent (YouTube links, etc.)
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action)) {
            // Handle YouTube URL from browser
            String url = intent.getDataString();
            if (url != null) {
                Log.d(TAG, "Received URL: " + url);
                // Process download here
                processDownload(url);
            }
        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            // Handle shared text/URL
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                Log.d(TAG, "Shared text: " + sharedText);
                // Process download here
                processDownload(sharedText);
            }
        }
    }

    private void processDownload(String url) {
        // TODO: Implement your download logic here
        Log.d(TAG, "Processing download for: " + url);
        
        // Example: Load URL in WebView or start download service
        // You can add your custom download handling here
    }
}
