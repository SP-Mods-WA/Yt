package com.spmods.ytpro;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.view.*;
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
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.SeekBar;

public class MainActivity extends Activity {

  private boolean portrait = false;
  private BroadcastReceiver broadcastReceiver;
  private AudioManager audioManager;
  private Dialog soundControlDialog;

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
  
  // ✅ NEW: Visualizer animation handler
  private Handler visualizerHandler = new Handler();
  private boolean isVisualizerRunning = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);

    if (!prefs.contains("bgplay")) {
      prefs.edit().putBoolean("bgplay", true).apply();
    }
    
    requestNotificationPermission();
    
    if (!isNetworkAvailable()) {
        showOfflineScreen();
    } else {
        load(false);
        checkForAppUpdate();
        startNotificationService();
        checkNotificationsNow();
        setupBottomNavigation();
    }
    
    MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  public void load(boolean dl) {
    web = findViewById(R.id.web);
    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
   
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setSupportZoom(true);
    settings.setBuiltInZoomControls(true);
    settings.setDisplayZoomControls(false);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);
    settings.setMediaPlaybackRequiresUserGesture(false);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    settings.setCacheMode(WebSettings.LOAD_DEFAULT);
    settings.setJavaScriptCanOpenWindowsAutomatically(true);
    settings.setLoadsImagesAutomatically(true);
    settings.setBlockNetworkImage(false);
    settings.setBlockNetworkLoads(false);
    settings.setUseWideViewPort(true);
    settings.setLoadWithOverviewMode(true);
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    
    web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

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
      public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        Log.d("WebView", "🌐 Requesting: " + url);

        if (url.contains("youtube.com/ytpro_cdn/")) {
            String modifiedUrl = null;

            if (url.contains("youtube.com/ytpro_cdn/esm")) {
                modifiedUrl = url.replace("youtube.com/ytpro_cdn/esm", "esm.sh");
                Log.d("CDN", "✅ ESM Redirect: " + modifiedUrl);
            } else if (url.contains("youtube.com/ytpro_cdn/npm/ytpro")) {
                if (url.contains("innertube.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/innertube.js";
                } else if (url.contains("bgplay.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/bgplay.js";
                } else if (url.contains("script.js")) {
                    modifiedUrl = "https://cdn.jsdelivr.net/gh/SP-Mods-WA/Yt@main/scripts/script.js";
                }
                Log.d("CDN", "✅ GitHub Redirect: " + modifiedUrl);
            }
            
            if (modifiedUrl == null) {
                Log.e("CDN", "❌ modifiedUrl is NULL for: " + url);
                return super.shouldInterceptRequest(view, request);
            }
            
            try {
                URL newUrl = new URL(modifiedUrl);
                HttpsURLConnection connection = (HttpsURLConnection) newUrl.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                Log.d("CDN", "📡 Response: " + responseCode + " for " + modifiedUrl);
                
                if (responseCode != 200) {
                    Log.e("CDN", "❌ Failed: " + responseCode);
                    return super.shouldInterceptRequest(view, request);
                }

                String contentType = connection.getContentType();
                if (contentType == null || contentType.isEmpty()) {
                    if (modifiedUrl.endsWith(".js")) {
                        contentType = "application/javascript";
                    } else {
                        contentType = "text/plain";
                    }
                }
                
                String encoding = connection.getContentEncoding();
                if (encoding == null) encoding = "utf-8";

                Map<String, String> headers = new HashMap<>();
                headers.put("Access-Control-Allow-Origin", "*");
                headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                headers.put("Access-Control-Allow-Headers", "*");
                headers.put("Content-Type", contentType + "; charset=utf-8");
                headers.put("Cross-Origin-Resource-Policy", "cross-origin");

                return new WebResourceResponse(
                    "application/javascript",
                    "utf-8",
                    connection.getResponseCode(),
                    "OK",
                    headers,
                    connection.getInputStream()
                );

            } catch (Exception e) {
                Log.e("CDN Error", "❌ Exception for " + modifiedUrl + ": " + e.getMessage());
                e.printStackTrace();
                return super.shouldInterceptRequest(view, request);
            }
        }
        return super.shouldInterceptRequest(view, request);
      }
      
      @Override
      public void onPageStarted(WebView p1, String p2, Bitmap p3) {
        super.onPageStarted(p1, p2, p3);
      }

      @Override
      public void onPageFinished(WebView p1, String url) {
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
        
        web.evaluateJavascript(
            "(function() {" +
            "  var originalPushState = history.pushState;" +
            "  history.pushState = function(state, title, url) {" +
            "    if (url && url.includes('/shorts') && !window.location.href.includes('/shorts')) {" +
            "      console.log('🛑 Blocked JS redirect to shorts');" +
            "      return;" +
            "    }" +
            "    return originalPushState.apply(this, arguments);" +
            "  };" +
            "  var originalReplaceState = history.replaceState;" +
            "  history.replaceState = function(state, title, url) {" +
            "    if (url && url.includes('/shorts') && !window.location.href.includes('/shorts')) {" +
            "      console.log('🛑 Blocked JS replace to shorts');" +
            "      return;" +
            "    }" +
            "    return originalReplaceState.apply(this, arguments);" +
            "  };" +
            "  window.addEventListener('popstate', function(e) {" +
            "    if (window.location.href.includes('/shorts')) {" +
            "      console.log('🛑 Blocked popstate to shorts');" +
            "      history.back();" +
            "    }" +
            "  });" +
            "})();",
            null
        );
        
        web.evaluateJavascript(
            "(function() {" +
            "  var style = document.createElement('style');" +
            "  style.innerHTML = 'ytm-pivot-bar-renderer { display: none !important; } body { padding-bottom: 65px !important; }';" +
            "  document.head.appendChild(style);" +
            "})();",
            null
        );
        
        String scriptLoader = 
            "(function() {" +
            "  if(window.YTPRO_LOADED) return;" +
            "  function loadScript(src, name) {" +
            "    return new Promise((resolve, reject) => {" +
            "      var script = document.createElement('script');" +
            "      script.src = src;" +
            "      script.async = false;" +
            "      script.onload = () => resolve();" +
            "      script.onerror = (e) => reject(e);" +
            "      document.body.appendChild(script);" +
            "    });" +
            "  }" +
            "  loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/script.js', 'Main')" +
            "    .then(() => loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/bgplay.js', 'BG'))" +
            "    .then(() => loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js', 'IT'))" +
            "    .then(() => { window.YTPRO_LOADED = true; })" +
            "    .catch((e) => console.error('❌ Load failed:', e));" +
            "})();";
        
        web.evaluateJavascript(scriptLoader, null);

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

        super.onPageFinished(p1, url);
      }

      @Override
      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
          if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || errorCode == WebViewClient.ERROR_CONNECT || errorCode == WebViewClient.ERROR_TIMEOUT) {
              runOnUiThread(() -> showOfflineScreen());
          }
          super.onReceivedError(view, errorCode, description, failingUrl);
      }
      
      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              if (request.isForMainFrame()) {
                  int errorCode = error.getErrorCode();
                  if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || errorCode == WebViewClient.ERROR_CONNECT || errorCode == WebViewClient.ERROR_TIMEOUT) {
                      runOnUiThread(() -> showOfflineScreen());
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
        showSoundControlDialog();
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
  }

  // ✅ SOUND CONTROL DIALOG METHODS
  
  private void showSoundControlDialog() {
    soundControlDialog = new Dialog(this);
    soundControlDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    soundControlDialog.setContentView(R.layout.dialog_sound_control);
    soundControlDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    soundControlDialog.getWindow().setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    );
    
    setupSoundControlDialog();
    soundControlDialog.show();
  }

  private void setupSoundControlDialog() {
    ImageView btnClose = soundControlDialog.findViewById(R.id.btnClose);
    Switch toggleSwitch = soundControlDialog.findViewById(R.id.toggleSwitch);
    View statusDot = soundControlDialog.findViewById(R.id.statusDot);
    
    btnClose.setOnClickListener(v -> {
        soundControlDialog.dismiss();
        stopVisualizer();
    });
    
    toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
        Log.d("SoundControl", "Switch: " + isChecked);
        
        if (isChecked) {
            statusDot.setBackgroundResource(R.drawable.status_dot_active);
            injectSoundControlScript();
            startVisualizer();
            Toast.makeText(MainActivity.this, "Sound control enabled ✅", Toast.LENGTH_SHORT).show();
        } else {
            statusDot.setBackgroundResource(R.drawable.status_dot_inactive);
            disableSoundControl();
            stopVisualizer();
            resetAllControls();
            Toast.makeText(MainActivity.this, "Sound control disabled ❌", Toast.LENGTH_SHORT).show();
        }
    });
    
    setupVolumeControl();
    setupBassControl();
    setupTrebleControl();
    setupBalanceControl();
    setupEqualizer();
    setupPresets();
  }

  private void setupVolumeControl() {
    SeekBar volumeSeek = soundControlDialog.findViewById(R.id.seekVolume);
    TextView volumeVal = soundControlDialog.findViewById(R.id.volumeValue);
    
    volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) return;
            volumeVal.setText(progress + "%");
            web.evaluateJavascript(
                "if(window.audioControls) window.audioControls.gainNode.gain.value = " + (progress / 100.0) + ";",
                null
            );
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    });
  }

  private void setupBassControl() {
    SeekBar bassSeek = soundControlDialog.findViewById(R.id.seekBass);
    TextView bassVal = soundControlDialog.findViewById(R.id.bassValue);
    
    bassSeek.setProgress(12);
    
    bassSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) return;
            int value = progress - 12;
            String sign = value >= 0 ? "+" : "";
            bassVal.setText(sign + value + " dB");
            web.evaluateJavascript(
                "if(window.audioControls) window.audioControls.bassFilter.gain.value = " + value + ";",
                null
            );
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    });
  }

  private void setupTrebleControl() {
    SeekBar trebleSeek = soundControlDialog.findViewById(R.id.seekTreble);
    TextView trebleVal = soundControlDialog.findViewById(R.id.trebleValue);
    
    trebleSeek.setProgress(12);
    
    trebleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) return;
            int value = progress - 12;
            String sign = value >= 0 ? "+" : "";
            trebleVal.setText(sign + value + " dB");
            web.evaluateJavascript(
                "if(window.audioControls) window.audioControls.trebleFilter.gain.value = " + value + ";",
                null
            );
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    });
  }

  private void setupBalanceControl() {
    SeekBar balanceSeek = soundControlDialog.findViewById(R.id.seekBalance);
    TextView balanceVal = soundControlDialog.findViewById(R.id.balanceValue);
    
    balanceSeek.setProgress(100);
    
    balanceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) return;
            float value = (progress - 100) / 100.0f;
            String text = "Center";
            if (value < -0.1) text = "Left " + Math.abs((int)(value * 100)) + "%";
            else if (value > 0.1) text = "Right " + (int)(value * 100) + "%";
            balanceVal.setText(text);
            web.evaluateJavascript(
                "if(window.audioControls) window.audioControls.panNode.pan.value = " + value + ";",
                null
            );
        }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    });
  }

  private void setupEqualizer() {
    int[] eqIds = {R.id.eq60, R.id.eq170, R.id.eq310, R.id.eq600, R.id.eq1k, R.id.eq3k, R.id.eq6k, R.id.eq12k};
    
    for (int i = 0; i < eqIds.length; i++) {
        SeekBar eqSeek = soundControlDialog.findViewById(eqIds[i]);
        final int index = i;
        eqSeek.setProgress(12);
        
        eqSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int value = progress - 12;
                web.evaluateJavascript(
                    "if(window.audioControls && window.audioControls.eqFilters[" + index + "]) " +
                    "window.audioControls.eqFilters[" + index + "].gain.value = " + value + ";",
                    null
                );
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
  }

  private void setupPresets() {
    soundControlDialog.findViewById(R.id.btnFlat).setOnClickListener(v -> applyPreset(new int[]{0,0,0,0,0,0,0,0}));
    soundControlDialog.findViewById(R.id.btnBass).setOnClickListener(v -> applyPreset(new int[]{8,6,4,2,0,-2,-3,-4}));
    soundControlDialog.findViewById(R.id.btnVocal).setOnClickListener(v -> applyPreset(new int[]{-2,-1,2,4,4,3,1,0}));
    soundControlDialog.findViewById(R.id.btnRock).setOnClickListener(v -> applyPreset(new int[]{5,3,-2,-3,-1,2,4,5}));
  }

  private void applyPreset(int[] values) {
    int[] eqIds = {R.id.eq60, R.id.eq170, R.id.eq310, R.id.eq600, R.id.eq1k, R.id.eq3k, R.id.eq6k, R.id.eq12k};
    
    for (int i = 0; i < eqIds.length; i++) {
        SeekBar eqSeek = soundControlDialog.findViewById(eqIds[i]);
        eqSeek.setProgress(values[i] + 12);
    }
  }
  private void injectSoundControlScript() {
    String script = 
        "(function() {" +
        "  if (window.soundControlActive) return;" +
        "  window.soundControlActive = true;" +
        "  " +
        "  const video = document.querySelector('video');" +
        "  if (!video) { console.log('❌ No video found'); return; }" +
        "  " +
        "  try {" +
        "    const audioCtx = new (window.AudioContext || window.webkitAudioContext)();" +
        "    const source = audioCtx.createMediaElementSource(video);" +
        "    const analyser = audioCtx.createAnalyser();" +
        "    const gainNode = audioCtx.createGain();" +
        "    const bassFilter = audioCtx.createBiquadFilter();" +
        "    const trebleFilter = audioCtx.createBiquadFilter();" +
        "    const panNode = audioCtx.createStereoPanner();" +
        "    " +
        "    analyser.fftSize = 128;" +
        "    bassFilter.type = 'lowshelf';" +
        "    bassFilter.frequency.value = 200;" +
        "    trebleFilter.type = 'highshelf';" +
        "    trebleFilter.frequency.value = 3000;" +
        "    " +
        "    const frequencies = [60, 170, 310, 600, 1000, 3000, 6000, 12000];" +
        "    const eqFilters = [];" +
        "    frequencies.forEach(freq => {" +
        "      const filter = audioCtx.createBiquadFilter();" +
        "      filter.type = 'peaking';" +
        "      filter.frequency.value = freq;" +
        "      filter.Q.value = 1;" +
        "      filter.gain.value = 0;" +
        "      eqFilters.push(filter);" +
        "    });" +
        "    " +
        "    let current = source;" +
        "    eqFilters.forEach(filter => {" +
        "      current.connect(filter);" +
        "      current = filter;" +
        "    });" +
        "    current.connect(bassFilter);" +
        "    bassFilter.connect(trebleFilter);" +
        "    trebleFilter.connect(panNode);" +
        "    panNode.connect(gainNode);" +
        "    gainNode.connect(analyser);" +
        "    analyser.connect(audioCtx.destination);" +
        "    " +
        "    window.audioControls = {" +
        "      audioCtx: audioCtx," +
        "      gainNode: gainNode," +
        "      bassFilter: bassFilter," +
        "      trebleFilter: trebleFilter," +
        "      panNode: panNode," +
        "      analyser: analyser," +
        "      eqFilters: eqFilters" +
        "    };" +
        "    console.log('✅ Sound control initialized');" +
        "  } catch(e) {" +
        "    console.error('❌ Sound control error:', e);" +
        "  }" +
        "})();";
    
    web.evaluateJavascript(script, result -> {
        Log.d("SoundControl", "Script injected: " + result);
    });
  }

  private void disableSoundControl() {
    web.evaluateJavascript(
        "(function() {" +
        "  if (window.audioControls) {" +
        "    try {" +
        "      window.audioControls.gainNode.disconnect();" +
        "      window.audioControls.bassFilter.disconnect();" +
        "      window.audioControls.trebleFilter.disconnect();" +
        "      window.audioControls.panNode.disconnect();" +
        "      window.audioControls.analyser.disconnect();" +
        "      window.audioControls.eqFilters.forEach(f => f.disconnect());" +
        "      window.audioControls = null;" +
        "      window.soundControlActive = false;" +
        "      console.log('✅ Sound control disabled');" +
        "      return true;" +
        "    } catch(e) {" +
        "      console.error('❌ Disable error:', e);" +
        "      return false;" +
        "    }" +
        "  }" +
        "  return false;" +
        "})();",
        null
    );
  }

  private void resetAllControls() {
    if (soundControlDialog == null) return;
    
    SeekBar volumeSeek = soundControlDialog.findViewById(R.id.seekVolume);
    if (volumeSeek != null) volumeSeek.setProgress(100);
    
    SeekBar bassSeek = soundControlDialog.findViewById(R.id.seekBass);
    SeekBar trebleSeek = soundControlDialog.findViewById(R.id.seekTreble);
    if (bassSeek != null) bassSeek.setProgress(12);
    if (trebleSeek != null) trebleSeek.setProgress(12);
    
    SeekBar balanceSeek = soundControlDialog.findViewById(R.id.seekBalance);
    if (balanceSeek != null) balanceSeek.setProgress(100);
    
    int[] eqIds = {R.id.eq60, R.id.eq170, R.id.eq310, R.id.eq600, R.id.eq1k, R.id.eq3k, R.id.eq6k, R.id.eq12k};
    for (int id : eqIds) {
        SeekBar eq = soundControlDialog.findViewById(id);
        if (eq != null) eq.setProgress(12);
    }
  }

  private void startVisualizer() {
    isVisualizerRunning = true;
    visualizerHandler.post(visualizerRunnable);
  }

  private void stopVisualizer() {
    isVisualizerRunning = false;
    visualizerHandler.removeCallbacks(visualizerRunnable);
  }

  private Runnable visualizerRunnable = new Runnable() {
    @Override
    public void run() {
        if (!isVisualizerRunning || soundControlDialog == null || !soundControlDialog.isShowing()) {
            return;
        }
        
        web.evaluateJavascript(
            "(function() {" +
            "  if (window.audioControls && window.audioControls.analyser) {" +
            "    const dataArray = new Uint8Array(window.audioControls.analyser.frequencyBinCount);" +
            "    window.audioControls.analyser.getByteFrequencyData(dataArray);" +
            "    const average = dataArray.reduce((a,b) => a+b, 0) / dataArray.length;" +
            "    return Math.round(average);" +
            "  }" +
            "  return 0;" +
            "})();",
            result -> {
                if (result != null && !result.equals("null")) {
                    try {
                        int level = Integer.parseInt(result);
                        updateVisualizer(level);
                    } catch (NumberFormatException e) {
                        Log.e("Visualizer", "Parse error: " + e.getMessage());
                    }
                }
            }
        );
        
        visualizerHandler.postDelayed(this, 50);
    }
  };

  private void updateVisualizer(int level) {
    if (soundControlDialog == null) return;
    
    View visualizer = soundControlDialog.findViewById(R.id.visualizer);
    if (visualizer != null) {
        visualizer.setBackgroundColor(Color.rgb(
            Math.min(255, level * 2),
            Math.min(255, 50 + level),
            Math.min(255, 30 + level / 2)
        ));
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
    stopVisualizer();
    Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
    stopService(intent);
    if (broadcastReceiver != null) unregisterReceiver(broadcastReceiver);
    if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null) {
      getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
    }
  }

  private void checkScriptStatus() {
    web.evaluateJavascript(
        "(function() {" +
        "  return JSON.stringify({" +
        "    loaded: window.YTPRO_LOADED || false," +
        "    hasMainScript: typeof YTProVer !== 'undefined'," +
        "    hasInnerTube: typeof window.getDownloadStreams !== 'undefined'," +
        "    hasBgPlay: typeof window.initBgPlay !== 'undefined'" +
        "  });" +
        "})();",
        value -> Log.d("YTPRO Status", "📊 Script Status: " + value)
    );
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
