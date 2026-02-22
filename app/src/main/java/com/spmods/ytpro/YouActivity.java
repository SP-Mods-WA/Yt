package com.spmods.ytpro;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class YouActivity extends Activity {

    private static final int BG       = Color.parseColor("#0F0F0F");
    private static final int SURFACE  = Color.parseColor("#161616");
    private static final int SURFACE2 = Color.parseColor("#1C1C1C");
    private static final int RED      = Color.parseColor("#FF0033");
    private static final int CYAN     = Color.parseColor("#00F2EA");
    private static final int TEXT     = Color.parseColor("#F5F5F5");
    private static final int SUB      = Color.parseColor("#999999");
    private static final int MUTED    = Color.parseColor("#555555");
    private static final int BORDER   = Color.parseColor("#1F1F1F");

    private SharedPreferences prefs;
    private LinearLayout rootLayout;

    private boolean bgPlayEnabled    = true;
    private boolean pipEnabled       = true;
    private boolean notifEnabled     = true;
    private boolean autoSkipSponsors = true;
    private boolean gestureControls  = true;
    private boolean shortsBlocked    = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        );

        prefs = getSharedPreferences("YTPRO", MODE_PRIVATE);
        bgPlayEnabled    = prefs.getBoolean("bgplay", true);
        autoSkipSponsors = "true".equals(prefs.getString("autoSpn", "true"));
        gestureControls  = "true".equals(prefs.getString("gesC",    "true"));
        shortsBlocked    = "true".equals(prefs.getString("shorts",  "false"));

        buildUI();
    }

    // ═══════════════════════════════════════════════════
    //  LOGIN CHECK
    // ═══════════════════════════════════════════════════
    private boolean isLoggedIn() {
        String cookies = CookieManager.getInstance().getCookie("https://m.youtube.com");
        if (cookies != null && cookies.contains("__Secure-1PSID=")) return true;
        String name = prefs.getString("yt_username", "");
        return name != null && !name.isEmpty();
    }

    private String getUserName() {
        if (!isLoggedIn()) return "Guest User";
        String name = prefs.getString("yt_username", "");
        return (name != null && !name.isEmpty()) ? name : "YT Pro User";
    }

    private String getUserHandle() {
        if (!isLoggedIn()) return "Sign in to access your account";
        String email = prefs.getString("yt_email", "");
        String name  = prefs.getString("yt_username", "");
        String handle = (name != null && !name.isEmpty())
            ? "@" + name.toLowerCase().replace(" ", "_") : "";
        if (email != null && !email.isEmpty()) return handle + " · " + email;
        return handle.isEmpty() ? "@ytpro_user" : handle;
    }

    private String getUserInitial() {
        if (!isLoggedIn()) return "?";
        String name = getUserName();
        return name.length() > 0 ? String.valueOf(name.charAt(0)).toUpperCase() : "Y";
    }

    // ═══════════════════════════════════════════════════
    //  BUILD UI
    // ═══════════════════════════════════════════════════
    private void buildUI() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        setContentView(root);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(BG);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        FrameLayout.LayoutParams svlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        svlp.topMargin    = dp(56);
        svlp.bottomMargin = dp(70);
        scrollView.setLayoutParams(svlp);
        root.addView(scrollView);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(BG);
        rootLayout.setPadding(0, 0, 0, dp(16));
        scrollView.addView(rootLayout);

        if (isLoggedIn()) {
            buildLoggedInUI();
        } else {
            buildGuestUI();
        }

        root.addView(buildHeader());
        root.addView(buildBottomNav());
    }

    // ═══════════════════════════════════════════════════
    //  LOGGED IN UI
    // ═══════════════════════════════════════════════════
    private void buildLoggedInUI() {
        addProfileSection();
        addStatsRow();
        addManageAccountButton();
        addDividerThick();
        addSectionLabel("Watch History", true);
        addHistoryScroll();
        addSectionLabel("Your Playlists", true);
        addPlaylistScroll();
        addDividerThick();
        addSectionLabel("YT Pro Features", false);
        addFeaturesMenu();
        addSectionLabel("General", false);
        addGeneralMenu();
        addSignOutButton();
        addVersionText();
    }

    // ═══════════════════════════════════════════════════
    //  GUEST UI
    // ═══════════════════════════════════════════════════
    private void buildGuestUI() {
        addGuestProfileSection();
        addDividerThick();
        addSectionLabel("Sign in to unlock", false);
        addSignInBenefits();
        addDividerThick();
        addSectionLabel("YT Pro Features", false);
        addFeaturesMenu();
        addSectionLabel("General", false);
        addGeneralMenu();
        addVersionText();
    }

    private void addGuestProfileSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setGravity(Gravity.CENTER);
        section.setPadding(dp(24), dp(40), dp(24), dp(24));

        // Avatar
        FrameLayout avatarWrap = new FrameLayout(this);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(dp(90), dp(90));
        awlp.gravity = Gravity.CENTER_HORIZONTAL;
        awlp.bottomMargin = dp(16);
        avatarWrap.setLayoutParams(awlp);

        TextView avatar = new TextView(this);
        avatar.setText("?");
        avatar.setTextSize(36);
        avatar.setTextColor(SUB);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(SURFACE2);
        avatarBg.setStroke(dp(2), BORDER);
        avatar.setBackground(avatarBg);
        avatarWrap.addView(avatar, new FrameLayout.LayoutParams(dp(90), dp(90)));
        section.addView(avatarWrap);

        // Name
        TextView nameView = new TextView(this);
        nameView.setText("Guest User");
        nameView.setTextColor(TEXT);
        nameView.setTextSize(22);
        nameView.setTypeface(null, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        section.addView(nameView);

        // Sub text
        TextView subView = new TextView(this);
        subView.setText("Sign in to sync your subscriptions,\nwatch history and more");
        subView.setTextColor(SUB);
        subView.setTextSize(13);
        subView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sublp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sublp.topMargin    = dp(8);
        sublp.bottomMargin = dp(28);
        subView.setLayoutParams(sublp);
        section.addView(subView);

        // ✅ Sign In Button
        TextView signInBtn = new TextView(this);
        signInBtn.setText("  Sign In with Google  ");
        signInBtn.setTextColor(Color.WHITE);
        signInBtn.setTextSize(15);
        signInBtn.setTypeface(null, Typeface.BOLD);
        signInBtn.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(dp(25));
        btnBg.setColor(RED);
        signInBtn.setBackground(btnBg);
        signInBtn.setPadding(dp(32), dp(14), dp(32), dp(14));
        LinearLayout.LayoutParams btnlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnlp.gravity = Gravity.CENTER_HORIZONTAL;
        signInBtn.setLayoutParams(btnlp);

        // ✅ Sign In — MainActivity හරහා Google login page load කරනවා
        signInBtn.setOnClickListener(v -> {
            Intent intent = new Intent(YouActivity.this, MainActivity.class);
            intent.putExtra("url",
                "https://accounts.google.com/ServiceLogin?service=youtube&hl=en" +
                "&continue=https://m.youtube.com/");
            intent.putExtra("fromLogin", true);
            startActivity(intent);
            finish();
        });

        section.addView(signInBtn);
        rootLayout.addView(section);
    }

    private void addSignInBenefits() {
        LinearLayout menu = makeMenuContainer();
        String[][] benefits = {
            {"📺", "#1AFF0033", "Watch History",    "Continue where you left off"},
            {"🔔", "#1AFFA500", "Subscriptions",    "Never miss a video"},
            {"❤️", "#1AFF0033", "Liked Videos",     "Save your favourite videos"},
            {"📋", "#1A4285F4", "Playlists",        "Create and manage playlists"},
            {"🤖", "#1A9B72CB", "Gemini AI",        "Ask questions about videos"},
        };
        for (int i = 0; i < benefits.length; i++) {
            menu.addView(buildNavRow(benefits[i][0], benefits[i][1], benefits[i][2], benefits[i][3]));
            if (i < benefits.length - 1) menu.addView(menuDivider());
        }
        rootLayout.addView(menu);
    }

    // ═══════════════════════════════════════════════════
    //  PROFILE SECTION (Logged In)
    // ═══════════════════════════════════════════════════
    private void addProfileSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(16), dp(24), dp(16), 0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Avatar wrap with gradient ring
        FrameLayout avatarWrap = new FrameLayout(this);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(dp(76), dp(76));
        awlp.rightMargin = dp(16);
        avatarWrap.setLayoutParams(awlp);

        GradientDrawable ring = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{RED, CYAN, RED});
        ring.setShape(GradientDrawable.OVAL);
        View ringView = new View(this);
        ringView.setBackground(ring);
        avatarWrap.addView(ringView, new FrameLayout.LayoutParams(dp(76), dp(76)));

        TextView avatar = new TextView(this);
        avatar.setText(getUserInitial());
        avatar.setTextSize(26);
        avatar.setTextColor(Color.WHITE);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(RED);
        avatar.setBackground(avatarBg);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(dp(68), dp(68));
        alp.gravity = Gravity.CENTER;
        avatarWrap.addView(avatar, alp);

        // Online dot
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.parseColor("#00D26A"));
        dotBg.setStroke(dp(2), BG);
        dot.setBackground(dotBg);
        FrameLayout.LayoutParams dotlp = new FrameLayout.LayoutParams(dp(14), dp(14));
        dotlp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        dotlp.bottomMargin = dp(2);
        dotlp.rightMargin  = dp(2);
        avatarWrap.addView(dot, dotlp);

        row.addView(avatarWrap);

        // Info
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(this);
        nameView.setText(getUserName());
        nameView.setTextColor(TEXT);
        nameView.setTextSize(18);
        nameView.setTypeface(null, Typeface.BOLD);
        info.addView(nameView);

        TextView handleView = new TextView(this);
        handleView.setText(getUserHandle());
        handleView.setTextColor(SUB);
        handleView.setTextSize(12);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(2);
        handleView.setLayoutParams(hlp);
        info.addView(handleView);

        // Badge
        TextView badge = new TextView(this);
        badge.setText("✦ YT Pro Member");
        badge.setTextColor(RED);
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(dp(20));
        badgeBg.setColor(Color.parseColor("#1AFF0033"));
        badgeBg.setStroke(dp(1), Color.parseColor("#44FF0033"));
        badge.setBackground(badgeBg);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        badge.setLayoutParams(blp);
        info.addView(badge);

        row.addView(info);
        section.addView(row);
        rootLayout.addView(section);
    }

    // ═══════════════════════════════════════════════════
    //  STATS ROW
    // ═══════════════════════════════════════════════════
    private void addStatsRow() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(16), dp(20), dp(16), 0);
        container.setLayoutParams(clp);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(SURFACE);
        bg.setStroke(dp(1), BORDER);
        container.setBackground(bg);
        container.setClipToOutline(true);

        String[][] stats = {{"24K","Subscribers"},{"1.2K","Watch Later"},{"348","Liked"}};

        for (int i = 0; i < stats.length; i++) {
            if (i > 0) {
                View d = new View(this);
                d.setBackgroundColor(BORDER);
                container.addView(d, new LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT));
            }
            LinearLayout stat = new LinearLayout(this);
            stat.setOrientation(LinearLayout.VERTICAL);
            stat.setGravity(Gravity.CENTER);
            stat.setPadding(dp(8), dp(16), dp(8), dp(16));
            stat.setLayoutParams(new LinearLayout.LayoutParams(0, dp(80), 1f));

            TextView num = new TextView(this);
            num.setText(stats[i][0]);
            num.setTextColor(TEXT);
            num.setTextSize(20);
            num.setTypeface(null, Typeface.BOLD);
            num.setGravity(Gravity.CENTER);
            stat.addView(num);

            TextView label = new TextView(this);
            label.setText(stats[i][1]);
            label.setTextColor(SUB);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(3);
            label.setLayoutParams(llp);
            stat.addView(label);
            container.addView(stat);
        }
        rootLayout.addView(container);
    }

    // ═══════════════════════════════════════════════════
    //  MANAGE ACCOUNT
    // ═══════════════════════════════════════════════════
    private void addManageAccountButton() {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(16), dp(12), dp(16), dp(4));
        btn.setLayoutParams(blp);
        btn.setPadding(dp(14), dp(14), dp(14), dp(14));
        btn.setBackground(makeRoundedBorder(SURFACE, BORDER, dp(14)));
        btn.setClickable(true);

        TextView icon = new TextView(this);
        icon.setText("👤");
        icon.setTextSize(18);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(dp(10));
        iconBg.setColor(Color.parseColor("#1AFF0033"));
        icon.setBackground(iconBg);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(38), dp(38));
        ilp.rightMargin = dp(12);
        icon.setLayoutParams(ilp);
        btn.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText("Manage Google Account");
        t.setTextColor(TEXT);
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        textCol.addView(t);

        TextView s = new TextView(this);
        s.setText("Privacy, data & security");
        s.setTextColor(SUB);
        s.setTextSize(12);
        textCol.addView(s);

        btn.addView(textCol);

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextColor(MUTED);
        chevron.setTextSize(20);
        btn.addView(chevron);

        btn.setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com"))));

        rootLayout.addView(btn);
    }

    // ═══════════════════════════════════════════════════
    //  HISTORY SCROLL
    // ═══════════════════════════════════════════════════
    private void addHistoryScroll() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(20);
        hsv.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(4));

        String[][] items = {
            {"🎵","#1A0A2E","Lo-fi Hip Hop Beats to Study & Relax","Lofi Girl · 2h ago","4:32","65"},
            {"💻","#0D2137","Android Studio Tutorial for Beginners 2024","Coding With Tea · Yesterday","18:45","30"},
            {"🌿","#1A2E0A","Sri Lanka Travel Vlog 2024","Travel SL · 2 days ago","12:20","100"},
            {"🎮","#2E0A0A","PUBG Mobile Pro Tips — Season 30","GamersLK · 3 days ago","32:10","15"},
        };

        for (String[] item : items)
            row.addView(buildHistoryCard(item[0],item[1],item[2],item[3],item[4],Integer.parseInt(item[5])));

        hsv.addView(row);
        rootLayout.addView(hsv);
    }

    private View buildHistoryCard(String emoji, String color, String title,
                                   String channel, String duration, int progress) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(180), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(12);
        card.setLayoutParams(clp);

        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(180), dp(101));
        tlp.bottomMargin = dp(8);
        thumb.setLayoutParams(tlp);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setShape(GradientDrawable.RECTANGLE);
        thumbBg.setCornerRadius(dp(10));
        thumbBg.setColor(Color.parseColor(color));
        thumb.setBackground(thumbBg);
        thumb.setClipToOutline(true);

        TextView emojiView = new TextView(this);
        emojiView.setText(emoji);
        emojiView.setTextSize(28);
        emojiView.setGravity(Gravity.CENTER);
        thumb.addView(emojiView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView durBadge = new TextView(this);
        durBadge.setText(duration);
        durBadge.setTextColor(Color.WHITE);
        durBadge.setTextSize(9);
        durBadge.setPadding(dp(4), dp(2), dp(4), dp(2));
        GradientDrawable durBg = new GradientDrawable();
        durBg.setShape(GradientDrawable.RECTANGLE);
        durBg.setCornerRadius(dp(3));
        durBg.setColor(Color.parseColor("#CC000000"));
        durBadge.setBackground(durBg);
        FrameLayout.LayoutParams durlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        durlp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        durlp.bottomMargin = dp(5);
        durlp.rightMargin  = dp(5);
        durBadge.setLayoutParams(durlp);
        thumb.addView(durBadge);

        FrameLayout progressBar = new FrameLayout(this);
        FrameLayout.LayoutParams pblp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(3));
        pblp.gravity = Gravity.BOTTOM;
        progressBar.setLayoutParams(pblp);
        progressBar.setBackgroundColor(Color.parseColor("#44FFFFFF"));

        View fill = new View(this);
        fill.setBackgroundColor(RED);
        int prog = progress;
        fill.post(() -> {
            ViewGroup.LayoutParams vlp = fill.getLayoutParams();
            vlp.width = (int)(progressBar.getWidth() * prog / 100f);
            fill.setLayoutParams(vlp);
        });
        progressBar.addView(fill, new FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT));
        thumb.addView(progressBar);
        card.addView(thumb);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(TEXT);
        titleView.setTextSize(12);
        titleView.setMaxLines(2);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(titleView);

        TextView chView = new TextView(this);
        chView.setText(channel);
        chView.setTextColor(SUB);
        chView.setTextSize(11);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chlp.topMargin = dp(2);
        chView.setLayoutParams(chlp);
        card.addView(chView);

        return card;
    }

    // ═══════════════════════════════════════════════════
    //  PLAYLISTS
    // ═══════════════════════════════════════════════════
    private void addPlaylistScroll() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        hsv.setLayoutParams(lp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), 0, dp(16), dp(4));

        String[][] pls = {
            {"🎵","#1A0A2E,#6E1A8A","Chill Vibes","24 videos"},
            {"💻","#0A1A2E,#1A5A8A","Dev Tutorials","18 videos"},
            {"🎬","#2E1A0A,#8A5A1A","Watch Later","7 videos"},
            {"❤️","#0A2E0A,#1A8A3A","Liked Videos","348 videos"},
        };

        for (String[] pl : pls) row.addView(buildPlaylistCard(pl[0],pl[1],pl[2],pl[3]));
        hsv.addView(row);
        rootLayout.addView(hsv);
    }

    private View buildPlaylistCard(String emoji, String colors, String name, String count) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.rightMargin = dp(12);
        card.setLayoutParams(clp);

        FrameLayout thumb = new FrameLayout(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(150), dp(150));
        tlp.bottomMargin = dp(8);
        thumb.setLayoutParams(tlp);
        String[] ca = colors.split(",");
        GradientDrawable tbg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.parseColor(ca[0]), Color.parseColor(ca[1])});
        tbg.setCornerRadius(dp(12));
        thumb.setBackground(tbg);
        thumb.setClipToOutline(true);

        TextView emojiView = new TextView(this);
        emojiView.setText(emoji);
        emojiView.setTextSize(36);
        emojiView.setGravity(Gravity.CENTER);
        thumb.addView(emojiView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView countView = new TextView(this);
        countView.setText(count);
        countView.setTextColor(Color.WHITE);
        countView.setTextSize(10);
        countView.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams cvlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cvlp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        cvlp.bottomMargin = dp(8);
        cvlp.rightMargin  = dp(8);
        countView.setLayoutParams(cvlp);
        thumb.addView(countView);
        card.addView(thumb);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(TEXT);
        nameView.setTextSize(13);
        nameView.setTypeface(null, Typeface.BOLD);
        card.addView(nameView);

        return card;
    }

    // ═══════════════════════════════════════════════════
    //  FEATURES MENU (Toggles)
    // ═══════════════════════════════════════════════════
    private void addFeaturesMenu() {
        LinearLayout menu = makeMenuContainer();

        menu.addView(buildToggleRow("🎵","#1AFF0033","Background Play","Screen off ව්ව‍ත් play කරයි",bgPlayEnabled,
            checked -> { bgPlayEnabled=checked; prefs.edit().putBoolean("bgplay",checked).apply(); }));
        menu.addView(menuDivider());

        menu.addView(buildToggleRow("📱","#1400F2EA","Picture in Picture","Mini window mode",pipEnabled,
            checked -> pipEnabled=checked));
        menu.addView(menuDivider());

        menu.addView(buildBadgeToggleRow("🔔","#1AFFA500","Push Notifications","New videos & updates","3 NEW",notifEnabled,
            checked -> notifEnabled=checked));
        menu.addView(menuDivider());

        menu.addView(buildToggleRow("⏭️","#1400D26A","Auto-skip Sponsors","Sponsor segments skip කරයි",autoSkipSponsors,
            checked -> { autoSkipSponsors=checked; prefs.edit().putString("autoSpn",checked?"true":"false").apply(); }));
        menu.addView(menuDivider());

        menu.addView(buildToggleRow("👋","#1A4285F4","Gesture Controls","Volume & brightness swipe",gestureControls,
            checked -> { gestureControls=checked; prefs.edit().putString("gesC",checked?"true":"false").apply(); }));
        menu.addView(menuDivider());

        menu.addView(buildToggleRow("🚫","#1A64C864","Hide Shorts","Shorts feed hide කරයි",shortsBlocked,
            checked -> { shortsBlocked=checked; prefs.edit().putString("shorts",checked?"true":"false").apply(); }));

        rootLayout.addView(menu);
    }

    // ═══════════════════════════════════════════════════
    //  GENERAL MENU
    // ═══════════════════════════════════════════════════
    private void addGeneralMenu() {
        LinearLayout menu = makeMenuContainer();
        String[][] items = {
            {"⬇️","#0DFFFFFF","Downloads","Offline videos (12 files)"},
            {"📊","#0DFFFFFF","Watch Time Stats","Today: 2h 34m"},
            {"🔒","#0DFFFFFF","Privacy & Security","Clear history, data"},
            {"🌙","#0DFFFFFF","Appearance","Dark · AMOLED · Light"},
            {"🌐","#0DFFFFFF","Language & Region","Sinhala · English"},
            {"🤖","#0D4285F4","Gemini AI","Ask about videos"},
            {"🔄","#0D00D26A","Check for Updates","Current: v2.1 ✓"},
            {"🐛","#0DFF6B6B","Report a Bug","spmodsofficial@gmail.com"},
        };
        for (int i = 0; i < items.length; i++) {
            View row = buildNavRow(items[i][0],items[i][1],items[i][2],items[i][3]);
            menu.addView(row);
            if (i < items.length-1) menu.addView(menuDivider());
            final int idx = i;
            row.setOnClickListener(v -> {
                switch (idx) {
                    case 6: {
                        Intent i2 = new Intent(this, MainActivity.class);
                        i2.putExtra("url","https://m.youtube.com/#settings");
                        startActivity(i2); finish(); break;
                    }
                    case 7:
                        startActivity(new Intent(Intent.ACTION_SENDTO,
                            Uri.parse("mailto:spmodsofficial@gmail.com"))); break;
                    default:
                        Toast.makeText(this, items[idx][2]+" — coming soon!",Toast.LENGTH_SHORT).show();
                }
            });
        }
        rootLayout.addView(menu);
    }

    // ═══════════════════════════════════════════════════
    //  SIGN OUT
    // ═══════════════════════════════════════════════════
    private void addSignOutButton() {
        TextView btn = new TextView(this);
        btn.setText("⏻   Sign Out");
        btn.setTextColor(RED);
        btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.TRANSPARENT);
        bg.setStroke(dp(1), Color.parseColor("#44FF0033"));
        btn.setBackground(bg);
        btn.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(16), dp(16), dp(8));
        btn.setLayoutParams(lp);
        btn.setClickable(true);

        btn.setOnClickListener(v ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (d, w) -> {
                    // ✅ Cookies clear
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    // ✅ Saved user info clear
                    prefs.edit().remove("yt_username").remove("yt_email").apply();
                    // ✅ Guest UI show කරන්න recreate
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show()
        );

        rootLayout.addView(btn);
    }

    private void addVersionText() {
        TextView ver = new TextView(this);
        ver.setText("YT Pro v2.1 · Not affiliated with YouTube™");
        ver.setTextColor(MUTED);
        ver.setTextSize(10);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        ver.setLayoutParams(lp);
        rootLayout.addView(ver);
    }

    // ═══════════════════════════════════════════════════
    //  HEADER
    // ═══════════════════════════════════════════════════
    private View buildHeader() {
        android.widget.RelativeLayout header = new android.widget.RelativeLayout(this);
        header.setBackgroundColor(Color.parseColor("#EE0F0F0F"));
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(56));
        lp.gravity = Gravity.TOP;
        header.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText("You");
        title.setTextColor(TEXT);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        android.widget.RelativeLayout.LayoutParams tlp =
            new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        tlp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_LEFT);
        tlp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        header.addView(title, tlp);

        TextView settingsBtn = makeIconButton("⚙️");
        settingsBtn.setId(View.generateViewId());
        android.widget.RelativeLayout.LayoutParams slp =
            new android.widget.RelativeLayout.LayoutParams(dp(38), dp(38));
        slp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_RIGHT);
        slp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        header.addView(settingsBtn, slp);
        settingsBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("url", "https://m.youtube.com/#settings");
            startActivity(i); finish();
        });

        TextView searchBtn = makeIconButton("🔍");
        searchBtn.setId(View.generateViewId());
        android.widget.RelativeLayout.LayoutParams sblp =
            new android.widget.RelativeLayout.LayoutParams(dp(38), dp(38));
        sblp.addRule(android.widget.RelativeLayout.LEFT_OF, settingsBtn.getId());
        sblp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
        sblp.rightMargin = dp(8);
        header.addView(searchBtn, sblp);
        searchBtn.setOnClickListener(v -> { startActivity(new Intent(this, MainActivity.class)); finish(); });

        View border = new View(this);
        border.setBackgroundColor(BORDER);
        FrameLayout.LayoutParams blp2 = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(1));
        blp2.gravity = Gravity.BOTTOM;
        header.addView(border, blp2);

        return header;
    }

    private TextView makeIconButton(String emoji) {
        TextView btn = new TextView(this);
        btn.setText(emoji);
        btn.setTextSize(16);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(19));
        bg.setColor(SURFACE2);
        btn.setBackground(bg);
        return btn;
    }

    // ═══════════════════════════════════════════════════
    //  BOTTOM NAV
    // ═══════════════════════════════════════════════════
    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(Color.parseColor("#F20A0A0A"));
        FrameLayout.LayoutParams navlp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(70));
        navlp.gravity = Gravity.BOTTOM;
        nav.setLayoutParams(navlp);
        nav.setPadding(dp(8), 0, dp(8), 0);

        String[][] navItems = {{"🏠","Home"},{"⚡","Shorts"},{"➕",""},{"📋","Subs"},{"👤","You"}};

        for (int i = 0; i < navItems.length; i++) {
            final int idx = i;
            if (i == 2) {
                FrameLayout wrap = new FrameLayout(this);
                wrap.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                TextView upload = new TextView(this);
                upload.setText("➕");
                upload.setTextSize(20);
                upload.setGravity(Gravity.CENTER);
                GradientDrawable upBg = new GradientDrawable();
                upBg.setShape(GradientDrawable.RECTANGLE);
                upBg.setCornerRadius(dp(14));
                upBg.setColor(RED);
                upload.setBackground(upBg);
                FrameLayout.LayoutParams uplp = new FrameLayout.LayoutParams(dp(48), dp(48));
                uplp.gravity = Gravity.CENTER;
                upload.setLayoutParams(uplp);
                upload.setOnClickListener(v -> Toast.makeText(this,"Upload coming soon! 🎥",Toast.LENGTH_SHORT).show());
                wrap.addView(upload);
                nav.addView(wrap);
                continue;
            }

            boolean isActive = (i == 4);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            item.setClickable(true);

            TextView icon = new TextView(this);
            icon.setText(navItems[i][0]);
            icon.setTextSize(20);
            icon.setGravity(Gravity.CENTER);
            item.addView(icon);

            TextView label = new TextView(this);
            label.setText(navItems[i][1]);
            label.setTextColor(isActive ? RED : SUB);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            item.addView(label);

            if (isActive) {
                View dot = new View(this);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(RED);
                dot.setBackground(dotBg);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(4), dp(4));
                dlp.gravity = Gravity.CENTER_HORIZONTAL;
                dlp.topMargin = -dp(2);
                dot.setLayoutParams(dlp);
                item.addView(dot);
            }

            item.setOnClickListener(v -> {
                if (idx == 4) return;
                Intent intent = new Intent(this, MainActivity.class);
                switch (idx) {
                    case 0: intent.putExtra("url","https://m.youtube.com/"); break;
                    case 1: intent.putExtra("url","https://m.youtube.com/shorts"); break;
                    case 3: intent.putExtra("url","https://m.youtube.com/feed/subscriptions"); break;
                }
                startActivity(intent);
                finish();
            });

            nav.addView(item);
        }
        return nav;
    }

    // ═══════════════════════════════════════════════════
    //  TOGGLE HELPERS
    // ═══════════════════════════════════════════════════
    interface ToggleCallback { void onToggle(boolean checked); }

    private View buildToggleRow(String emoji, String iconColor, String title, String desc,
                                 boolean on, ToggleCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView icon = new TextView(this);
        icon.setText(emoji); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.RECTANGLE); ib.setCornerRadius(dp(10));
        ib.setColor(Color.parseColor(iconColor)); icon.setBackground(ib);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc);  dv.setTextColor(SUB);  dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        FrameLayout toggle = buildToggle(on);
        row.addView(toggle);

        boolean[] state = {on};
        row.setOnClickListener(v -> { state[0]=!state[0]; updateToggle(toggle,state[0]); cb.onToggle(state[0]); });
        return row;
    }

    private View buildBadgeToggleRow(String emoji, String iconColor, String title, String desc,
                                      String badge, boolean on, ToggleCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView icon = new TextView(this);
        icon.setText(emoji); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.RECTANGLE); ib.setCornerRadius(dp(10));
        ib.setColor(Color.parseColor(iconColor)); icon.setBackground(ib);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc);  dv.setTextColor(SUB);  dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        TextView badgeView = new TextView(this);
        badgeView.setText(badge); badgeView.setTextColor(Color.WHITE);
        badgeView.setTextSize(10); badgeView.setTypeface(null, Typeface.BOLD);
        GradientDrawable bb = new GradientDrawable();
        bb.setShape(GradientDrawable.RECTANGLE); bb.setCornerRadius(dp(10)); bb.setColor(RED);
        badgeView.setBackground(bb); badgeView.setPadding(dp(6),dp(2),dp(6),dp(2));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.rightMargin = dp(8); badgeView.setLayoutParams(blp); row.addView(badgeView);

        FrameLayout toggle = buildToggle(on);
        row.addView(toggle);

        boolean[] state = {on};
        row.setOnClickListener(v -> { state[0]=!state[0]; updateToggle(toggle,state[0]); cb.onToggle(state[0]); });
        return row;
    }

    private View buildNavRow(String emoji, String iconColor, String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setClickable(true); row.setFocusable(true);

        TextView icon = new TextView(this);
        icon.setText(emoji); icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.RECTANGLE); ib.setCornerRadius(dp(10));
        ib.setColor(Color.parseColor(iconColor)); icon.setBackground(ib);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(40), dp(40));
        ilp.rightMargin = dp(12); icon.setLayoutParams(ilp); row.addView(icon);

        LinearLayout tc = new LinearLayout(this);
        tc.setOrientation(LinearLayout.VERTICAL);
        tc.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tv = new TextView(this); tv.setText(title); tv.setTextColor(TEXT); tv.setTextSize(14); tc.addView(tv);
        TextView dv = new TextView(this); dv.setText(desc);  dv.setTextColor(SUB);  dv.setTextSize(12); tc.addView(dv);
        row.addView(tc);

        TextView ch = new TextView(this); ch.setText("›"); ch.setTextColor(MUTED); ch.setTextSize(20); row.addView(ch);
        return row;
    }

    private FrameLayout buildToggle(boolean on) {
        FrameLayout toggle = new FrameLayout(this);
        toggle.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(24)));
        GradientDrawable t = new GradientDrawable();
        t.setShape(GradientDrawable.RECTANGLE); t.setCornerRadius(dp(12));
        t.setColor(on ? RED : SURFACE2); t.setStroke(dp(1), on ? RED : BORDER);
        toggle.setBackground(t);
        View knob = new View(this);
        GradientDrawable k = new GradientDrawable(); k.setShape(GradientDrawable.OVAL); k.setColor(Color.WHITE);
        knob.setBackground(k);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(dp(18), dp(18));
        klp.gravity = Gravity.CENTER_VERTICAL; klp.leftMargin = on ? dp(21) : dp(3);
        knob.setLayoutParams(klp); toggle.addView(knob);
        return toggle;
    }

    private void updateToggle(FrameLayout toggle, boolean on) {
        GradientDrawable t = new GradientDrawable();
        t.setShape(GradientDrawable.RECTANGLE); t.setCornerRadius(dp(12));
        t.setColor(on ? RED : SURFACE2); t.setStroke(dp(1), on ? RED : BORDER);
        toggle.setBackground(t);
        View knob = toggle.getChildAt(0);
        FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(dp(18), dp(18));
        klp.gravity = Gravity.CENTER_VERTICAL; klp.leftMargin = on ? dp(21) : dp(3);
        knob.setLayoutParams(klp);
    }

    // ═══════════════════════════════════════════════════
    //  MISC HELPERS
    // ═══════════════════════════════════════════════════
    private void addSectionLabel(String label, boolean showSeeAll) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(dp(16), dp(20), dp(16), dp(10));
        row.setLayoutParams(rlp);

        TextView t = new TextView(this);
        t.setText(label); t.setTextColor(TEXT); t.setTextSize(16); t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);

        if (showSeeAll) {
            TextView sa = new TextView(this);
            sa.setText("See all"); sa.setTextColor(RED); sa.setTextSize(13);
            sa.setTypeface(null, Typeface.BOLD);
            sa.setOnClickListener(v -> Toast.makeText(this,"See all — coming soon!",Toast.LENGTH_SHORT).show());
            row.addView(sa);
        }
        rootLayout.addView(row);
    }

    private LinearLayout makeMenuContainer() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(dp(16), 0, dp(16), dp(20));
        menu.setLayoutParams(mlp);
        menu.setBackground(makeRoundedBorder(SURFACE, BORDER, dp(16)));
        menu.setClipToOutline(true);
        return menu;
    }

    private View menuDivider() {
        View d = new View(this);
        d.setBackgroundColor(BORDER);
        d.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return d;
    }

    private void addDividerThick() {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#1A000000"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        lp.topMargin = dp(8);
        d.setLayoutParams(lp);
        rootLayout.addView(d);
    }

    private android.graphics.drawable.Drawable makeRoundedBorder(int color, int border, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radius);
        gd.setColor(color);
        gd.setStroke(dp(1), border);
        return gd;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
