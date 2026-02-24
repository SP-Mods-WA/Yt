package com.spmods.ytpro;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.*;
import android.os.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import android.widget.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.net.*;
import android.util.*;
import android.webkit.*;
import java.io.*;
import org.json.*;
import android.content.pm.*;
import android.provider.Settings;
import java.net.URLEncoder;
import android.content.SharedPreferences;
import android.webkit.CookieManager;
import android.media.AudioManager;
import android.os.PowerManager;
import java.net.*;
import java.util.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;

public class MainActivity extends Activity {

    // ─── State ────────────────────────────────────────────────────────────────
    private boolean portrait       = false;
    private boolean isPip          = false;
    private boolean dL             = false;
    private boolean isPlaying      = false;
    private boolean mediaSession   = false;
    private boolean isOffline      = false;
    private boolean userNavigated  = false;
    private boolean scriptsInjected = false;

    private String icon = "", title = "", subtitle = "";
    private long duration;

    // ─── Views ────────────────────────────────────────────────────────────────
    private YTProWebview web;
    private RelativeLayout offlineLayout;
    private RelativeLayout loadingScreen;
    private View outerCircle, innerCircle;
    private ObjectAnimator outerRotation, innerRotation;
    private TextView notificationBadge;

    // ─── Services / helpers ───────────────────────────────────────────────────
    private BroadcastReceiver broadcastReceiver;
    private AudioManager audioManager;
    private PowerManager.WakeLock wakeLock;
    private OnBackInvokedCallback backCallback;
    private NotificationPreferences notificationPrefs;
    private NotificationFetcher notificationFetcher;

    // ─── Network ──────────────────────────────────────────────────────────────
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    // ─── Pre-cache script strings (load once) ─────────────────────────────────
    private String cachedScriptLoader = null;

    // ─── Handler for UI tasks ─────────────────────────────────────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ══════════════════════════════════════════════════════════════════════════
    //  onCreate
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge before setContentView to avoid layout jumps
        applyEdgeToEdge();

        setContentView(R.layout.main);

        setupStatusBarColor();
        setupSystemBarsInsets();

        // Bring chrome UI to front
        bringToFront(R.id.customHeader);
        bringToFront(R.id.bottomNavBar);

        // Prefs defaults
        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        if (!prefs.contains("bgplay")) prefs.edit().putBoolean("bgplay", true).apply();

        // WakeLock
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YTPro::PIPWakeLock");

        // Services
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        audioManager        = (AudioManager) getSystemService(AUDIO_SERVICE);

        requestNotificationPermission();
        setupCustomHeader();

        notificationPrefs   = new NotificationPreferences(this);
        notificationFetcher = new NotificationFetcher(this);
        notificationBadge   = findViewById(R.id.notificationBadge);

        fetchAndUpdateNotifications();
        setupBottomNavigation();
        initLoadingScreen();

        // Pre-build script string in background so injection is instant later
        new Thread(this::preloadScripts).start();

        if (!isNetworkAvailable()) {
            hideLoadingScreen();
            showOfflineScreen();
        } else {
            showLoadingScreen();
            load(false);
            setupNetworkMonitor();
            checkForAppUpdate();
            startNotificationService();
            checkNotificationsNow();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Edge-to-edge / system bars
    // ══════════════════════════════════════════════════════════════════════════
    private void applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    private void setupStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window w = getWindow();
            w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            w.setStatusBarColor(Color.parseColor("#0F0F0F"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                w.getDecorView().setSystemUiVisibility(0);
            }
        }
    }

    private void bringToFront(int viewId) {
        View v = findViewById(viewId);
        if (v != null) v.bringToFront();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Loading screen
    // ══════════════════════════════════════════════════════════════════════════
    private void initLoadingScreen() {
        loadingScreen = new RelativeLayout(this);
        loadingScreen.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT));
        loadingScreen.setBackgroundColor(Color.parseColor("#CC0F0F0F"));
        loadingScreen.setVisibility(View.GONE);

        RelativeLayout animContainer = new RelativeLayout(this);
        RelativeLayout.LayoutParams ap = new RelativeLayout.LayoutParams(dpToPx(40), dpToPx(40));
        ap.addRule(RelativeLayout.CENTER_IN_PARENT);
        animContainer.setLayoutParams(ap);

        outerCircle = createDot("#00F2EA");
        innerCircle = createDot("#FF0050");
        animContainer.addView(outerCircle);
        animContainer.addView(innerCircle);
        loadingScreen.addView(animContainer);

        addContentView(loadingScreen, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View createDot(String color) {
        View v = new View(this);
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(dpToPx(16), dpToPx(16));
        p.addRule(RelativeLayout.CENTER_IN_PARENT);
        v.setLayoutParams(p);
        v.setBackground(createCircle(color));
        return v;
    }

    private void showLoadingScreen() {
        runOnUiThread(() -> {
            loadingScreen.setVisibility(View.VISIBLE);
            loadingScreen.bringToFront();
            startDotAnimation();
        });
    }

    private void startDotAnimation() {
        outerRotation = spin(outerCircle, 0f, 360f);
        innerRotation = spin(innerCircle, 360f, 0f);
        ObjectAnimator tx1 = translate(outerCircle, "translationX",  0f,  10f, 0f, -10f, 0f);
        ObjectAnimator ty1 = translate(outerCircle, "translationY",  0f, -10f, 0f,  10f, 0f);
        ObjectAnimator tx2 = translate(innerCircle, "translationX",  0f, -10f, 0f,  10f, 0f);
        ObjectAnimator ty2 = translate(innerCircle, "translationY",  0f,  10f, 0f, -10f, 0f);
        for (ObjectAnimator a : new ObjectAnimator[]{outerRotation, innerRotation, tx1, ty1, tx2, ty2}) {
            a.start();
        }
    }

    private ObjectAnimator spin(View v, float from, float to) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "rotation", from, to);
        a.setDuration(1200); a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        return a;
    }

    private ObjectAnimator translate(View v, String prop, float... values) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, prop, values);
        a.setDuration(1200); a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        return a;
    }

    private void hideLoadingScreen() {
        runOnUiThread(() -> {
            cancelAnimator(outerRotation);
            cancelAnimator(innerRotation);
            if (outerCircle != null) outerCircle.animate().cancel();
            if (innerCircle != null) innerCircle.animate().cancel();

            loadingScreen.animate().alpha(0f).setDuration(200)
                .setListener(new Animator.AnimatorListener() {
                    @Override public void onAnimationEnd(Animator a) {
                        loadingScreen.setVisibility(View.GONE);
                        loadingScreen.setAlpha(1f);
                    }
                    @Override public void onAnimationStart(Animator a)  {}
                    @Override public void onAnimationCancel(Animator a) {}
                    @Override public void onAnimationRepeat(Animator a) {}
                }).start();
        });
    }

    private void cancelAnimator(ObjectAnimator a) { if (a != null) a.cancel(); }

    // ══════════════════════════════════════════════════════════════════════════
    //  WebView setup
    // ══════════════════════════════════════════════════════════════════════════
    public void load(boolean dl) {
        web = findViewById(R.id.web);

        configureWebSettings();

        // Determine start URL
        String url = resolveStartUrl();
        web.loadUrl(url);

        web.addJavascriptInterface(new WebAppInterface(this), "Android");
        web.setWebChromeClient(new CustomWebClient());

        // Cookies
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(web, true);
        }

        web.setWebViewClient(new OptimizedWebViewClient());
        setReceiver();

        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = () -> { if (web.canGoBack()) web.goBack(); else finish(); };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    private void configureWebSettings() {
        WebSettings s = web.getSettings();

        // ── JavaScript / DOM ──────────────────────────────────────────────
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);

        // ── Caching — LOAD_CACHE_ELSE_NETWORK: serve cache instantly,
        //    fallback to network only on miss. Best for YouTube mobile. ────
        s.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            s.setDatabasePath(getDir("databases", Context.MODE_PRIVATE).getPath());
        }
        // AppCache removed in API 33 — WebView handles caching via setCacheMode instead

        // ── Rendering ─────────────────────────────────────────────────────
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        try { s.setRenderPriority(WebSettings.RenderPriority.HIGH); } catch (Exception ignored) {}

        // ── Media ─────────────────────────────────────────────────────────
        s.setMediaPlaybackRequiresUserGesture(false);

        // ── Images / network ──────────────────────────────────────────────
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setBlockNetworkLoads(false);

        // ── Viewport ──────────────────────────────────────────────────────
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        s.setSupportZoom(false);

        // ── Access ────────────────────────────────────────────────────────
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        web.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        // ── DNS pre-connect tweak ─────────────────────────────────────────
        System.setProperty("networkaddress.cache.ttl", "60");
        System.setProperty("networkaddress.cache.negative.ttl", "0");
    }

    private String resolveStartUrl() {
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();

        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            userNavigated = true;
            return data.toString();
        }
        if (Intent.ACTION_SEND.equals(action)) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null && (shared.contains("youtube.com") || shared.contains("youtu.be"))) {
                userNavigated = true;
                return shared;
            }
        }
        return "https://m.youtube.com/";
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Optimized WebViewClient
    // ══════════════════════════════════════════════════════════════════════════
    private class OptimizedWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String newUrl = request.getUrl().toString();
            if (newUrl.contains("/shorts") && !userNavigated) {
                String cur = view.getUrl();
                if (cur != null && !cur.contains("/shorts")) return true; // block
            }
            userNavigated = false;
            return false;
        }

        @Override
        public void onPageStarted(WebView v, String url, Bitmap favicon) {
            super.onPageStarted(v, url, favicon);
            scriptsInjected = false;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            super.onPageFinished(v, url);

            // Basic layout reset — always fast
            v.evaluateJavascript(JS_LAYOUT_RESET, null);

            if (url.contains("/feed/notifications")) {
                v.evaluateJavascript(JS_NOTIFICATION_PAGE, null);
                hideLoadingScreen();
                return;
            }

            if (!scriptsInjected) {
                injectYTProScriptsFromAssets();
                scriptsInjected = true;
            }

            v.evaluateJavascript(JS_HIDE_NATIVE_NAV, null);
            v.evaluateJavascript(JS_BLOCK_SHORTS_PUSH, null);

            if (dL) {
                mainHandler.postDelayed(() -> {
                    web.evaluateJavascript(
                        "if(typeof window.ytproDownVid==='function'){window.location.hash='download';}", null);
                    dL = false;
                }, 1500); // reduced from 2000
            }

            if (!url.contains("youtube.com/watch") && !url.contains("youtube.com/shorts") && isPlaying) {
                isPlaying = false; mediaSession = false;
                stopService(new Intent(getApplicationContext(), ForegroundService.class));
            }

            // Hide loading immediately — no extra delay
            hideLoadingScreen();
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String desc, String failingUrl) {
            handleLoadError(errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                            errorCode == WebViewClient.ERROR_CONNECT  ||
                            errorCode == WebViewClient.ERROR_TIMEOUT);
            super.onReceivedError(view, errorCode, desc, failingUrl);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && req.isForMainFrame()) {
                int code = err.getErrorCode();
                handleLoadError(code == WebViewClient.ERROR_HOST_LOOKUP ||
                                code == WebViewClient.ERROR_CONNECT     ||
                                code == WebViewClient.ERROR_TIMEOUT);
            }
            super.onReceivedError(view, req, err);
        }

        private void handleLoadError(boolean isNetworkError) {
            if (isNetworkError) {
                runOnUiThread(() -> { hideLoadingScreen(); showOfflineScreen(); });
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Inline JS constants (built once, reused every page load)
    // ══════════════════════════════════════════════════════════════════════════
    private static final String JS_LAYOUT_RESET =
        "(function(){" +
        "var s=document.body?document.body.style:null;if(!s)return;" +
        "s.margin='0';s.padding='0';" +
        "document.documentElement.style.cssText='margin:0;padding:0;overflow:auto;';" +
        "})();";

    private static final String JS_NOTIFICATION_PAGE =
        "(function(){" +
        "var st=document.createElement('style');" +
        "st.innerHTML='*{margin:0;padding:0;box-sizing:border-box;}html,body{margin:0!important;padding:0!important;width:100%!important;overflow-x:hidden!important;}ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer,#masthead{display:none!important;}body{padding-top:0px!important;padding-bottom:70px!important;background:#0F0F0F!important;}';" +
        "document.head.appendChild(st);" +
        "})();";

    private static final String JS_HIDE_NATIVE_NAV =
        "(function(){" +
        "if(window.__ytproNavHidden)return;window.__ytproNavHidden=true;" +
        "var css='ytm-mobile-topbar-renderer,#masthead,.mobile-topbar-header,ytm-pivot-bar-renderer,ytm-pivot-bar-item-renderer,.pivot-bar-item-tab,.pivot-bar,c3-tab-bar-renderer{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;max-height:0!important;opacity:0!important;overflow:hidden!important;}body{padding-top:0!important;padding-bottom:70px!important;}#page-manager{padding-bottom:70px!important;}';" +
        "var st=document.createElement('style');st.id='ytpro-hide-nav';st.textContent=css;document.head.appendChild(st);" +
        "function hide(){['ytm-mobile-topbar-renderer','#masthead','ytm-pivot-bar-renderer','ytm-pivot-bar-item-renderer','c3-tab-bar-renderer'].forEach(function(sel){document.querySelectorAll(sel).forEach(function(el){el.style.cssText='display:none!important;visibility:hidden!important;height:0!important;opacity:0!important;';});});}" +
        "hide();" +
        "new MutationObserver(hide).observe(document.body,{childList:true,subtree:true});" +
        "})();";

    private static final String JS_BLOCK_SHORTS_PUSH =
        "(function(){" +
        "if(window.__ytproShortBlocked)return;window.__ytproShortBlocked=true;" +
        "var orig=history.pushState;" +
        "history.pushState=function(st,t,url){if(url&&url.includes('/shorts')&&!window.location.href.includes('/shorts'))return;return orig.apply(this,arguments);};" +
        "})();";

    // ══════════════════════════════════════════════════════════════════════════
    //  Script injection — pre-loaded strings, zero file I/O on main thread
    // ══════════════════════════════════════════════════════════════════════════

    /** Called from background thread in onCreate to build script string once. */
    private void preloadScripts() {
        cachedScriptLoader = buildScriptLoader();
    }

    private void injectYTProScriptsFromAssets() {
        // Trusted-types policy
        web.evaluateJavascript(
            "if(window.trustedTypes&&window.trustedTypes.createPolicy&&!window.trustedTypes.defaultPolicy){" +
            "window.trustedTypes.createPolicy('default',{createHTML:s=>s,createScriptURL:s=>s,createScript:s=>s});}", null);

        // Use pre-cached scripts if ready, otherwise build now (rare)
        String loader = (cachedScriptLoader != null) ? cachedScriptLoader : buildScriptLoader();
        web.evaluateJavascript(loader, null);

        // Video + player optimizations
        web.evaluateJavascript(JS_PLAYER_BOOST, null);
        // Status-bar color sync
        web.evaluateJavascript(JS_STATUS_BAR_SYNC, null);
    }

    private String buildScriptLoader() {
        String[] scripts = {
            "script.js","bgplay.js","innertube.js","styles.js",
            "welcome.js","subscriptions.js","login.js","darkmode.js"
        };
        StringBuilder sb = new StringBuilder(65536);
        sb.append("(function(){if(window.YTPRO_LOADED)return;");
        sb.append("function L(c){var s=document.createElement('script');s.textContent=c;s.async=false;document.body.appendChild(s);}");
        for (String f : scripts) {
            String content = readRawScript(f);
            if (!content.isEmpty()) {
                sb.append("L(`").append(content).append("`);");
            }
        }
        sb.append("window.YTPRO_LOADED=true;})();");
        return sb.toString();
    }

    private String readRawScript(String filename) {
        try {
            int id = getResources().getIdentifier(
                filename.replace(".js",""), "raw", getPackageName());
            if (id == 0) return "";
            InputStream is = getResources().openRawResource(id);
            BufferedReader br = new BufferedReader(new InputStreamReader(is), 16384);
            StringBuilder sb = new StringBuilder(8192);
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString()
                .replace("\\", "\\\\")
                .replace("`",  "\\`")
                .replace("${", "\\${")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        } catch (IOException e) {
            Log.e("Script", "Failed: " + filename + " — " + e.getMessage());
            return "";
        }
    }

    private static final String JS_PLAYER_BOOST =
        "(function(){" +
        "setTimeout(function(){" +
        // Remove premium upsells
        "document.querySelectorAll('ytm-purchase-offer-renderer,ytm-upsell-dialog-renderer').forEach(function(e){e.remove();});" +
        // Force autoplay + background
        "if(window.ytplayer&&window.ytplayer.config){window.ytplayer.config.args.autoplay=1;window.ytplayer.config.args.background=1;}" +
        // Video element optimizations
        "document.querySelectorAll('video').forEach(function(v){" +
        "v.preload='auto';" +
        "v.setAttribute('playsinline','true');" +
        "v.setAttribute('x-webkit-airplay','allow');" +
        "if(v.buffered&&v.buffered.length===0)v.load();" +
        "});" +
        // Disable adaptive throttling that causes stuttering
        "if(window.yt&&window.yt.config_){" +
        "var f=window.yt.config_.EXPERIMENT_FLAGS=window.yt.config_.EXPERIMENT_FLAGS||{};" +
        "f.html5_enable_vp9_on_mobile=true;" +
        "f.html5_prefer_server_bandwidth_cap=false;" +
        "f.html5_disable_stop_buffering=false;" +
        "}" +
        "},800);" + // slightly reduced from 1000
        "})();";

    private static final String JS_STATUS_BAR_SYNC =
        "(function(){" +
        "function rgb2hex(rgb){var m=rgb.match(/\\d+/g);if(!m||m.length<3)return'#0F0F0F';" +
        "return'#'+[m[0],m[1],m[2]].map(function(x){return(+x).toString(16).padStart(2,'0');}).join('');}" +
        "function update(){var sels=['ytm-mobile-topbar-renderer','#masthead','.mobile-topbar-header'];" +
        "for(var i=0;i<sels.length;i++){var h=document.querySelector(sels[i]);" +
        "if(h){var c=rgb2hex(getComputedStyle(h).backgroundColor);" +
        "if(window.Android&&window.Android.setStatusBarColor)window.Android.setStatusBarColor(c);break;}}}" +
        "new MutationObserver(update).observe(document.body,{attributes:true,childList:true,subtree:true});" +
        "setTimeout(update,500);" +
        "})();";

    // ══════════════════════════════════════════════════════════════════════════
    //  Network monitor (auto-reconnect)
    // ══════════════════════════════════════════════════════════════════════════
    private void setupNetworkMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    runOnUiThread(() -> {
                        if (isOffline) {
                            hideOfflineScreen();
                            load(false);
                            setupBottomNavigation();
                            setupNetworkMonitor();
                        } else if (web != null) {
                            // If page is blank/empty → silent reload
                            web.evaluateJavascript(
                                "(function(){if(document.body&&document.body.innerHTML.length<500)location.reload();})();",
                                null);
                        }
                    });
                }
                @Override public void onLost(Network network) {
                    runOnUiThread(() -> { if (!isOffline) showOfflineScreen(); });
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Custom Header / Search
    // ══════════════════════════════════════════════════════════════════════════
    private void setupCustomHeader() {
        ImageView iconSearch        = findViewById(R.id.iconSearch);
        ImageView iconNotifications = findViewById(R.id.iconNotifications);
        ImageView iconCast          = findViewById(R.id.iconCast);
        ImageView iconSettings      = findViewById(R.id.iconSettings);

        RelativeLayout searchBarContainer = findViewById(R.id.searchBarContainer);
        ImageView searchBackButton        = findViewById(R.id.searchBackButton);
        EditText  searchInput             = findViewById(R.id.searchInput);
        ImageView voiceSearchButton       = findViewById(R.id.voiceSearchButton);
        ListView  suggestionsList         = findViewById(R.id.suggestionsList);

        ArrayAdapter<String> suggestionsAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_list_item_1, new ArrayList<>());

        if (suggestionsList != null) {
            suggestionsList.setAdapter(suggestionsAdapter);
            suggestionsList.setBackgroundColor(Color.parseColor("#1A1A1A"));
            suggestionsList.setOnItemClickListener((parent, view, position, id) -> {
                String query = suggestionsAdapter.getItem(position);
                if (query != null) {
                    userNavigated = true;
                    web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query));
                    closeSearchBar(searchBarContainer, searchInput);
                }
            });
        }

        iconSearch.setOnClickListener(v -> {
            searchBarContainer.setVisibility(View.VISIBLE);
            searchBarContainer.setAlpha(0f);
            searchBarContainer.setTranslationY(-56f);
            searchBarContainer.animate().alpha(1f).translationY(0f).setDuration(250).start();
            searchInput.requestFocus();
            imm().showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        });

        searchBackButton.setOnClickListener(v -> closeSearchBar(searchBarContainer, searchInput));

        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            private final Handler h = new Handler(Looper.getMainLooper());
            private Runnable r;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (r != null) h.removeCallbacks(r);
                String q = s.toString().trim();
                if (q.isEmpty()) {
                    if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
                    return;
                }
                r = () -> fetchSuggestions(q, suggestionsAdapter, suggestionsList);
                h.postDelayed(r, 250); // reduced from 300
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = searchInput.getText().toString();
                if (!q.isEmpty()) {
                    userNavigated = true;
                    web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(q));
                    closeSearchBar(searchBarContainer, searchInput);
                }
                return true;
            }
            return false;
        });

        if (voiceSearchButton != null) {
            voiceSearchButton.setOnClickListener(v ->
                startVoiceSearch(searchInput, suggestionsAdapter, suggestionsList));
        }

        iconNotifications.setOnClickListener(v ->
            startActivity(new Intent(this, NotificationActivity.class)));

        iconCast.setOnClickListener(v ->
            Toast.makeText(this, "Cast feature coming soon! 📡", Toast.LENGTH_SHORT).show());

        iconSettings.setOnClickListener(v -> {
            userNavigated = true;
            web.evaluateJavascript("window.location.hash='settings';", null);
        });
    }

    private InputMethodManager imm() {
        return (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    private void closeSearchBar(RelativeLayout container, EditText input) {
        container.animate().alpha(0f).translationY(-56f).setDuration(250)
            .withEndAction(() -> {
                container.setVisibility(View.GONE);
                if (input != null) input.setText("");
                ListView sl = findViewById(R.id.suggestionsList);
                if (sl != null) sl.setVisibility(View.GONE);
            }).start();
        imm().hideSoftInputFromWindow(container.getWindowToken(), 0);
    }

    private void fetchSuggestions(String query, ArrayAdapter<String> adapter, ListView list) {
        new Thread(() -> {
            try {
                URL url = new URL("https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=" + Uri.encode(query));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(2500);
                conn.setRequestProperty("Accept-Encoding", "gzip");

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                String response = sb.toString();
                List<String> suggestions = new ArrayList<>();
                int start = response.indexOf(",[");
                if (start != -1) {
                    java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("\\[\"([^\"]+)\"").matcher(response.substring(start + 2));
                    while (m.find() && suggestions.size() < 8) suggestions.add(m.group(1));
                }
                runOnUiThread(() -> {
                    adapter.clear();
                    adapter.addAll(suggestions);
                    adapter.notifyDataSetChanged();
                    if (list != null) list.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e("Suggestions", e.getMessage());
            }
        }).start();
    }

    private void startVoiceSearch(EditText input, ArrayAdapter<String> adapter, ListView list) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
            return;
        }
        android.speech.SpeechRecognizer sr = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
        Intent si = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        si.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        si.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        si.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search YouTube...");

        sr.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onResults(Bundle b) {
                ArrayList<String> m = b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                if (m != null && !m.isEmpty()) {
                    String q = m.get(0);
                    runOnUiThread(() -> { input.setText(q); input.setSelection(q.length()); fetchSuggestions(q, adapter, list); });
                }
                sr.destroy();
            }
            @Override public void onPartialResults(Bundle b) {
                ArrayList<String> p = b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                if (p != null && !p.isEmpty()) {
                    String t = p.get(0);
                    runOnUiThread(() -> { input.setText(t); input.setSelection(t.length()); });
                }
            }
            @Override public void onError(int e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Voice search failed 🎤", Toast.LENGTH_SHORT).show());
                sr.destroy();
            }
            @Override public void onReadyForSpeech(Bundle p) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Listening... 🎤", Toast.LENGTH_SHORT).show());
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float r) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int t, Bundle p) {}
        });
        sr.startListening(si);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Bottom Navigation
    // ══════════════════════════════════════════════════════════════════════════
    private void setupBottomNavigation() {
        LinearLayout navHome          = findViewById(R.id.navHome);
        LinearLayout navShorts        = findViewById(R.id.navShorts);
        LinearLayout navUpload        = findViewById(R.id.navUpload);
        LinearLayout navSubscriptions = findViewById(R.id.navSubscriptions);
        LinearLayout navYou           = findViewById(R.id.navYou);

        ImageView iHome  = findViewById(R.id.iconHome);
        ImageView iShort = findViewById(R.id.iconShorts);
        ImageView iSubs  = findViewById(R.id.iconSubscriptions);
        ImageView iYou   = findViewById(R.id.iconYou);

        TextView tHome  = findViewById(R.id.textHome);
        TextView tShort = findViewById(R.id.textShorts);
        TextView tSubs  = findViewById(R.id.textSubscriptions);
        TextView tYou   = findViewById(R.id.textYou);

        navHome.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iHome, tHome, iShort, tShort, iSubs, tSubs, iYou, tYou);
            web.loadUrl("https://m.youtube.com/");
        });
        navShorts.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iShort, tShort, iHome, tHome, iSubs, tSubs, iYou, tYou);
            web.loadUrl("https://m.youtube.com/shorts");
        });
        navUpload.setOnClickListener(v ->
            Toast.makeText(this, "Upload feature coming soon! 🎥", Toast.LENGTH_SHORT).show());
        navSubscriptions.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iSubs, tSubs, iHome, tHome, iShort, tShort, iYou, tYou);
            web.loadUrl("https://m.youtube.com/feed/subscriptions");
        });
        navYou.setOnClickListener(v -> {
            userNavigated = true;
            setActiveTab(iYou, tYou, iHome, tHome, iShort, tShort, iSubs, tSubs);
            web.loadUrl("https://m.youtube.com/feed/account");
        });
    }

    private void setActiveTab(ImageView activeIcon, TextView activeText, Object... rest) {
        activeIcon.setColorFilter(Color.parseColor("#FF0000"));
        activeText.setTextColor(Color.WHITE);
        for (Object o : rest) {
            if (o instanceof ImageView) ((ImageView) o).setColorFilter(Color.parseColor("#AAAAAA"));
            else if (o instanceof TextView) ((TextView) o).setTextColor(Color.parseColor("#AAAAAA"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Broadcast receiver (media controls)
    // ══════════════════════════════════════════════════════════════════════════
    public void setReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                String action = intent.getExtras().getString("actionname");
                switch (action) {
                    case "PLAY_ACTION":  web.evaluateJavascript("playVideo();", null); break;
                    case "PAUSE_ACTION": web.evaluateJavascript("pauseVideo();", null); break;
                    case "NEXT_ACTION":  web.evaluateJavascript("playNext();", null); break;
                    case "PREV_ACTION":  web.evaluateJavascript("playPrev();", null); break;
                    case "SEEKTO":
                        web.evaluateJavascript("seekTo('" + intent.getExtras().getString("pos") + "');", null);
                        break;
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"), RECEIVER_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CustomWebChromeClient (fullscreen video)
    // ══════════════════════════════════════════════════════════════════════════
    public class CustomWebClient extends WebChromeClient {
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCallback;
        private int mOrigOrientation;
        private int mOrigSystemUiVis;

        @Override
        public Bitmap getDefaultVideoPoster() {
            return BitmapFactory.decodeResource(getResources(), 2130837573);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            mOrigOrientation = portrait
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            if (isPip) mOrigOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                WindowManager.LayoutParams p = getWindow().getAttributes();
                p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(p);
            }
            if (mCustomView != null) { onHideCustomView(); return; }
            mCustomView = view;
            mOrigSystemUiVis = getWindow().getDecorView().getSystemUiVisibility();
            setRequestedOrientation(mOrigOrientation);
            mOrigOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            mCallback = callback;
            ((FrameLayout) getWindow().getDecorView()).addView(mCustomView, new FrameLayout.LayoutParams(-1, -1));
            getWindow().getDecorView().setSystemUiVisibility(3846);
        }

        @Override
        public void onHideCustomView() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                WindowManager.LayoutParams p = getWindow().getAttributes();
                p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                getWindow().setAttributes(p);
            }
            ((FrameLayout) getWindow().getDecorView()).removeView(mCustomView);
            mCustomView = null;
            getWindow().getDecorView().setSystemUiVisibility(mOrigSystemUiVis);
            setRequestedOrientation(mOrigOrientation);
            mOrigOrientation = portrait
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            mCallback = null;
            web.clearFocus();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            if (Build.VERSION.SDK_INT > 22 && request.getOrigin().toString().contains("youtube.com")) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                } else {
                    request.grant(request.getResources());
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  JS Interface
    // ══════════════════════════════════════════════════════════════════════════
    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface public void showToast(String txt) { Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_SHORT).show(); }
        @JavascriptInterface public void gohome(String x) { Intent i = new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
        @JavascriptInterface public void downvid(String name, String url, String m) { downloadFile(name, url, m); }
        @JavascriptInterface public void fullScreen(boolean value) { portrait = value; }
        @JavascriptInterface public void oplink(String url) { Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)); startActivity(i); }
        @JavascriptInterface public String getInfo() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1.0"; } }
        @JavascriptInterface public void setBgPlay(boolean bg) { getSharedPreferences("YTPRO", MODE_PRIVATE).edit().putBoolean("bgplay", bg).apply(); }

        @JavascriptInterface public void bgStart(String ic, String ti, String su, long du) {
            icon=ic; title=ti; subtitle=su; duration=du; isPlaying=true; mediaSession=true;
            Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
            intent.putExtra("icon",ic).putExtra("title",ti).putExtra("subtitle",su)
                  .putExtra("duration",du).putExtra("currentPosition",0).putExtra("action","play");
            startService(intent);
        }
        @JavascriptInterface public void bgUpdate(String ic, String ti, String su, long du) {
            icon=ic; title=ti; subtitle=su; duration=du; isPlaying=true;
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon",ic).putExtra("title",ti).putExtra("subtitle",su)
                .putExtra("duration",du).putExtra("currentPosition",0).putExtra("action","pause"));
        }
        @JavascriptInterface public void bgStop()  { isPlaying=false; mediaSession=false; stopService(new Intent(getApplicationContext(), ForegroundService.class)); }
        @JavascriptInterface public void bgPause(long ct) { isPlaying=false; sendBroadcast(new Intent("UPDATE_NOTIFICATION").putExtra("icon",icon).putExtra("title",title).putExtra("subtitle",subtitle).putExtra("duration",duration).putExtra("currentPosition",ct).putExtra("action","pause")); }
        @JavascriptInterface public void bgPlay(long ct)  { isPlaying=true;  sendBroadcast(new Intent("UPDATE_NOTIFICATION").putExtra("icon",icon).putExtra("title",title).putExtra("subtitle",subtitle).putExtra("duration",duration).putExtra("currentPosition",ct).putExtra("action","play")); }
        @JavascriptInterface public void bgBuffer(long ct){ isPlaying=true;  sendBroadcast(new Intent("UPDATE_NOTIFICATION").putExtra("icon",icon).putExtra("title",title).putExtra("subtitle",subtitle).putExtra("duration",duration).putExtra("currentPosition",ct).putExtra("action","buffer")); }

        @JavascriptInterface public void getSNlM0e(String cookies) { new Thread(() -> { String r = GeminiWrapper.getSNlM0e(cookies); runOnUiThread(() -> web.evaluateJavascript("callbackSNlM0e.resolve(`"+r+"`)", null)); }).start(); }
        @JavascriptInterface public void GeminiClient(String url, String headers, String body) { new Thread(() -> { JSONObject r = GeminiWrapper.getStream(url, headers, body); runOnUiThread(() -> web.evaluateJavascript("callbackGeminiClient.resolve("+r+")", null)); }).start(); }
        @JavascriptInterface public String getAllCookies(String url) { return CookieManager.getInstance().getCookie(url); }
        @JavascriptInterface public float  getVolume()  { return (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); }
        @JavascriptInterface public void   setVolume(float v) { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*v), 0); }
        @JavascriptInterface public float  getBrightness() { try { return (Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS)/255f)*100f; } catch (Exception e) { return 50f; } }
        @JavascriptInterface public void   setBrightness(float val) { runOnUiThread(() -> { WindowManager.LayoutParams lp = getWindow().getAttributes(); lp.screenBrightness = Math.max(0f, Math.min(val,1f)); getWindow().setAttributes(lp); }); }
        @JavascriptInterface public void   pipvid(String x) {
            if (Build.VERSION.SDK_INT >= 26) {
                try { enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(x.equals("portrait")?9:16, x.equals("portrait")?16:9)).build());
                } catch (Exception ignored) {}
            } else { Toast.makeText(getApplicationContext(), getString(R.string.no_pip), Toast.LENGTH_SHORT).show(); }
        }
        @JavascriptInterface public void setStatusBarColor(String color) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try { getWindow().setStatusBarColor(Color.parseColor(color)); } catch (Exception ignored) {}
                }
            });
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Download
    // ══════════════════════════════════════════════════════════════════════════
    private void downloadFile(String filename, String url, String mtype) {
        if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.grant_storage, Toast.LENGTH_SHORT).show());
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        try {
            String encodedName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle(filename).setDescription(filename).setMimeType(mtype)
               .setAllowedOverMetered(true).setAllowedOverRoaming(true)
               .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedName)
               .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            dm.enqueue(req);
            Toast.makeText(this, getString(R.string.dl_started), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PiP
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void onPictureInPictureModeChanged(boolean inPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        web.loadUrl(inPip ? "javascript:PIPlayer();" : "javascript:removePIP();", null);
        isPip = inPip;

        if (!inPip) {
            runOnUiThread(() -> {
                bringToFront(R.id.customHeader);
                View sb = findViewById(R.id.searchBarContainer);
                if (sb != null) sb.setVisibility(View.GONE);
                bringToFront(R.id.bottomNavBar);
                if (web != null) { web.requestLayout(); web.scrollTo(0,0); }
                setupSystemBarsInsets();
            });
            mainHandler.postDelayed(() -> web.evaluateJavascript(
                "(function(){document.body.style.paddingTop='0px';document.body.style.marginTop='0px';" +
                "['ytm-mobile-topbar-renderer','ytm-pivot-bar-renderer'].forEach(function(s){var e=document.querySelector(s);if(e)e.remove();});})();", null), 150);
        }

        if (inPip && isPlaying) { if (!wakeLock.isHeld()) wakeLock.acquire(10*60*1000L); }
        else { if (wakeLock.isHeld()) wakeLock.release(); }
    }

    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); }

    // ══════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════════
    @Override protected void onResume() {
        super.onResume();
        if (notificationPrefs != null) updateNotificationBadge();
    }

    @Override protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        stopService(new Intent(getApplicationContext(), ForegroundService.class));
        if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if (web != null) { web.stopLoading(); web.destroy(); }
    }

    @Override public void onBackPressed() {
        RelativeLayout searchBar = findViewById(R.id.searchBarContainer);
        if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) {
            closeSearchBar(searchBar, findViewById(R.id.searchInput));
            return;
        }
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Permissions
    // ══════════════════════════════════════════════════════════════════════════
    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 101) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                web.loadUrl("https://m.youtube.com");
            else Toast.makeText(this, getString(R.string.grant_mic), Toast.LENGTH_SHORT).show();
        } else if (code == 1) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_DENIED)
                Toast.makeText(this, getString(R.string.grant_storage), Toast.LENGTH_SHORT).show();
        } else if (code == 102) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                startNotificationService();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Notifications
    // ══════════════════════════════════════════════════════════════════════════
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 102);
        }
    }

    private void startNotificationService() {
        startService(new Intent(this, NotificationCheckService.class));
    }

    private void checkNotificationsNow() {
        new NotificationFetcher(this).fetchNotifications(new NotificationFetcher.NotificationCallback() {
            @Override public void onSuccess(List<NotificationModel> n) { new AppNotificationManager(MainActivity.this).showNotifications(n); }
            @Override public void onError(String e) { Log.e("MainActivity", "Notification error: " + e); }
        });
    }

    private void fetchAndUpdateNotifications() {
        notificationFetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
            @Override public void onSuccess(List<NotificationModel> n) {
                runOnUiThread(() -> { notificationPrefs.saveNotifications(n); updateNotificationBadge(); });
            }
            @Override public void onError(String e) { runOnUiThread(() -> updateNotificationBadge()); }
        });
    }

    private void updateNotificationBadge() {
        int count = notificationPrefs.getUnviewedCount();
        notificationBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) notificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Offline screen
    // ══════════════════════════════════════════════════════════════════════════
    private void showOfflineScreen() {
        isOffline = true;
        offlineLayout = new RelativeLayout(this);
        offlineLayout.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        offlineLayout.setBackgroundColor(Color.parseColor("#0F0F0F"));

        LinearLayout center = new LinearLayout(this);
        RelativeLayout.LayoutParams cp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        cp.addRule(RelativeLayout.CENTER_IN_PARENT);
        center.setLayoutParams(cp);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);

        TextView icon = new TextView(this);
        icon.setText("📡"); icon.setTextSize(80); icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ip.bottomMargin = dpToPx(24); icon.setLayoutParams(ip);
        center.addView(icon);

        TextView titleV = new TextView(this);
        titleV.setText("No internet connection");
        titleV.setTextSize(20); titleV.setTextColor(Color.WHITE);
        titleV.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = dpToPx(8); titleV.setLayoutParams(tp);
        center.addView(titleV);

        TextView msg = new TextView(this);
        msg.setText("Check your Wi-Fi or mobile data\nconnection.");
        msg.setTextSize(14); msg.setTextColor(Color.parseColor("#AAAAAA")); msg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dpToPx(280), LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.bottomMargin = dpToPx(32); msg.setLayoutParams(mp);
        center.addView(msg);

        Button retry = new Button(this);
        retry.setText("Try again"); retry.setTextColor(Color.WHITE);
        retry.setTextSize(16); retry.setTypeface(null, Typeface.BOLD); retry.setAllCaps(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            retry.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF0000")));
        else retry.setBackgroundColor(Color.parseColor("#FF0000"));
        retry.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(200), dpToPx(50)));
        retry.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                hideOfflineScreen();
                load(false);
                setupBottomNavigation();
                setupNetworkMonitor();
            } else {
                Toast.makeText(MainActivity.this, "Still no connection", Toast.LENGTH_SHORT).show();
            }
        });
        center.addView(retry);
        offlineLayout.addView(center);
        addContentView(offlineLayout, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideOfflineScreen() {
        if (offlineLayout != null && offlineLayout.getParent() != null) {
            ((ViewGroup) offlineLayout.getParent()).removeView(offlineLayout);
            isOffline = false;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Update check
    // ══════════════════════════════════════════════════════════════════════════
    private void checkForAppUpdate() {
        mainHandler.postDelayed(() -> {
            if (isNetworkAvailable()) new UpdateChecker(MainActivity.this).checkForUpdate();
        }, 3000); // slightly delayed so app loads first
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  System bars / insets
    // ══════════════════════════════════════════════════════════════════════════
    private void setupSystemBarsInsets() {
        View root = findViewById(android.R.id.content);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                applyInsetsToViews(bars.top, bars.bottom, bars.left, bars.right);
                return WindowInsets.CONSUMED;
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                applyInsetsToViews(insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetBottom(),
                    insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetRight());
                return insets.consumeSystemWindowInsets();
            });
        } else {
            root.post(() -> applyInsetsToViews(getStatusBarHeight(), getNavigationBarHeight(), 0, 0));
        }
        root.requestApplyInsets();
    }

    private void applyInsetsToViews(int top, int bottom, int left, int right) {
        runOnUiThread(() -> {
            applyTopMargin(R.id.customHeader, top);
            applyTopMargin(R.id.searchBarContainer, top);
            applyBottomMargin(R.id.bottomNavBar, bottom);
        });
    }

    private void applyTopMargin(int viewId, int px) {
        View v = findViewById(viewId);
        if (v != null) {
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) v.getLayoutParams();
            p.topMargin = px; v.setLayoutParams(p); v.requestLayout();
        }
    }

    private void applyBottomMargin(int viewId, int px) {
        View v = findViewById(viewId);
        if (v != null) {
            RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) v.getLayoutParams();
            p.bottomMargin = px; v.setLayoutParams(p); v.requestLayout();
        }
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) return getResources().getDimensionPixelSize(id);
        return (int) Math.ceil(25 * getResources().getDisplayMetrics().density);
    }

    private int getNavigationBarHeight() {
        if (!hasNavigationBar()) return 0;
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private boolean hasNavigationBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            android.util.DisplayMetrics real = new android.util.DisplayMetrics();
            android.util.DisplayMetrics display = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(real);
            getWindowManager().getDefaultDisplay().getMetrics(display);
            return (real.widthPixels - display.widthPixels) > 0 || (real.heightPixels - display.heightPixels) > 0;
        }
        return !ViewConfiguration.get(this).hasPermanentMenuKey() &&
               !KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private boolean isNetworkAvailable() {
        if (connectivityManager == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = connectivityManager.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities nc = connectivityManager.getNetworkCapabilities(net);
            return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                  nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                  nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo ni = connectivityManager.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private android.graphics.drawable.Drawable createCircle(String color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(Color.parseColor(color));
        return d;
    }
}
