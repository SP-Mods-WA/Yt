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
        
        // ✅ Full screen setup (title bar remove)
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // ✅ Full Immersive Mode - Status bar සහ Navigation bar hide කරන්න
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
        
        // ✅ Full screen flag එකත් set කරන්න
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
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // ✅ User swipe කරලා status bar එක show කරද්දි ආයේ hide කරන්න
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }
    
    @Override
    public void onBackPressed() {
        // ✅ Disable back button during splash
        // Do nothing - prevent user from going back
    }
}
