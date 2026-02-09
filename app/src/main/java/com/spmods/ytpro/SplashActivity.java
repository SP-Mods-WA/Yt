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

// ✅ මේ import එක add කරන්න
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends Activity {

    private static final int SPLASH_DURATION = 2500; // 2.5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ✅ Android 12+ automatic splash එක disable කරන්න
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
            splashScreen.setKeepOnScreenCondition(() -> false);
        }
        
        super.onCreate(savedInstanceState);
        
        // ✅ Full screen setup
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        // ✅ Status bar color change කරන්න (white background එකට අනුව)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFFFFFFFF); // White color
            
            // ✅ Status bar icons dark කරන්න (white background එකට readable වෙන්න)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                );
            }
        }
        
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
