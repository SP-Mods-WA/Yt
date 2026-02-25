package com.spmods.ytpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class YouActivity extends Activity {

    private LinearLayout rootLayout;
    private ScrollView scrollView;
    private ImageView avatarImage;
    private TextView nameText, handleText, subsText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String channelId = "";

    private static final String BG       = "#0F0F0F";
    private static final String CARD     = "#1A1A1A";
    private static final String ACCENT   = "#FF0000";
    private static final String TEXT_SEC = "#AAAAAA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor(BG));
        }
        buildUI();
        fetchAccountInfo();
    }

    private void buildUI() {
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor(BG));

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        buildTopBar();
        buildProfileCard();

        addSectionLabel("Your content");
        addRow("History",      "History",      () -> goTo("https://m.youtube.com/feed/history"));
        addRow("Playlists",    "Playlists",     () -> goTo("https://m.youtube.com/feed/library"));
        addRow("Your videos",  "Your videos",   () -> goTo("https://m.youtube.com/channel/me/videos"));
        addRow("Liked videos", "Liked videos",  () -> goTo("https://m.youtube.com/playlist?list=LL"));

        addSectionLabel("Account");
        addRow("Settings",     "Settings",      () -> goTo("https://m.youtube.com/account"));
        addRow("Notifications","Notifications", () -> startActivity(new Intent(this, NotificationActivity.class)));
        addRow("Privacy",      "Privacy",       () -> goTo("https://myaccount.google.com/privacy"));

        addSectionLabel("App");
        addRow("BgPlay",   "Background play",   this::toggleBgPlay);
        addRow("Version",  "App version",        this::showVersion);
        addRow("SignOut",  "Sign out",            this::confirmSignOut);

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80)));
        rootLayout.addView(space);

        scrollView.addView(rootLayout);
        setContentView(scrollView);
    }

    private void buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor(BG));
        int statusH = getStatusBarHeight();
        bar.setPadding(dpToPx(16), statusH + dpToPx(8), dpToPx(16), dpToPx(8));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("You");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        bar.addView(title);

        rootLayout.addView(bar);
    }

    private void buildProfileCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dpToPx(20), dpToPx(28), dpToPx(20), dpToPx(24));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), 0);
        card.setLayoutParams(clp);
        card.setBackground(roundedBg(CARD, 16));

        avatarImage = new ImageView(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dpToPx(84), dpToPx(84));
        alp.bottomMargin = dpToPx(14);
        avatarImage.setLayoutParams(alp);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#333333"));
        avatarImage.setBackground(circle);
        avatarImage.setClipToOutline(true);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(avatarImage);

        nameText = new TextView(this);
        nameText.setText("Loading...");
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(19f);
        nameText.setTypeface(null, Typeface.BOLD);
        nameText.setGravity(Gravity.CENTER);
        card.addView(nameText);

        handleText = new TextView(this);
        handleText.setText("");
        handleText.setTextColor(Color.parseColor(TEXT_SEC));
        handleText.setTextSize(13f);
        handleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dpToPx(2);
        handleText.setLayoutParams(hlp);
        card.addView(handleText);

        subsText = new TextView(this);
        subsText.setText("");
        subsText.setTextColor(Color.parseColor(TEXT_SEC));
        subsText.setTextSize(12f);
        subsText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dpToPx(2);
        subsText.setLayoutParams(slp);
        card.addView(subsText);

        Button viewBtn = new Button(this);
        viewBtn.setText("View channel");
        viewBtn.setTextColor(Color.WHITE);
        viewBtn.setTextSize(14f);
        viewBtn.setAllCaps(false);
        viewBtn.setTypeface(null, Typeface.BOLD);
        viewBtn.setBackground(roundedBg(ACCENT, 20));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40));
        blp.topMargin = dpToPx(16);
        blp.gravity = Gravity.CENTER;
        viewBtn.setLayoutParams(blp);
        viewBtn.setPadding(dpToPx(28), 0, dpToPx(28), 0);
        viewBtn.setOnClickListener(v -> {
            String url = channelId.isEmpty()
                ? "https://m.youtube.com/channel/me"
                : "https://m.youtube.com/channel/" + channelId;
            goTo(url);
        });
        card.addView(viewBtn);

        rootLayout.addView(card);
    }

    private void addSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase());
        tv.setTextColor(Color.parseColor(TEXT_SEC));
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(24), dpToPx(20), dpToPx(16), dpToPx(6));
        tv.setLayoutParams(lp);
        rootLayout.addView(tv);
    }

    private void addRow(String tag, String label, Runnable action) {
        RelativeLayout row = new RelativeLayout(this);
        row.setBackground(roundedBg(CARD, 12));
        row.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56));
        lp.setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(2));
        row.setLayoutParams(lp);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(15f);
        RelativeLayout.LayoutParams llp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        llp.addRule(RelativeLayout.CENTER_VERTICAL);
        llp.addRule(RelativeLayout.ALIGN_PARENT_START);
        lbl.setLayoutParams(llp);
        row.addView(lbl);

        TextView chev = new TextView(this);
        chev.setText(">");
        chev.setTextColor(Color.parseColor(TEXT_SEC));
        chev.setTextSize(16f);
        RelativeLayout.LayoutParams clp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        clp.addRule(RelativeLayout.CENTER_VERTICAL);
        clp.addRule(RelativeLayout.ALIGN_PARENT_END);
        chev.setLayoutParams(clp);
        row.addView(chev);

        row.setOnClickListener(v -> {
            v.animate().alpha(0.6f).setDuration(80)
                .withEndAction(() -> v.animate().alpha(1f).setDuration(80).start()).start();
            action.run();
        });

        rootLayout.addView(row);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  InnerTube API — fetch account info using existing cookies
    // ══════════════════════════════════════════════════════════════════════════
    private void fetchAccountInfo() {
        // CookieManager must be accessed on main thread
        String[] cookieHolder = new String[1];
        try {
            if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                cookieHolder[0] = CookieManager.getInstance().getCookie("https://m.youtube.com");
            } else {
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                mainHandler.post(() -> {
                    cookieHolder[0] = CookieManager.getInstance().getCookie("https://m.youtube.com");
                    latch.countDown();
                });
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            cookieHolder[0] = null;
        }

        final String initialCookies = cookieHolder[0];
        new Thread(() -> {
            try {
                String cookies = initialCookies;
                if (cookies == null || cookies.isEmpty()) {
                    mainHandler.post(() -> {
                        nameText.setText("Not signed in");
                        handleText.setText("Sign in to YouTube first");
                    });
                    return;
                }

                // Use ANDROID client — more stable, no API key needed
                URL url = new URL("https://www.youtube.com/youtubei/v1/account/account_menu");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
                conn.setRequestProperty("X-YouTube-Client-Name", "2");
                conn.setRequestProperty("X-YouTube-Client-Version", "2.20240101.00.00");
                conn.setRequestProperty("Origin", "https://www.youtube.com");
                conn.setRequestProperty("Referer", "https://www.youtube.com/");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

                // Extract SAPISID for auth hash
                String sapisid = "";
                String[] cookieParts = cookies.split(";");
                for (String part : cookieParts) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("SAPISID=") || trimmed.startsWith("__Secure-3PAPISID=")) {
                        sapisid = trimmed.split("=", 2)[1].trim();
                        break;
                    }
                }

                // Generate SAPISIDHASH for Authorization header
                if (!sapisid.isEmpty()) {
                    long ts = System.currentTimeMillis() / 1000L;
                    String raw = ts + " " + sapisid + " https://www.youtube.com";
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                    byte[] sha1 = md.digest(raw.getBytes("UTF-8"));
                    StringBuilder hexSb = new StringBuilder();
                    for (byte b : sha1) hexSb.append(String.format("%02x", b));
                    conn.setRequestProperty("Authorization", "SAPISIDHASH " + ts + "_" + hexSb.toString());
                }

                String body = "{\"context\":{\"client\":{\"clientName\":\"MWEB\","
                    + "\"clientVersion\":\"2.20240101.00.00\","
                    + "\"hl\":\"en\",\"gl\":\"US\"}},"
                    + "\"deviceInfo\":{\"deviceModel\":\"Android\"}}";
                byte[] bodyBytes = body.getBytes("UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
                OutputStream os = conn.getOutputStream();
                os.write(bodyBytes);
                os.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());

                String name = "", handle = "", avatarUrl = "", subs = "";

                JSONObject hdr = root.optJSONObject("header");
                if (hdr != null) {
                    JSONObject ahr = hdr.optJSONObject("accountHeaderRenderer");
                    if (ahr != null) {
                        name   = getSimpleText(ahr.optJSONObject("accountName"));
                        handle = getSimpleText(ahr.optJSONObject("accountByline"));

                        JSONObject photo = ahr.optJSONObject("accountPhoto");
                        if (photo != null) {
                            JSONArray thumbs = photo.optJSONArray("thumbnails");
                            if (thumbs != null && thumbs.length() > 0) {
                                String raw = thumbs.getJSONObject(thumbs.length() - 1)
                                    .optString("url", "");
                                avatarUrl = raw.startsWith("//") ? "https:" + raw : raw;
                            }
                        }
                    }
                }

                // Extract channelId from JSON string
                String json = sb.toString();
                int chIdx = json.indexOf("/channel/");
                if (chIdx != -1) {
                    String sub = json.substring(chIdx + 9);
                    channelId = sub.replaceAll("([A-Za-z0-9_-]+).*", "$1");
                    if (channelId.length() < 10) channelId = "";
                }

                final String fName = name.isEmpty() ? "YouTube User" : name;
                final String fHandle = handle;
                final String fAvatar = avatarUrl;

                mainHandler.post(() -> {
                    nameText.setText(fName);
                    if (!fHandle.isEmpty()) handleText.setText(fHandle);
                });

                if (!fAvatar.isEmpty()) loadAvatar(fAvatar);

            } catch (Exception e) {
                Log.e("YouActivity", "Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                mainHandler.post(() -> {
                    nameText.setText("Unable to load");
                    handleText.setText(e.getMessage() != null ? e.getMessage() : "Check your connection");
                });
            }
        }).start();
    }

    private void loadAvatar(String avatarUrl) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(avatarUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                Bitmap raw = BitmapFactory.decodeStream(conn.getInputStream());
                if (raw == null) return;
                int size = Math.min(raw.getWidth(), raw.getHeight());
                Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                canvas.drawBitmap(raw, 0, 0, paint);
                mainHandler.post(() -> {
                    avatarImage.setBackground(null);
                    avatarImage.setImageBitmap(out);
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private String getSimpleText(JSONObject obj) {
        if (obj == null) return "";
        String s = obj.optString("simpleText", "");
        if (!s.isEmpty()) return s;
        try {
            JSONArray runs = obj.optJSONArray("runs");
            if (runs != null && runs.length() > 0)
                return runs.getJSONObject(0).optString("text", "");
        } catch (Exception ignored) {}
        return "";
    }

    private GradientDrawable roundedBg(String color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dpToPx(radiusDp));
        return d;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dpToPx(24);
    }

    private void goTo(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void toggleBgPlay() {
        SharedPreferences prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        boolean cur = prefs.getBoolean("bgplay", true);
        prefs.edit().putBoolean("bgplay", !cur).apply();
        Toast.makeText(this, "Background play " + (!cur ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
    }

    private void showVersion() {
        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            Toast.makeText(this, "YTPro v" + ver, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "YTPro", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
            .setTitle("Sign out?")
            .setMessage("This will clear cookies and sign you out.")
            .setPositiveButton("Sign out", (d, w) -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                goTo("https://m.youtube.com/");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
