package com.spmods.ytpro;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.media3.common.*;
import androidx.media3.common.util.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.*;
import androidx.media3.session.*;
import androidx.media3.ui.PlayerView;
import com.google.common.util.concurrent.*;

public class PIPVideoPlayer extends AppCompatActivity {
    
    private PlayerView playerView;
    private SimpleExoPlayer player;
    private MediaSession mediaSession;
    private MediaSessionService mediaSessionService;
    private boolean isPipMode = false;
    private String currentVideoUrl = "";
    private String videoTitle = "";
    private String videoChannel = "";
    private ImageView pipToggleBtn;
    private Button closeBtn;
    private TextView titleView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Hide action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        // Set layout
        setContentView(R.layout.activity_pip_player);
        
        // Get video data from intent
        Intent intent = getIntent();
        currentVideoUrl = intent.getStringExtra("VIDEO_URL");
        videoTitle = intent.getStringExtra("VIDEO_TITLE");
        videoChannel = intent.getStringExtra("VIDEO_CHANNEL");
        
        // Initialize views
        playerView = findViewById(R.id.playerView);
        pipToggleBtn = findViewById(R.id.pipToggleBtn);
        closeBtn = findViewById(R.id.closeBtn);
        titleView = findViewById(R.id.videoTitle);
        
        // Set video title
        titleView.setText(videoTitle != null ? videoTitle : "YouTube Video");
        
        // Setup player
        initializePlayer();
        
        // Setup PIP toggle
        pipToggleBtn.setOnClickListener(v -> togglePIPMode());
        
        // Setup close button
        closeBtn.setOnClickListener(v -> finish());
        
        // Load video
        if (currentVideoUrl != null && !currentVideoUrl.isEmpty()) {
            loadVideo(currentVideoUrl);
        }
    }
    
    private void initializePlayer() {
        // Create data source factory
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        
        // Create media source
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(currentVideoUrl)));
        
        // Build player
        player = new SimpleExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build();
        
        // Attach player to view
        playerView.setPlayer(player);
        
        // Prepare player
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
        
        // Setup media session
        setupMediaSession();
    }
    
    private void setupMediaSession() {
        mediaSession = new MediaSession.Builder(this, player)
            .setCallback(new MediaSession.Callback() {
                @Override
                public ListenableFuture<SessionResult> onCustomCommand(
                    MediaSession session,
                    SessionCommand command,
                    Bundle args
                ) {
                    if ("TOGGLE_PIP".equals(command.customAction)) {
                        togglePIPMode();
                        return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    return super.onCustomCommand(session, command, args);
                }
            })
            .build();
    }
    
    private void loadVideo(String videoUrl) {
        if (player != null) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
        }
    }
    
    private void togglePIPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isPipMode) {
                enterPIPMode();
            } else {
                exitPIPMode();
            }
        } else {
            Toast.makeText(this, "PIP requires Android 8.0+", Toast.LENGTH_SHORT).show();
        }
    }
    
    @TargetApi(Build.VERSION_CODES.O)
    private void enterPIPMode() {
        try {
            PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
            
            // Calculate aspect ratio from video
            if (player != null && player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                Rational aspectRatio = new Rational(
                    player.getVideoSize().width,
                    player.getVideoSize().height
                );
                pipBuilder.setAspectRatio(aspectRatio);
            } else {
                pipBuilder.setAspectRatio(new Rational(16, 9));
            }
            
            // Set auto-enter enabled
            pipBuilder.setAutoEnterEnabled(true);
            
            // Set actions for PIP
            ArrayList<RemoteAction> actions = new ArrayList<>();
            
            // Play/Pause action
            Intent playPauseIntent = new Intent(this, PIPActionReceiver.class);
            playPauseIntent.setAction("PLAY_PAUSE");
            PendingIntent playPausePendingIntent = PendingIntent.getBroadcast(
                this, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE
            );
            
            Icon playPauseIcon = Icon.createWithResource(this, 
                player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play
            );
            
            RemoteAction playPauseAction = new RemoteAction(
                playPauseIcon,
                player.isPlaying() ? "Pause" : "Play",
                player.isPlaying() ? "Pause video" : "Play video",
                playPausePendingIntent
            );
            actions.add(playPauseAction);
            
            // Close action
            Intent closeIntent = new Intent(this, PIPActionReceiver.class);
            closeIntent.setAction("CLOSE");
            PendingIntent closePendingIntent = PendingIntent.getBroadcast(
                this, 1, closeIntent, PendingIntent.FLAG_IMMUTABLE
            );
            
            Icon closeIcon = Icon.createWithResource(this, R.drawable.ic_close);
            RemoteAction closeAction = new RemoteAction(
                closeIcon,
                "Close",
                "Close PIP",
                closePendingIntent
            );
            actions.add(closeAction);
            
            pipBuilder.setActions(actions);
            
            // Enter PIP mode
            enterPictureInPictureMode(pipBuilder.build());
            isPipMode = true;
            
            // Hide UI elements
            playerView.hideController();
            pipToggleBtn.setVisibility(View.GONE);
            closeBtn.setVisibility(View.GONE);
            titleView.setVisibility(View.GONE);
            
        } catch (Exception e) {
            Log.e("PIPVideoPlayer", "Error entering PIP: " + e.getMessage());
            Toast.makeText(this, "PIP failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void exitPIPMode() {
        // Show UI elements
        playerView.showController();
        pipToggleBtn.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
        titleView.setVisibility(View.VISIBLE);
        isPipMode = false;
    }
    
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        isPipMode = isInPictureInPictureMode;
        
        if (isInPictureInPictureMode) {
            // Hide UI in PIP
            playerView.hideController();
            pipToggleBtn.setVisibility(View.GONE);
            closeBtn.setVisibility(View.GONE);
            titleView.setVisibility(View.GONE);
        } else {
            // Show UI when exiting PIP
            playerView.showController();
            pipToggleBtn.setVisibility(View.VISIBLE);
            closeBtn.setVisibility(View.VISIBLE);
            titleView.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Auto-enter PIP when user presses home
        if (player != null && player.isPlaying()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                togglePIPMode();
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (isPipMode) {
            // Keep playing in PIP
            if (player != null) {
                player.setPlayWhenReady(true);
            }
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Don't release player if in PIP
        if (!isPipMode && player != null) {
            player.release();
            player = null;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
