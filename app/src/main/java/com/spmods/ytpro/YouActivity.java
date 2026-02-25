package com.spmods.ytpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;

public class YouActivity extends Activity {

    private WebView web;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#0F0F0F"));
        }

        // Full screen WebView
        web = new WebView(this);
        web.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(Color.parseColor("#0F0F0F"));

        // Settings
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // Share cookies
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectStyles(view);
            }
        });

        web.loadUrl("https://m.youtube.com/channel/me");
        setContentView(web);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Inject CSS — hide YouTube nav, style the account page cleanly
    // ══════════════════════════════════════════════════════════════════════════
    private void injectStyles(WebView view) {
        String css =
            // Hide YouTube native top bar + bottom nav
            "ytm-mobile-topbar-renderer," +
            "ytm-pivot-bar-renderer," +
            "#masthead," +
            ".mobile-topbar-header," +
            "ytm-pivot-bar-item-renderer {" +
            "  display: none !important;" +
            "}" +

            // Page background
            "body, html {" +
            "  background: #0F0F0F !important;" +
            "  padding-top: 0 !important;" +
            "  padding-bottom: 70px !important;" +
            "}" +

            // Remove white backgrounds from cards
            "ytm-item-section-renderer," +
            "ytm-compact-link-renderer," +
            ".compact-link-renderer," +
            "ytm-section-list-renderer {" +
            "  background: #1A1A1A !important;" +
            "  border-radius: 12px !important;" +
            "  margin: 4px 16px !important;" +
            "  border: none !important;" +
            "}" +

            // Text colors
            "* {" +
            "  color: #FFFFFF !important;" +
            "}" +
            ".compact-link-title," +
            ".compact-link-renderer .title {" +
            "  color: #FFFFFF !important;" +
            "  font-size: 15px !important;" +
            "}" +

            // Channel header styling
            "ytm-c4-tabbed-header-renderer," +
            "#channel-header," +
            ".channel-header {" +
            "  background: #1A1A1A !important;" +
            "  border-radius: 16px !important;" +
            "  margin: 8px 16px !important;" +
            "  padding: 20px !important;" +
            "}" +

            // Avatar — make circular + centered
            "ytm-c4-tabbed-header-renderer img," +
            "#avatar img," +
            ".avatar img {" +
            "  border-radius: 50% !important;" +
            "  width: 80px !important;" +
            "  height: 80px !important;" +
            "}" +

            // Subscribe / button row
            ".ytm-button-renderer," +
            "ytm-button-renderer {" +
            "  background: #FF0000 !important;" +
            "  border-radius: 20px !important;" +
            "  border: none !important;" +
            "}" +

            // Remove banners/ads
            "ytm-banner-renderer," +
            "ytm-ads-renderer," +
            "ytm-promoted-sparkles-web-renderer," +
            "ytm-display-ad-renderer," +
            ".ytm-banner {" +
            "  display: none !important;" +
            "}" +

            // Dividers
            ".compact-link-renderer + .compact-link-renderer {" +
            "  border-top: 1px solid #2A2A2A !important;" +
            "}" +

            // Section titles
            ".section-title," +
            "ytm-section-title-renderer {" +
            "  color: #AAAAAA !important;" +
            "  font-size: 11px !important;" +
            "  letter-spacing: 1px !important;" +
            "  text-transform: uppercase !important;" +
            "  padding: 16px 16px 4px !important;" +
            "  background: transparent !important;" +
            "}";

        // Inject CSS
        String jsCSS = "(function(){" +
            "  var existing = document.getElementById('ytpro-you-style');" +
            "  if (existing) existing.remove();" +
            "  var st = document.createElement('style');" +
            "  st.id = 'ytpro-you-style';" +
            "  st.textContent = '" + css.replace("'", "\\'") + "';" +
            "  document.head.appendChild(st);" +
            "})();";

        view.evaluateJavascript(jsCSS, null);

        // Re-apply on DOM changes (YouTube is SPA)
        String jsObserver = "(function(){" +
            "  if (window.__ytproYouObserver) return;" +
            "  window.__ytproYouObserver = true;" +
            "  new MutationObserver(function(){" +
            "    var st = document.getElementById('ytpro-you-style');" +
            "    if (!st) {" +
            "      var s = document.createElement('style');" +
            "      s.id = 'ytpro-you-style';" +
            "      s.textContent = '" + css.replace("'", "\\'") + "';" +
            "      document.head.appendChild(s);" +
            "    }" +
            "  }).observe(document.body, {childList: true, subtree: true});" +
            "})();";

        view.evaluateJavascript(jsObserver, null);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
    }
}
