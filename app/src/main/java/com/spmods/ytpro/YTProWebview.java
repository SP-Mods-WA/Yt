package com.spmods.ytpro;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class YTProWebview extends WebView {

    private ValueCallback<Uri[]> filePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;

    public YTProWebview(Context context) {
        super(context);
        init();
    }

    public YTProWebview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public YTProWebview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        WebSettings webSettings = getSettings();
        
        // Enable JavaScript
        webSettings.setJavaScriptEnabled(true);
        
        // Enable DOM storage
        webSettings.setDomStorageEnabled(true);
        
        // Enable database
        webSettings.setDatabaseEnabled(true);
        
        // Enable caching
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Enable zoom controls
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        
        // Enable media playback
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        // Keep mobile user agent for compatibility with your JS code
        // Don't change to desktop mode
        webSettings.setUserAgentString(webSettings.getUserAgentString());
        
        // Enable wide viewport
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        
        // Enable safe browsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(true);
        }
        
        // Enable mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(this, true);
        }
        
        // Set WebViewClient
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                
                // Handle YouTube URLs
                if (url.contains("youtube.com") || url.contains("youtu.be")) {
                    view.loadUrl(url);
                    return true;
                }
                
                // Handle external URLs
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                getContext().startActivity(intent);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                // Inject CSS to show notification bell and TV icons
                injectNotificationIcons(view);
            }
        });
        
        // Set WebChromeClient for file upload and other features
        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                            FileChooserParams fileChooserParams) {
                if (YTProWebview.this.filePathCallback != null) {
                    YTProWebview.this.filePathCallback.onReceiveValue(null);
                }
                YTProWebview.this.filePathCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    ((Activity) getContext()).startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    YTProWebview.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void injectNotificationIcons(WebView view) {
        // Inject CSS to force show notification bell and TV/Cast icons on mobile YouTube
        String customCSS = "javascript:(function() {" +
                "var style = document.createElement('style');" +
                "style.innerHTML = '" +
                "ytm-topbar-menu-button-renderer[button-renderer-id=\"FEnotifications\"] { display: flex !important; visibility: visible !important; }" +
                "ytm-topbar-menu-button-renderer[button-renderer-id=\"FEcast\"] { display: flex !important; visibility: visible !important; }" +
                "ytm-notification-topbar-button-view-model { display: flex !important; }" +
                "';" +
                "document.head.appendChild(style);" +
                "})()";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(customCSS, null);
        } else {
            view.loadUrl(customCSS);
        }
    }

    public void handleFileChooserResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (filePathCallback == null) {
                return;
            }
            
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    public void loadYouTube() {
        loadUrl("https://m.youtube.com");
    }
}
