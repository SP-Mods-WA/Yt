package com.spmods.ytpro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

public class YouTubeLoginActivity extends Activity {
    
    private WebView webView;
    private ProgressBar progressBar;
    private ImageView backButton;
    private static final String YOUTUBE_LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com";
    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_login);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#0F0F0F"));
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                View decorView = window.getDecorView();
                decorView.setSystemUiVisibility(0);
            }
        }
        
        webView = findViewById(R.id.loginWebView);
        progressBar = findViewById(R.id.loginProgressBar);
        backButton = findViewById(R.id.backButton);
        
        backButton.setOnClickListener(v -> onBackPressed());
        
        setupWebView();
        loadLoginPage();
    }
    
    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        
        webSettings.setJavaScriptEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        
        webSettings.setUserAgentString("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        webSettings.setGeolocationEnabled(true);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                
                if (url.contains("youtube.com") && !url.contains("accounts.google.com") && !url.contains("ServiceLogin")) {
                    syncCookies();
                    
                    Toast.makeText(YouTubeLoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                    
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("login_success", true);
                    setResult(RESULT_OK, resultIntent);
                    
                    finish();
                }
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Toast.makeText(YouTubeLoginActivity.this, "Error loading page: " + description, Toast.LENGTH_SHORT).show();
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
                
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
            
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
    }
    
    private void loadLoginPage() {
        webView.loadUrl(YOUTUBE_LOGIN_URL);
    }
    
    private void syncCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.flush();
        
        String cookies = cookieManager.getCookie("https://www.youtube.com");
        if (cookies != null) {
            // Cookies automatically shared between WebViews
        }
    }
    
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }
    
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
        }
        super.onDestroy();
    }
}
