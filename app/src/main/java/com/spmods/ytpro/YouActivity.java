package com.spmods.ytpro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class YouActivity extends Activity {

    // ─── Views ───────────────────────────────────────────────────────────────
    private ScrollView scrollView;
    private LinearLayout rootLayout;
    private ImageView avatarImage;
    private TextView nameText;
    private TextView handleText;
    private TextView subscriberText;
    private LinearLayout statsRow;

    // ─── State ───────────────────────────────────────────────────────────────
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String channelId = "";

    // ─── Colors ──────────────────────────────────────────────────────────────
    private static final String BG       = "#0F0F0F";
    private static final String CARD     = "#1A1A1A";
    private static final String ACCENT   = "#FF0000";
    private static final String TEXT_PRI = "#FFFFFF";
    private static final String TEXT_SEC = "#AAAAAA";
    private static final String DIVIDER  = "#2A2A2A";

    // ══════════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor(BG));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        buildUI();
        loadUserData();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Build UI
    // ══════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        // Root scroll
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor(BG));
        scrollView.setFillViewport(true);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Header bar ───────────────────────────────────────────────────────
        buildHeader();

        // ── Profile card ─────────────────────────────────────────────────────
        buildProfileCard();

        // ── Stats row ────────────────────────────────────────────────────────
        buildStatsRow();

        // ── Quick action buttons ──────────────────────────────────────────────
        buildQuickActions();

        // ── Section: Your content ─────────────────────────────────────────────
        buildSectionTitle("Your content");
        buildMenuRow("Watch history",    "🕐", () -> openYT("https://m.youtube.com/feed/history"));
        buildMenuRow("Playlists",        "📋", () -> openYT("https://m.youtube.com/feed/library"));
        buildMenuRow("Your videos",      "🎬", () -> openYT("https://m.youtube.com/channel/me/videos"));
        buildMenuRow("Your clips",       "✂️",  () -> openYT("https://m.youtube.com/feed/clips"));
        buildMenuRow("Liked videos",     "👍", () -> openYT("https://m.youtube.com/playlist?list=LL"));

        // ── Section: Settings ─────────────────────────────────────────────────
        buildSectionTitle("Settings");
        buildMenuRow("Account settings",   "⚙️",  () -> openYT("https://m.youtube.com/account"));
        buildMenuRow("Notifications",      "🔔", () -> startActivity(new Intent(this, NotificationActivity.class)));
        buildMenuRow("Privacy",            "🔒", () -> openYT("https://myaccount.google.com/privacy"));
        buildMenuRow("Billing",            "💳", () -> openYT("https://m.youtube.com/paid_memberships"));
        buildMenuRow("Help & feedback",    "❓", () -> openYT("https://support.google.com/youtube"));

        // ── Section: App ──────────────────────────────────────────────────────
        buildSectionTitle("App");
        buildMenuRow("Background play",    "🎵", this::toggleBgPlay);
        buildMenuRow("App version",        "📱", this::showVersion);
        buildMenuRow("Sign out",           "🚪", this::confirmSignOut);

        // Bottom padding
        View pad = new View(this);
        pad.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(32)));
        rootLayout.addView(pad);

        scrollView.addView(rootLayout);
        setContentView(scrollView);
    }

    // ── Top header bar ────────────────────────────────────────────────────────
    private void buildHeader() {
        RelativeLayout header = new RelativeLayout(this);
        int statusH = getStatusBarHeight();
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56) + statusH);
        header.setLayoutParams(hlp);
        header.setBackgroundColor(Color.parseColor(BG));
        header.setPadding(dpToPx(16), statusH, dpToPx(16), 0);

        TextView title = new TextView(this);
        title.setText("You");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        RelativeLayout.LayoutParams tlp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        tlp.addRule(RelativeLayout.CENTER_VERTICAL);
        tlp.addRule(RelativeLayout.ALIGN_PARENT_START);
        title.setLayoutParams(tlp);
        header.addView(title);

        // Back button
        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setColorFilter(Color.WHITE);
        RelativeLayout.LayoutParams blp = new RelativeLayout.LayoutParams(dpToPx(44), dpToPx(44));
        blp.addRule(RelativeLayout.CENTER_VERTICAL);
        blp.addRule(RelativeLayout.ALIGN_PARENT_END);
        back.setLayoutParams(blp);
        back.setOnClickListener(v -> finish());
        header.addView(back);

        rootLayout.addView(header);
    }

    // ── Profile card ──────────────────────────────────────────────────────────
    private void buildProfileCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dpToPx(20), dpToPx(24), dpToPx(20), dpToPx(20));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), 0);
        card.setLayoutParams(clp);
        card.setBackground(roundedCard(CARD, 16));

        // Avatar
        avatarImage = new ImageView(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dpToPx(88), dpToPx(88));
        alp.bottomMargin = dpToPx(12);
        avatarImage.setLayoutParams(alp);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#333333"));
        avatarImage.setBackground(circle);
        avatarImage.setClipToOutline(true);
        avatarImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(avatarImage);

        // Name
        nameText = new TextView(this);
        nameText.setText("Loading...");
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(20f);
        nameText.setTypeface(null, Typeface.BOLD);
        nameText.setGravity(Gravity.CENTER);
        card.addView(nameText);

        // Handle
        handleText = new TextView(this);
        handleText.setText("");
        handleText.setTextColor(Color.parseColor(TEXT_SEC));
        handleText.setTextSize(13f);
        handleText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp2.topMargin = dpToPx(2);
        hlp2.bottomMargin = dpToPx(4);
        handleText.setLayoutParams(hlp2);
        card.addView(handleText);

        // Subscriber count
        subscriberText = new TextView(this);
        subscriberText.setText("");
        subscriberText.setTextColor(Color.parseColor(TEXT_SEC));
        subscriberText.setTextSize(12f);
        subscriberText.setGravity(Gravity.CENTER);
        card.addView(subscriberText);

        // "View channel" button
        Button viewChannel = new Button(this);
        viewChannel.setText("View channel");
        viewChannel.setTextColor(Color.WHITE);
        viewChannel.setTextSize(14f);
        viewChannel.setAllCaps(false);
        viewChannel.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(dpToPx(20));
        btnBg.setColor(Color.parseColor(ACCENT));
        viewChannel.setBackground(btnBg);
        LinearLayout.LayoutParams btnlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(40));
        btnlp.topMargin = dpToPx(14);
        btnlp.gravity = Gravity.CENTER;
        viewChannel.setLayoutParams(btnlp);
        viewChannel.setPadding(dpToPx(24), 0, dpToPx(24), 0);
        viewChannel.setOnClickListener(v -> {
            if (!channelId.isEmpty()) {
                openYT("https://m.youtube.com/channel/" + channelId);
            } else {
                openYT("https://m.youtube.com/channel/me");
            }
        });
        card.addView(viewChannel);

        rootLayout.addView(card);
    }

    // ── Stats row (videos, views, watch time) ─────────────────────────────────
    private void buildStatsRow() {
        statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setWeightSum(3f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dpToPx(16), dpToPx(10), dpToPx(16), 0);
        statsRow.setLayoutParams(slp);

        // Will be populated after data loads
        // placeholder
        buildStatCell(statsRow, "—", "Videos");
        buildStatCell(statsRow, "—", "Views");
        buildStatCell(statsRow, "—", "Subscribers");

        rootLayout.addView(statsRow);
    }

    private void buildStatCell(LinearLayout parent, String value, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dpToPx(8), dpToPx(16), dpToPx(8), dpToPx(16));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        cell.setLayoutParams(clp);
        cell.setBackground(roundedCard(CARD, 12));

        TextView valView = new TextView(this);
        valView.setText(value);
        valView.setTextColor(Color.WHITE);
        valView.setTextSize(18f);
        valView.setTypeface(null, Typeface.BOLD);
        valView.setGravity(Gravity.CENTER);
        valView.setTag("stat_" + label.toLowerCase());
        cell.addView(valView);

        TextView lblView = new TextView(this);
        lblView.setText(label);
        lblView.setTextColor(Color.parseColor(TEXT_SEC));
        lblView.setTextSize(11f);
        lblView.setGravity(Gravity.CENTER);
        cell.addView(lblView);

        parent.addView(cell);
    }

    // ── Quick action buttons ──────────────────────────────────────────────────
    private void buildQuickActions() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(3f);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(dpToPx(16), dpToPx(10), dpToPx(16), 0);
        row.setLayoutParams(rlp);

        buildActionBtn(row, "📤", "Share",     () -> shareChannel());
        buildActionBtn(row, "🎙️", "Studio",   () -> openYT("https://studio.youtube.com"));
        buildActionBtn(row, "💰", "Monetize",  () -> openYT("https://m.youtube.com/monetization"));

        rootLayout.addView(row);
    }

    private void buildActionBtn(LinearLayout parent, String icon, String label, Runnable action) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dpToPx(8), dpToPx(14), dpToPx(8), dpToPx(14));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(dpToPx(4), 0, dpToPx(4), 0);
        btn.setLayoutParams(blp);
        btn.setBackground(roundedCard(CARD, 12));
        btn.setOnClickListener(v -> action.run());

        TextView ico = new TextView(this);
        ico.setText(icon);
        ico.setTextSize(22f);
        ico.setGravity(Gravity.CENTER);
        btn.addView(ico);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor(TEXT_SEC));
        lbl.setTextSize(11f);
        lbl.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dpToPx(4);
        lbl.setLayoutParams(llp);
        btn.addView(lbl);

        parent.addView(btn);
    }

    // ── Section title ─────────────────────────────────────────────────────────
    private void buildSectionTitle(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor(TEXT_SEC));
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(24), dpToPx(20), dpToPx(16), dpToPx(6));
        tv.setLayoutParams(lp);
        tv.setLetterSpacing(0.08f);
        rootLayout.addView(tv);
    }

    // ── Menu row ──────────────────────────────────────────────────────────────
    private boolean firstRow = true;

    private void buildMenuRow(String label, String icon, Runnable action) {
        // Card container per group — add top divider if not first
        RelativeLayout row = new RelativeLayout(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(56));
        rlp.setMargins(dpToPx(16), 0, dpToPx(16), dpToPx(2));
        row.setLayoutParams(rlp);
        row.setBackground(roundedCard(CARD, 12));
        row.setPadding(dpToPx(16), 0, dpToPx(16), 0);

        // Icon
        TextView ico = new TextView(this);
        ico.setText(icon);
        ico.setTextSize(18f);
        RelativeLayout.LayoutParams ilp = new RelativeLayout.LayoutParams(
            dpToPx(36), RelativeLayout.LayoutParams.MATCH_PARENT);
        ilp.addRule(RelativeLayout.CENTER_VERTICAL);
        ilp.addRule(RelativeLayout.ALIGN_PARENT_START);
        ico.setLayoutParams(ilp);
        ico.setGravity(Gravity.CENTER);
        row.addView(ico);

        // Label
        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.WHITE);
        lbl.setTextSize(15f);
        RelativeLayout.LayoutParams llp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        llp.addRule(RelativeLayout.CENTER_VERTICAL);
        llp.setMarginStart(dpToPx(48));
        lbl.setLayoutParams(llp);
        row.addView(lbl);

        // Chevron
        TextView chev = new TextView(this);
        chev.setText("›");
        chev.setTextColor(Color.parseColor(TEXT_SEC));
        chev.setTextSize(22f);
        RelativeLayout.LayoutParams clp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        clp.addRule(RelativeLayout.CENTER_VERTICAL);
        clp.addRule(RelativeLayout.ALIGN_PARENT_END);
        chev.setLayoutParams(clp);
        row.addView(chev);

        // Ripple click
        row.setOnClickListener(v -> {
            v.animate().alpha(0.7f).setDuration(80)
                .withEndAction(() -> v.animate().alpha(1f).setDuration(80).start()).start();
            action.run();
        });

        rootLayout.addView(row);
        firstRow = false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Data loading — fetch from YouTube InnerTube API using existing cookies
    // ══════════════════════════════════════════════════════════════════════════
    private void loadUserData() {
        new Thread(() -> {
            try {
                String cookies = CookieManager.getInstance().getCookie("https://m.youtube.com");
                if (cookies == null || cookies.isEmpty()) {
                    showNotSignedIn();
                    return;
                }

                // InnerTube API — get account info
                URL url = new URL("https://m.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("X-YouTube-Client-Name", "2");
                conn.setRequestProperty("X-YouTube-Client-Version", "2.20240101.00.00");
                conn.setRequestProperty("Origin", "https://m.youtube.com");
                conn.setRequestProperty("Referer", "https://m.youtube.com/");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String body = "{\"context\":{\"client\":{\"clientName\":\"MWEB\",\"clientVersion\":\"2.20240101.00.00\"}}}";
                conn.getOutputStream().write(body.getBytes("UTF-8"));

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                parseAccountData(sb.toString());

            } catch (Exception e) {
                Log.e("YouActivity", "Load error: " + e.getMessage());
                showNotSignedIn();
            }
        }).start();
    }

    private void parseAccountData(String json) {
        try {
            JSONObject root = new JSONObject(json);

            // Navigate to account header
            JSONObject header = root
                .optJSONObject("header");

            String name        = "";
            String handle      = "";
            String avatarUrl   = "";
            String channelIdL  = "";
            String subscribers = "";

            if (header != null) {
                JSONObject acct = header.optJSONObject("accountHeaderRenderer");
                if (acct != null) {
                    name = acct.optJSONObject("accountName") != null
                        ? acct.getJSONObject("accountName").optString("simpleText", "") : "";
                    handle = acct.optJSONObject("accountByline") != null
                        ? acct.getJSONObject("accountByline").optString("simpleText", "") : "";

                    // Avatar
                    JSONObject thumb = acct.optJSONObject("accountPhoto");
                    if (thumb != null) {
                        JSONArray thumbs = thumb.optJSONArray("thumbnails");
                        if (thumbs != null && thumbs.length() > 0) {
                            JSONObject last = thumbs.getJSONObject(thumbs.length() - 1);
                            avatarUrl = last.optString("url", "");
                            if (avatarUrl.startsWith("//")) avatarUrl = "https:" + avatarUrl;
                        }
                    }

                    // Channel ID from actions
                    JSONArray actions = acct.optJSONArray("channelHandle");
                    channelIdL = acct.optString("channelHandle", "");
                }
            }

            // Try actions array for channel ID
            JSONArray acts = root.optJSONArray("actions");
            if (acts != null) {
                for (int i = 0; i < acts.length(); i++) {
                    JSONObject act = acts.optJSONObject(i);
                    if (act != null) {
                        String url2 = act.optString("url", "");
                        if (url2.contains("/channel/")) {
                            channelIdL = url2.replaceAll(".*?/channel/([^/?]+).*", "$1");
                        }
                    }
                }
            }

            final String fName      = name;
            final String fHandle    = handle;
            final String fAvatarUrl = avatarUrl;
            final String fChannelId = channelIdL;

            mainHandler.post(() -> {
                if (!fName.isEmpty()) nameText.setText(fName);
                if (!fHandle.isEmpty()) handleText.setText(fHandle);
                if (!fChannelId.isEmpty()) {
                    channelId = fChannelId;
                    // Load channel stats
                    loadChannelStats(fChannelId);
                }
                if (!fAvatarUrl.isEmpty()) loadAvatar(fAvatarUrl);
            });

        } catch (Exception e) {
            Log.e("YouActivity", "Parse error: " + e.getMessage());
            mainHandler.post(this::showNotSignedIn);
        }
    }

    private void loadChannelStats(String chId) {
        new Thread(() -> {
            try {
                String cookies = CookieManager.getInstance().getCookie("https://m.youtube.com");
                URL url = new URL("https://m.youtube.com/youtubei/v1/browse?prettyPrint=false");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Cookie", cookies);
                conn.setRequestProperty("X-YouTube-Client-Name", "2");
                conn.setRequestProperty("X-YouTube-Client-Version", "2.20240101.00.00");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String body = "{\"context\":{\"client\":{\"clientName\":\"MWEB\",\"clientVersion\":\"2.20240101.00.00\"}},"
                    + "\"browseId\":\"" + chId + "\"}";
                conn.getOutputStream().write(body.getBytes("UTF-8"));

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject root = new JSONObject(sb.toString());

                // Extract subscriber count
                String subs  = "";
                String views = "";
                String vids  = "";

                try {
                    JSONObject header = root.optJSONObject("header");
                    if (header != null) {
                        JSONObject ch = header.optJSONObject("c4TabbedHeaderRenderer");
                        if (ch == null) ch = header.optJSONObject("carouselHeaderRenderer");
                        if (ch != null) {
                            JSONObject subCount = ch.optJSONObject("subscriberCountText");
                            if (subCount != null) subs = subCount.optString("simpleText", "");

                            JSONObject vidCount = ch.optJSONObject("videosCountText");
                            if (vidCount != null) {
                                JSONArray runs = vidCount.optJSONArray("runs");
                                if (runs != null && runs.length() > 0) {
                                    vids = runs.getJSONObject(0).optString("text", "");
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                final String fSubs  = subs.isEmpty()  ? "—" : subs;
                final String fViews = views.isEmpty() ? "—" : views;
                final String fVids  = vids.isEmpty()  ? "—" : vids;

                mainHandler.post(() -> {
                    subscriberText.setText(fSubs.isEmpty() ? "" : fSubs + " subscribers");
                    updateStatCell("videos",      fVids);
                    updateStatCell("views",       fViews);
                    updateStatCell("subscribers", fSubs);
                });

            } catch (Exception e) {
                Log.e("YouActivity", "Stats error: " + e.getMessage());
            }
        }).start();
    }

    private void updateStatCell(String tag, String value) {
        View v = statsRow.findViewWithTag("stat_" + tag);
        if (v instanceof TextView) ((TextView) v).setText(value);
    }

    private void loadAvatar(String avatarUrl) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(avatarUrl).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.connect();
                Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
                if (bmp != null) {
                    // Crop to circle
                    Bitmap circular = toCircleBitmap(bmp);
                    mainHandler.post(() -> avatarImage.setImageBitmap(circular));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private Bitmap toCircleBitmap(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, 0, 0, paint);
        return output;
    }

    private void showNotSignedIn() {
        mainHandler.post(() -> {
            nameText.setText("Not signed in");
            handleText.setText("Sign in to see your account");
            subscriberText.setText("");
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════════════════════════
    private void openYT(String url) {
        Intent i = new Intent(this, MainActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void shareChannel() {
        String url = channelId.isEmpty()
            ? "https://www.youtube.com/channel/me"
            : "https://www.youtube.com/channel/" + channelId;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(share, "Share channel"));
    }

    private void toggleBgPlay() {
        android.content.SharedPreferences prefs =
            getSharedPreferences("YTPRO", MODE_PRIVATE);
        boolean current = prefs.getBoolean("bgplay", true);
        prefs.edit().putBoolean("bgplay", !current).apply();
        Toast.makeText(this,
            "Background play " + (!current ? "enabled" : "disabled"),
            Toast.LENGTH_SHORT).show();
    }

    private void showVersion() {
        try {
            String ver = getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName;
            Toast.makeText(this, "YTPro v" + ver, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "YTPro", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmSignOut() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Sign out?")
            .setMessage("This will clear all cookies and sign you out of YouTube.")
            .setPositiveButton("Sign out", (d, w) -> {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                openYT("https://m.youtube.com/");
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private GradientDrawable roundedCard(String color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dpToPx(radiusDp));
        return d;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id)
            : (int) Math.ceil(25 * getResources().getDisplayMetrics().density);
    }
}
