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
    } else if (Intent.ACTION_SEND.equals(action)) {
      String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
      if (sharedText != null && (sharedText.contains("youtube.com") || sharedText.contains("youtu.be"))) {
        url = sharedText;
      }
    }
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
        optimizeVideoPlayback();
        addHeaderIcons();
        injectMiniPlayerScript(); // ✅ මෙන්න මේකයි වැදගත් line එක!
        
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
            "  console.log('🔄 Starting YTPRO script loader...');" +
            "  function loadScript(src, name) {" +
            "    return new Promise((resolve, reject) => {" +
            "      console.log('📥 Loading ' + name + ': ' + src);" +
            "      var script = document.createElement('script');" +
            "      script.src = src;" +
            "      script.async = false;" +
            "      script.onload = function() {" +
            "        console.log('✅ Loaded ' + name);" +
            "        resolve();" +
            "      };" +
            "      script.onerror = function(e) {" +
            "        console.error('❌ Failed to load ' + name + ':', e);" +
            "        reject(new Error('Failed to load ' + name));" +
            "      };" +
            "      document.body.appendChild(script);" +
            "    });" +
            "  }" +
            "  loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/script.js', 'Main Script')" +
            "    .then(() => loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/bgplay.js', 'BG Play'))" +
            "    .then(() => loadScript('https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js', 'InnerTube'))" +
            "    .then(() => {" +
            "      console.log('✅ All YTPRO scripts loaded successfully!');" +
            "      window.YTPRO_LOADED = true;" +
            "    })" +
            "    .catch((error) => {" +
            "      console.error('❌ YTPRO script loading failed:', error);" +
            "      window.YTPRO_LOADED = false;" +
            "    });" +
            "})();";
        
        web.evaluateJavascript(scriptLoader, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d("WebView", "📜 Script loader injected");
            }
        });

        if (dl) {
            web.postDelayed(new Runnable() {
                @Override
                public void run() {
                    web.evaluateJavascript(
                        "if (typeof window.ytproDownVid === 'function') {" +
                        "  window.location.hash='download';" +
                        "} else {" +
                        "  console.error('❌ ytproDownVid not available yet');" +
                        "}",
                        null
                    );
                    dL = false;
                }
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
          if (errorCode == WebViewClient.ERROR_HOST_LOOKUP || 
              errorCode == WebViewClient.ERROR_CONNECT || 
              errorCode == WebViewClient.ERROR_TIMEOUT) {
              runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                      showOfflineScreen();
                  }
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
                      runOnUiThread(new Runnable() {
                          @Override
                          public void run() {
                              showOfflineScreen();
                          }
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
      backCallback = new OnBackInvokedCallback() {
          @Override
          public void onBackInvoked() {
              handleBackPress();
          }
      };
      dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
    }
  }

// ✅ PART 2 මෙතනින් පටන් ගන්නවා
  
// ✅ COMPLETE FIX - Enhanced back button handler
  private void handleBackPress() {
    String currentUrl = web.getUrl();
    
    // Check if mini player is already visible
    web.evaluateJavascript(
        "(function() { return document.getElementById('ytpro-mini-player') !== null; })();",
        new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String hasMiniPlayer) {
                boolean miniPlayerExists = "true".equals(hasMiniPlayer);
                
                // Video play වෙනවා නම් AND mini player නැත්නම්
                if ((currentUrl.contains("youtube.com/watch") || currentUrl.contains("youtube.com/shorts")) && isPlaying && !miniPlayerExists) {
                    // Mini player create කරලා home එකට යනවා
                    web.evaluateJavascript(
                        "javascript:(function() {" +
                        "  console.log('🎬 Creating mini player...');" +
                        "  if (typeof showMiniPlayer === 'function') {" +
                        "    showMiniPlayer();" +
                        "    console.log('✅ Mini player shown');" +
                        "  } else {" +
                        "    console.error('❌ showMiniPlayer not found');" +
                        "  }" +
                        "})();",
                        null
                    );
                    
                    // Mini player load වෙලා home එකට යනවා
                    web.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            web.loadUrl("https://m.youtube.com");
                        }
                    }, 300);
                } else if (web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            }
        }
    );
  }
  
  
  // ✅ Optimize video playback
  private void optimizeVideoPlayback() {
    String js = "javascript:(function() {" +
            "var videos = document.querySelectorAll('video');" +
            "videos.forEach(function(video) {" +
            "  video.preload = 'auto';" +
            "  video.setAttribute('playsinline', '');" +
            "  video.setAttribute('webkit-playsinline', '');" +
            "});" +
            "})()";
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        web.evaluateJavascript(js, null);
    } else {
        web.loadUrl(js);
    }
  }
  
  // ✅ Add header icons
  private void addHeaderIcons() {
    String js = "javascript:(function() {" +
            "if(document.getElementById('ytpro-custom-icons')) return;" +
            "function addIcons() {" +
            "  var header = document.querySelector('ytm-mobile-topbar-renderer');" +
            "  if(!header) { setTimeout(addIcons, 500); return; }" +
            "  var buttonsContainer = header.querySelector('.mobile-topbar-header-content');" +
            "  if(!buttonsContainer) { setTimeout(addIcons, 500); return; }" +
            "  var iconsDiv = document.createElement('div');" +
            "  iconsDiv.id = 'ytpro-custom-icons';" +
            "  iconsDiv.style.cssText = 'display:flex;align-items:center;margin-right:8px;';" +
            "  var bellBtn = document.createElement('button');" +
            "  bellBtn.style.cssText = 'background:transparent;border:0;padding:8px;display:flex;align-items:center;cursor:pointer;';" +
            "  bellBtn.innerHTML = '<svg height=\"24\" width=\"24\" viewBox=\"0 0 24 24\" fill=\"currentColor\"><path d=\"M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z\"/></svg>';" +
            "  bellBtn.onclick = function() { window.location.href = 'https://m.youtube.com/feed/notifications'; };" +
            "  var castBtn = document.createElement('button');" +
            "  castBtn.style.cssText = 'background:transparent;border:0;padding:8px;display:flex;align-items:center;cursor:pointer;';" +
            "  castBtn.innerHTML = '<svg height=\"24\" width=\"24\" viewBox=\"0 0 24 24\" fill=\"currentColor\"><path d=\"M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z\"/></svg>';" +
            "  castBtn.onclick = function() { Android.showToast('Cast feature coming soon!'); };" +
            "  iconsDiv.appendChild(bellBtn);" +
            "  iconsDiv.appendChild(castBtn);" +
            "  var searchBtn = buttonsContainer.querySelector('ytm-topbar-menu-button-renderer[button-renderer-id=\"FEsearch\"]');" +
            "  if(searchBtn) { buttonsContainer.insertBefore(iconsDiv, searchBtn); }" +
            "}" +
            "addIcons();" +
            "})()";
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        web.evaluateJavascript(js, null);
    } else {
        web.loadUrl(js);
    }
  }

  // ✅ COMPLETE MINI PLAYER - Persistent across page navigation
  private void injectMiniPlayerScript() {
    String miniPlayerJS = 
        "javascript:(function() {" +
        "  if (window.miniPlayerInjected) return;" +
        "  window.miniPlayerInjected = true;" +
        "  console.log('🎯 Mini Player Script Loaded');" +
        "  " +
        "  var globalVideoData = {" +
        "    title: ''," +
        "    channel: ''," +
        "    thumbnail: ''," +
        "    videoElement: null," +
        "    isPlaying: false" +
        "  };" +
        "  " +
        "  window.showMiniPlayer = function() {" +
        "    console.log('📺 Creating Mini Player...');" +
        "    " +
        "    // Get current video info" +
        "    var video = document.querySelector('video');" +
        "    if (!video) {" +
        "      console.error('❌ No video element found!');" +
        "      return;" +
        "    }" +
        "    " +
        "    globalVideoData.videoElement = video;" +
        "    globalVideoData.isPlaying = !video.paused;" +
        "    globalVideoData.title = document.title.replace(' - YouTube', '').replace(' - YouTube Music', '');" +
        "    " +
        "    var channelEl = document.querySelector('.ytm-slim-owner-renderer .yt-core-attributed-string');" +
        "    if (!channelEl) channelEl = document.querySelector('ytm-channel-name a');" +
        "    globalVideoData.channel = channelEl ? channelEl.textContent : '';" +
        "    " +
        "    var thumbMeta = document.querySelector('meta[property=\"og:image\"]');" +
        "    globalVideoData.thumbnail = thumbMeta ? thumbMeta.content : '';" +
        "    " +
        "    // Remove existing mini player if any" +
        "    var existing = document.getElementById('ytpro-mini-player');" +
        "    if (existing) existing.remove();" +
        "    " +
        "    // Create mini player" +
        "    var miniPlayer = document.createElement('div');" +
        "    miniPlayer.id = 'ytpro-mini-player';" +
        "    miniPlayer.className = 'ytpro-persistent-player';" +
        "    miniPlayer.style.cssText = '" +
        "      position: fixed !important;" +
        "      bottom: 50px !important;" +
        "      left: 0 !important;" +
        "      right: 0 !important;" +
        "      height: 80px !important;" +
        "      background: #0f0f0f !important;" +
        "      z-index: 2147483647 !important;" +
        "      display: flex !important;" +
        "      align-items: center !important;" +
        "      padding: 8px 12px !important;" +
        "      box-shadow: 0 -2px 16px rgba(0,0,0,0.8) !important;" +
        "      border-top: 1px solid #303030 !important;" +
        "      pointer-events: auto !important;" +
        "    ';" +
        "    " +
        "    // Thumbnail container" +
        "    var thumbContainer = document.createElement('div');" +
        "    thumbContainer.style.cssText = 'width:120px;height:68px;background:#000;margin-right:12px;border-radius:8px;overflow:hidden;flex-shrink:0;';" +
        "    " +
        "    if (globalVideoData.thumbnail) {" +
        "      var img = document.createElement('img');" +
        "      img.src = globalVideoData.thumbnail;" +
        "      img.style.cssText = 'width:100%;height:100%;object-fit:cover;';" +
        "      thumbContainer.appendChild(img);" +
        "    }" +
        "    " +
        "    // Info container" +
        "    var infoContainer = document.createElement('div');" +
        "    infoContainer.style.cssText = 'flex:1;min-width:0;display:flex;flex-direction:column;justify-content:center;';" +
        "    " +
        "    var titleDiv = document.createElement('div');" +
        "    titleDiv.style.cssText = 'color:#fff;font-size:14px;font-weight:500;margin-bottom:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';" +
        "    titleDiv.textContent = globalVideoData.title;" +
        "    " +
        "    var channelDiv = document.createElement('div');" +
        "    channelDiv.style.cssText = 'color:#aaa;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';" +
        "    channelDiv.textContent = globalVideoData.channel;" +
        "    " +
        "    infoContainer.appendChild(titleDiv);" +
        "    infoContainer.appendChild(channelDiv);" +
        "    " +
        "    // Controls" +
        "    var controls = document.createElement('div');" +
        "    controls.style.cssText = 'display:flex;align-items:center;gap:8px;flex-shrink:0;';" +
        "    " +
        "    var playBtn = document.createElement('button');" +
        "    playBtn.id = 'ytpro-play-btn';" +
        "    playBtn.style.cssText = 'background:transparent;border:none;color:#fff;font-size:32px;padding:8px;cursor:pointer;line-height:1;';" +
        "    playBtn.innerHTML = globalVideoData.isPlaying ? '⏸' : '▶';" +
        "    playBtn.onclick = function(e) {" +
        "      e.stopPropagation();" +
        "      var v = document.querySelector('video');" +
        "      if (v) {" +
        "        if (v.paused) {" +
        "          v.play();" +
        "          playBtn.innerHTML = '⏸';" +
        "        } else {" +
        "          v.pause();" +
        "          playBtn.innerHTML = '▶';" +
        "        }" +
        "      }" +
        "    };" +
        "    " +
        "    var closeBtn = document.createElement('button');" +
        "    closeBtn.style.cssText = 'background:transparent;border:none;color:#fff;font-size:28px;padding:8px;cursor:pointer;line-height:1;';" +
        "    closeBtn.innerHTML = '✖';" +
        "    closeBtn.onclick = function(e) {" +
        "      e.stopPropagation();" +
        "      var v = document.querySelector('video');" +
        "      if (v) v.pause();" +
        "      miniPlayer.remove();" +
        "    };" +
        "    " +
        "    controls.appendChild(playBtn);" +
        "    controls.appendChild(closeBtn);" +
        "    " +
        "    miniPlayer.appendChild(thumbContainer);" +
        "    miniPlayer.appendChild(infoContainer);" +
        "    miniPlayer.appendChild(controls);" +
        "    " +
        "    // Click to go back to video" +
        "    miniPlayer.onclick = function(e) {" +
        "      if (e.target === closeBtn || e.target === playBtn) return;" +
        "      window.history.back();" +
        "    };" +
        "    " +
        "    document.body.appendChild(miniPlayer);" +
        "    console.log('✅ Mini Player Created & Displayed');" +
        "    " +
        "    // Update play button when video state changes" +
        "    setTimeout(function() {" +
        "      var v = document.querySelector('video');" +
        "      if (v) {" +
        "        v.addEventListener('play', function() {" +
        "          var btn = document.getElementById('ytpro-play-btn');" +
        "          if (btn) btn.innerHTML = '⏸';" +
        "        });" +
        "        v.addEventListener('pause', function() {" +
        "          var btn = document.getElementById('ytpro-play-btn');" +
        "          if (btn) btn.innerHTML = '▶';" +
        "        });" +
        "      }" +
        "    }, 100);" +
        "  };" +
        "  " +
        "  window.hideMiniPlayer = function() {" +
        "    var mp = document.getElementById('ytpro-mini-player');" +
        "    if (mp) mp.remove();" +
        "  };" +
        "  " +
        "  // Keep mini player visible across page loads" +
        "  window.addEventListener('load', function() {" +
        "    setTimeout(function() {" +
        "      var mp = document.getElementById('ytpro-mini-player');" +
        "      if (mp && !window.location.href.includes('/watch') && !window.location.href.includes('/shorts')) {" +
        "        mp.style.display = 'flex';" +
        "      }" +
        "    }, 500);" +
        "  });" +
        "})();";
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        web.evaluateJavascript(miniPlayerJS, null);
    } else {
        web.loadUrl(miniPlayerJS);
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
    handleBackPress();
  }
  
  @Override
  public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
    web.loadUrl(isInPictureInPictureMode ?
      "javascript:PIPlayer();" :
      "javascript:removePIP();",null);
      
    if(isInPictureInPictureMode){
        isPip=true;
    }else{
        isPip=false;
    }
  }

  @Override
  protected void onUserLeaveHint() {
    super.onUserLeaveHint();
    
    if (android.os.Build.VERSION.SDK_INT >= 26 && 
        web.getUrl().contains("watch") && 
        isPlaying &&
        !isFinishing()) {
        
        try {
          PictureInPictureParams params;
          isPip=true;
          if (portrait) {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
            enterPictureInPictureMode(params);
          } else{
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
            enterPictureInPictureMode(params);
          }
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
      if (MainActivity.this == null) {
        return null;
      }
      return BitmapFactory.decodeResource(MainActivity.this.getApplicationContext().getResources(), 2130837573);
    }

    public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {
      this.mOriginalOrientation = portrait ?
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
      
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
      this.mOriginalOrientation = portrait ?
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT :
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

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
    if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && 
        checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
      runOnUiThread(() -> Toast.makeText(getApplicationContext(), R.string.grant_storage, Toast.LENGTH_SHORT).show());
      requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 1);
    }
    
    try {
      String encodedFileName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
      DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
      DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
      request.setTitle(filename)
        .setDescription(filename)
        .setMimeType(mtype)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedFileName)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE |
          DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      downloadManager.enqueue(request);
      Toast.makeText(this, getString(R.string.dl_started), Toast.LENGTH_SHORT).show();
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (Exception ignored) {
      Toast.makeText(this, ignored.toString(), Toast.LENGTH_SHORT).show();
    }
  }

// ✅ PART 3 - අවසාන කොටස මෙතනින් පටන් ගන්නවා

  public class WebAppInterface {
    Context mContext;
    WebAppInterface(Context c) {
      mContext = c;
    }

    @JavascriptInterface
    public void showToast(String txt) {
      Toast.makeText(getApplicationContext(), txt + "", Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void gohome() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    @JavascriptInterface
    public void gohome(String x) {
        gohome();
    }

    @JavascriptInterface
    public void downvid(String name, String url, String m) {
      downloadFile(name, url, m);
    }

    @JavascriptInterface
    public void fullScreen(boolean value) {
      portrait = value;
    }

    @JavascriptInterface
    public void oplink(String url) {
      Intent i = new Intent();
      i.setAction(Intent.ACTION_VIEW);
      i.setData(Uri.parse(url));
      startActivity(i);
    }

    @JavascriptInterface
    public String getInfo() {
      PackageManager manager = getApplicationContext().getPackageManager();
      try {
        PackageInfo info = manager.getPackageInfo(getApplicationContext().getPackageName(), PackageManager.GET_ACTIVITIES);
        return info.versionName + "";
      } catch (PackageManager.NameNotFoundException e) {
        return "1.0";
      }
    }

    @JavascriptInterface
    public void setBgPlay(boolean bgplay) {
      SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
      prefs.edit().putBoolean("bgplay", bgplay).apply();
    }

    @JavascriptInterface
    public void bgStart(String iconn, String titlen, String subtitlen, long dura) {
      icon = iconn;
      title = titlen;
      subtitle = subtitlen;
      duration = dura;
      isPlaying = true;
      mediaSession=true;

      Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
      intent.putExtra("icon", icon);
      intent.putExtra("title", title);
      intent.putExtra("subtitle", subtitle);
      intent.putExtra("duration", duration);
      intent.putExtra("currentPosition", 0);
      intent.putExtra("action", "play");
      startService(intent);
    }

    @JavascriptInterface
    public void bgUpdate(String iconn, String titlen, String subtitlen, long dura) {
      icon = iconn;
      title = titlen;
      subtitle = subtitlen;
      duration = (long)(dura);
      isPlaying=true;

      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", 0)
        .putExtra("action", "pause")
      );
    }

    @JavascriptInterface
    public void bgStop() {
      isPlaying = false;
      mediaSession=false;
      stopService(new Intent(getApplicationContext(), ForegroundService.class));
    }

    @JavascriptInterface
    public void bgPause(long ct) {
      isPlaying=false;
      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "pause")
      );
    }

    @JavascriptInterface
    public void bgPlay(long ct) {
      isPlaying=true;
      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "play")
      );
    }

    @JavascriptInterface
    public void bgBuffer(long ct) {
      isPlaying=true;
      getApplicationContext().sendBroadcast(new Intent("UPDATE_NOTIFICATION")
        .putExtra("icon", icon)
        .putExtra("title", title)
        .putExtra("subtitle", subtitle)
        .putExtra("duration", duration)
        .putExtra("currentPosition", ct)
        .putExtra("action", "buffer")
      );
    }

    @JavascriptInterface
    public void getSNlM0e(String cookies) {
      new Thread(() -> {
        String response = GeminiWrapper.getSNlM0e(cookies);
        runOnUiThread(() -> web.evaluateJavascript("callbackSNlM0e.resolve(`" + response + "`)", null));
      }).start();
    }

    @JavascriptInterface
    public void GeminiClient(String url, String headers, String body) {
      new Thread(() -> {
        JSONObject response = GeminiWrapper.getStream(url, headers, body);
        runOnUiThread(() -> web.evaluateJavascript("callbackGeminiClient.resolve(" + response + ")", null));
      }).start();
    }

    @JavascriptInterface
    public String getAllCookies(String url) {
      String cookies = CookieManager.getInstance().getCookie(url);
      return cookies;
    }

    @JavascriptInterface
    public float getVolume() {
      int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
      int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      return (float) currentVolume / maxVolume;
    }

    @JavascriptInterface
    public void setVolume(float volume) {
      int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      int targetVolume = (int) (max * volume);
      audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
    }

    @JavascriptInterface
    public float getBrightness() {
      float brightnessPercent;
      try {
        int sysBrightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        brightnessPercent = (sysBrightness / 255f) * 100f;
      } catch (Settings.SettingNotFoundException e) {
        brightnessPercent = 50f;
      }
      return brightnessPercent;
    }

    @JavascriptInterface
    public void setBrightness(final float brightnessValue){
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          final float brightness = Math.max(0f, Math.min(brightnessValue, 1f));
          WindowManager.LayoutParams layout = getWindow().getAttributes();
          layout.screenBrightness = brightness;
          getWindow().setAttributes(layout);
        }
      });
    }

    @JavascriptInterface
    public void pipvid(String x) {
      if (android.os.Build.VERSION.SDK_INT >= 26) {
        try {
          PictureInPictureParams params;
          if (x.equals("portrait")) {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(9, 16)).build();
          } else {
            params = new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build();
          }
          enterPictureInPictureMode(params);
        } catch (IllegalStateException e) {
          e.printStackTrace();
        }
      } else {
        Toast.makeText(getApplicationContext(), getString(R.string.no_pip), Toast.LENGTH_SHORT).show();
      }
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
            Log.e("play", "play called");
            break;
          case "PAUSE_ACTION":
            web.evaluateJavascript("pauseVideo();",null);
            Log.e("pause", "pause called");
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
        new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d("YTPRO Status", "📊 Script Status: " + value);
            }
        }
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
    retryButton.setText("Try again.");
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
    retryButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isNetworkAvailable()) {
                hideOfflineScreen();
                load(false);
            } else {
                Toast.makeText(MainActivity.this, "Still no connection.", Toast.LENGTH_SHORT).show();
            }
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
    new android.os.Handler().postDelayed(new Runnable() {
        @Override
        public void run() {
            if (isNetworkAvailable()) {
                UpdateChecker updateChecker = new UpdateChecker(MainActivity.this);
                updateChecker.checkForUpdate();
            }
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

