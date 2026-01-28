package com.spmods.ytpro;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

public class SplashActivity extends Activity {

    private static final int SPLASH_DURATION = 2500; // 2.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ✅ CRITICAL: Disable Android 12+ default splash FIRST (before super.onCreate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                getSplashScreen().setOnExitAnimationListener(splashScreenView -> {
                    // Immediately remove default splash screen
                    splashScreenView.remove();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        super.onCreate(savedInstanceState);
        
        // ✅ Full screen setup (optional - if you want full screen)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.splash_logo);
        TextView fromText = findViewById(R.id.from_text);
        TextView spmodsText = findViewById(R.id.spmods_text);

        // Load animations
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        
        // Start animations
        logo.startAnimation(fadeIn);
        fromText.startAnimation(fadeIn);
        spmodsText.startAnimation(fadeIn);

        // Navigate to MainActivity after delay
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                
                // Pass any intent data from splash to main
                if (getIntent() != null && getIntent().getData() != null) {
                    intent.setData(getIntent().getData());
                    intent.setAction(getIntent().getAction());
                }
                
                startActivity(intent);
                finish();
                
                // Add smooth transition
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, SPLASH_DURATION);
    }
    
    @Override
    public void onBackPressed() {
        // ✅ Disable back button during splash
        // Do nothing - prevent user from going back
    }
}
