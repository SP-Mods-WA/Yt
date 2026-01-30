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
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
import java.util.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {

  private boolean portrait = false;
  private BroadcastReceiver broadcastReceiver;
  private AudioManager audioManager;

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
  
  // Loading Animation Views
  private RelativeLayout loadingScreen;
  private View outerCircle;
  private View innerCircle;
  private View dot1, dot2, dot3;
  private TextView progressText;
  private ObjectAnimator outerRotation;
  private ObjectAnimator innerRotation;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);

    if (!prefs.contains("bgplay")) {
      prefs.edit().putBoolean("bgplay", true).apply();
    }
    
    requestNotificationPermission();
    
    // Initialize loading screen
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
        setupBottomNavigation();
    }
    
    MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }
  
  private void initLoadingScreen() {
    loadingScreen = new RelativeLayout(this);
    loadingScreen.setLayoutParams(new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.MATCH_PARENT,
        RelativeLayout.LayoutParams.MATCH_PARENT
    ));
    loadingScreen.setBackgroundColor(Color.parseColor("#0F0F0F"));
    loadingScreen.setVisibility(View.GONE);
    
    // Center container
    LinearLayout centerLayout = new LinearLayout(this);
    RelativeLayout.LayoutParams centerParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    centerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    centerLayout.setLayoutParams(centerParams);
    centerLayout.setOrientation(LinearLayout.VERTICAL);
    centerLayout.setGravity(Gravity.CENTER);
    
    // Animation container
    RelativeLayout animContainer = new RelativeLayout(this);
    LinearLayout.LayoutParams animParams = new LinearLayout.LayoutParams(dpToPx(120), dpToPx(120));
    animParams.bottomMargin = dpToPx(32);
    animContainer.setLayoutParams(animParams);
    
    // Outer circle
    outerCircle = new View(this);
    RelativeLayout.LayoutParams outerParams = new RelativeLayout.LayoutParams(dpToPx(100), dpToPx(100));
    outerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    outerCircle.setLayoutParams(outerParams);
    outerCircle.setBackground(createGradientRing(dpToPx(100), 3, 8, true));
    animContainer.addView(outerCircle);
    
    // Inner circle
    innerCircle = new View(this);
    RelativeLayout.LayoutParams innerParams = new RelativeLayout.LayoutParams(dpToPx(70), dpToPx(70));
    innerParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    innerCircle.setLayoutParams(innerParams);
    innerCircle.setBackground(createGradientRing(dpToPx(70), 3.5f, 12, false));
    animContainer.addView(innerCircle);
    
    // Center play icon
    TextView playIcon = new TextView(this);
    playIcon.setText("▶");
    playIcon.setTextColor(Color.parseColor("#FF0000"));
    playIcon.setTextSize(36);
    playIcon.setTypeface(null, Typeface.BOLD);
    RelativeLayout.LayoutParams iconParams = new RelativeLayout.LayoutParams(
        RelativeLayout.LayoutParams.WRAP_CONTENT,
        RelativeLayout.LayoutParams.WRAP_CONTENT
    );
    iconParams.addRule(RelativeLayout.CENTER_IN_PARENT);
    playIcon.setLayoutParams(iconParams);
    animContainer.addView(playIcon);
    
    centerLayout.addView(animContainer);
    
    // Loading text
    TextView loadingText = new TextView(this);
    loadingText.setText("Loading YTPro");
    loadingText.setTextColor(Color.WHITE);
    loadingText.setTextSize(18);
    loadingText.setTypeface(null, Typeface.BOLD);
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    textParams.bottomMargin = dpToPx(8);
    loadingText.setLayoutParams(textParams);
    centerLayout.addView(loadingText);
    
    // Dots container
    LinearLayout dotsLayout = new LinearLayout(this);
    dotsLayout.setOrientation(LinearLayout.HORIZONTAL);
    
    dot1 = createDot();
    dot2 = createDot();
    dot3 = createDot();
    
    dotsLayout.addView(dot1);
    dotsLayout.addView(dot2);
    dotsLayout.addView(dot3);
    centerLayout.addView(dotsLayout);
    
    // Progress text
    progressText = new TextView(this);
    progressText.setText("Initializing...");
    progressText.setTextColor(Color.parseColor("#AAAAAA"));
    progressText.setTextSize(12);
    LinearLayout.LayoutParams progParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    progParams.topMargin = dpToPx(24);
    progressText.setLayoutParams(progParams);
    centerLayout.addView(progressText);
    
    loadingScreen.addView(centerLayout);
    
    // Add to content view
    addContentView(loadingScreen, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    ));
  }
  
  private android.graphics.drawable.Drawable createGradientRing(int size, float innerRadiusRatio, float thicknessRatio, boolean isOuter) {
    android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable();
    gradient.setShape(android.graphics.drawable.GradientDrawable.RING);
    
    float innerRadius = size / (2 * innerRadiusRatio);
    float thickness = size / (2 * thicknessRatio);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        gradient.setInnerRadius((int)innerRadius);
        gradient.setThickness((int)thickness);
    }
    
    if (isOuter) {
        gradient.setColors(new int[]{
            Color.parseColor("#FF0000"),
            Color.parseColor("#FF6666"),
            Color.parseColor("#00FFFFFF")
        });
    } else {
        gradient.setColors(new int[]{
            Color.parseColor("#00FFFFFF"),
            Color.parseColor("#FF3333"),
            Color.parseColor("#FF0000")
        });
    }
    
    gradient.setGradientType(android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT);
    
    return gradient;
  }
  
  private View createDot() {
    View dot = new View(this);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dpToPx(8), dpToPx(8));
    params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
    dot.setLayoutParams(params);
    
    android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
    shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    shape.setColor(Color.parseColor("#FF0000"));
    dot.setBackground(shape);
    
    return dot;
  }
  
  private void showLoadingScreen() {
    runOnUiThread(() -> {
        loadingScreen.setVisibility(View.VISIBLE);
        loadingScreen.bringToFront();
        
        // Outer circle rotation (clockwise)
        outerRotation = ObjectAnimator.ofFloat(outerCircle, "rotation", 0f, 360f);
        outerRotation.setDuration(2000);
        outerRotation.setRepeatCount(ValueAnimator.INFINITE);
        outerRotation.setInterpolator(new LinearInterpolator());
        outerRotation.start();
        
        // Inner circle rotation (counter-clockwise)
        innerRotation = ObjectAnimator.ofFloat(innerCircle, "rotation", 360f, 0f);
        innerRotation.setDuration(1500);
        innerRotation.setRepeatCount(ValueAnimator.INFINITE);
        innerRotation.setInterpolator(new LinearInterpolator());
        innerRotation.start();
        
        // Dots animation
        startDotsAnimation();
    });
  }
  
  private void startDotsAnimation() {
    long delay = 200;
    
    AnimatorSet dot1Set = createDotAnimator(dot1, 0);
    AnimatorSet dot2Set = createDotAnimator(dot2, delay);
    AnimatorSet dot3Set = createDotAnimator(dot3, delay * 2);
    
    dot1Set.start();
    dot2Set.start();
    dot3Set.start();
  }
  
  private AnimatorSet createDotAnimator(View dot, long startDelay) {
    ObjectAnimator scaleX = ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.5f, 1f);
    ObjectAnimator scaleY = ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.5f, 1f);
    ObjectAnimator alpha = ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.3f, 1f);
    
    // එක එක animator වලට repeat count set කරන්න
    scaleX.setRepeatCount(ValueAnimator.INFINITE);
    scaleY.setRepeatCount(ValueAnimator.INFINITE);
    alpha.setRepeatCount(ValueAnimator.INFINITE);
    
    AnimatorSet set = new AnimatorSet();
    set.playTogether(scaleX, scaleY, alpha);
    set.setDuration(600);
    set.setStartDelay(startDelay);
    set.setInterpolator(new AccelerateDecelerateInterpolator());
    
    return set;
}
  
  public void updateLoadingProgress(String message) {
    runOnUiThread(() -> {
        if (progressText != null) {
            progressText.setText(message);
        }
    });
  }
  
  private void hideLoadingScreen() {
    runOnUiThread(() -> {
        if (outerRotation != null) outerRotation.cancel();
        if (innerRotation != null) innerRotation.cancel();
        
        // Fade out animation
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
   
    updateLoadingProgress("Setting up WebView...");
    
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

    updateLoadingProgress("Loading YouTube...");

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
        updateLoadingProgress("Loading page...");
      }

      @Override
      public void onPageFinished(WebView p1, String url) {
        updateLoadingProgress("Injecting YTPro scripts...");
        
        // ✅ Inject scripts from assets
        if (!scriptsInjected) {
            injectYTProScriptsFromAssets();
            scriptsInjected = true;
        }
        
        updateLoadingProgress("Applying customizations...");
        
        // ✅ Hide YouTube bottom nav immediately
        web.evaluateJavascript(
            "(function() {" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = 'ytm-pivot-bar-renderer { display: none !important; } body { padding-bottom: 65px !important; }';" +
            "  document.head.appendChild(style);" +
            "})();",
            null
        );
        
        // ✅ Block shorts auto-redirect
        web.evaluateJavascript(
            "(function() {" +
            "  var originalPushState = history.pushState;" +
            "  history.pushState = function(state, title, url) {" +
            "    if (url && url.includes('/shorts') && !window.location.href.includes('/shorts')) {" +
            "      return;" +
            "    }" +
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

        // Hide loading after everything is done
        new Handler().postDelayed(() -> {
            updateLoadingProgress("Ready!");
            new Handler().postDelayed(() -> hideLoadingScreen(), 300);
        }, 500);

        super.onPageFinished(p1, url);
      }

      @Override
      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
          if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || errorCode == WebViewClient.ERROR_CONNECT || errorCode == WebViewClient.ERROR_TIMEOUT) {
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
                  if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || errorCode == WebViewClient.ERROR_CONNECT || errorCode == WebViewClient.ERROR_TIMEOUT) {
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
  
  // ✅ Scripts inject කරන්න assets folder එකෙන්
  private void injectYTProScriptsFromAssets() {
    try {
        // 1. Trusted Types policy
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
        
        // 2. Load scripts from assets
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
            "  window.YTPRO_LOADED = true;" +
            "  console.log('✅ YTPRO scripts loaded from assets');" +
            "})();";
        
        web.evaluateJavascript(scriptLoader, null);
        
        // 3. Additional YouTube modifications
        web.evaluateJavascript(
            "(function() {" +
            "  setTimeout(function() {" +
            "    // Remove YouTube premium offers" +
            "    var premiumElements = document.querySelectorAll('ytm-purchase-offer-renderer, ytm-upsell-dialog-renderer');" +
            "    premiumElements.forEach(function(el) { el.remove(); });" +
            "    " +
            "    // Enable background play" +
            "    if (window.ytplayer && window.ytplayer.config) {" +
            "      window.ytplayer.config.args.autoplay = 1;" +
            "      window.ytplayer.config.args.background = 1;" +
            "    }" +
            "  }, 1000);" +
            "})();",
            null
        );
        
        // 4. ✅ Status bar color sync with YouTube header
        web.evaluateJavascript(
            "(function() {" +
            "  console.log('🎨 Status bar sync initialized');" +
            "  function rgbToHex(rgb) {" +
            "    var match = rgb.match(/\\d+/g);" +
            "    if (!match || match.length < 3) return '#000000';" +
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
            "          console.log('🎨 Status bar color:', hexColor);" +
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
        InputStream inputStream = getAssets().open(filename);
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
        Log.e("Assets", "❌ Failed to load " + filename + ": " + e.getMessage());
        return "";
    }
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
        userNavigated = true;
        setActiveTab(iconYou, textYou, iconHome, textHome, iconShorts, textShorts, iconSubscriptions, textSubscriptions);
        web.loadUrl("https://m.youtube.com/feed/account");
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
    if (web.canGoBack()) {
      web.goBack();
    } else {
      finish();
    }
  }

  @Override
  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
    web.loadUrl(isInPictureInPictureMode ? "javascript:PIPlayer();" : "javascript:removePIP();",null);
    isPip = isInPictureInPictureMode;
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    if (android.os.Build.VERSION.SDK_INT >= 26 && web.getUrl().contains("watch") && isPlaying) {
        try {
          PictureInPictureParams params;
          isPip=true;
          if (portrait) {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
          } else{
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
          }
          enterPictureInPictureMode(params);
        } catch (IllegalStateException e) {
          e.printStackTrace();
        }
    }
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
    
    // ✅ Status Bar Color Change Method
    @JavascriptInterface 
    public void setStatusBarColor(String color) { 
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Window window = getWindow();
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.setStatusBarColor(Color.parseColor(color));
                    Log.d("StatusBar", "🎨 Color changed to: " + color);
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
        Log.e("Action MainActivity", action);

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
  
  @Override
  protected void onPause() {
    super.onPause();
    CookieManager.getInstance().flush();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
    stopService(intent);
    if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
    if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
      getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
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

  private int dpToPx(int dp) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(dp * density);
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
    Log.d("MainActivity", "📢 Notification service started");
  }

  private void checkNotificationsNow() {
    NotificationFetcher fetcher = new NotificationFetcher(this);
    AppNotificationManager notificationManager = new AppNotificationManager(this);
    
    fetcher.fetchNotifications(new NotificationFetcher.NotificationCallback() {
        @Override
        public void onSuccess(List<NotificationModel> notifications) {
            Log.d("MainActivity", "✅ Fetched " + notifications.size() + " notifications");
            notificationManager.showNotifications(notifications);
        }

        @Override
        public void onError(String error) {
            Log.e("MainActivity", "❌ Notification fetch error: " + error);
        }
    });
  }
}
