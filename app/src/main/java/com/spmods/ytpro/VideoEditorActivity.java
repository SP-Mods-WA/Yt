package com.spmods.ytpro;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class VideoEditorActivity extends Activity {

    // ─── Colors ───────────────────────────────────────────────────────────────
    private static final int BG_DARK       = 0xFF0A0A0A;
    private static final int BG_PANEL      = 0xFF141414;
    private static final int BG_CARD       = 0xFF1C1C1C;
    private static final int BG_ITEM       = 0xFF242424;
    private static final int ACCENT_RED    = 0xFFFF0033;
    private static final int ACCENT_CYAN   = 0xFF00F2EA;
    private static final int ACCENT_PINK   = 0xFFFF0070;
    private static final int TEXT_WHITE    = 0xFFFFFFFF;
    private static final int TEXT_GRAY     = 0xFF888888;
    private static final int TEXT_LIGHT    = 0xFFCCCCCC;
    private static final int DIVIDER       = 0xFF2A2A2A;

    // ─── UI State ─────────────────────────────────────────────────────────────
    private enum Tool { NONE, TRIM, SPEED, FILTER, TEXT, MUSIC, STICKER, TRANSITIONS, ADJUST, CROP, VOICEOVER, AUDIO }

    private Tool activeTool = Tool.NONE;
    private String selectedVideoPath = null;
    private Uri selectedVideoUri = null;
    private boolean isPlaying = false;
    private boolean isMuted = false;
    private float currentSpeed = 1.0f;
    private float currentVolume = 1.0f;
    private int selectedFilterIndex = 0;
    private String selectedTextContent = "";
    private long videoDurationMs = 0;
    private long trimStartMs = 0;
    private long trimEndMs = 0;

    // ─── Views ────────────────────────────────────────────────────────────────
    private RelativeLayout rootLayout;
    private FrameLayout previewContainer;
    private ImageView previewImageView;
    private View playPauseOverlay;
    private ImageView playPauseIcon;
    private TextView timeDisplay;
    private SeekBar progressBar;

    private HorizontalScrollView timelineScroll;
    private LinearLayout timelineContainer;
    private List<FrameLayout> timelineFrames = new ArrayList<>();

    private LinearLayout toolsPanel;
    private LinearLayout toolOptionsPanel;
    private ScrollView toolOptionsScroll;

    private TextView trimStartLabel, trimEndLabel;
    private SeekBar trimStartBar, trimEndBar;

    private Handler progressHandler = new Handler();
    private Runnable progressRunnable;

    private int dpUnit;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(BG_DARK);

        dpUnit = dp(1);
        buildUI();
        checkIncomingIntent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        if (activeTool != Tool.NONE) {
            closeTool();
        } else {
            showExitDialog();
        }
    }

    // ─── Intent Handling ──────────────────────────────────────────────────────

    private void checkIncomingIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            Uri videoUri = intent.getData();
            if (videoUri == null && intent.hasExtra("video_uri")) {
                videoUri = Uri.parse(intent.getStringExtra("video_uri"));
            }
            if (videoUri != null) {
                loadVideo(videoUri);
            }
        }
    }

    // ─── UI Building ──────────────────────────────────────────────────────────

    private void buildUI() {
        rootLayout = new RelativeLayout(this);
        rootLayout.setBackgroundColor(BG_DARK);
        setContentView(rootLayout);

        buildTopBar();
        buildPreviewArea();
        buildTimeline();
        buildToolsPanel();
        buildToolOptionsPanel();
        buildExportButton();
    }

    private void buildTopBar() {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setId(View.generateViewId());
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(BG_PANEL);
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));

        RelativeLayout.LayoutParams topParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, dp(52));
        topParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        topBar.setLayoutParams(topParams);
        topBar.setElevation(dp(4));

        // Back button
        TextView backBtn = makeTextIconButton("←", dp(36), dp(36));
        backBtn.setTextSize(20);
        backBtn.setOnClickListener(v -> onBackPressed());
        topBar.addView(backBtn);

        // Spacer
        View s1 = new View(this);
        s1.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(s1);

        // Title
        TextView title = new TextView(this);
        title.setText("Video Editor");
        title.setTextColor(TEXT_WHITE);
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setLetterSpacing(0.05f);
        topBar.addView(title);

        View s2 = new View(this);
        s2.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        topBar.addView(s2);

        // Undo
        TextView undoBtn = makeTextIconButton("↩", dp(36), dp(36));
        undoBtn.setOnClickListener(v -> showToast("Undo"));
        topBar.addView(undoBtn);

        // Redo
        TextView redoBtn = makeTextIconButton("↪", dp(36), dp(36));
        redoBtn.setOnClickListener(v -> showToast("Redo"));
        topBar.addView(redoBtn);

        topBar.setTag("topbar");
        rootLayout.addView(topBar);
    }

    private void buildPreviewArea() {
        // Preview container
        previewContainer = new FrameLayout(this);
        previewContainer.setId(View.generateViewId());
        previewContainer.setBackgroundColor(Color.BLACK);

        RelativeLayout.LayoutParams previewParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        previewParams.addRule(RelativeLayout.BELOW, findTopBarId());
        previewParams.setMargins(0, 0, 0, 0);
        previewContainer.setLayoutParams(previewParams);

        // Preview image / video placeholder
        previewImageView = new ImageView(this);
        previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImageView.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(220));
        previewImageView.setLayoutParams(imgParams);
        previewContainer.addView(previewImageView);

        // Placeholder overlay when no video
        buildEmptyPreviewState();

        // Play/Pause overlay
        playPauseOverlay = new View(this);
        playPauseOverlay.setBackgroundColor(0x44000000);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(220));
        playPauseOverlay.setLayoutParams(overlayParams);
        playPauseOverlay.setVisibility(View.GONE);
        previewContainer.addView(playPauseOverlay);

        // Play icon
        playPauseIcon = new ImageView(this);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(56), dp(56));
        iconParams.gravity = Gravity.CENTER;
        playPauseIcon.setLayoutParams(iconParams);
        playPauseIcon.setVisibility(View.GONE);
        setPlayIcon(false);
        previewContainer.addView(playPauseIcon);

        // Tap to play/pause
        previewContainer.setOnClickListener(v -> {
            if (selectedVideoPath != null) togglePlayPause();
        });

        // Time display
        timeDisplay = new TextView(this);
        timeDisplay.setText("00:00 / 00:00");
        timeDisplay.setTextColor(TEXT_GRAY);
        timeDisplay.setTextSize(11);
        FrameLayout.LayoutParams timeParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        timeParams.gravity = Gravity.BOTTOM | Gravity.END;
        timeParams.setMargins(0, 0, dp(8), dp(6));
        timeDisplay.setLayoutParams(timeParams);
        previewContainer.addView(timeDisplay);

        // Progress bar
        progressBar = new SeekBar(this);
        progressBar.setProgressTintList(
            android.content.res.ColorStateList.valueOf(ACCENT_RED));
        progressBar.setThumbTintList(
            android.content.res.ColorStateList.valueOf(ACCENT_RED));
        FrameLayout.LayoutParams pbParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        pbParams.gravity = Gravity.BOTTOM;
        progressBar.setLayoutParams(pbParams);
        previewContainer.addView(progressBar);

        rootLayout.addView(previewContainer);
    }

    private void buildEmptyPreviewState() {
        LinearLayout emptyView = new LinearLayout(this);
        emptyView.setId(View.generateViewId());
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(220));
        emptyView.setLayoutParams(ep);

        TextView plusIcon = new TextView(this);
        plusIcon.setText("🎬");
        plusIcon.setTextSize(44);
        plusIcon.setGravity(Gravity.CENTER);
        emptyView.addView(plusIcon);

        TextView label = new TextView(this);
        label.setText("Tap to import video");
        label.setTextColor(TEXT_GRAY);
        label.setTextSize(14);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        label.setLayoutParams(lp);
        emptyView.addView(label);

        emptyView.setOnClickListener(v -> openMediaPicker());
        emptyView.setTag("empty_preview");
        previewContainer.addView(emptyView);
    }

    private void buildTimeline() {
        // Timeline wrapper
        LinearLayout timelineWrapper = new LinearLayout(this);
        timelineWrapper.setId(View.generateViewId());
        timelineWrapper.setOrientation(LinearLayout.VERTICAL);
        timelineWrapper.setBackgroundColor(BG_PANEL);
        timelineWrapper.setPadding(0, dp(8), 0, dp(4));

        RelativeLayout.LayoutParams tlWrapParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, dp(90));
        tlWrapParams.addRule(RelativeLayout.BELOW, previewContainer.getId());
        timelineWrapper.setLayoutParams(tlWrapParams);
        timelineWrapper.setTag("timeline_wrapper");

        // Timeline label + add clip button
        LinearLayout tlHeader = new LinearLayout(this);
        tlHeader.setOrientation(LinearLayout.HORIZONTAL);
        tlHeader.setGravity(Gravity.CENTER_VERTICAL);
        tlHeader.setPadding(dp(12), 0, dp(12), dp(4));

        TextView tlLabel = new TextView(this);
        tlLabel.setText("TIMELINE");
        tlLabel.setTextColor(TEXT_GRAY);
        tlLabel.setTextSize(10);
        tlLabel.setTypeface(null, Typeface.BOLD);
        tlLabel.setLetterSpacing(0.1f);
        tlHeader.addView(tlLabel);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        tlHeader.addView(spacer);

        TextView addBtn = new TextView(this);
        addBtn.setText("+ Add Clip");
        addBtn.setTextColor(ACCENT_CYAN);
        addBtn.setTextSize(12);
        addBtn.setOnClickListener(v -> openMediaPicker());
        tlHeader.addView(addBtn);

        timelineWrapper.addView(tlHeader);

        // Horizontal scrolling timeline
        timelineScroll = new HorizontalScrollView(this);
        timelineScroll.setHorizontalScrollBarEnabled(false);
        timelineScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        timelineContainer = new LinearLayout(this);
        timelineContainer.setOrientation(LinearLayout.HORIZONTAL);
        timelineContainer.setGravity(Gravity.CENTER_VERTICAL);
        timelineContainer.setPadding(dp(16), 0, dp(16), 0);

        // Default empty frame
        addTimelineAddFrame();

        timelineScroll.addView(timelineContainer);
        timelineWrapper.addView(timelineScroll);
        rootLayout.addView(timelineWrapper);
    }

    private void addTimelineAddFrame() {
        FrameLayout addFrame = new FrameLayout(this);
        addFrame.setBackgroundColor(BG_CARD);
        GradientDrawable border = new GradientDrawable();
        border.setShape(GradientDrawable.RECTANGLE);
        border.setColor(BG_CARD);
        border.setStroke(dp(1), DIVIDER);
        border.setCornerRadius(dp(6));
        addFrame.setBackground(border);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(56), dp(56));
        fp.setMargins(dp(4), 0, dp(4), 0);
        addFrame.setLayoutParams(fp);

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(TEXT_GRAY);
        plus.setTextSize(22);
        plus.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        plus.setLayoutParams(pp);
        addFrame.addView(plus);
        addFrame.setOnClickListener(v -> openMediaPicker());
        timelineContainer.addView(addFrame);
    }

    private void buildToolsPanel() {
        // Main editing tools
        ScrollView toolsScroll = new ScrollView(this);
        toolsScroll.setId(View.generateViewId());
        toolsScroll.setTag("tools_scroll");
        toolsScroll.setBackgroundColor(BG_PANEL);
        toolsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        RelativeLayout.LayoutParams toolsScrollParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        toolsScrollParams.addRule(RelativeLayout.ABOVE, getExportBarTagId());
        toolsScrollParams.addRule(RelativeLayout.BELOW, getTimelineTagId());
        toolsScroll.setLayoutParams(toolsScrollParams);
        toolsScroll.setTag("tools_scroll");

        toolsPanel = new LinearLayout(this);
        toolsPanel.setOrientation(LinearLayout.VERTICAL);
        toolsPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        toolsPanel.setBackgroundColor(BG_PANEL);

        // Row 1 of tools
        LinearLayout row1 = buildToolRow(new String[][]{
            {"✂️", "Trim"},
            {"⚡", "Speed"},
            {"🎨", "Filter"},
            {"📝", "Text"},
        }, new Tool[]{Tool.TRIM, Tool.SPEED, Tool.FILTER, Tool.TEXT});

        // Row 2 of tools
        LinearLayout row2 = buildToolRow(new String[][]{
            {"🎵", "Music"},
            {"😊", "Sticker"},
            {"🔀", "Transitions"},
            {"🎛️", "Adjust"},
        }, new Tool[]{Tool.MUSIC, Tool.STICKER, Tool.TRANSITIONS, Tool.ADJUST});

        // Row 3 of tools
        LinearLayout row3 = buildToolRow(new String[][]{
            {"📐", "Crop"},
            {"🎙️", "Voiceover"},
            {"🔊", "Audio"},
            {"📤", "Export"},
        }, new Tool[]{Tool.CROP, Tool.VOICEOVER, Tool.AUDIO, Tool.NONE});

        toolsPanel.addView(row1);
        toolsPanel.addView(makeDivider());
        toolsPanel.addView(row2);
        toolsPanel.addView(makeDivider());
        toolsPanel.addView(row3);

        toolsScroll.addView(toolsPanel);
        rootLayout.addView(toolsScroll);
    }

    private LinearLayout buildToolRow(String[][] items, Tool[] tools) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < items.length; i++) {
            final String emoji = items[i][0];
            final String label = items[i][1];
            final Tool tool = tools[i];
            final int idx = i;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                0, dp(72), 1f);
            itemParams.setMargins(dp(4), dp(4), dp(4), dp(4));
            item.setLayoutParams(itemParams);
            item.setPadding(0, dp(8), 0, dp(8));

            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setShape(GradientDrawable.RECTANGLE);
            itemBg.setColor(BG_CARD);
            itemBg.setCornerRadius(dp(10));
            item.setBackground(itemBg);

            TextView emojiView = new TextView(this);
            emojiView.setText(emoji);
            emojiView.setTextSize(22);
            emojiView.setGravity(Gravity.CENTER);
            item.addView(emojiView);

            TextView labelView = new TextView(this);
            labelView.setText(label);
            labelView.setTextColor(TEXT_LIGHT);
            labelView.setTextSize(10);
            labelView.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(4);
            labelView.setLayoutParams(lp);
            item.addView(labelView);

            item.setOnClickListener(v -> {
                animateToolTap(item);
                if (label.equals("Export")) {
                    startExport();
                } else {
                    openTool(tool, label);
                }
            });

            item.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    item.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start();
                } else if (e.getAction() == MotionEvent.ACTION_UP ||
                           e.getAction() == MotionEvent.ACTION_CANCEL) {
                    item.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                }
                return false;
            });

            row.addView(item);
        }
        return row;
    }

    private void buildToolOptionsPanel() {
        toolOptionsScroll = new ScrollView(this);
        toolOptionsScroll.setId(View.generateViewId());
        toolOptionsScroll.setVisibility(View.GONE);
        toolOptionsScroll.setBackgroundColor(BG_DARK);
        toolOptionsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        toolOptionsScroll.setTag("tool_options");

        RelativeLayout.LayoutParams topParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT);
        topParams.addRule(RelativeLayout.ABOVE, getExportBarTagId());
        topParams.addRule(RelativeLayout.BELOW, getTimelineTagId());
        toolOptionsScroll.setLayoutParams(topParams);

        toolOptionsPanel = new LinearLayout(this);
        toolOptionsPanel.setOrientation(LinearLayout.VERTICAL);
        toolOptionsPanel.setPadding(dp(16), dp(12), dp(16), dp(16));
        toolOptionsScroll.addView(toolOptionsPanel);
        rootLayout.addView(toolOptionsScroll);
    }

    private void buildExportButton() {
        // Export/bottom action bar
        LinearLayout exportBar = new LinearLayout(this);
        exportBar.setId(0x7F0B0001);
        exportBar.setTag("export_bar");
        exportBar.setOrientation(LinearLayout.HORIZONTAL);
        exportBar.setGravity(Gravity.CENTER_VERTICAL);
        exportBar.setBackgroundColor(BG_PANEL);
        exportBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        exportBar.setElevation(dp(8));

        RelativeLayout.LayoutParams exportParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, dp(60));
        exportParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        exportBar.setLayoutParams(exportParams);

        // Draft button
        TextView draftBtn = new TextView(this);
        draftBtn.setText("Save Draft");
        draftBtn.setTextColor(TEXT_GRAY);
        draftBtn.setTextSize(14);
        draftBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        draftBtn.setOnClickListener(v -> showToast("Draft saved!"));
        exportBar.addView(draftBtn);

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        exportBar.addView(sp);

        // Export button
        TextView exportBtn = new TextView(this);
        exportBtn.setText("Export Video  →");
        exportBtn.setTextColor(TEXT_WHITE);
        exportBtn.setTextSize(14);
        exportBtn.setTypeface(null, Typeface.BOLD);
        exportBtn.setPadding(dp(20), dp(10), dp(20), dp(10));

        GradientDrawable exportBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ACCENT_RED, ACCENT_PINK});
        exportBg.setCornerRadius(dp(24));
        exportBtn.setBackground(exportBg);
        exportBtn.setOnClickListener(v -> startExport());
        exportBar.addView(exportBtn);

        rootLayout.addView(exportBar);
    }

    // ─── Tool System ──────────────────────────────────────────────────────────

    private void openTool(Tool tool, String toolName) {
        activeTool = tool;
        toolOptionsPanel.removeAllViews();

        // Show tool panel, hide tools panel
        View toolsScroll = rootLayout.findViewWithTag("tools_scroll");
        if (toolsScroll != null) toolsScroll.setVisibility(View.GONE);
        toolOptionsScroll.setVisibility(View.VISIBLE);

        // Tool header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = dp(16);
        header.setLayoutParams(hp);

        TextView toolTitle = new TextView(this);
        toolTitle.setText(toolName);
        toolTitle.setTextColor(TEXT_WHITE);
        toolTitle.setTextSize(18);
        toolTitle.setTypeface(null, Typeface.BOLD);
        header.addView(toolTitle);

        View hsp = new View(this);
        hsp.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(hsp);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕  Done");
        closeBtn.setTextColor(ACCENT_CYAN);
        closeBtn.setTextSize(13);
        closeBtn.setOnClickListener(v -> closeTool());
        header.addView(closeBtn);

        toolOptionsPanel.addView(header);
        toolOptionsPanel.addView(makeDivider());

        // Build tool-specific content
        switch (tool) {
            case TRIM:       buildTrimTool();        break;
            case SPEED:      buildSpeedTool();       break;
            case FILTER:     buildFilterTool();      break;
            case TEXT:       buildTextTool();        break;
            case MUSIC:      buildMusicTool();       break;
            case STICKER:    buildStickerTool();     break;
            case TRANSITIONS:buildTransitionsTool(); break;
            case ADJUST:     buildAdjustTool();      break;
            case CROP:       buildCropTool();        break;
            case VOICEOVER:  buildVoiceoverTool();   break;
            case AUDIO:      buildAudioTool();       break;
        }

        // Animate in
        toolOptionsScroll.setTranslationY(200);
        toolOptionsScroll.setAlpha(0);
        toolOptionsScroll.animate().translationY(0).alpha(1f).setDuration(250)
            .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeTool() {
        activeTool = Tool.NONE;
        toolOptionsScroll.animate().translationY(100).alpha(0)
            .setDuration(200).withEndAction(() -> {
                toolOptionsScroll.setVisibility(View.GONE);
                View toolsScroll = rootLayout.findViewWithTag("tools_scroll");
                if (toolsScroll != null) {
                    toolsScroll.setVisibility(View.VISIBLE);
                    toolsScroll.setAlpha(0);
                    toolsScroll.animate().alpha(1f).setDuration(200).start();
                }
            }).start();
    }

    // ─── Trim Tool ────────────────────────────────────────────────────────────

    private void buildTrimTool() {
        TextView info = sectionLabel("Set in/out points for your clip");
        toolOptionsPanel.addView(info);

        // Trim start
        toolOptionsPanel.addView(makePadding(dp(12)));
        trimStartLabel = new TextView(this);
        trimStartLabel.setText("Start: 0:00");
        trimStartLabel.setTextColor(TEXT_LIGHT);
        trimStartLabel.setTextSize(13);
        toolOptionsPanel.addView(trimStartLabel);

        trimStartBar = makeSeekBar(100);
        trimStartBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                trimStartMs = (long)(p / 100.0 * videoDurationMs);
                trimStartLabel.setText("Start: " + formatTime(trimStartMs));
                if (trimStartBar.getProgress() >= trimEndBar.getProgress()) {
                    trimStartBar.setProgress(trimEndBar.getProgress() - 1);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        toolOptionsPanel.addView(trimStartBar);

        // Trim end
        toolOptionsPanel.addView(makePadding(dp(12)));
        trimEndLabel = new TextView(this);
        trimEndLabel.setText("End: " + formatTime(videoDurationMs));
        trimEndLabel.setTextColor(TEXT_LIGHT);
        trimEndLabel.setTextSize(13);
        toolOptionsPanel.addView(trimEndLabel);

        trimEndBar = makeSeekBar(100);
        trimEndBar.setProgress(100);
        trimEndBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                trimEndMs = (long)(p / 100.0 * videoDurationMs);
                trimEndLabel.setText("End: " + formatTime(trimEndMs));
                if (trimEndBar.getProgress() <= trimStartBar.getProgress()) {
                    trimEndBar.setProgress(trimStartBar.getProgress() + 1);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        toolOptionsPanel.addView(trimEndBar);

        toolOptionsPanel.addView(makePadding(dp(16)));
        TextView applyBtn = makeActionButton("Apply Trim", ACCENT_RED);
        applyBtn.setOnClickListener(v -> {
            showToast("Trim applied: " + formatTime(trimStartMs) + " → " + formatTime(trimEndMs));
            closeTool();
        });
        toolOptionsPanel.addView(applyBtn);
    }

    // ─── Speed Tool ───────────────────────────────────────────────────────────

    private void buildSpeedTool() {
        TextView info = sectionLabel("Choose playback speed");
        toolOptionsPanel.addView(info);
        toolOptionsPanel.addView(makePadding(dp(16)));

        float[] speeds = {0.3f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};
        String[] labels = {"0.3×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×", "3×"};

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setPadding(0, 0, 0, 0);

        for (int i = 0; i < speeds.length; i++) {
            final float spd = speeds[i];
            final String lbl = labels[i];
            final boolean isActive = spd == currentSpeed;

            TextView btn = new TextView(this);
            btn.setText(lbl);
            btn.setTextSize(14);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(16), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.setMargins(dp(4), 0, dp(4), 0);
            btn.setLayoutParams(bp);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(20));
            if (isActive) {
                bg.setColor(ACCENT_RED);
                btn.setTextColor(TEXT_WHITE);
                btn.setTypeface(null, Typeface.BOLD);
            } else {
                bg.setColor(BG_CARD);
                btn.setTextColor(TEXT_GRAY);
            }
            btn.setBackground(bg);

            btn.setOnClickListener(v -> {
                currentSpeed = spd;
                showToast("Speed: " + lbl);
                // Refresh tool
                toolOptionsPanel.removeAllViews();
                buildSpeedHeader();
                buildSpeedTool();
            });
            speedRow.addView(btn);
        }

        hsv.addView(speedRow);
        toolOptionsPanel.addView(hsv);

        toolOptionsPanel.addView(makePadding(dp(20)));
        TextView speedLabel = new TextView(this);
        speedLabel.setText("Current: " + currentSpeed + "×");
        speedLabel.setTextColor(ACCENT_CYAN);
        speedLabel.setTextSize(15);
        speedLabel.setTypeface(null, Typeface.BOLD);
        speedLabel.setGravity(Gravity.CENTER);
        toolOptionsPanel.addView(speedLabel);
    }

    private void buildSpeedHeader() {
        // header recreation helper
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = dp(16);
        header.setLayoutParams(hp);
        TextView toolTitle = new TextView(this);
        toolTitle.setText("Speed");
        toolTitle.setTextColor(TEXT_WHITE);
        toolTitle.setTextSize(18);
        toolTitle.setTypeface(null, Typeface.BOLD);
        header.addView(toolTitle);
        View hsp = new View(this);
        hsp.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        header.addView(hsp);
        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕  Done");
        closeBtn.setTextColor(ACCENT_CYAN);
        closeBtn.setTextSize(13);
        closeBtn.setOnClickListener(v -> closeTool());
        header.addView(closeBtn);
        toolOptionsPanel.addView(header);
        toolOptionsPanel.addView(makeDivider());
    }

    // ─── Filter Tool ──────────────────────────────────────────────────────────

    private void buildFilterTool() {
        String[][] filters = {
            {"🎞️", "Original"}, {"⬛", "B&W"}, {"🌅", "Warm"}, {"❄️", "Cool"},
            {"🌿", "Nature"}, {"🎬", "Cinema"}, {"✨", "Vivid"}, {"🌫️", "Fade"},
            {"💜", "Purple"}, {"🟠", "Sunset"}, {"🌊", "Ocean"}, {"🍂", "Vintage"}
        };

        TextView info = sectionLabel("Choose a filter");
        toolOptionsPanel.addView(info);
        toolOptionsPanel.addView(makePadding(dp(12)));

        // Grid layout
        int cols = 4;
        for (int row = 0; row < (filters.length + cols - 1) / cols; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(8);
            rowLayout.setLayoutParams(rlp);

            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= filters.length) {
                    View filler = new View(this);
                    filler.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                    rowLayout.addView(filler);
                    continue;
                }

                final int filterIdx = idx;
                final String emoji = filters[idx][0];
                final String name = filters[idx][1];
                final boolean isSelected = idx == selectedFilterIndex;

                LinearLayout filterItem = new LinearLayout(this);
                filterItem.setOrientation(LinearLayout.VERTICAL);
                filterItem.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(80), 1f);
                fp.setMargins(dp(4), 0, dp(4), 0);
                filterItem.setLayoutParams(fp);

                GradientDrawable fbg = new GradientDrawable();
                fbg.setShape(GradientDrawable.RECTANGLE);
                fbg.setCornerRadius(dp(8));
                if (isSelected) {
                    fbg.setColor(0xFF1A0A00);
                    fbg.setStroke(dp(2), ACCENT_RED);
                } else {
                    fbg.setColor(BG_CARD);
                    fbg.setStroke(dp(1), DIVIDER);
                }
                filterItem.setBackground(fbg);

                TextView filterEmoji = new TextView(this);
                filterEmoji.setText(emoji);
                filterEmoji.setTextSize(24);
                filterEmoji.setGravity(Gravity.CENTER);
                filterItem.addView(filterEmoji);

                TextView filterName = new TextView(this);
                filterName.setText(name);
                filterName.setTextColor(isSelected ? ACCENT_RED : TEXT_GRAY);
                filterName.setTextSize(10);
                filterName.setGravity(Gravity.CENTER);
                filterItem.addView(filterName);

                filterItem.setOnClickListener(v -> {
                    selectedFilterIndex = filterIdx;
                    showToast("Filter: " + name);
                    // Rebuild
                    toolOptionsPanel.removeAllViews();
                    buildFilterTool();
                });
                rowLayout.addView(filterItem);
            }
            toolOptionsPanel.addView(rowLayout);
        }

        toolOptionsPanel.addView(makePadding(dp(8)));
        TextView intensityLabel = new TextView(this);
        intensityLabel.setText("Filter Intensity");
        intensityLabel.setTextColor(TEXT_LIGHT);
        intensityLabel.setTextSize(13);
        toolOptionsPanel.addView(intensityLabel);
        toolOptionsPanel.addView(makeSeekBar(80));
    }

    // ─── Text Tool ────────────────────────────────────────────────────────────

    private void buildTextTool() {
        TextView info = sectionLabel("Add text to your video");
        toolOptionsPanel.addView(info);
        toolOptionsPanel.addView(makePadding(dp(12)));

        EditText textInput = new EditText(this);
        textInput.setHint("Type something...");
        textInput.setHintTextColor(TEXT_GRAY);
        textInput.setTextColor(TEXT_WHITE);
        textInput.setTextSize(16);
        textInput.setText(selectedTextContent);
        textInput.setBackgroundColor(BG_CARD);
        textInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setColor(BG_CARD);
        inputBg.setStroke(dp(1), DIVIDER);
        inputBg.setCornerRadius(dp(8));
        textInput.setBackground(inputBg);
        toolOptionsPanel.addView(textInput);

        toolOptionsPanel.addView(makePadding(dp(12)));
        toolOptionsPanel.addView(sectionLabel("Text Style"));

        // Style row
        String[] styles = {"Bold", "Italic", "Shadow", "Outline", "Glow"};
        String[] styleEmojis = {"B", "I", "🌑", "O", "✨"};
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < styles.length; i++) {
            final String style = styles[i];
            TextView styleBtn = new TextView(this);
            styleBtn.setText(styleEmojis[i]);
            styleBtn.setTextColor(TEXT_GRAY);
            styleBtn.setTextSize(14);
            styleBtn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            slp.setMargins(dp(3), 0, dp(3), 0);
            styleBtn.setLayoutParams(slp);
            GradientDrawable sbg = new GradientDrawable();
            sbg.setShape(GradientDrawable.RECTANGLE);
            sbg.setColor(BG_CARD);
            sbg.setStroke(dp(1), DIVIDER);
            sbg.setCornerRadius(dp(6));
            styleBtn.setBackground(sbg);
            styleBtn.setOnClickListener(v -> showToast("Style: " + style));
            styleRow.addView(styleBtn);
        }
        toolOptionsPanel.addView(styleRow);

        toolOptionsPanel.addView(makePadding(dp(12)));
        toolOptionsPanel.addView(sectionLabel("Text Color"));
        toolOptionsPanel.addView(buildColorPicker());

        toolOptionsPanel.addView(makePadding(dp(16)));
        TextView addTextBtn = makeActionButton("Add to Video", ACCENT_RED);
        addTextBtn.setOnClickListener(v -> {
            selectedTextContent = textInput.getText().toString();
            showToast("Text added: \"" + selectedTextContent + "\"");
            closeTool();
        });
        toolOptionsPanel.addView(addTextBtn);
    }

    // ─── Music Tool ───────────────────────────────────────────────────────────

    private void buildMusicTool() {
        TextView info = sectionLabel("Add background music");
        toolOptionsPanel.addView(info);
        toolOptionsPanel.addView(makePadding(dp(12)));

        String[][] tracks = {
            {"🎵", "Trending #1", "3:24"},
            {"🎸", "Rock Vibes", "2:48"},
            {"🎹", "Piano Mood", "4:05"},
            {"🎤", "Pop Hits", "3:12"},
            {"🎧", "Lo-Fi Chill", "2:55"},
            {"🥁", "Hip Hop Beat", "2:33"},
        };

        for (String[] track : tracks) {
            LinearLayout trackItem = new LinearLayout(this);
            trackItem.setOrientation(LinearLayout.HORIZONTAL);
            trackItem.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams tip = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
            tip.bottomMargin = dp(8);
            trackItem.setLayoutParams(tip);
            trackItem.setPadding(dp(12), 0, dp(12), 0);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setShape(GradientDrawable.RECTANGLE);
            tbg.setColor(BG_CARD);
            tbg.setCornerRadius(dp(8));
            trackItem.setBackground(tbg);

            TextView trackEmoji = new TextView(this);
            trackEmoji.setText(track[0]);
            trackEmoji.setTextSize(22);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ep.rightMargin = dp(12);
            trackEmoji.setLayoutParams(ep);
            trackItem.addView(trackEmoji);

            LinearLayout trackInfo = new LinearLayout(this);
            trackInfo.setOrientation(LinearLayout.VERTICAL);
            trackInfo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView trackName = new TextView(this);
            trackName.setText(track[1]);
            trackName.setTextColor(TEXT_WHITE);
            trackName.setTextSize(14);
            trackInfo.addView(trackName);

            TextView trackDur = new TextView(this);
            trackDur.setText(track[2]);
            trackDur.setTextColor(TEXT_GRAY);
            trackDur.setTextSize(12);
            trackInfo.addView(trackDur);
            trackItem.addView(trackInfo);

            TextView useBtn = new TextView(this);
            useBtn.setText("Use");
            useBtn.setTextColor(ACCENT_CYAN);
            useBtn.setTextSize(13);
            useBtn.setOnClickListener(v -> {
                showToast("Music added: " + track[1]);
                closeTool();
            });
            trackItem.addView(useBtn);
            toolOptionsPanel.addView(trackItem);
        }

        toolOptionsPanel.addView(makePadding(dp(8)));
        TextView importBtn = makeActionButton("Import from Device 📁", BG_CARD);
        importBtn.setTextColor(ACCENT_CYAN);
        importBtn.setOnClickListener(v -> showToast("Import music coming soon!"));
        toolOptionsPanel.addView(importBtn);
    }

    // ─── Sticker Tool ─────────────────────────────────────────────────────────

    private void buildStickerTool() {
        toolOptionsPanel.addView(sectionLabel("Choose a sticker"));
        toolOptionsPanel.addView(makePadding(dp(12)));

        String[][] rows = {
            {"😀","😂","🥰","😎","🤩","😜","🥳","😍"},
            {"👍","🔥","💯","⭐","🎉","💥","❤️","✨"},
            {"🎬","📱","🎵","🎮","🏆","🚀","💎","👑"},
            {"🌟","🌈","🦋","🌺","🍕","🎂","🎁","🎊"},
        };

        for (String[] row : rows) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            rlp.bottomMargin = dp(4);
            rowLayout.setLayoutParams(rlp);

            for (String emoji : row) {
                TextView emojiBtn = new TextView(this);
                emojiBtn.setText(emoji);
                emojiBtn.setTextSize(26);
                emojiBtn.setGravity(Gravity.CENTER);
                emojiBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                emojiBtn.setOnClickListener(v -> {
                    showToast("Sticker added: " + emoji);
                    closeTool();
                });
                rowLayout.addView(emojiBtn);
            }
            toolOptionsPanel.addView(rowLayout);
        }
    }

    // ─── Transitions Tool ─────────────────────────────────────────────────────

    private void buildTransitionsTool() {
        toolOptionsPanel.addView(sectionLabel("Clip transition style"));
        toolOptionsPanel.addView(makePadding(dp(12)));

        String[][] transitions = {
            {"⬅️", "Slide Left"}, {"➡️", "Slide Right"}, {"⬆️", "Slide Up"},
            {"⬇️", "Slide Down"}, {"✨", "Fade"}, {"🌀", "Spin"},
            {"💥", "Flash"}, {"🔲", "Zoom In"}, {"🔳", "Zoom Out"},
            {"🎭", "Wipe"}, {"📺", "Static"}, {"🌊", "Ripple"},
        };

        int cols = 3;
        for (int row = 0; row < (transitions.length + cols - 1) / cols; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
            rlp.bottomMargin = dp(8);
            rowLayout.setLayoutParams(rlp);

            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= transitions.length) {
                    rowLayout.addView(spacerView(0, 1, 1f));
                    continue;
                }
                final String name = transitions[idx][1];
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                ip.setMargins(dp(4), 0, dp(4), 0);
                item.setLayoutParams(ip);
                GradientDrawable ibg = new GradientDrawable();
                ibg.setShape(GradientDrawable.RECTANGLE);
                ibg.setColor(BG_CARD);
                ibg.setCornerRadius(dp(8));
                item.setBackground(ibg);

                TextView emojiV = new TextView(this);
                emojiV.setText(transitions[idx][0]);
                emojiV.setTextSize(22);
                emojiV.setGravity(Gravity.CENTER);
                item.addView(emojiV);

                TextView labelV = new TextView(this);
                labelV.setText(name);
                labelV.setTextColor(TEXT_GRAY);
                labelV.setTextSize(10);
                labelV.setGravity(Gravity.CENTER);
                item.addView(labelV);

                item.setOnClickListener(v -> {
                    showToast("Transition: " + name);
                    closeTool();
                });
                rowLayout.addView(item);
            }
            toolOptionsPanel.addView(rowLayout);
        }
    }

    // ─── Adjust Tool ──────────────────────────────────────────────────────────

    private void buildAdjustTool() {
        toolOptionsPanel.addView(sectionLabel("Fine-tune your video"));
        toolOptionsPanel.addView(makePadding(dp(8)));

        String[][] adjustments = {
            {"☀️", "Brightness", "50"},
            {"🌗", "Contrast", "50"},
            {"🎨", "Saturation", "50"},
            {"🌡️", "Temperature", "50"},
            {"💧", "Clarity", "50"},
            {"🌑", "Shadow", "50"},
            {"💡", "Highlight", "50"},
            {"🔆", "Exposure", "50"},
        };

        for (String[] adj : adjustments) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            rlp.bottomMargin = dp(8);
            row.setLayoutParams(rlp);

            TextView emoji = new TextView(this);
            emoji.setText(adj[0]);
            emoji.setTextSize(18);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT);
            emoji.setLayoutParams(ep);
            row.addView(emoji);

            TextView label = new TextView(this);
            label.setText(adj[1]);
            label.setTextColor(TEXT_LIGHT);
            label.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT);
            label.setLayoutParams(lp);
            row.addView(label);

            SeekBar bar = new SeekBar(this);
            bar.setMax(100);
            bar.setProgress(Integer.parseInt(adj[2]));
            bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT_CYAN));
            bar.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT_CYAN));
            bar.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(bar);

            TextView valueLabel = new TextView(this);
            valueLabel.setText(adj[2]);
            valueLabel.setTextColor(TEXT_GRAY);
            valueLabel.setTextSize(12);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT);
            vlp.leftMargin = dp(8);
            valueLabel.setLayoutParams(vlp);
            row.addView(valueLabel);

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                    valueLabel.setText(String.valueOf(p));
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });

            toolOptionsPanel.addView(row);
        }

        toolOptionsPanel.addView(makePadding(dp(8)));
        TextView resetBtn = makeActionButton("Reset All", BG_CARD);
        resetBtn.setTextColor(TEXT_GRAY);
        resetBtn.setOnClickListener(v -> {
            toolOptionsPanel.removeAllViews();
            buildAdjustHeader();
            buildAdjustTool();
        });
        toolOptionsPanel.addView(resetBtn);
    }

    private void buildAdjustHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.bottomMargin = dp(16);
        header.setLayoutParams(hp);
        TextView t = new TextView(this); t.setText("Adjust"); t.setTextColor(TEXT_WHITE);
        t.setTextSize(18); t.setTypeface(null, Typeface.BOLD);
        header.addView(t);
        View sp = new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f));
        header.addView(sp);
        TextView close = new TextView(this); close.setText("✕  Done");
        close.setTextColor(ACCENT_CYAN); close.setTextSize(13);
        close.setOnClickListener(v -> closeTool()); header.addView(close);
        toolOptionsPanel.addView(header);
        toolOptionsPanel.addView(makeDivider());
    }

    // ─── Crop Tool ────────────────────────────────────────────────────────────

    private void buildCropTool() {
        toolOptionsPanel.addView(sectionLabel("Choose aspect ratio"));
        toolOptionsPanel.addView(makePadding(dp(12)));

        String[][] ratios = {
            {"📱", "9:16\nPortrait"}, {"🖥️", "16:9\nLandscape"},
            {"⬛", "1:1\nSquare"}, {"📸", "4:3\nPhoto"},
            {"🎬", "21:9\nCinema"}, {"📲", "4:5\nInstagram"},
        };

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(100)));

        for (int i = 0; i < Math.min(ratios.length, 4); i++) {
            final String name = ratios[i][1];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            ip.setMargins(dp(4), 0, dp(4), 0);
            item.setLayoutParams(ip);
            GradientDrawable ibg = new GradientDrawable();
            ibg.setShape(GradientDrawable.RECTANGLE);
            ibg.setColor(BG_CARD); ibg.setStroke(dp(1), DIVIDER);
            ibg.setCornerRadius(dp(8)); item.setBackground(ibg);

            TextView emojiV = new TextView(this);
            emojiV.setText(ratios[i][0]); emojiV.setTextSize(22);
            emojiV.setGravity(Gravity.CENTER); item.addView(emojiV);

            TextView labelV = new TextView(this);
            labelV.setText(name.replace("\\n", "\n")); labelV.setTextColor(TEXT_GRAY);
            labelV.setTextSize(10); labelV.setGravity(Gravity.CENTER);
            item.addView(labelV);

            item.setOnClickListener(v -> {
                showToast("Ratio: " + name.replace("\\n", " "));
                closeTool();
            });
            grid.addView(item);
        }
        toolOptionsPanel.addView(grid);

        toolOptionsPanel.addView(makePadding(dp(8)));

        LinearLayout grid2 = new LinearLayout(this);
        grid2.setOrientation(LinearLayout.HORIZONTAL);
        grid2.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(100)));

        for (int i = 4; i < ratios.length; i++) {
            final String name = ratios[i][1];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            ip.setMargins(dp(4), 0, dp(4), 0);
            item.setLayoutParams(ip);
            GradientDrawable ibg = new GradientDrawable();
            ibg.setShape(GradientDrawable.RECTANGLE);
            ibg.setColor(BG_CARD); ibg.setStroke(dp(1), DIVIDER);
            ibg.setCornerRadius(dp(8)); item.setBackground(ibg);

            TextView emojiV = new TextView(this);
            emojiV.setText(ratios[i][0]); emojiV.setTextSize(22);
            emojiV.setGravity(Gravity.CENTER); item.addView(emojiV);

            TextView labelV = new TextView(this);
            labelV.setText(name.replace("\\n", "\n")); labelV.setTextColor(TEXT_GRAY);
            labelV.setTextSize(10); labelV.setGravity(Gravity.CENTER);
            item.addView(labelV);

            item.setOnClickListener(v -> {
                showToast("Ratio: " + name.replace("\\n", " "));
                closeTool();
            });
            grid2.addView(item);
        }
        // Fill remaining spots
        for (int i = ratios.length % 4; i < 4 && ratios.length % 4 != 0; i++) {
            grid2.addView(spacerView(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        }
        toolOptionsPanel.addView(grid2);

        toolOptionsPanel.addView(makePadding(dp(16)));
        toolOptionsPanel.addView(sectionLabel("Rotation"));
        toolOptionsPanel.addView(makePadding(dp(8)));

        LinearLayout rotRow = new LinearLayout(this);
        rotRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] rotLabels = {"↺ -90°", "↩ -15°", "↪ +15°", "↻ +90°"};
        for (String rl : rotLabels) {
            TextView btn = new TextView(this);
            btn.setText(rl);
            btn.setTextColor(TEXT_LIGHT);
            btn.setTextSize(13);
            btn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            bp.setMargins(dp(4), 0, dp(4), 0);
            btn.setLayoutParams(bp);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setColor(BG_CARD); bg.setCornerRadius(dp(6));
            btn.setBackground(bg);
            btn.setOnClickListener(v -> showToast("Rotate: " + rl));
            rotRow.addView(btn);
        }
        toolOptionsPanel.addView(rotRow);
    }

    // ─── Voiceover Tool ───────────────────────────────────────────────────────

    private void buildVoiceoverTool() {
        toolOptionsPanel.addView(sectionLabel("Record voiceover"));
        toolOptionsPanel.addView(makePadding(dp(24)));

        // Big record button
        FrameLayout recordContainer = new FrameLayout(this);
        recordContainer.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(140)));

        // Outer ring
        View outerRing = new View(this);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setColor(0x22FF0033);
        outerRing.setBackground(ringBg);
        FrameLayout.LayoutParams outerParams = new FrameLayout.LayoutParams(dp(110), dp(110));
        outerParams.gravity = Gravity.CENTER;
        outerRing.setLayoutParams(outerParams);
        recordContainer.addView(outerRing);

        // Record button
        TextView recordBtn = new TextView(this);
        recordBtn.setText("🎙️");
        recordBtn.setTextSize(36);
        recordBtn.setGravity(Gravity.CENTER);
        GradientDrawable recBg = new GradientDrawable();
        recBg.setShape(GradientDrawable.OVAL);
        recBg.setColor(ACCENT_RED);
        recordBtn.setBackground(recBg);
        FrameLayout.LayoutParams recParams = new FrameLayout.LayoutParams(dp(80), dp(80));
        recParams.gravity = Gravity.CENTER;
        recordBtn.setLayoutParams(recParams);
        recordContainer.addView(recordBtn);

        final boolean[] isRecording = {false};
        recordBtn.setOnClickListener(v -> {
            isRecording[0] = !isRecording[0];
            if (isRecording[0]) {
                recordBtn.setText("⏹");
                recordBtn.setTextSize(28);
                showToast("Recording... 🎙️");
                // Pulse animation
                ObjectAnimator pulse = ObjectAnimator.ofFloat(outerRing, "scaleX", 1f, 1.3f, 1f);
                pulse.setDuration(800);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.start();
                ObjectAnimator pulse2 = ObjectAnimator.ofFloat(outerRing, "scaleY", 1f, 1.3f, 1f);
                pulse2.setDuration(800);
                pulse2.setRepeatCount(ValueAnimator.INFINITE);
                pulse2.start();
            } else {
                recordBtn.setText("🎙️");
                recordBtn.setTextSize(36);
                outerRing.setScaleX(1f); outerRing.setScaleY(1f);
                showToast("Recording saved!");
            }
        });

        toolOptionsPanel.addView(recordContainer);

        toolOptionsPanel.addView(makePadding(dp(8)));
        TextView hint = new TextView(this);
        hint.setText("Tap the mic to start / stop recording");
        hint.setTextColor(TEXT_GRAY);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        hint.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        toolOptionsPanel.addView(hint);
    }

    // ─── Audio Tool ───────────────────────────────────────────────────────────

    private void buildAudioTool() {
        toolOptionsPanel.addView(sectionLabel("Audio controls"));
        toolOptionsPanel.addView(makePadding(dp(16)));

        // Volume
        buildSliderRow("🔊  Volume", 100, v -> currentVolume = v / 100f);
        toolOptionsPanel.addView(makePadding(dp(12)));

        // Fade in
        buildSliderRow("⬆️  Fade In Duration (sec)", 30, v -> {});
        toolOptionsPanel.addView(makePadding(dp(12)));

        // Fade out
        buildSliderRow("⬇️  Fade Out Duration (sec)", 30, v -> {});
        toolOptionsPanel.addView(makePadding(dp(16)));

        // Mute toggle
        LinearLayout muteRow = new LinearLayout(this);
        muteRow.setOrientation(LinearLayout.HORIZONTAL);
        muteRow.setGravity(Gravity.CENTER_VERTICAL);
        muteRow.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        TextView muteLabel = new TextView(this);
        muteLabel.setText("🔇  Mute Original Audio");
        muteLabel.setTextColor(TEXT_LIGHT);
        muteLabel.setTextSize(14);
        muteLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        muteRow.addView(muteLabel);

        Switch muteSwitch = new Switch(this);
        muteSwitch.setChecked(isMuted);
        muteSwitch.setOnCheckedChangeListener((btn, c) -> {
            isMuted = c;
            showToast(isMuted ? "Audio muted" : "Audio enabled");
        });
        muteRow.addView(muteSwitch);
        toolOptionsPanel.addView(muteRow);

        toolOptionsPanel.addView(makeDivider());
        toolOptionsPanel.addView(makePadding(dp(8)));
        toolOptionsPanel.addView(sectionLabel("Noise Reduction"));
        Switch noiseSwitch = new Switch(this);
        noiseSwitch.setText("Enable noise reduction");
        noiseSwitch.setTextColor(TEXT_LIGHT);
        toolOptionsPanel.addView(noiseSwitch);
    }

    // ─── Video Loading ────────────────────────────────────────────────────────

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(intent, 200);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 200 && resultCode == RESULT_OK && data != null) {
            Uri videoUri = data.getData();
            if (videoUri != null) loadVideo(videoUri);
        }
    }

    private void loadVideo(Uri uri) {
        selectedVideoUri = uri;
        selectedVideoPath = getRealPathFromUri(uri);

        // Get video metadata
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, uri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                videoDurationMs = Long.parseLong(durationStr);
                trimEndMs = videoDurationMs;
            }

            // Get thumbnail
            Bitmap thumbnail = retriever.getFrameAtTime(0);
            if (thumbnail != null) {
                previewImageView.setImageBitmap(thumbnail);
            }
            retriever.release();
        } catch (Exception e) {
            Log.e("VideoEditor", "Error loading video: " + e.getMessage());
        }

        // Hide empty state, show controls
        View emptyPreview = previewContainer.findViewWithTag("empty_preview");
        if (emptyPreview != null) emptyPreview.setVisibility(View.GONE);

        playPauseOverlay.setVisibility(View.VISIBLE);
        playPauseIcon.setVisibility(View.VISIBLE);
        timeDisplay.setText("00:00 / " + formatTime(videoDurationMs));
        progressBar.setMax(100);

        // Update timeline
        timelineContainer.removeAllViews();
        addVideoToTimeline(uri);
        addTimelineAddFrame();

        showToast("Video loaded! " + formatTime(videoDurationMs));

        // Animate preview in
        previewContainer.setAlpha(0f);
        previewContainer.animate().alpha(1f).setDuration(300).start();
    }

    private void addVideoToTimeline(Uri uri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, uri);

            int frameCount = 6;
            for (int i = 0; i < frameCount; i++) {
                long timeUs = (long) (i * (videoDurationMs * 1000L) / frameCount);
                Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                FrameLayout frameLayout = new FrameLayout(this);
                LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(48), dp(56));
                frameLayout.setLayoutParams(fp);

                ImageView frameImg = new ImageView(this);
                frameImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (frame != null) frameImg.setImageBitmap(frame);
                else frameImg.setBackgroundColor(BG_CARD);
                frameLayout.addView(frameImg);

                timelineContainer.addView(frameLayout);
                timelineFrames.add(frameLayout);
            }
            retriever.release();
        } catch (Exception e) {
            // Fallback: colored blocks
            for (int i = 0; i < 6; i++) {
                View block = new View(this);
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(48), dp(56));
                block.setLayoutParams(bp);
                block.setBackgroundColor(i % 2 == 0 ? BG_CARD : BG_ITEM);
                timelineContainer.addView(block);
            }
        }
    }

    private void togglePlayPause() {
        isPlaying = !isPlaying;
        setPlayIcon(isPlaying);
        showToast(isPlaying ? "▶ Playing" : "⏸ Paused");

        if (isPlaying) startProgressSimulation();
        else progressHandler.removeCallbacksAndMessages(null);
    }

    private void setPlayIcon(boolean playing) {
        playPauseIcon.setImageDrawable(null);
        // We use a text view trick since we can't easily use vector drawables programmatically
        // In real implementation, use actual play/pause drawable resources
    }

    private void startProgressSimulation() {
        if (videoDurationMs <= 0) return;
        final long[] elapsed = {0};
        final long interval = 500;

        progressRunnable = () -> {
            if (!isPlaying) return;
            elapsed[0] += interval;
            if (elapsed[0] > videoDurationMs) {
                elapsed[0] = 0;
                isPlaying = false;
            }
            int progress = (int)(elapsed[0] * 100 / videoDurationMs);
            progressBar.setProgress(progress);
            timeDisplay.setText(formatTime(elapsed[0]) + " / " + formatTime(videoDurationMs));
            if (isPlaying) progressHandler.postDelayed(progressRunnable, interval);
        };
        progressHandler.postDelayed(progressRunnable, interval);
    }

    // ─── Export ───────────────────────────────────────────────────────────────

    private void startExport() {
        if (selectedVideoPath == null && selectedVideoUri == null) {
            showToast("Please import a video first!");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Export Video");

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(24), dp(16), dp(24), dp(16));
        dialogLayout.setBackgroundColor(BG_PANEL);

        String[] qualities = {"480p  (Fast)", "720p  (Standard)", "1080p  (High)", "4K  (Ultra)"};
        final int[] selectedQuality = {1};

        for (int i = 0; i < qualities.length; i++) {
            final int qi = i;
            RadioButton rb = new RadioButton(this);
            rb.setText(qualities[i]);
            rb.setTextColor(TEXT_WHITE);
            rb.setChecked(i == selectedQuality[0]);
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT_RED));
            rb.setOnClickListener(v -> selectedQuality[0] = qi);
            dialogLayout.addView(rb);
        }

        builder.setView(dialogLayout);
        builder.setPositiveButton("Export", (d, w) -> {
            showExportProgress(qualities[selectedQuality[0]]);
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(BG_PANEL));
        }
        dialog.show();
    }

    private void showExportProgress(String quality) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(24), dp(24), dp(24));
        layout.setBackgroundColor(BG_PANEL);
        layout.setGravity(Gravity.CENTER);

        TextView exportTitle = new TextView(this);
        exportTitle.setText("Exporting at " + quality);
        exportTitle.setTextColor(TEXT_WHITE);
        exportTitle.setTextSize(16);
        exportTitle.setTypeface(null, Typeface.BOLD);
        exportTitle.setGravity(Gravity.CENTER);
        layout.addView(exportTitle);

        layout.addView(makePadding(dp(16)));

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(0);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT_RED));
        layout.addView(pb);

        TextView pct = new TextView(this);
        pct.setText("0%");
        pct.setTextColor(ACCENT_CYAN);
        pct.setTextSize(14);
        pct.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.topMargin = dp(8);
        pct.setLayoutParams(pp);
        layout.addView(pct);

        builder.setView(layout);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(BG_PANEL));
        }
        dialog.show();

        // Simulate export progress
        final int[] progress = {0};
        Handler h = new Handler();
        Runnable tick = new Runnable() {
            @Override public void run() {
                progress[0] += (int)(Math.random() * 8 + 2);
                if (progress[0] >= 100) {
                    progress[0] = 100;
                    pb.setProgress(100);
                    pct.setText("Done! ✅");
                    h.postDelayed(() -> {
                        dialog.dismiss();
                        showToast("Video exported successfully! 🎬");
                    }, 800);
                } else {
                    pb.setProgress(progress[0]);
                    pct.setText(progress[0] + "%");
                    h.postDelayed(this, 120);
                }
            }
        };
        h.postDelayed(tick, 200);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void showExitDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Discard changes?")
            .setMessage("Your unsaved edits will be lost.")
            .setPositiveButton("Discard", (d, w) -> finish())
            .setNegativeButton("Keep editing", null)
            .show();
    }

    private String getRealPathFromUri(Uri uri) {
        if (uri == null) return null;
        try {
            String[] proj = {MediaStore.Video.Media.DATA};
            Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
            if (cursor != null) {
                int idx = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                cursor.moveToFirst();
                String path = cursor.getString(idx);
                cursor.close();
                return path;
            }
        } catch (Exception e) {
            Log.e("VideoEditor", "Path error: " + e.getMessage());
        }
        return uri.getPath();
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long secs = ms / 1000;
        long min = secs / 60;
        long sec = secs % 60;
        return min + ":" + String.format("%02d", sec);
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density);
    }

    private View makeDivider() {
        View d = new View(this);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dp.topMargin = dp(4); dp.bottomMargin = dp(4);
        d.setLayoutParams(dp);
        d.setBackgroundColor(DIVIDER);
        return d;
    }

    private View makePadding(int height) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));
        return v;
    }

    private SeekBar makeSeekBar(int progress) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(progress);
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT_RED));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT_RED));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(6); slp.bottomMargin = dp(4);
        sb.setLayoutParams(slp);
        return sb;
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(TEXT_GRAY);
        tv.setTextSize(12);
        tv.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView makeActionButton(String text, int bgColor) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(TEXT_WHITE);
        btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(14), 0, dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(10));
        btn.setBackground(bg);
        btn.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return btn;
    }

    // Override to use TextView as icon button
    private TextView makeTextIconButton(String text, int w, int h) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(TEXT_WHITE);
        btn.setTextSize(18);
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return btn;
    }

    private LinearLayout buildColorPicker() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        rlp.topMargin = dp(8);
        row.setLayoutParams(rlp);

        int[] colors = {0xFFFFFFFF, 0xFF000000, 0xFFFF0033, 0xFF00F2EA,
                        0xFFFFD700, 0xFF00FF88, 0xFF8B5CF6, 0xFFFF6B35};
        for (int color : colors) {
            View swatch = new View(this);
            GradientDrawable sbg = new GradientDrawable();
            sbg.setShape(GradientDrawable.OVAL);
            sbg.setColor(color);
            sbg.setStroke(dp(1), 0x44FFFFFF);
            swatch.setBackground(sbg);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, dp(32), 1f);
            sp.setMargins(dp(3), dp(4), dp(3), dp(4));
            swatch.setLayoutParams(sp);
            final int c = color;
            swatch.setOnClickListener(v -> showToast("Color selected"));
            row.addView(swatch);
        }
        return row;
    }

    private void buildSliderRow(String label, int progress, SliderListener listener) {
        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(TEXT_LIGHT);
        lbl.setTextSize(13);
        toolOptionsPanel.addView(lbl);

        SeekBar bar = makeSeekBar(progress);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { listener.onChange(p); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        toolOptionsPanel.addView(bar);
    }

    interface SliderListener { void onChange(int value); }

    private View spacerView(int w, int h, float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h, weight));
        return v;
    }

    private void animateToolTap(View v) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.92f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.92f, 1f);
        scaleX.setDuration(200); scaleY.setDuration(200);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ID helpers for relative layout rules
    private int findTopBarId() {
        View v = rootLayout.findViewWithTag("topbar");
        return v != null ? v.getId() : 0;
    }

    private int getExportBarTagId() {
        return 0x7F0B0001; // fixed ID for export bar
    }

    private int getTimelineTagId() {
        View v = rootLayout.findViewWithTag("timeline_wrapper");
        return v != null ? v.getId() : 0;
    }
}
