package com.troubadour.player;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.troubadour.app.R;
import com.troubadour.utils.Logger;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Video player activity using Android's MediaPlayer.
 * To be enhanced with SABR stream handling capabilities.
 */
public class MediaPlayerVideoActivity extends AppCompatActivity implements
        SurfaceHolder.Callback,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnInfoListener,
        View.OnClickListener {

    private static final String TAG = "MediaPlayerVideoActivity";
    private static final int UPDATE_INTERVAL = 1000; // 1 second

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private Handler handler = new Handler();
    private Timer timer;
    private boolean isPrepared = false;
    private boolean isPlaying = false;

    // UI Elements
    private ImageButton btnPlayPause;
    private ImageButton btnStop;
    private SeekBar seekBarProgress;
    private TextView txtCurrentTime;
    private TextView txtTotalTime;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);
        Logger.i("MediaPlayerVideoActivity created");

        // Initialize UI
        surfaceView = findViewById(R.id.surface_view);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnStop = findViewById(R.id.btn_stop);
        seekBarProgress = findViewById(R.id.seek_bar_progress);
        txtCurrentTime = findViewById(R.id.txt_current_time);
        txtTotalTime = findViewById(R.id.txt_total_time);

        // Set up surface view
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        // Set up buttons
        btnPlayPause.setOnClickListener(this);
        btnStop.setOnClickListener(this);
        seekBarProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && isPrepared) {
                    mediaPlayer.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Get video URI from intent
        Uri videoUri = getIntent().getData();
        if (videoUri == null) {
            // For testing, use a sample URL
            videoUri = Uri.parse("https://archive.org/download/ksnn_compilation_master_the_internet/ksnn_compilation_master_the_internet_512kb.mp4");
            Toast.makeText(this, "Using sample video for testing", Toast.LENGTH_SHORT).show();
        }

        // Initialize MediaPlayer
        initializePlayer(videoUri);
    }

    /**
     * Initializes the MediaPlayer with the given URI.
     */
    private void initializePlayer(Uri uri) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.reset();
            } else {
                mediaPlayer = new MediaPlayer();
                // Set up MediaPlayer listeners
                mediaPlayer.setOnPreparedListener(this);
                mediaPlayer.setOnCompletionListener(this);
                mediaPlayer.setOnErrorListener(this);
                mediaPlayer.setOnInfoListener(this);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.prepareAsync(); // Prepare asynchronously
            showLoadingState();
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source: " + e.getMessage());
            Toast.makeText(this, "Error loading video: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showLoadingState() {
        btnPlayPause.setEnabled(false);
        btnStop.setEnabled(false);
        seekBarProgress.setEnabled(false);
        // Show loading indicator (we could add a ProgressBar to the layout)
    }

    private void showReadyState() {
        btnPlayPause.setEnabled(true);
        btnStop.setEnabled(true);
        seekBarProgress.setEnabled(true);
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
    }

    private void showPlayingState() {
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
    }

    private void showPausedState() {
        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Logger.i("Surface created");
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setDisplay(surfaceHolder);
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                Log.e(TAG, "Error setting display: " + e.getMessage());
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Logger.i("Surface changed: " + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Logger.i("Surface destroyed");
        if (mediaPlayer != null) {
            mediaPlayer.setDisplay(null);
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        Logger.i("MediaPlayer prepared");
        isPrepared = true;
        isPlaying = false;
        showReadyState();

        // Set up seek bar
        seekBarProgress.setMax(mediaPlayer.getDuration());
        seekBarProgress.setProgress(0);

        // Set total time
        txtTotalTime.setText(formatTime(mediaPlayer.getDuration()));

        // Start updating progress
        startProgressTimer();

        // Start playback
        mediaPlayer.start();
        isPlaying = true;
        showPlayingState();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Logger.i("Playback completed");
        isPlaying = false;
        showPausedState();
        seekBarProgress.setProgress(seekBarProgress.getMax());
        stopProgressTimer();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
        Toast.makeText(this, "Video playback error", Toast.LENGTH_LONG).show();
        return true; // True indicates we handled the error
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        // Handle media player info events
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_play_pause) {
            if (mediaPlayer != null && isPrepared) {
                if (isPlaying) {
                    mediaPlayer.pause();
                    isPlaying = false;
                    showPausedState();
                } else {
                    mediaPlayer.start();
                    isPlaying = true;
                    showPlayingState();
                }
            }
        } else if (v.getId() == R.id.btn_stop) {
            if (mediaPlayer != null) {
                mediaPlayer.seekTo(0);
                if (isPlaying) {
                    mediaPlayer.pause();
                }
                isPlaying = false;
                showPausedState();
                seekBarProgress.setProgress(0);
            }
        }
    }

    private void startProgressTimer() {
        stopProgressTimer(); // Ensure we don't have multiple timers
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaPlayer != null && isPrepared && isPlaying) {
                            int currentPosition = mediaPlayer.getCurrentPosition();
                            seekBarProgress.setProgress(currentPosition);
                            txtCurrentTime.setText(formatTime(currentPosition));
                        }
                    }
                });
            }
        }, 0, UPDATE_INTERVAL);
    }

    private void stopProgressTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = milliseconds / 1000;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressTimer();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        Logger.i("MediaPlayerVideoActivity destroyed");
    }
}