package com.troubadour.player;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.MediaController;
import android.widget.VideoView;

import com.troubadour.app.R;
import com.troubadour.utils.Logger;

/**
 * Basic video player activity.
 * To be enhanced with ExoPlayer for SABR stream handling,
 * adaptive bitrate, background playback, etc.
 */
public class VideoPlayerActivity extends AppCompatActivity {
    private static final String TAG = "VideoPlayerActivity";
    private VideoView videoView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        Logger.i("VideoPlayerActivity created");

        videoView = findViewById(R.id.video_view);
        MediaController mediaController = new MediaController(this);
        videoView.setMediaController(mediaController);
        mediaController.setAnchorView(videoView);

        // Get video URL from intent (to be implemented with actual stream URL)
        Uri videoUri = getIntent().getData();
        if (videoUri == null) {
            // For testing, use a sample URL
            videoUri = Uri.parse("https://archive.org/download/ksnn_compilation_master_the_internet/ksnn_compilation_master_the_internet_512kb.mp4");
        }

        videoView.setVideoURI(videoUri);
        videoView.requestFocus();
        videoView.setOnPreparedListener(mp -> {
            Logger.i("Video prepared, duration: " + mp.getDuration());
            videoView.start();
        });
        videoView.setOnErrorListener((vv, what, extra) -> {
            Log.e(TAG, "Video error: " + what + ", " + extra);
            return false;
        });
        videoView.setOnCompletionListener(mp -> {
            Logger.i("Video playback completed");
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
        Logger.i("VideoPlayerActivity destroyed");
    }
}