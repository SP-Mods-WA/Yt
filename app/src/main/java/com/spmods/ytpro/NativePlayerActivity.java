package com.spmods.ytpro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class NativePlayerActivity extends Activity {
    
    private ExoPlayer player;
    private PlayerView playerView;
    private String videoUrl;
    private String videoTitle;
    private long resumePosition = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Fullscreen setup
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        
        // Get video info from intent
        Intent intent = getIntent();
        videoUrl = intent.getStringExtra("video_url");
        videoTitle = intent.getStringExtra("video_title");
        resumePosition = intent.getLongExtra("resume_position", 0);
        
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Invalid video URL", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        setupUI();
        initializePlayer();
    }
    
    private void setupUI() {
        RelativeLayout mainLayout = new RelativeLayout(this);
        mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ));
        mainLayout.setBackgroundColor(Color.BLACK);
        
        // ExoPlayer PlayerView
        playerView = new PlayerView(this);
        RelativeLayout.LayoutParams playerParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );
        playerView.setLayoutParams(playerParams);
        playerView.setControllerShowTimeoutMs(3000);
        playerView.setControllerHideOnTouch(true);
        
        // Custom close button
        ImageButton closeBtn = new ImageButton(this);
        RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
            dpToPx(48), dpToPx(48)
        );
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        closeParams.setMargins(dpToPx(16), dpToPx(16), 0, 0);
        closeBtn.setLayoutParams(closeParams);
        closeBtn.setBackgroundColor(Color.parseColor("#80000000"));
        closeBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        closeBtn.setColorFilter(Color.WHITE);
        closeBtn.setOnClickListener(v -> finish());
        
        // Title overlay
        TextView titleView = new TextView(this);
        titleView.setText(videoTitle != null ? videoTitle : "Playing Video");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        titleView.setBackgroundColor(Color.parseColor("#80000000"));
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        titleView.setLayoutParams(titleParams);
        
        mainLayout.addView(playerView);
        mainLayout.addView(closeBtn);
        mainLayout.addView(titleView);
        
        setContentView(mainLayout);
    }
    
    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        
        // Prepare media source
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(
            this,
            Util.getUserAgent(this, "YTPro")
        );
        
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl));
        
        player.setMediaSource(mediaSource);
        player.prepare();
        
        // Resume from position
        if (resumePosition > 0) {
            player.seekTo(resumePosition);
        }
        
        player.setPlayWhenReady(true);
        
        // Add listener
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    finish();
                }
            }
            
            @Override
            public void onPlayerError(PlaybackException error) {
                Toast.makeText(NativePlayerActivity.this, 
                    "Playback error: " + error.getMessage(), 
                    Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            resumePosition = player.getCurrentPosition();
            player.setPlayWhenReady(false);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
    
    @Override
    public void onBackPressed() {
        if (player != null) {
            Intent result = new Intent();
            result.putExtra("resume_position", player.getCurrentPosition());
            setResult(RESULT_OK, result);
        }
        super.onBackPressed();
    }
    
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
