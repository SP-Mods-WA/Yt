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
        // Sync cookies from main app session
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
            CookieManager.getInstance().flush();
        }

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectStyles(view);
            }
        });

        web.loadUrl("https://m.youtube.com/feed/account");
        setContentView(web);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Inject CSS — hide YouTube nav, style the account page cleanly
    // ══════════════════════════════════════════════════════════════════════════
    private void injectStyles(WebView view) {
        String css =
            // Hide native top bar, bottom nav, search bar
            "ytm-mobile-topbar-renderer," +
            "ytm-pivot-bar-renderer," +
            "#masthead, .mobile-topbar-header," +
            "ytm-pivot-bar-item-renderer," +
            "ytm-searchbox," +
            ".searchbox { display:none!important; }" +

            // Dark background
            "body,html { background:#0F0F0F!important; padding-top:0!important; padding-bottom:72px!important; margin:0!important; }" +

            // Account header card (profile area)
            "ytm-account-header-renderer {" +
            "  background:#1A1A1A!important;" +
            "  border-radius:16px!important;" +
            "  margin:12px 16px 8px!important;" +
            "  padding:20px 16px!important;" +
            "  display:block!important;" +
            "}" +

            // Avatar circle
            "ytm-account-header-renderer img," +
            ".account-header-image img {" +
            "  border-radius:50%!important;" +
            "  width:80px!important; height:80px!important;" +
            "}" +

            // All text white
            "ytm-account-header-renderer *," +
            ".account-header * { color:#FFFFFF!important; }" +

            // Channel name bold
            ".account-header-name," +
            "ytm-account-header-renderer .account-name {" +
            "  font-size:18px!important; font-weight:bold!important;" +
            "}" +

            // Menu section list
            "ytm-compact-link-renderer {" +
            "  background:#1A1A1A!important;" +
            "  border-bottom:1px solid #2A2A2A!important;" +
            "  border-radius:0!important;" +
            "  margin:0 16px!important;" +
            "  padding:0 4px!important;" +
            "}" +
            "ytm-compact-link-renderer:first-child { border-radius:12px 12px 0 0!important; }" +
            "ytm-compact-link-renderer:last-child  { border-radius:0 0 12px 12px!important; border-bottom:none!important; }" +

            // Row text
            ".compact-link-renderer-title," +
            "ytm-compact-link-renderer .title," +
            "ytm-compact-link-renderer span {" +
            "  color:#FFFFFF!important; font-size:15px!important;" +
            "}" +

            // Section headers
            ".section-header-title," +
            "ytm-item-section-renderer > .section-header {" +
            "  color:#AAAAAA!important;" +
            "  font-size:11px!important;" +
            "  text-transform:uppercase!important;" +
            "  letter-spacing:1px!important;" +
            "  padding:16px 16px 6px!important;" +
            "  background:transparent!important;" +
            "}" +

            // Remove ads + promos
            "ytm-banner-renderer,ytm-ads-renderer," +
            "ytm-promoted-sparkles-web-renderer," +
            "ytm-display-ad-renderer { display:none!important; }" +

            // Item sections spacing
            "ytm-item-section-renderer {" +
            "  background:transparent!important;" +
            "  margin:0!important; padding:0!important;" +
            "}" +

            // Sign in button
            "ytm-account-header-renderer paper-button," +
            "ytm-account-header-renderer .sign-in-button {" +
            "  background:#FF0000!important;" +
            "  border-radius:20px!important;" +
            "  color:#FFFFFF!important;" +
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
