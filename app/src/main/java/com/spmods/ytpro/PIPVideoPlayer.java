package com.spmods.ytpro;

import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.app.PendingIntent;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerView;

import java.util.ArrayList;

@UnstableApi
public class PIPVideoPlayer extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;  // SimpleExoPlayer → ExoPlayer (නව නම)
    private MediaSession mediaSession;
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

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.activity_pip_player);

        Intent intent = getIntent();
        currentVideoUrl = intent.getStringExtra("VIDEO_URL");
        videoTitle = intent.getStringExtra("VIDEO_TITLE");
        videoChannel = intent.getStringExtra("VIDEO_CHANNEL");

        playerView = findViewById(R.id.playerView);
        pipToggleBtn = findViewById(R.id.pipToggleBtn);
        closeBtn = findViewById(R.id.closeBtn);
        titleView = findViewById(R.id.videoTitle);

        titleView.setText(videoTitle != null ? videoTitle : "YouTube Video");

        initializePlayer();

        pipToggleBtn.setOnClickListener(v -> togglePIPMode());
        closeBtn.setOnClickListener(v -> finish());

        if (currentVideoUrl != null && !currentVideoUrl.isEmpty()) {
            loadVideo(currentVideoUrl);
        }

        // Android 12+ auto PIP සඳහා params set කරන්න
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updatePipParams();
        }
    }

    private void initializePlayer() {
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);

        ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(currentVideoUrl)));

        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();

        playerView.setPlayer(player);
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);

        setupMediaSession();
    }

    private void setupMediaSession() {
        mediaSession = new MediaSession.Builder(this, player)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public ListenableFuture<MediaSession.SessionResult> onCustomCommand(
                            @NonNull MediaSession session,
                            @NonNull MediaSession.ControllerInfo controller,
                            @NonNull androidx.media3.session.SessionCommand command,
                            @NonNull Bundle args) {
                        if ("TOGGLE_PIP".equals(command.customAction)) {
                            togglePIPMode();
                            return Futures.immediateFuture(new MediaSession.SessionResult(MediaSession.SessionResult.RESULT_SUCCESS));
                        }
                        return super.onCustomCommand(session, controller, command, args);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                updatePipParams();
            }
        }
    }

    private void togglePIPMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "PIP requires Android 8.0+", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isInPictureInPictureMode()) {
            // Already in PIP → do nothing or handle exit if needed
            return;
        }

        enterPIPMode();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void enterPIPMode() {
        try {
            PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();

            if (player != null && player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                Rational aspect = new Rational(player.getVideoSize().width, player.getVideoSize().height);
                pipBuilder.setAspectRatio(aspect);
            } else {
                pipBuilder.setAspectRatio(new Rational(16, 9));
            }

            // Android 12+ auto enter
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pipBuilder.setAutoEnterEnabled(true);
            }

            // Actions (play/pause, close)
            ArrayList<RemoteAction> actions = new ArrayList<>();

            // Play/Pause example (ඔයාගේ receiver එකට match වෙන්න)
            Intent playPauseIntent = new Intent(this, PIPActionReceiver.class).setAction("PLAY_PAUSE");
            PendingIntent ppPi = PendingIntent.getBroadcast(this, 0, playPauseIntent, PendingIntent.FLAG_IMMUTABLE);

            Icon ppIcon = Icon.createWithResource(this, player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            RemoteAction ppAction = new RemoteAction(ppIcon, player.isPlaying() ? "Pause" : "Play", "Toggle playback", ppPi);
            actions.add(ppAction);

            // Close
            Intent closeIntent = new Intent(this, PIPActionReceiver.class).setAction("CLOSE");
            PendingIntent closePi = PendingIntent.getBroadcast(this, 1, closeIntent, PendingIntent.FLAG_IMMUTABLE);
            Icon closeIcon = Icon.createWithResource(this, R.drawable.ic_close);
            RemoteAction closeAction = new RemoteAction(closeIcon, "Close", "Close PiP", closePi);
            actions.add(closeAction);

            pipBuilder.setActions(actions);

            enterPictureInPictureMode(pipBuilder.build());
            isPipMode = true;

            hideNonEssentialUi();

        } catch (Exception e) {
            Log.e("PIPVideoPlayer", "PIP enter failed", e);
            Toast.makeText(this, "PIP failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideNonEssentialUi() {
        playerView.hideController();
        pipToggleBtn.setVisibility(View.GONE);
        closeBtn.setVisibility(View.GONE);
        titleView.setVisibility(View.GONE);
    }

    private void showNonEssentialUi() {
        playerView.showController();
        pipToggleBtn.setVisibility(View.VISIBLE);
        closeBtn.setVisibility(View.VISIBLE);
        titleView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        isPipMode = isInPictureInPictureMode;

        if (isInPictureInPictureMode) {
            hideNonEssentialUi();
        } else {
            showNonEssentialUi();
        }
    }

    // Android 12+ auto PIP සඳහා params update කරන්න (player ready වෙනකොට / size change වෙනකොට)
    @RequiresApi(Build.VERSION_CODES.S)
    private void updatePipParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();

        if (player != null && player.getVideoSize().width > 0) {
            builder.setAspectRatio(new Rational(player.getVideoSize().width, player.getVideoSize().height));
        } else {
            builder.setAspectRatio(new Rational(16, 9));
        }

        builder.setAutoEnterEnabled(true);
        // actions ඕනේ නම් මෙතනටත් එකතු කරන්න

        setPictureInPictureParams(builder.build());
    }

    @Override
    protected void onPause() {
        // super.onPause();   ← ඕනේ නැහැ, default එකෙන්ම handle වෙනවා
        if (isPipMode && player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onStop() {
        // super.onStop();   ← ඕනේ නැහැ
        if (!isInPictureInPictureMode() && player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {
        // super.onDestroy();   ← ඕනේ නැහැ
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();  // අන්තිමට මේක දාන්න (හරිම safe)
    }

    // onUserLeaveHint ඕනේ නැහැ Android 12+ වලදි → ඉවත් කරලා තියෙනවා
                                                    }
