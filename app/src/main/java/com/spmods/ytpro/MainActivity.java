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
import javax.net.ssl.*;
import java.util.*;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;

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
    
    // Native encryption library
    static {
        System.loadLibrary("ytproencrypt");
    }
    
    // Native method declarations
    public native String encryptStringNative(String input);
    public native String decryptStringNative(String input);
    public native String getEncryptedEndpoint(int endpointType);
    public native String encryptCookies(String cookies);
    public native String generateDownloadHash(String videoId, String quality);
    
    // SSL/TLS Socket Factory for CDN connections
    private class TLSSocketFactory extends SSLSocketFactory {
        private SSLSocketFactory internalSSLSocketFactory;

        public TLSSocketFactory() throws KeyManagementException, NoSuchAlgorithmException {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            internalSSLSocketFactory = context.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return internalSSLSocketFactory.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return internalSSLSocketFactory.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort));
        }

        private Socket enableTLSOnSocket(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            }
            return socket;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        // Enable WebView debugging for development
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);

        if (!prefs.contains("bgplay")) {
            prefs.edit().putBoolean("bgplay", true).apply();
        }
        
        if (!isNetworkAvailable()) {
            showOfflineScreen();
        } else {
            load(false);
            checkForAppUpdate();
        }
        
        MainActivity.this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void load(boolean dl) {
        web = findViewById(R.id.web);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
       
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setSupportZoom(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setDatabaseEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowContentAccess(true);
        web.getSettings().setLoadWithOverviewMode(true);
        web.getSettings().setUseWideViewPort(true);
        web.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

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
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String method = "GET";
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    method = request.getMethod();
                }
                
                Log.d("WebView", "🌐 " + method + " Request: " + url);

                // Handle CDN requests
                if (url.contains("youtube.com/ytpro_cdn/")) {
                    return handleCDNRequest(url);
                }
                
                // Handle encrypted requests
                if (url.startsWith("ytpro://enc/")) {
                    return handleEncryptedRequest(url.substring(12));
                }

                return super.shouldInterceptRequest(view, request);
            }
            
            private WebResourceResponse handleCDNRequest(String originalUrl) {
                try {
                    String modifiedUrl = null;
                    String userAgent = "Mozilla/5.0 (Linux; Android 10; SM-G973F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.6045.163 Mobile Safari/537.36";
                    
                    // ESM.sh requests
                    if (originalUrl.contains("youtube.com/ytpro_cdn/esm/")) {
                        modifiedUrl = originalUrl.replace("youtube.com/ytpro_cdn/esm/", "https://esm.sh/");
                        Log.d("CDN", "📦 ESM.sh: " + modifiedUrl);
                        
                    // jsDelivr requests
                    } else if (originalUrl.contains("youtube.com/ytpro_cdn/npm/ytpro/")) {
                        String path = originalUrl.substring(originalUrl.indexOf("npm/ytpro/") + 10);
                        
                        // Direct mapping for known files
                        if (path.contains("script.js")) {
                            modifiedUrl = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/script.js";
                        } else if (path.contains("bgplay.js")) {
                            modifiedUrl = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/bgplay.js";
                        } else if (path.contains("innertube.js")) {
                            modifiedUrl = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/innertube.js";
                        } else {
                            // Generic fallback
                            modifiedUrl = "https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/" + path;
                        }
                        Log.d("CDN", "📦 jsDelivr: " + modifiedUrl);
                    }
                    
                    if (modifiedUrl == null || modifiedUrl.isEmpty()) {
                        Log.e("CDN", "❌ Could not map URL: " + originalUrl);
                        return null;
                    }
                    
                    // Add cache busting
                    if (!modifiedUrl.contains("?")) {
                        modifiedUrl += "?t=" + System.currentTimeMillis();
                    }
                    
                    return fetchResource(modifiedUrl, userAgent);
                    
                } catch (Exception e) {
                    Log.e("CDN", "❌ Error handling CDN request: " + e.getMessage());
                    return null;
                }
            }
            
            private WebResourceResponse fetchResource(String urlString, String userAgent) {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(urlString);
                    
                    if (urlString.startsWith("https://")) {
                        HttpsURLConnection httpsConn = (HttpsURLConnection) url.openConnection();
                        
                        // Setup SSL
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                            SSLContext sslContext = SSLContext.getInstance("TLS");
                            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                                @Override
                                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                                @Override
                                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                                @Override
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[]{}; }
                            }}, new java.security.SecureRandom());
                            httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
                            httpsConn.setHostnameVerifier((hostname, session) -> true);
                        }
                        
                        connection = httpsConn;
                    } else {
                        connection = (HttpURLConnection) url.openConnection();
                    }
                    
                    // Set connection properties
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setRequestProperty("User-Agent", userAgent);
                    connection.setRequestProperty("Accept", "*/*");
                    connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.setInstanceFollowRedirects(true);
                    
                    // Connect
                    connection.connect();
                    
                    int responseCode = connection.getResponseCode();
                    Log.d("CDN Fetch", "📡 " + responseCode + " from " + urlString);
                    
                    // Handle redirects
                    if (responseCode >= 300 && responseCode < 400) {
                        String redirectUrl = connection.getHeaderField("Location");
                        if (redirectUrl != null && !redirectUrl.isEmpty()) {
                            connection.disconnect();
                            return fetchResource(redirectUrl, userAgent);
                        }
                    }
                    
                    if (responseCode != 200) {
                        Log.e("CDN Fetch", "❌ Bad response: " + responseCode);
                        connection.disconnect();
                        return null;
                    }
                    
                    // Determine content type
                    String contentType = connection.getContentType();
                    if (contentType == null || contentType.isEmpty()) {
                        if (urlString.endsWith(".js")) {
                            contentType = "application/javascript";
                        } else if (urlString.endsWith(".css")) {
                            contentType = "text/css";
                        } else {
                            contentType = "text/plain";
                        }
                    }
                    
                    // Clean content type
                    if (contentType.contains(";")) {
                        contentType = contentType.split(";")[0].trim();
                    }
                    
                    // Create response headers
                    Map<String, String> headers = new HashMap<>();
                    headers.put("Access-Control-Allow-Origin", "*");
                    headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    headers.put("Access-Control-Allow-Headers", "*");
                    headers.put("Content-Type", contentType + "; charset=utf-8");
                    headers.put("Cross-Origin-Resource-Policy", "cross-origin");
                    
                    return new WebResourceResponse(
                        contentType,
                        "utf-8",
                        responseCode,
                        "OK",
                        headers,
                        connection.getInputStream()
                    );
                    
                } catch (Exception e) {
                    Log.e("CDN Fetch", "❌ Fetch failed: " + e.getMessage());
                    if (connection != null) {
                        connection.disconnect();
                    }
                    return null;
                }
            }
            
            private WebResourceResponse handleEncryptedRequest(String encryptedData) {
                try {
                    // Decrypt the request data
                    String decryptedUrl = decryptStringNative(encryptedData);
                    Log.d("Encrypted", "🔓 Decrypted: " + decryptedUrl);
                    
                    // Fetch the resource
                    return fetchResource(decryptedUrl, "YTPro/1.0");
                } catch (Exception e) {
                    Log.e("Encrypted", "❌ Failed to handle encrypted request", e);
                    return null;
                }
            }
            
            @Override
            public void onPageStarted(WebView p1, String p2, Bitmap p3) {
                Log.d("Page", "🚀 Loading: " + p2);
                super.onPageStarted(p1, p2, p3);
            }

            @Override
            public void onPageFinished(WebView p1, String url) {
                Log.d("Page", "✅ Loaded: " + url);
                
                // Inject TrustedTypes policy
                web.evaluateJavascript(
                    "if (window.trustedTypes && window.trustedTypes.createPolicy) {" +
                    "  window.trustedTypes.createPolicy('default', {" +
                    "    createHTML: s => s," +
                    "    createScriptURL: s => s," +
                    "    createScript: s => s" +
                    "  });" +
                    "}",
                    null
                );
                
                // Enhanced script loader with multiple fallbacks
                String scriptLoader = 
                    "(function() {" +
                    "  console.log('🚀 YTPro Script Loader Starting...');" +
                    "  " +
                    "  var scripts = [" +
                    "    {name: 'main', url: 'https://youtube.com/ytpro_cdn/npm/ytpro/script.js'}," +
                    "    {name: 'bgplay', url: 'https://youtube.com/ytpro_cdn/npm/ytpro/bgplay.js'}," +
                    "    {name: 'innertube', url: 'https://youtube.com/ytpro_cdn/npm/ytpro/innertube.js'}" +
                    "  ];" +
                    "  " +
                    "  var fallbacks = {" +
                    "    'main': 'https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/script.js'," +
                    "    'bgplay': 'https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/bgplay.js'," +
                    "    'innertube': 'https://raw.githubusercontent.com/SP-Mods-WA/Yt/main/scripts/innertube.js'" +
                    "  };" +
                    "  " +
                    "  function loadScript(src, name, retryCount) {" +
                    "    return new Promise((resolve, reject) => {" +
                    "      var cacheBuster = '?v=' + Date.now();" +
                    "      var finalSrc = src + cacheBuster;" +
                    "      console.log('📥 Loading ' + name + ' from: ' + finalSrc);" +
                    "      " +
                    "      var script = document.createElement('script');" +
                    "      script.src = finalSrc;" +
                    "      script.async = false;" +
                    "      script.onload = function() {" +
                    "        console.log('✅ Success: ' + name);" +
                    "        resolve();" +
                    "      };" +
                    "      script.onerror = function(e) {" +
                    "        console.error('❌ Failed: ' + name, e);" +
                    "        if (retryCount < 2) {" +
                    "          console.log('🔄 Retrying ' + name + ' (' + (retryCount + 1) + ')');" +
                    "          setTimeout(function() {" +
                    "            loadScript(src, name, retryCount + 1).then(resolve).catch(reject);" +
                    "          }, 1000);" +
                    "        } else {" +
                    "          reject();" +
                    "        }" +
                    "      };" +
                    "      document.body.appendChild(script);" +
                    "    });" +
                    "  }" +
                    "  " +
                    "  function loadWithFallback(scriptObj, index) {" +
                    "    if (index >= scripts.length) {" +
                    "      console.log('🎉 All scripts loaded!');" +
                    "      window.YTPRO_LOADED = true;" +
                    "      if (typeof window.initYTPro === 'function') {" +
                    "        window.initYTPro();" +
                    "      }" +
                    "      return;" +
                    "    }" +
                    "    " +
                    "    var current = scripts[index];" +
                    "    loadScript(current.url, current.name, 0)" +
                    "      .then(function() {" +
                    "        loadWithFallback(scriptObj, index + 1);" +
                    "      })" +
                    "      .catch(function() {" +
                    "        console.warn('⚠️ Using fallback for ' + current.name);" +
                    "        loadScript(fallbacks[current.name], current.name + '_fb', 0)" +
                    "          .then(function() {" +
                    "            loadWithFallback(scriptObj, index + 1);" +
                    "          })" +
                    "          .catch(function() {" +
                    "            console.error('💥 All sources failed for ' + current.name);" +
                    "            loadWithFallback(scriptObj, index + 1);" +
                    "          });" +
                    "      });" +
                    "  }" +
                    "  " +
                    "  // Start loading" +
                    "  loadWithFallback(scripts, 0);" +
                    "})();";
                
                web.evaluateJavascript(scriptLoader, null);
                
                // Handle download trigger
                if (dl) {
                    web.postDelayed(() -> {
                        web.evaluateJavascript(
                            "if (window.YTPRO_LOADED && typeof window.ytproDownVid === 'function') {" +
                            "  console.log('⬇️ Triggering download...');" +
                            "  window.location.hash = '#download';" +
                            "} else {" +
                            "  console.log('⏳ Waiting for YTPRO...');" +
                            "  var check = setInterval(function() {" +
                            "    if (window.YTPRO_LOADED && typeof window.ytproDownVid === 'function') {" +
                            "      clearInterval(check);" +
                            "      window.location.hash = '#download';" +
                            "    }" +
                            "  }, 1000);" +
                            "}",
                            null
                        );
                        dL = false;
                    }, 2000);
                }

                // Clean up media session
                if (!url.contains("/watch") && !url.contains("/shorts") && isPlaying) {
                    stopMediaSession();
                }

                super.onPageFinished(p1, url);
            }
            
            private void stopMediaSession() {
                isPlaying = false;
                mediaSession = false;
                stopService(new Intent(getApplicationContext(), ForegroundService.class));
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e("WebError", "Code: " + errorCode + " | " + description + " | " + failingUrl);
                
                if (!isOffline && (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT)) {
                    runOnUiThread(() -> showOfflineScreen());
                }
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.e("WebError", "Advanced: " + error.getErrorCode() + " | " + error.getDescription());
                    
                    if (request.isForMainFrame()) {
                        int errorCode = error.getErrorCode();
                        if (!isOffline && (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT)) {
                            runOnUiThread(() -> showOfflineScreen());
                        }
                    }
                }
                super.onReceivedError(view, request, error);
            }
            
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.w("HTTP", errorResponse.getStatusCode() + " for " + request.getUrl());
                }
                super.onReceivedHttpError(view, request, errorResponse);
            }
        });

        setReceiver();

        // Handle back navigation
        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
            backCallback = () -> {
                if (web.canGoBack()) {
                    web.goBack();
                } else {
                    finish();
                }
            };
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                web.loadUrl("https://m.youtube.com");
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
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
        web.evaluateJavascript(isInPictureInPictureMode ? "javascript:PIPlayer();" : "javascript:removePIP();", null);
        isPip = isInPictureInPictureMode;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
       
        if (Build.VERSION.SDK_INT >= 26 && web != null && web.getUrl() != null && 
            web.getUrl().contains("watch") && isPlaying) {
            try {
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                if (portrait) {
                    builder.setAspectRatio(new Rational(9, 16));
                } else {
                    builder.setAspectRatio(new Rational(16, 9));
                }
                enterPictureInPictureMode(builder.build());
                isPip = true;
            } catch (Exception e) {
                Log.e("PIP", "Failed to enter PiP", e);
            }
        }
    }

    public class CustomWebClient extends WebChromeClient {
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;
        
        public CustomWebClient() {}

        public Bitmap getDefaultVideoPoster() {
            // Create a simple bitmap if ic_video_poster doesn't exist
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.DKGRAY);
            return bitmap;
        }

        public void onShowCustomView(View paramView, WebChromeClient.CustomViewCallback viewCallback) {
            if (mCustomView != null) {
                onHideCustomView();
                return;
            }
            
            mOriginalOrientation = getRequestedOrientation();
            mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            
            // Set orientation based on video
            int newOrientation = portrait ? 
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : 
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            if (isPip) newOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
            
            setRequestedOrientation(newOrientation);
            
            // Handle cutout mode for modern devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(params);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
            
            mCustomView = paramView;
            mCustomViewCallback = viewCallback;
            
            // Add custom view
            FrameLayout decor = (FrameLayout) getWindow().getDecorView();
            decor.addView(mCustomView, new FrameLayout.LayoutParams(-1, -1));
            
            // Hide system UI
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
        
        public void onHideCustomView() {
            if (mCustomView == null) return;
            
            // Restore window properties
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                getWindow().setAttributes(params);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
            
            // Remove custom view
            FrameLayout decor = (FrameLayout) getWindow().getDecorView();
            decor.removeView(mCustomView);
            
            // Restore system UI
            getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
            setRequestedOrientation(mOriginalOrientation);
            
            mCustomView = null;
            mCustomViewCallback.onCustomViewHidden();
            mCustomViewCallback = null;
            web.clearFocus();
        }
        
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            if (Build.VERSION.SDK_INT > 22 && request.getOrigin().toString().contains("youtube.com")) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                } else {
                    request.grant(request.getResources());
                }
            }
        }
        
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d("WebConsole", consoleMessage.message() + " at " + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
            return true;
        }
    }

    private void downloadFile(String filename, String url, String mtype) {
        if (Build.VERSION.SDK_INT > 22 && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && 
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        
        try {
            String encodedName = URLEncoder.encode(filename, "UTF-8").replaceAll("\\+", "%20");
            
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            
            request.setTitle(filename)
                   .setDescription("Downloading file")
                   .setMimeType(mtype)
                   .setAllowedOverMetered(true)
                   .setAllowedOverRoaming(true)
                   .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, encodedName)
                   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            
            dm.enqueue(request);
            Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Log.e("Download", "Failed to download", e);
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public class WebAppInterface {
        Context mContext;
        
        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showToast(String txt) {
            Toast.makeText(mContext, txt, Toast.LENGTH_SHORT).show();
        }
        
        @JavascriptInterface
        public void gohome(String x) {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        }

        @JavascriptInterface
        public void downvid(String name, String url, String m) {
            downloadFile(name, url, m);
        }
        
        @JavascriptInterface
        public void secureDownload(String encryptedData) {
            try {
                String decrypted = decryptStringNative(encryptedData);
                JSONObject data = new JSONObject(decrypted);
                downloadFile(
                    data.getString("filename"),
                    data.getString("url"),
                    data.getString("mimeType")
                );
            } catch (Exception e) {
                Log.e("SecureDL", "Failed", e);
                Toast.makeText(mContext, "Secure download failed", Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public void fullScreen(boolean value) {
            portrait = value;
        }
        
        @JavascriptInterface
        public void oplink(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(mContext, "Cannot open link", Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public String getInfo() {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                return info.versionName;
            } catch (Exception e) {
                return "1.0";
            }
        }
        
        @JavascriptInterface
        public void setBgPlay(boolean bgplay) {
            getSharedPreferences("YTPRO", MODE_PRIVATE).edit().putBoolean("bgplay", bgplay).apply();
        }

        @JavascriptInterface
        public void bgStart(String iconn, String titlen, String subtitlen, long dura) {
            icon = iconn;
            title = titlen;
            subtitle = subtitlen;
            duration = dura;
            isPlaying = true;
            mediaSession = true;

            Intent intent = new Intent(getApplicationContext(), ForegroundService.class);
            intent.putExtra("icon", encryptStringNative(icon));
            intent.putExtra("title", encryptStringNative(title));
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
            duration = dura;
            isPlaying = true;

            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", 0)
                .putExtra("action", "pause"));
        }
        
        @JavascriptInterface
        public void bgStop() {
            isPlaying = false;
            mediaSession = false;
            stopService(new Intent(getApplicationContext(), ForegroundService.class));
        }
        
        @JavascriptInterface
        public void bgPause(long ct) {
            isPlaying = false;
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "pause"));
        }
        
        @JavascriptInterface
        public void bgPlay(long ct) {
            isPlaying = true;
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "play"));
        }
        
        @JavascriptInterface
        public void bgBuffer(long ct) {
            isPlaying = true;
            sendBroadcast(new Intent("UPDATE_NOTIFICATION")
                .putExtra("icon", encryptStringNative(icon))
                .putExtra("title", encryptStringNative(title))
                .putExtra("subtitle", subtitle)
                .putExtra("duration", duration)
                .putExtra("currentPosition", ct)
                .putExtra("action", "buffer"));
        }
        
        @JavascriptInterface
        public void getSNlM0e(String cookies) {
            new Thread(() -> {
                String response = GeminiWrapper.getSNlM0e(cookies);
                String encrypted = encryptStringNative(response);
                runOnUiThread(() -> web.evaluateJavascript("callbackSNlM0e.resolve(`" + encrypted + "`)", null));
            }).start();
        }
        
        @JavascriptInterface
        public void GeminiClient(String url, String headers, String body) {
            new Thread(() -> {
                JSONObject response = GeminiWrapper.getStream(url, headers, body);
                try {
                    if (response.has("url")) {
                        String encryptedUrl = encryptStringNative(response.getString("url"));
                        response.put("url", encryptedUrl);
                    }
                } catch (JSONException e) {
                    Log.e("Gemini", "Encryption error", e);
                }
                runOnUiThread(() -> web.evaluateJavascript("callbackGeminiClient.resolve(" + response + ")", null));
            }).start();
        }
        
        @JavascriptInterface
        public String getAllCookies(String url) {
            String cookies = CookieManager.getInstance().getCookie(url);
            return cookies != null ? cookies : "";
        }
        
        @JavascriptInterface
        public String getSecureCookies(String url) {
            String cookies = CookieManager.getInstance().getCookie(url);
            return encryptCookies(cookies != null ? cookies : "");
        }
        
        @JavascriptInterface
        public float getVolume() {
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return max > 0 ? (float) current / max : 0.5f;
        }
        
        @JavascriptInterface
        public void setVolume(float volume) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = Math.max(0, Math.min((int)(max * volume), max));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        }
        
        @JavascriptInterface
        public float getBrightness() {
            try {
                int brightness = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                return (brightness / 255f) * 100f;
            } catch (Exception e) {
                return 50f;
            }
        }
        
        @JavascriptInterface
        public void setBrightness(final float percent) {
            runOnUiThread(() -> {
                float brightness = Math.max(0, Math.min(percent / 100f, 1f));
                WindowManager.LayoutParams layout = getWindow().getAttributes();
                layout.screenBrightness = brightness;
                getWindow().setAttributes(layout);
            });
        }
        
        @JavascriptInterface
        public void pipvid(String mode) {
            if (Build.VERSION.SDK_INT >= 26) {
                try {
                    PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
                    if ("portrait".equals(mode)) {
                        builder.setAspectRatio(new Rational(9, 16));
                    } else {
                        builder.setAspectRatio(new Rational(16, 9));
                    }
                    enterPictureInPictureMode(builder.build());
                } catch (Exception e) {
                    Toast.makeText(mContext, "PiP not available", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(mContext, "PiP not available", Toast.LENGTH_SHORT).show();
            }
        }
        
        @JavascriptInterface
        public String encryptData(String data) {
            return encryptStringNative(data);
        }
        
        @JavascriptInterface
        public String decryptData(String encrypted) {
            return decryptStringNative(encrypted);
        }
        
        @JavascriptInterface
        public String generateSecureHash(String videoId, String quality) {
            return generateDownloadHash(videoId, quality);
        }
        
        @JavascriptInterface
        public String getSecureEndpoint(int type) {
            return getEncryptedEndpoint(type);
        }
        
        @JavascriptInterface
        public void log(String message) {
            Log.d("JSLog", message);
        }
    }

    private void setReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getStringExtra("actionname");
                Log.d("Receiver", "Action: " + action);

                if (action == null) return;
                
                switch (action) {
                    case "PLAY_ACTION":
                        web.evaluateJavascript("playVideo();", null);
                        break;
                    case "PAUSE_ACTION":
                        web.evaluateJavascript("pauseVideo();", null);
                        break;
                    case "NEXT_ACTION":
                        web.evaluateJavascript("playNext();", null);
                        break;
                    case "PREV_ACTION":
                        web.evaluateJavascript("playPrev();", null);
                        break;
                    case "SEEKTO":
                        String pos = intent.getStringExtra("pos");
                        if (pos != null) {
                            web.evaluateJavascript("seekTo('" + pos + "');", null);
                        }
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter("TRACKS_TRACKS");
        if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
            registerReceiver(broadcastReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isOffline && isNetworkAvailable()) {
            hideOfflineScreen();
            if (web == null) {
                load(false);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this, ForegroundService.class));
        
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
        
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        
        if (web != null) {
            web.destroy();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    private void showOfflineScreen() {
        if (isOffline) return;
        
        isOffline = true;
        runOnUiThread(() -> {
            offlineLayout = new RelativeLayout(this);
            offlineLayout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            ));
            offlineLayout.setBackgroundColor(Color.parseColor("#0F0F0F"));
            
            LinearLayout center = new LinearLayout(this);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setGravity(Gravity.CENTER);
            
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            );
            params.addRule(RelativeLayout.CENTER_IN_PARENT);
            center.setLayoutParams(params);
            
            // Icon
            TextView icon = new TextView(this);
            icon.setText("📡");
            icon.setTextSize(80);
            icon.setGravity(Gravity.CENTER);
            center.addView(icon);
            
            // Title
            TextView title = new TextView(this);
            title.setText("No Internet Connection");
            title.setTextSize(20);
            title.setTextColor(Color.WHITE);
            title.setTypeface(null, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            titleParams.setMargins(0, dpToPx(20), 0, dpToPx(10));
            title.setLayoutParams(titleParams);
            center.addView(title);
            
            // Message
            TextView message = new TextView(this);
            message.setText("Check your Wi-Fi or mobile data connection.");
            message.setTextSize(14);
            message.setTextColor(Color.parseColor("#AAAAAA"));
            message.setGravity(Gravity.CENTER);
            message.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(250),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            center.addView(message);
            
            // Retry Button
            Button retry = new Button(this);
            retry.setText("Try Again");
            retry.setTextColor(Color.WHITE);
            retry.setAllCaps(false);
            retry.setBackgroundColor(Color.parseColor("#FF0000"));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                dpToPx(150),
                dpToPx(45)
            );
            btnParams.setMargins(0, dpToPx(30), 0, 0);
            retry.setLayoutParams(btnParams);
            
            retry.setOnClickListener(v -> {
                if (isNetworkAvailable()) {
                    hideOfflineScreen();
                    if (web == null) {
                        load(false);
                    } else {
                        web.reload();
                    }
                } else {
                    Toast.makeText(this, "Still offline", Toast.LENGTH_SHORT).show();
                }
            });
            
            center.addView(retry);
            offlineLayout.addView(center);
            
            ViewGroup root = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
            root.addView(offlineLayout);
        });
    }

    private void hideOfflineScreen() {
        if (!isOffline || offlineLayout == null) return;
        
        isOffline = false;
        runOnUiThread(() -> {
            ViewGroup root = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
            root.removeView(offlineLayout);
            offlineLayout = null;
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void checkForAppUpdate() {
        new Handler().postDelayed(() -> {
            if (isNetworkAvailable()) {
                // Update checker implementation
                Log.d("Update", "Checking for updates...");
            }
        }, 2000);
    }
}
