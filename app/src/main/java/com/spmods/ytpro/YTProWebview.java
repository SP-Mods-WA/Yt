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
        
        // ===== CRITICAL: Video Streaming Settings =====
        // Enable range requests for video streaming (progressive loading)
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        
        // Cache settings for smooth video playback
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // Enable hardware acceleration for video
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
        }
        
        // Enable zoom controls
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        
        // ===== MOST IMPORTANT: Media playback settings =====
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        
        // Enable video autoplay and background play
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            webSettings.setMediaPlaybackRequiresUserGesture(false);
        }
        
        // Keep mobile user agent
        webSettings.setUserAgentString(webSettings.getUserAgentString());
        
        // Enable wide viewport
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        
        // Enable safe browsing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webSettings.setSafeBrowsingEnabled(true);
        }
        
        // Enable mixed content (important for video streams)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        
        // Enable cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(this, true);
        }
        
        // ===== Additional settings for better performance =====
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkImage(false);
        webSettings.setBlockNetworkLoads(false);
        
        // Enable plugins (for older Android versions)
        try {
            webSettings.setPluginState(WebSettings.PluginState.ON);
        } catch (Exception e) {
            // Ignore for newer Android versions
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
                
                // Add notification and TV icons to header
                addHeaderIcons(view);
                
                // Inject video optimization script
                optimizeVideoPlayback(view);
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
            
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
            }
        });
    }

    private void optimizeVideoPlayback(WebView view) {
        // JavaScript to optimize video buffering and playback
        String js = "javascript:(function() {" +
                "var videos = document.querySelectorAll('video');" +
                "videos.forEach(function(video) {" +
                // Enable preload for smooth playback
                "  video.preload = 'auto';" +
                // Enable playsinline
                "  video.setAttribute('playsinline', '');" +
                "  video.setAttribute('webkit-playsinline', '');" +
                // Remove any buffering delays
                "  if(video.buffered && video.buffered.length > 0) {" +
                "    video.currentTime = video.currentTime;" +
                "  }" +
                "});" +
                "})()";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(js, null);
        } else {
            view.loadUrl(js);
        }
    }

    private void addHeaderIcons(WebView view) {
        // JavaScript to add notification bell and TV icons to YouTube mobile header
        String js = "javascript:(function() {" +
                "if(document.getElementById('ytpro-custom-icons')) return;" +
                
                // Wait for header to load
                "function addIcons() {" +
                "  var header = document.querySelector('ytm-mobile-topbar-renderer');" +
                "  if(!header) { setTimeout(addIcons, 500); return; }" +
                "  " +
                "  var buttonsContainer = header.querySelector('.mobile-topbar-header-content');" +
                "  if(!buttonsContainer) { setTimeout(addIcons, 500); return; }" +
                "  " +
                // Create container for custom icons
                "  var iconsDiv = document.createElement('div');" +
                "  iconsDiv.id = 'ytpro-custom-icons';" +
                "  iconsDiv.style.cssText = 'display:flex;align-items:center;margin-right:8px;';" +
                "  " +
                // Notification Bell Icon
                "  var bellBtn = document.createElement('button');" +
                "  bellBtn.style.cssText = 'background:transparent;border:0;padding:8px;display:flex;align-items:center;cursor:pointer;';" +
                "  bellBtn.innerHTML = '<svg height=\"24\" width=\"24\" viewBox=\"0 0 24 24\" fill=\"currentColor\"><path d=\"M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z\"/></svg>';" +
                "  bellBtn.onclick = function() { window.location.href = 'https://m.youtube.com/feed/notifications'; };" +
                "  " +
                // TV/Cast Icon  
                "  var castBtn = document.createElement('button');" +
                "  castBtn.style.cssText = 'background:transparent;border:0;padding:8px;display:flex;align-items:center;cursor:pointer;';" +
                "  castBtn.innerHTML = '<svg height=\"24\" width=\"24\" viewBox=\"0 0 24 24\" fill=\"currentColor\"><path d=\"M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z\"/></svg>';" +
                "  castBtn.onclick = function() { Android.showToast('Cast feature coming soon!'); };" +
                "  " +
                // Add icons to container
                "  iconsDiv.appendChild(bellBtn);" +
                "  iconsDiv.appendChild(castBtn);" +
                "  " +
                // Insert before search button
                "  var searchBtn = buttonsContainer.querySelector('ytm-topbar-menu-button-renderer[button-renderer-id=\"FEsearch\"]');" +
                "  if(searchBtn) {" +
                "    buttonsContainer.insertBefore(iconsDiv, searchBtn);" +
                "  }" +
                "}" +
                "addIcons();" +
                "})()";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.evaluateJavascript(js, null);
        } else {
            view.loadUrl(js);
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
