package com.spmods.ytpro;

import android.os.Bundle;
import android.content.Intent;
import android.widget.Toast;

public class DownloadFromIntentFilter extends MainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Don't call load(true) - it doesn't exist
        // Instead, handle the intent and let MainActivity initialize normally
        
        // Check if we have a download intent
        handleDownloadIntent();
    }
    
    private void handleDownloadIntent() {
        Intent intent = getIntent();
        
        if (intent != null && intent.getAction() != null) {
            // This activity was started from an external intent
            String action = intent.getAction();
            String data = intent.getDataString();
            
            if (Intent.ACTION_VIEW.equals(action) && data != null) {
                // Handle URL from browser
                Toast.makeText(this, "Download URL: " + data, Toast.LENGTH_LONG).show();
                
                // Add your download logic here
                // Example: parse YouTube URL and start download
            } else if (Intent.ACTION_SEND.equals(action)) {
                // Handle shared content
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    Toast.makeText(this, "Shared: " + sharedText, Toast.LENGTH_LONG).show();
                    
                    // Add your download logic here
                }
            }
        }
        
        // Continue with normal MainActivity initialization
        // MainActivity's onCreate will handle the rest
    }
}
