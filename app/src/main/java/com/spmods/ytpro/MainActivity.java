package com.spmods.ytpro;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.*;
import android.os.*;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
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
import javax.net.ssl.HttpsURLConnection;
import java.util.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;

public class MainActivity extends Activity {

  private boolean portrait = false;
  private BroadcastReceiver broadcastReceiver;
  private AudioManager audioManager;
  private PowerManager.WakeLock wakeLock;

  private String icon = "";
  private String title = "";
  private String subtitle = "";
  private long duration;
  private boolean isPlaying = false;
  private boolean mediaSession = false;
  private boolean isPip = false;
  private boolean dL = false;

  private YTProWebview web;
  private OnBackInvokedCallback backCallback;
  
  private RelativeLayout offlineLayout;
  private boolean isOffline = false;
  
  private boolean userNavigated = false;
  private String lastUrl = "";
  
  private boolean scriptsInjected = false;
  
  private RelativeLayout loadingScreen;
  private View outerCircle;
  private View innerCircle;
  
  private ObjectAnimator outerRotation;
  private ObjectAnimator innerRotation;
  
  private TextView notificationBadge;
  private NotificationPreferences notificationPrefs;
  private NotificationFetcher notificationFetcher;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.show(WindowInsets.Type.systemBars());
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
    }

    View customHeader = findViewById(R.id.customHeader);
    if (customHeader != null) {
        customHeader.bringToFront();
    }
    
    View bottomNav = findViewById(R.id.bottomNavBar);
    if (bottomNav != null) {
        bottomNav.bringToFront();
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#0F0F0F"));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(0);
        }
    }

    setupSystemBarsInsets();

    SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
    if (!prefs.contains("bgplay")) {
      prefs.edit().putBoolean("bgplay", true).apply();
    }
    
    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    wakeLock = powerManager.newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK, 
        "YTPro::PIPWakeLock"
    );
    
    requestNotificationPermission();
    setupCustomHeader();
    
    notificationPrefs = new NotificationPreferences(this);
    notificationFetcher = new NotificationFetcher(this);
    notificationBadge = findViewById(R.id.notificationBadge);
    fetchAndUpdateNotifications();
    
    setupBottomNavigation();
    initLoadingScreen();
    
    if (!isNetworkAvailable()) {
        hideLoadingScreen();
        showOfflineScreen();
    } else {
        showLoadingScreen();
        load(false);
        checkForAppUpdate();
        startNotificationService();
        checkNotificationsNow();
    }
    
    MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }
  
  private void initLoadingScreen() {
    loadingScreen = new RelativeLayout(this);
    loadingScreen.setLayoutParams(new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    ));
    loadingScreen.setBackgroundColor(Color.parseColor("#CC0F0F0F"));
    loadingScreen.setVisibility(View.GONE);
    
    RelativeLayout animContainer = new RelativeLayout(this);
    RelativeLayout.LayoutParams animParams = new RelativeLayout.LayoutParams(
        dpToPx(40), 
        dpToPx(40)
    );
    animParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    animContainer.setLayoutParams(animParams);
    
    outerCircle = new View(this);
    RelativeLayout.LayoutParams ball1Params = new RelativeLayout.LayoutParams(
        dpToPx(16), 
        dpToPx(16)
    );
    ball1Params.addRule(RelativeLayout.CENTER_IN_PARENT);
    outerCircle.setLayoutParams(ball1Params);
    outerCircle.setBackground(createCircle("#00F2EA"));
    animContainer.addView(outerCircle);
    
    innerCircle = new View(this);
    RelativeLayout.LayoutParams ball2Params = new RelativeLayout.LayoutParams(
        dpToPx(16), 
        dpToPx(16)
    );
    ball2Params.addRule(RelativeLayout.CENTER_IN_PARENT);
    innerCircle.setLayoutParams(ball2Params);
    innerCircle.setBackground(createCircle("#FF0050"));
    animContainer.addView(innerCircle);
    
    loadingScreen.addView(animContainer);
    addContentView(loadingScreen, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    ));
  }
  
  private void showLoadingScreen() {
    runOnUiThread(() -> {
        loadingScreen.setVisibility(View.VISIBLE);
        loadingScreen.bringToFront();
        
        ObjectAnimator rotate1 = ObjectAnimator.ofFloat(outerCircle, "rotation", 0f, 360f);
        ObjectAnimator translateX1 = ObjectAnimator.ofFloat(outerCircle, "translationX", 0f, 10f, 0f, -10f, 0f);
        ObjectAnimator translateY1 = ObjectAnimator.ofFloat(outerCircle, "translationY", 0f, -10f, 0f, 10f, 0f);
        
        rotate1.setDuration(1200);
        translateX1.setDuration(1200);
        translateY1.setDuration(1200);
        
        rotate1.setRepeatCount(ValueAnimator.INFINITE);
        translateX1.setRepeatCount(ValueAnimator.INFINITE);
        translateY1.setRepeatCount(ValueAnimator.INFINITE);
        
        rotate1.setInterpolator(new LinearInterpolator());
        translateX1.setInterpolator(new LinearInterpolator());
        translateY1.setInterpolator(new LinearInterpolator());
        
        ObjectAnimator rotate2 = ObjectAnimator.ofFloat(innerCircle, "rotation", 360f, 0f);
        ObjectAnimator translateX2 = ObjectAnimator.ofFloat(innerCircle, "translationX", 0f, -10f, 0f, 10f, 0f);
        ObjectAnimator translateY2 = ObjectAnimator.ofFloat(innerCircle, "translationY", 0f, 10f, 0f, -10f, 0f);
        
        rotate2.setDuration(1200);
        translateX2.setDuration(1200);
        translateY2.setDuration(1200);
        
        rotate2.setRepeatCount(ValueAnimator.INFINITE);
        translateX2.setRepeatCount(ValueAnimator.INFINITE);
        translateY2.setRepeatCount(ValueAnimator.INFINITE);
        
        rotate2.setInterpolator(new LinearInterpolator());
        translateX2.setInterpolator(new LinearInterpolator());
        translateY2.setInterpolator(new LinearInterpolator());
        
        rotate1.start();
        translateX1.start();
        translateY1.start();
        rotate2.start();
        translateX2.start();
        translateY2.start();
        
        outerRotation = rotate1;
        innerRotation = rotate2;
    });
  }
  
  private void hideLoadingScreen() {
    runOnUiThread(() -> {
        if (outerRotation != null) outerRotation.cancel();
        if (innerRotation != null) innerRotation.cancel();
        
        outerCircle.animate().cancel();
        innerCircle.animate().cancel();
        
        loadingScreen.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loadingScreen.setVisibility(View.GONE);
                    loadingScreen.setAlpha(1f);
                }
                @Override public void onAnimationStart(Animator animation) {}
                @Override public void onAnimationCancel(Animator animation) {}
                @Override public void onAnimationRepeat(Animator animation) {}
            })
            .start();
    });
  }
  
  public void load(boolean dl) {
    web = findViewById(R.id.web);
    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
   
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setCacheMode(WebSettings.LOAD_DEFAULT);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }
    
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setLoadsImagesAutomatically(true);
    settings.setBlockNetworkImage(false);
    settings.setBlockNetworkLoads(false);
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(true);
    settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
    settings.setSupportZoom(false);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    settings.setJavaScriptCanOpenWindowsAutomatically(true);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    
    web.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
    
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
        settings.setDatabasePath(getDir("databases", Context.MODE_PRIVATE).getPath());
    }

    Intent intent = getIntent();
    String action = intent.getAction();
    Uri data = intent.getData();
    String url = "https://m.youtube.com/";
    
    if (Intent.ACTION_VIEW.equals(action) && data != null) {
      url = data.toString();
      userNavigated = true;
      Log.d("MainActivity", "📲 External link: " + url);
    } else if (Intent.ACTION_SEND.equals(action)) {
      String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
      if (sharedText != null && (sharedText.contains("youtube.com") || sharedText.contains("youtu.be"))) {
        url = sharedText;
        userNavigated = true;
        Log.d("MainActivity", "📤 Shared: " + url);
      }
    } else {
      Log.d("MainActivity", "🏠 Default: Home page");
    }
    
    lastUrl = url;
    web.loadUrl(url);
    web.addJavascriptInterface(new WebAppInterface(this), "Android");
    web.setWebChromeClient(new CustomWebClient());

    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.setAcceptCookie(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        cookieManager.setAcceptThirdPartyCookies(web, true);
    }
    
    web.setWebViewClient(new WebViewClient() {
      
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String newUrl = request.getUrl().toString();
        
        if (newUrl.contains("/shorts") && !userNavigated) {
          String currentUrl = view.getUrl();
          if (currentUrl != null && !currentUrl.contains("/shorts")) {
            Log.d("WebView", "🛑 Blocked auto-redirect to shorts");
            return true;
          }
        }
        
        userNavigated = false;
        return false;
      }
      
      @Override
      public void onPageStarted(WebView p1, String p2, Bitmap p3) {
        super.onPageStarted(p1, p2, p3);
        scriptsInjected = false;
      }

      @Override
      public void onPageFinished(WebView p1, String url) {
        p1.evaluateJavascript(
            "(function() {" +
            "  document.body.style.margin = '0';" +
            "  document.body.style.padding = '0';" +
            "  document.documentElement.style.margin = '0';" +
            "  document.documentElement.style.padding = '0';" +
            "  document.documentElement.style.overflow = 'auto';" +
            "})();",
            null
        );

        if (url.contains("/feed/notifications")) {
            p1.evaluateJavascript(
                "(function() {" +
                "  var style = document.createElement('style');" +
                "  style.innerHTML = '" +
                "    * { margin: 0; padding: 0; box-sizing: border-box; }" +
                "    html, body { margin: 0 !important; padding: 0 !important; width: 100% !important; overflow-x: hidden !important; }" +
                "    ytm-mobile-topbar-renderer { display: none !important; }" +
                "    ytm-pivot-bar-renderer { display: none !important; }" +
                "    #masthead { display: none !important; }" +
                "    body { padding-top: 0px !important; padding-bottom: 70px !important; background: #0F0F0F !important; }" +
                "    ytm-item-section-renderer { margin-top: 0 !important; }" +
                "  ';" +
                "  document.head.appendChild(style);" +
                "})();",
                null
            );
            
            // ✅ Page load වෙද්දී user info extract කරනවා
if (url != null && (url.contains("m.youtube.com") || url.contains("youtube.com"))) {
    extractAndSaveUserInfo();
}

// ✅ Login page ගිහිල්ලා YouTube main page redirect වෙලා ආවාද
// Google login success → m.youtube.com redirect වෙනවා
// ඒ වෙලාවේ YouActivity open කරලා logged in UI show කරනවා
if (url != null && (url.equals("https://m.youtube.com/") || url.equals("https://m.youtube.com"))
        && getIntent().getBooleanExtra("fromLogin", false)) {
    getIntent().removeExtra("fromLogin");
    extractAndSaveUserInfo();
    // 1 second delay දීලා user info save වෙන්න ඉඩ දෙනවා
    new android.os.Handler().postDelayed(() -> {
        Intent intent = new Intent(MainActivity.this, YouActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }, 1000);
}


            hideLoadingScreen();
            return;
        }

        if (!scriptsInjected) {
            injectYTProScriptsFromAssets();
            scriptsInjected = true;
        }

        web.evaluateJavascript(
            "(function() {" +
            "  function hideYouTubeNavigation() {" +
            "    if (!document.getElementById('ytpro-hide-nav')) {" +
            "      var style = document.createElement('style');" +
            "      style.id = 'ytpro-hide-nav';" +
            "      style.innerHTML = '" +
            "        ytm-mobile-topbar-renderer, #masthead, .mobile-topbar-header," +
            "        ytm-pivot-bar-renderer, ytm-pivot-bar-item-renderer, .pivot-bar-item-tab, .pivot-bar," +
            "        c3-tab-bar-renderer, ytm-app > ytm-pivot-bar-renderer, div[class*=pivot], div[id*=pivot] {" +
            "          display: none !important; visibility: hidden !important; height: 0 !important;" +
            "          min-height: 0 !important; max-height: 0 !important; opacity: 0 !important; overflow: hidden !important;" +
            "        }" +
            "        body { padding-top: 0px !important; padding-bottom: 70px !important; }" +
            "        #page-manager { padding-bottom: 70px !important; }" +
            "      ';" +
            "      document.head.appendChild(style);" +
            "    }" +
            "    var hideSelectors = ['ytm-mobile-topbar-renderer', '#masthead', 'ytm-pivot-bar-renderer'," +
            "                         'ytm-pivot-bar-item-renderer', 'c3-tab-bar-renderer'];" +
            "    hideSelectors.forEach(function(selector) {" +
            "      document.querySelectorAll(selector).forEach(function(el) {" +
            "        el.style.display = 'none'; el.style.visibility = 'hidden';" +
            "        el.style.height = '0px'; el.style.opacity = '0';" +
            "      });" +
            "    });" +
            "  }" +
            "  hideYouTubeNavigation();" +
            "  var observer = new MutationObserver(hideYouTubeNavigation);" +
            "  observer.observe(document.body, { childList: true, subtree: true });" +
            "  setInterval(hideYouTubeNavigation, 500);" +
            "})();",
            null
        );
        
        web.evaluateJavascript(
            "(function() {" +
            "  var originalPushState = history.pushState;" +
            "  history.pushState = function(state, title, url) {" +
            "    if (url && url.includes('/shorts') && !window.location.href.includes('/shorts')) return;" +
            "    return originalPushState.apply(this, arguments);" +
            "  };" +
            "})();",
            null
        );

        if (dL) {
            web.postDelayed(() -> {
                web.evaluateJavascript("if (typeof window.ytproDownVid === 'function') { window.location.hash='download'; }", null);
                dL = false;
            }, 2000);
        }

        if (!url.contains("youtube.com/watch") && !url.contains("youtube.com/shorts") && isPlaying) {
            isPlaying = false;
            mediaSession = false;
            stopService(new Intent(getApplicationContext(), ForegroundService.class));
        }

        new Handler().postDelayed(() -> hideLoadingScreen(), 500);
        super.onPageFinished(p1, url);
      }

      @Override
      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
            errorCode == WebViewClient.ERROR_CONNECT || 
            errorCode == WebViewClient.ERROR_TIMEOUT) {
            runOnUiThread(() -> {
                hideLoadingScreen();
                showOfflineScreen();
            });
        }
        super.onReceivedError(view, errorCode, description, failingUrl);
      }

      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (request.isForMainFrame()) {
                int errorCode = error.getErrorCode();
                if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
                    errorCode == WebViewClient.ERROR_CONNECT || 
                    errorCode == WebViewClient.ERROR_TIMEOUT) {
                    runOnUiThread(() -> {
                        hideLoadingScreen();
                        showOfflineScreen();
                    });
                }
            }
        }
        super.onReceivedError(view, request, error);
      }
    });

    setReceiver();

    if (android.os.Build.VERSION.SDK_INT >= 33) {
      OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
      backCallback = () -> {
          if (web.canGoBack()) web.goBack();
          else finish();
      };
      dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }
  }
  
  private void injectYTProScriptsFromAssets() {
    try {
        web.evaluateJavascript(
            "if (window.trustedTypes && window.trustedTypes.createPolicy && !window.trustedTypes.defaultPolicy) {" +
            "  window.trustedTypes.createPolicy('default', {" +
            "    createHTML: (string) => string," +
            "    createScriptURL: string => string," +
            "    createScript: string => string" +
            "  });" +
            "}",
            null
        );
        
        String scriptLoader = 
            "(function() {" +
            "  if(window.YTPRO_LOADED) return;" +
            "  function loadScriptFromString(content) {" +
            "    var script = document.createElement('script');" +
            "    script.textContent = content;" +
            "    script.async = false;" +
            "    document.body.appendChild(script);" +
            "  }" +
            "  " + loadScriptFromAssets("script.js") + " " +
            "  " + loadScriptFromAssets("bgplay.js") + " " +
            "  " + loadScriptFromAssets("innertube.js") + " " +
            "  " + loadScriptFromAssets("styles.js") + " " +
            "  " + loadScriptFromAssets("welcome.js") + " " +
            "  " + loadScriptFromAssets("subscriptions.js") + " " +
            "  " + loadScriptFromAssets("login.js") + " " +
            "  " + loadScriptFromAssets("darkmode.js") + " " +
            "  window.YTPRO_LOADED = true;" +
            "})();";
        
        web.evaluateJavascript(scriptLoader, null);
        
        web.evaluateJavascript(
            "(function() {" +
            "  setTimeout(function() {" +
            "    var premiumElements = document.querySelectorAll('ytm-purchase-offer-renderer, ytm-upsell-dialog-renderer');" +
            "    premiumElements.forEach(function(el) { el.remove(); });" +
            "    if (window.ytplayer && window.ytplayer.config) {" +
            "      window.ytplayer.config.args.autoplay = 1;" +
            "      window.ytplayer.config.args.background = 1;" +
            "    }" +
            "  }, 1000);" +
            "})();",
            null
        );
        
        web.evaluateJavascript(
            "(function() {" +
            "  function rgbToHex(rgb) {" +
            "    var match = rgb.match(/\\d+/g);" +
            "    if (!match || match.length < 3) return '#0F0F0F';" +
            "    var r = parseInt(match[0]).toString(16).padStart(2, '0');" +
            "    var g = parseInt(match[1]).toString(16).padStart(2, '0');" +
            "    var b = parseInt(match[2]).toString(16).padStart(2, '0');" +
            "    return '#' + r + g + b;" +
            "  }" +
            "  function updateStatusBarColor() {" +
            "    var selectors = ['ytm-mobile-topbar-renderer', '#masthead', 'ytm-pivot-bar-renderer', '.mobile-topbar-header'];" +
            "    for (var i = 0; i < selectors.length; i++) {" +
            "      var header = document.querySelector(selectors[i]);" +
            "      if (header) {" +
            "        var bgColor = window.getComputedStyle(header).backgroundColor;" +
            "        var hexColor = rgbToHex(bgColor);" +
            "        if (window.Android && window.Android.setStatusBarColor) {" +
            "          window.Android.setStatusBarColor(hexColor);" +
            "        }" +
            "        break;" +
            "      }" +
            "    }" +
            "  }" +
            "  var observer = new MutationObserver(updateStatusBarColor);" +
            "  observer.observe(document.body, { attributes: true, childList: true, subtree: true });" +
            "  setTimeout(updateStatusBarColor, 500);" +
            "  setInterval(updateStatusBarColor, 3000);" +
            "})();",
            null
        );
        
    } catch (Exception e) {
        Log.e("Script Injection", "❌ Error: " + e.getMessage());
    }
  }
  
  private String loadScriptFromAssets(String filename) {
    try {
        int resourceId = getResources().getIdentifier(
            filename.replace(".js", ""),
            "raw",
            getPackageName()
        );
        
        if (resourceId == 0) {
            Log.e("Script", "❌ File not found: " + filename);
            return "";
        }
        
        InputStream inputStream = getResources().openRawResource(resourceId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder content = new StringBuilder();
        String line;
        
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        
        String escaped = content.toString()
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("${", "\\${")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
            
        return "loadScriptFromString(`" + escaped + "`);";
        
    } catch (IOException e) {
        Log.e("Script", "❌ Failed to load " + filename + ": " + e.getMessage());
        return "";
    }
  }
  
  private void setupCustomHeader() {
    ImageView iconSearch = findViewById(R.id.iconSearch);
    ImageView iconNotifications = findViewById(R.id.iconNotifications);
    ImageView iconCast = findViewById(R.id.iconCast);
    ImageView iconSettings = findViewById(R.id.iconSettings);

    RelativeLayout searchBarContainer = findViewById(R.id.searchBarContainer);
    ImageView searchBackButton = findViewById(R.id.searchBackButton);
    EditText searchInput = findViewById(R.id.searchInput);
    ImageView voiceSearchButton = findViewById(R.id.voiceSearchButton);
    ListView suggestionsList = findViewById(R.id.suggestionsList);

    ArrayAdapter<String> suggestionsAdapter = new ArrayAdapter<>(this,
        android.R.layout.simple_list_item_1, new ArrayList<>());
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

    Runnable hideSearchBar = () -> closeSearchBar(searchBarContainer, searchInput);

    iconSearch.setOnClickListener(v -> {
        searchBarContainer.setVisibility(View.VISIBLE);
        searchBarContainer.setAlpha(0f);
        searchBarContainer.setTranslationY(-56f);
        searchBarContainer.animate().alpha(1f).translationY(0f).setDuration(300).start();
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    });

    searchBackButton.setOnClickListener(v -> hideSearchBar.run());

    searchInput.addTextChangedListener(new android.text.TextWatcher() {
        private final Handler handler = new Handler();
        private Runnable searchRunnable;

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(android.text.Editable s) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
            String query = s.toString().trim();
            if (query.isEmpty()) {
                if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
                return;
            }
            searchRunnable = () -> fetchSuggestions(query, suggestionsAdapter, suggestionsList);
            handler.postDelayed(searchRunnable, 300);
        }
    });

    searchInput.setOnEditorActionListener((v, actionId, event) -> {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            String query = searchInput.getText().toString();
            if (!query.isEmpty()) {
                userNavigated = true;
                web.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query));
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

    iconNotifications.setOnClickListener(v -> {
        Intent intent = new Intent(MainActivity.this, NotificationActivity.class);
        startActivity(intent);
    });

    iconCast.setOnClickListener(v ->
        Toast.makeText(this, "Cast feature coming soon! 📡", Toast.LENGTH_SHORT).show());

    iconSettings.setOnClickListener(v -> {
        userNavigated = true;
        web.evaluateJavascript("window.location.hash='settings';", null);
    });
}

  private void setupBottomNavigation() {
    LinearLayout navHome = findViewById(R.id.navHome);
    LinearLayout navShorts = findViewById(R.id.navShorts);
    LinearLayout navUpload = findViewById(R.id.navUpload);
    LinearLayout navSubscriptions = findViewById(R.id.navSubscriptions);
    LinearLayout navYou = findViewById(R.id.navYou);
    
    final ImageView iconHome = findViewById(R.id.iconHome);
    final ImageView iconShorts = findViewById(R.id.iconShorts);
    final ImageView iconSubscriptions = findViewById(R.id.iconSubscriptions);
    final ImageView iconYou = findViewById(R.id.iconYou);
    
    final TextView textHome = findViewById(R.id.textHome);
    final TextView textShorts = findViewById(R.id.textShorts);
    final TextView textSubscriptions = findViewById(R.id.textSubscriptions);
    final TextView textYou = findViewById(R.id.textYou);
    
    navHome.setOnClickListener(v -> {
        userNavigated = true;
        setActiveTab(iconHome, textHome, iconShorts, textShorts, iconSubscriptions, textSubscriptions, iconYou, textYou);
        web.loadUrl("https://m.youtube.com/");
    });
    
    navShorts.setOnClickListener(v -> {
        userNavigated = true;
        setActiveTab(iconShorts, textShorts, iconHome, textHome, iconSubscriptions, textSubscriptions, iconYou, textYou);
        web.loadUrl("https://m.youtube.com/shorts");
    });
    
    navUpload.setOnClickListener(v -> {
        Toast.makeText(MainActivity.this, "Upload feature coming soon! 🎥", Toast.LENGTH_SHORT).show();
    });
    
    navSubscriptions.setOnClickListener(v -> {
        userNavigated = true;
        setActiveTab(iconSubscriptions, textSubscriptions, iconHome, textHome, iconShorts, textShorts, iconYou, textYou);
        web.loadUrl("https://m.youtube.com/feed/subscriptions");
    });
    
        navYou.setOnClickListener(v -> {
        setActiveTab(iconYou, textYou, iconHome, textHome,
                     iconShorts, textShorts, iconSubscriptions, textSubscriptions);
        // User info extract කරලා save කරනවා — YouActivity open වෙන්න කලිනුත්
        extractAndSaveUserInfo();
        Intent intent = new Intent(MainActivity.this, YouActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    });
  }

  private void setActiveTab(ImageView activeIcon, TextView activeText, Object... inactiveElements) {
    activeIcon.setColorFilter(Color.parseColor("#FF0000"));
    activeText.setTextColor(Color.WHITE);
    
    for (Object element : inactiveElements) {
        if (element instanceof ImageView) {
            ((ImageView) element).setColorFilter(Color.parseColor("#AAAAAA"));
        } else if (element instanceof TextView) {
            ((TextView) element).setTextColor(Color.parseColor("#AAAAAA"));
        }
    }
  }
  
  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    if (requestCode == 101) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        web.loadUrl("https://m.youtube.com");
      } else {
        Toast.makeText(getApplicationContext(), getString(R.string.grant_mic), Toast.LENGTH_SHORT).show();
      }
    } else if (requestCode == 1) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
        Toast.makeText(getApplicationContext(), getString(R.string.grant_storage), Toast.LENGTH_SHORT).show();
      }
    } else if (requestCode == 102) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          Log.d("MainActivity", "✅ Notification permission granted");
          startNotificationService();
      } else {
          Log.d("MainActivity", "❌ Notification permission denied");
      }
    }
  }

  @Override
public void onBackPressed() {
    RelativeLayout searchBarContainer = findViewById(R.id.searchBarContainer);
    if (searchBarContainer != null && searchBarContainer.getVisibility() == View.VISIBLE) {
        searchBarContainer.animate()
            .alpha(0f)
            .translationY(-56f)
            .setDuration(300)
            .withEndAction(() -> {
                searchBarContainer.setVisibility(View.GONE);
                EditText searchInput = findViewById(R.id.searchInput);
                if (searchInput != null) searchInput.setText("");
            })
            .start();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchBarContainer.getWindowToken(), 0);
        return;
    }
    
    if (web.canGoBack()) {
        web.goBack();
    } else {
        finish();
    }
}

  @Override
  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
    
    web.loadUrl(isInPictureInPictureMode ? "javascript:PIPlayer();" : "javascript:removePIP();", null);
    isPip = isInPictureInPictureMode;
    
    if (!isInPictureInPictureMode) {
        runOnUiThread(() -> {
            View customHeader = findViewById(R.id.customHeader);
            View searchBarContainer = findViewById(R.id.searchBarContainer);
            View bottomNavBar = findViewById(R.id.bottomNavBar);
            
            if (customHeader != null) {
                customHeader.requestLayout();
                customHeader.invalidate();
            }
            
            if (searchBarContainer != null) {
                searchBarContainer.setVisibility(View.GONE);
            }
            
            if (bottomNavBar != null) {
                bottomNavBar.requestLayout();
                bottomNavBar.invalidate();
            }
            
            if (web != null) {
                web.requestLayout();
                web.invalidate();
                web.scrollTo(0, 0);
            }
            
            setupSystemBarsInsets();
        });
        
        new Handler().postDelayed(() -> {
            web.evaluateJavascript(
                "(function() {" +
                "  document.body.style.paddingTop = '0px';" +
                "  document.body.style.marginTop = '0px';" +
                "  var header = document.querySelector('ytm-mobile-topbar-renderer');" +
                "  if (header) header.remove();" +
                "  var pivot = document.querySelector('ytm-pivot-bar-renderer');" +
                "  if (pivot) pivot.remove();" +
                "})();",
                null
            );
        }, 100);
        
        new Handler().postDelayed(() -> {
            web.evaluateJavascript(
                "(function() {" +
                "  document.body.style.paddingTop = '0px';" +
                "  document.body.style.marginTop = '0px';" +
                "})();",
                null
            );
        }, 500);
    }
    
    if (isInPictureInPictureMode && isPlaying) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10*60*1000L);
        }
    } else {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
  }

@Override
protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    // ✅ Home button pressed - do NOTHING
    // User must manually click PIP button to enter PIP mode
    Log.d("MainActivity", "🏠 Home button - PIP disabled");
}

  @Override
  protected void onPause() {
    super.onPause();
    CookieManager.getInstance().flush();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    
    if (wakeLock != null && wakeLock.isHeld()) {
        wakeLock.release();
    }
    
    Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
    stopService(intent);
    if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
    if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
      getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
    }
  }
  
  private void extractAndSaveUserInfo() {
    if (web == null) return;

    web.evaluateJavascript(
        "(function() {" +
        "  try {" +
        "    var name  = '';" +
        "    var email = '';" +

        // Method 1: ytInitialData
        "    if (window.ytInitialData) {" +
        "      try {" +
        "        var hdr = ytInitialData.header;" +
        "        if (hdr && hdr.pageHeaderRenderer) {" +
        "          name = hdr.pageHeaderRenderer.pageTitle || '';" +
        "        }" +
        "      } catch(e1) {}" +
        "    }" +

        // Method 2: account header element
        "    if (!name) {" +
        "      var ah = document.querySelector('ytm-account-header-renderer');" +
        "      if (ah) {" +
        "        var nameNode  = ah.querySelector('.account-name, .name');" +
        "        var emailNode = ah.querySelector('.account-email, .email');" +
        "        if (nameNode)  name  = nameNode.innerText.trim();" +
        "        if (emailNode) email = emailNode.innerText.trim();" +
        "      }" +
        "    }" +

        // Method 3: DOM scan for email pattern
        "    if (!email) {" +
        "      document.querySelectorAll('span, div, p').forEach(function(el) {" +
        "        var t = el.innerText ? el.innerText.trim() : '';" +
        "        if (!email && t.includes('@') && t.includes('.') && t.length < 60) {" +
        "          email = t;" +
        "        }" +
        "      });" +
        "    }" +

        // Method 4: ytInitialGuideData
        "    if (!name) {" +
        "      try {" +
        "        if (window.ytInitialGuideData) {" +
        "          var entries = ytInitialGuideData.items || [];" +
        "          if (entries[0] && entries[0].guideEntryRenderer) {" +
        "            name = entries[0].guideEntryRenderer.formattedTitle.runs[0].text || '';" +
        "          }" +
        "        }" +
        "      } catch(e2) {}" +
        "    }" +

        "    return JSON.stringify({name: name, email: email});" +
        "  } catch(e) {" +
        "    return JSON.stringify({name:'', email:''});" +
        "  }" +
        "})();",
        result -> {
            if (result == null || result.equals("null")) return;
            try {
                // JavaScript string quotes remove
                String json = result;
                if (json.startsWith("\"")) {
                    json = json.substring(1, json.length() - 1)
                               .replace("\\\"", "\"")
                               .replace("\\\\", "\\");
                }

                org.json.JSONObject obj = new org.json.JSONObject(json);
                String name  = obj.optString("name",  "").trim();
                String email = obj.optString("email", "").trim();

                SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();

                if (!name.isEmpty()) {
                    editor.putString("yt_username", name);
                    android.util.Log.d("YouTab", "✅ Name saved: " + name);
                }
                if (!email.isEmpty()) {
                    editor.putString("yt_email", email);
                    android.util.Log.d("YouTab", "✅ Email saved: " + email);
                }

                editor.apply();

            } catch (Exception e) {
                android.util.Log.e("YouTab", "❌ Parse error: " + e.getMessage());
            }
        }
    );
}
  
  public class CustomWebClient extends WebChromeClient {
    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    protected FrameLayout frame;
    private int mOriginalOrientation;
    private int mOriginalSystemUiVisibility;
    
    public CustomWebClient() {}

    public Bitmap getDefaultVideoPoster() {
      if (MainActivity.this == null) return null;
      return BitmapFactory.decodeResource(MainActivity.this.getApplicationContext().getResources(), 2130837573);
    }

    public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {
      this.mOriginalOrientation = portrait ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
      if (isPip) this.mOriginalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        WindowManager.LayoutParams params = MainActivity.this.getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        MainActivity.this.getWindow().setAttributes(params);
      }
      if (this.mCustomView != null) {
        onHideCustomView();
        return;
      }
      this.mCustomView = paramView;
      this.mOriginalSystemUiVisibility = MainActivity.this.getWindow().getDecorView().getSystemUiVisibility();
      MainActivity.this.setRequestedOrientation(this.mOriginalOrientation);
      this.mOriginalOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
      this.mCustomViewCallback = viewCallback;
      ((FrameLayout) MainActivity.this.getWindow().getDecorView()).addView(this.mCustomView, new FrameLayout.LayoutParams(-1, -1));
      MainActivity.this.getWindow().getDecorView().setSystemUiVisibility(3846);
    }

    public void onHideCustomView() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        MainActivity.this.getWindow().setAttributes(params);
      }
      ((FrameLayout) MainActivity.this.getWindow().getDecorView()).removeView(this.mCustomView);
      this.mCustomView = null;
      MainActivity.this.getWindow().getDecorView().setSystemUiVisibility(this.mOriginalSystemUiVisibility);
      MainActivity.this.setRequestedOrientation(this.mOriginalOrientation);
      this.mOriginalOrientation = portrait ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
      this.mCustomViewCallback = null;
      web.clearFocus();
    }

    @Override
    public void onPermissionRequest(final PermissionRequest request) {
      if (Build.VERSION.SDK_INT > 22 && request.getOrigin().toString().contains("youtube.com")) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
          requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 101);
        } else {
          request.grant(request.getResources());
        }
      }
    }
  }
  
  private void downloadFile(String filename, String url, String mtype) {
    if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
      runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.grant_storage, Toast.LENGTH_SHORT).show());
      requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
    }
    try {
      String encodedFileName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
      DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
      DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
      request.setTitle(filename).setDescription(filename).setMimeType(mtype).setAllowedOverMetered(true).setAllowedOverRoaming(true)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedFileName)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE | DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      downloadManager.enqueue(request);
      Toast.makeText(this, getString(R.string.dl_started), Toast.LENGTH_SHORT).show();
    } catch (Exception e) {
      Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
    }
  }

  public class WebAppInterface {
    Context mContext;
    WebAppInterface(Context c) { mContext = c; }

    @JavascriptInterface public void showToast(String txt) { Toast.makeText(getApplicationContext(), txt, Toast.LENGTH_SHORT).show(); }
    @JavascriptInterface public void gohome(String x) { Intent i = new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }
    @JavascriptInterface public void downvid(String name, String url, String m) { downloadFile(name, url, m); }
    @JavascriptInterface public void fullScreen(boolean value) { portrait = value; }
    @JavascriptInterface public void oplink(String url) { Intent i = new Intent(); i.setAction(Intent.ACTION_VIEW); i.setData(Uri.parse(url)); startActivity(i); }
    @JavascriptInterface public String getInfo() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1.0"; } }
    @JavascriptInterface public void setBgPlay(boolean bgplay) { getSharedPreferences("YTPRO", MODE_PRIVATE).edit().putBoolean("bgplay", bgplay).apply(); }
    
    @JavascriptInterface public void bgStart(String iconn, String titlen, String subtitlen, long dura) { icon=iconn; title=titlen; subtitle=subtitlen; duration=dura; isPlaying=true; mediaSession=true; Intent intent = new Intent(getApplicationContext(), ForegroundService.class); intent.putExtra("icon", icon).putExtra("title", title).putExtra("subtitle", subtitle).putExtra("duration", duration).putExtra("currentPosition", 0).putExtra("action", "play"); startService(intent); }
    @JavascriptInterface public void bgUpdate(String iconn, String titlen, String subtitlen, long dura) { icon=iconn; title=titlen; subtitle=subtitlen; duration=dura; isPlaying=true; sendBroadcast(new Intent("UPDATE_NOTIFICATION").putExtra("icon", icon).putExtra("title", title).putExtra("subtitle", subtitle).putExtra("duration", duration).putExtra("currentPosition", 0).putExtra("action", "pause")); }
    @JavascriptInterface public void bgStop() { isPlaying=false; mediaSession=false; stopService(new Intent(getApplicationContext(), ForegroundService.class)); }
    @JavascriptInterface public void bgPause(long ct) { isPlaying=false; sendBroadcast(new Intent("UPDATE_NOTIFICATION").putExtra("icon", icon).putExtra("title", title).putExtra("subtitle", subtitle).putExtra("duration", duration).putExtra("currentPosition", ct).putExtra("action", "pause")); }
    @JavascriptInterface public void bgPlay(long ct) { isPlaying=true; sendBroadcast(new Intent("UPDATE_NOTIFICATION").putExtra("icon", icon).putExtra("title", title).putExtra("subtitle", subtitle).putExtra("duration", duration).putExtra("currentPosition", ct).putExtra("action", "play")); }
    @JavascriptInterface public void bgBuffer(long ct) { isPlaying=true; sendBroadcast(new Intent("UPDATE_NOTIFICATION").putExtra("icon", icon).putExtra("title", title).putExtra("subtitle", subtitle).putExtra("duration", duration).putExtra("currentPosition", ct).putExtra("action", "buffer")); }
    
    @JavascriptInterface public void getSNlM0e(String cookies) { new Thread(() -> { String response = GeminiWrapper.getSNlM0e(cookies); runOnUiThread(() -> web.evaluateJavascript("callbackSNlM0e.resolve(`" + response + "`)", null)); }).start(); }
    @JavascriptInterface public void GeminiClient(String url, String headers, String body) { new Thread(() -> { JSONObject response = GeminiWrapper.getStream(url, headers, body); runOnUiThread(() -> web.evaluateJavascript("callbackGeminiClient.resolve(" + response + ")", null)); }).start(); }
    @JavascriptInterface public String getAllCookies(String url) { return CookieManager.getInstance().getCookie(url); }
    @JavascriptInterface public float getVolume() { return (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); }
    @JavascriptInterface public void setVolume(float volume) { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * volume), 0); }
    @JavascriptInterface public float getBrightness() { try { return (Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f) * 100f; } catch (Exception e) { return 50f; } }
    @JavascriptInterface public void setBrightness(final float value){ runOnUiThread(() -> { WindowManager.LayoutParams layout = getWindow().getAttributes(); layout.screenBrightness = Math.max(0f, Math.min(value, 1f)); getWindow().setAttributes(layout); }); }
    @JavascriptInterface public void pipvid(String x) { if (Build.VERSION.SDK_INT >= 26) { try { enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(x.equals("portrait") ? 9 : 16, x.equals("portrait") ? 16 : 9)).build()); } catch (Exception e) {} } else { Toast.makeText(getApplicationContext(), getString(R.string.no_pip), Toast.LENGTH_SHORT).show(); } }
    
    @JavascriptInterface 
    public void setStatusBarColor(String color) { 
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Window window = getWindow();
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.setStatusBarColor(Color.parseColor(color));
                } catch (Exception e) {
                    Log.e("StatusBar", "❌ Error setting color: " + e.getMessage());
                }
            }
        });
    }
  }
  
  public void setReceiver() {
    broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getExtras().getString("actionname");

        switch (action) {
          case "PLAY_ACTION":
            web.evaluateJavascript("playVideo();",null);
            break;
          case "PAUSE_ACTION":
            web.evaluateJavascript("pauseVideo();",null);
            break;
          case "NEXT_ACTION":
            web.evaluateJavascript("playNext();",null);
            break;
          case "PREV_ACTION":
            web.evaluateJavascript("playPrev();",null);
            break;
          case "SEEKTO":
            web.evaluateJavascript("seekTo('" + intent.getExtras().getString("pos") + "');",null);
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

  private boolean isNetworkAvailable() {
    ConnectivityManager connectivityManager = 
        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    
    if (connectivityManager != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }
    return false;
  }

  private int dpToPx(int dp) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(dp * density);
  }
  
  private android.graphics.drawable.Drawable createCircle(String color) {
    android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
    circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    circle.setColor(Color.parseColor(color));
    return circle;
  }
  
  private void showOfflineScreen() {
    isOffline = true;
    offlineLayout = new RelativeLayout(this);
    offlineLayout.setLayoutParams(new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    ));
    offlineLayout.setBackgroundColor(Color.parseColor("#0F0F0F"));
    
    LinearLayout centerLayout = new LinearLayout(this);
    RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    centerLayout.setLayoutParams(centerParams);
    centerLayout.setOrientation(LinearLayout.VERTICAL);
    centerLayout.setGravity(Gravity.CENTER);
    
    TextView iconView = new TextView(this);
    iconView.setText("📡");
    iconView.setTextSize(80);
    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    iconParams.bottomMargin = dpToPx(24);
    iconView.setLayoutParams(iconParams);
    iconView.setGravity(Gravity.CENTER);
    centerLayout.addView(iconView);
    
    TextView titleView = new TextView(this);
    titleView.setText("No internet connection");
    titleView.setTextSize(20);
    titleView.setTextColor(Color.WHITE);
    titleView.setTypeface(null, Typeface.BOLD);
    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    titleParams.bottomMargin = dpToPx(8);
    titleView.setLayoutParams(titleParams);
    centerLayout.addView(titleView);
    
    TextView messageView = new TextView(this);
    messageView.setText("Check your Wi-Fi or mobile data\nconnection.");
    messageView.setTextSize(14);
    messageView.setTextColor(Color.parseColor("#AAAAAA"));
    messageView.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
        dpToPx(280),
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    msgParams.bottomMargin = dpToPx(32);
    messageView.setLayoutParams(msgParams);
    centerLayout.addView(messageView);
    
    Button retryButton = new Button(this);
    retryButton.setText("Try again");
    retryButton.setTextColor(Color.WHITE);
    retryButton.setTextSize(16);
    retryButton.setTypeface(null, Typeface.BOLD);
    retryButton.setAllCaps(false);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        retryButton.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(Color.parseColor("#FF0000"))
        );
    } else {
        retryButton.setBackgroundColor(Color.parseColor("#FF0000"));
    }
    
    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dpToPx(200), dpToPx(50));
    retryButton.setLayoutParams(btnParams);
    retryButton.setOnClickListener(v -> {
        if (isNetworkAvailable()) {
            hideOfflineScreen();
            load(false);
            setupBottomNavigation();
        } else {
            Toast.makeText(MainActivity.this, "Still no connection", Toast.LENGTH_SHORT).show();
        }
    });
    
    centerLayout.addView(retryButton);
    offlineLayout.addView(centerLayout);
    addContentView(offlineLayout, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    ));
  }

  private void hideOfflineScreen() {
    if (offlineLayout != null && offlineLayout.getParent() != null) {
        ((ViewGroup) offlineLayout.getParent()).removeView(offlineLayout);
        isOffline = false;
    }
  }

  private void checkForAppUpdate() {
    new android.os.Handler().postDelayed(() -> {
        if (isNetworkAvailable()) {
            UpdateChecker updateChecker = new UpdateChecker(MainActivity.this);
            updateChecker.checkForUpdate();
        }
    }, 2000);
  }

  private void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                102
            );
        }
    }
  }

  private void startNotificationService() {
    Intent serviceIntent = new Intent(this, NotificationCheckService.class);
    startService(serviceIntent);
  }

  private void checkNotificationsNow() {
    NotificationFetcher fetcher = new NotificationFetcher(this);
    AppNotificationManager notificationManager = new AppNotificationManager(this);
    
    fetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
        @Override
        public void onSuccess(List<NotificationModel> notifications) {
            notificationManager.showNotifications(notifications);
        }

        @Override
        public void onError(String error) {
            Log.e("MainActivity", "❌ Notification fetch error: " + error);
        }
    });
  }
  
  private void fetchAndUpdateNotifications() {
    notificationFetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
        @Override
        public void onSuccess(List<NotificationModel> notifications) {
            runOnUiThread(() -> {
                notificationPrefs.saveNotifications(notifications);
                updateNotificationBadge();
            });
        }
        
        @Override
        public void onError(String error) {
            runOnUiThread(() -> {
                updateNotificationBadge();
            });
        }
    });
  }

  private void updateNotificationBadge() {
    int unviewedCount = notificationPrefs.getUnviewedCount();
    
    if (unviewedCount > 0) {
        notificationBadge.setVisibility(View.VISIBLE);
        notificationBadge.setText(unviewedCount > 99 ? "99+" : String.valueOf(unviewedCount));
    } else {
        notificationBadge.setVisibility(View.GONE);
    }
  }



@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);

    String url = intent.getStringExtra("url");
    boolean fromLogin = intent.getBooleanExtra("fromLogin", false);

    if (url != null && !url.isEmpty() && web != null) {
        web.loadUrl(url);
    }

    // Login redirect ආවාද — YouActivity back open කරනවා
    if (fromLogin && web != null) {
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String finishedUrl) {
                super.onPageFinished(view, finishedUrl);

                // Google login success → YouTube redirect
                if (finishedUrl != null &&
                    (finishedUrl.contains("m.youtube.com") &&
                     !finishedUrl.contains("accounts.google.com"))) {

                    extractAndSaveUserInfo();

                    new android.os.Handler().postDelayed(() -> {
                        Intent youIntent = new Intent(MainActivity.this, YouActivity.class);
                        startActivity(youIntent);
                        overridePendingTransition(
                            android.R.anim.slide_in_left,
                            android.R.anim.fade_out
                        );
                    }, 1200); // 1.2s delay — user info save වෙන්න ඉඩ දෙනවා
                }
            }
        });
    }
}




@Override
protected void onResume() {
    super.onResume();

    if (notificationPrefs != null) {
        updateNotificationBadge();
    }

    ImageView iconHome          = findViewById(R.id.iconHome);
    ImageView iconShorts        = findViewById(R.id.iconShorts);
    ImageView iconSubscriptions = findViewById(R.id.iconSubscriptions);
    ImageView iconYou           = findViewById(R.id.iconYou);
    TextView  textHome          = findViewById(R.id.textHome);
    TextView  textShorts        = findViewById(R.id.textShorts);
    TextView  textSubscriptions = findViewById(R.id.textSubscriptions);
    TextView  textYou           = findViewById(R.id.textYou);

    if (iconHome == null || web == null) return;

    // ✅ YouActivity ගිහිල්ලා ආවාම — login redirect හරහා ආවාද බලනවා
    boolean fromLogin = getIntent().getBooleanExtra("fromLogin", false);
    if (fromLogin) {
        // Login page load කරනවා
        getIntent().removeExtra("fromLogin");
        String url = getIntent().getStringExtra("url");
        if (url != null && !url.isEmpty()) {
            web.loadUrl(url);
        }
    }

    // Current URL බලලා active tab set කරනවා
    String currentUrl = web.getUrl();
    if (currentUrl == null) currentUrl = "";

    if (currentUrl.contains("/shorts")) {
        setActiveTab(iconShorts, textShorts, iconHome, textHome,
                     iconSubscriptions, textSubscriptions, iconYou, textYou);
    } else if (currentUrl.contains("/feed/subscriptions")) {
        setActiveTab(iconSubscriptions, textSubscriptions, iconHome, textHome,
                     iconShorts, textShorts, iconYou, textYou);
    } else {
        setActiveTab(iconHome, textHome, iconShorts, textShorts,
                     iconSubscriptions, textSubscriptions, iconYou, textYou);
    }
}
  
  private void closeSearchBar(RelativeLayout searchBarContainer, EditText searchInput) {
    searchBarContainer.animate()
        .alpha(0f)
        .translationY(-56f)
        .setDuration(300)
        .withEndAction(() -> {
            searchBarContainer.setVisibility(View.GONE);
            if (searchInput != null) searchInput.setText("");
            ListView suggestionsList = findViewById(R.id.suggestionsList);
            if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
        })
        .start();
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(searchBarContainer.getWindowToken(), 0);
}

private void fetchSuggestions(String query, ArrayAdapter<String> adapter, ListView list) {
    new Thread(() -> {
        try {
            String url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q="
                + Uri.encode(query);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String response = sb.toString();
            List<String> suggestions = new ArrayList<>();

            int start = response.indexOf(",[");
            if (start != -1) {
                String inner = response.substring(start + 2);
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[\"([^\"]+)\"");
                java.util.regex.Matcher m = p.matcher(inner);
                while (m.find() && suggestions.size() < 8) {
                    suggestions.add(m.group(1));
                }
            }

            runOnUiThread(() -> {
                adapter.clear();
                adapter.addAll(suggestions);
                adapter.notifyDataSetChanged();
                if (list != null) {
                    list.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        } catch (Exception e) {
            Log.e("Suggestions", "Error: " + e.getMessage());
        }
    }).start();
}

private void startVoiceSearch(EditText searchInput, ArrayAdapter<String> adapter, ListView list) {
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        return;
    }

    android.speech.SpeechRecognizer recognizer =
        android.speech.SpeechRecognizer.createSpeechRecognizer(this);
    Intent speechIntent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
    speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search YouTube...");

    recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
        @Override
        public void onResults(Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(
                android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String query = matches.get(0);
                runOnUiThread(() -> {
                    searchInput.setText(query);
                    searchInput.setSelection(query.length());
                    fetchSuggestions(query, adapter, list);
                });
            }
            recognizer.destroy();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> partial = partialResults.getStringArrayList(
                android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            if (partial != null && !partial.isEmpty()) {
                String text = partial.get(0);
                runOnUiThread(() -> {
                    searchInput.setText(text);
                    searchInput.setSelection(text.length());
                });
            }
        }

        @Override
        public void onError(int error) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                "Voice search failed, try again 🎤", Toast.LENGTH_SHORT).show());
            recognizer.destroy();
        }

        @Override public void onReadyForSpeech(Bundle params) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                "Listening... 🎤", Toast.LENGTH_SHORT).show());
        }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}
        @Override public void onEvent(int eventType, Bundle params) {}
    });

    recognizer.startListening(speechIntent);
}

  private void setupSystemBarsInsets() {
    View rootView = findViewById(android.R.id.content);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        rootView.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets systemBars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
            );
            
            Log.d("Insets", "Top: " + systemBars.top + ", Bottom: " + systemBars.bottom);
            applyInsetsToViews(systemBars.top, systemBars.bottom, systemBars.left, systemBars.right);
            return WindowInsets.CONSUMED;
        });
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
        rootView.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            int left = insets.getSystemWindowInsetLeft();
            int right = insets.getSystemWindowInsetRight();
            
            Log.d("Insets", "Top: " + top + ", Bottom: " + bottom);
            applyInsetsToViews(top, bottom, left, right);
            return insets.consumeSystemWindowInsets();
        });
    } else {
        rootView.post(() -> {
            int statusBarHeight = getStatusBarHeight();
            int navBarHeight = getNavigationBarHeight();
            applyInsetsToViews(statusBarHeight, navBarHeight, 0, 0);
        });
    }
    
    rootView.requestApplyInsets();
  }

  private void applyInsetsToViews(int topInset, int bottomInset, int leftInset, int rightInset) {
    runOnUiThread(() -> {
        View customHeader = findViewById(R.id.customHeader);
        if (customHeader != null) {
            RelativeLayout.LayoutParams headerParams = 
                (RelativeLayout.LayoutParams) customHeader.getLayoutParams();
            headerParams.topMargin = topInset;
            customHeader.setLayoutParams(headerParams);
            customHeader.requestLayout();
            Log.d("Insets", "✅ Header top margin: " + topInset);
        }
        
        View searchBarContainer = findViewById(R.id.searchBarContainer);
        if (searchBarContainer != null) {
            RelativeLayout.LayoutParams searchParams = 
                (RelativeLayout.LayoutParams) searchBarContainer.getLayoutParams();
            searchParams.topMargin = topInset;
            searchBarContainer.setLayoutParams(searchParams);
        }
        
        View bottomNav = findViewById(R.id.bottomNavBar);
        if (bottomNav != null) {
            RelativeLayout.LayoutParams navParams = 
                (RelativeLayout.LayoutParams) bottomNav.getLayoutParams();
            navParams.bottomMargin = bottomInset;
            bottomNav.setLayoutParams(navParams);
            bottomNav.requestLayout();
            Log.d("Insets", "✅ Bottom nav margin: " + bottomInset);
        }
        
        View webView = findViewById(R.id.web);
        if (webView != null) {
            RelativeLayout.LayoutParams webParams = 
                (RelativeLayout.LayoutParams) webView.getLayoutParams();
            webView.setLayoutParams(webParams);
            webView.requestLayout();
        }
    });
  }

  private int getStatusBarHeight() {
    int result = 0;
    int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
        result = getResources().getDimensionPixelSize(resourceId);
    }
    if (result == 0) {
        result = (int) Math.ceil(25 * getResources().getDisplayMetrics().density);
    }
    return result;
  }

  private int getNavigationBarHeight() {
    if (!hasNavigationBar()) return 0;
    
    int result = 0;
    int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
    if (resourceId > 0) {
        result = getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }

  private boolean hasNavigationBar() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        Display display = getWindowManager().getDefaultDisplay();
        android.util.DisplayMetrics realMetrics = new android.util.DisplayMetrics();
        display.getRealMetrics(realMetrics);
        
        int realHeight = realMetrics.heightPixels;
        int realWidth = realMetrics.widthPixels;
        
        android.util.DisplayMetrics displayMetrics = new android.util.DisplayMetrics();
        display.getMetrics(displayMetrics);
        
        int displayHeight = displayMetrics.heightPixels;
        int displayWidth = displayMetrics.widthPixels;
        
        return (realWidth - displayWidth) > 0 || (realHeight - displayHeight) > 0;
    } else {
        boolean hasMenuKey = ViewConfiguration.get(this).hasPermanentMenuKey();
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
        return !hasMenuKey && !hasBackKey;
    }
  }
}
