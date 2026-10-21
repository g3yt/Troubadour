package com.troubadour.player;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import com.troubadour.app.R;
import com.troubadour.utils.Logger;

/**
 * Video player activity using ExoPlayer.
 * To be enhanced with custom SabrDataSource for YouTube SABR streams.
 */
public class ExoPlayerVideoActivity extends AppCompatActivity {
    private static final String TAG = "ExoPlayerVideoActivity";
    private SimpleExoPlayer player;
    private PlayerView playerView;
    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_exo_player);
        Logger.i("ExoPlayerVideoActivity created");

        playerView = findViewById(R.id.player_view);

        // Initialize ExoPlayer
        initializePlayer(Uri.parse("https://archive.org/download/ksnn_compilation_master_the_internet/ksnn_compilation_master_the_internet_512kb.mp4"));
    }

    /**
     * Initializes the ExoPlayer instance.
     */
    private void initializePlayer(Uri mediaUri) {
        if (player == null) {
            // Create a bandwidth meter to monitor network conditions
            BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

            // Create a track selection handler for adaptive streaming
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveTrackSelection.Factory(bandwidthMeter);

            // Create the track selector
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

            // Create the load control handler
            LoadControl loadControl = /* TODO: specify */ null;

            // Create the player
            player = ExoPlayerFactory.newInstance(
                    this,
                    trackSelector,
                    loadControl
            );

            playerView.setPlayer(player);

            // Set player to fill the view
            playerView.setUseController(true);
            playerView.setResizeMode(PlayerView.RESIZE_MODE_FILL);
        }

        // Set the media source
        MediaSource mediaSource = buildMediaSource(mediaUri);
        player.prepare(mediaSource, true, false);
        player.setPlayWhenReady(playWhenReady);
        player.seekTo(currentWindow, playbackPosition);
    }

    /**
     * Builds the media source for the given URI.
     * For YouTube SABR streams, we would use our custom SabrDataSource.
     */
    private MediaSource buildMediaSource(Uri uri) {
        // For regular media, we use the default data source factory
        // For YouTube SABR streams, we would create a custom data source factory
        // that uses our SabrDataSource

        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(
                this,
                Util.getUserAgent(this, "Troubadour"),
                /* bandwidthMeter= */ new DefaultBandwidthMeter()
        );

        // TODO: For YouTube SABR streams, replace with:
        // DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(
        //         this,
        //         Util.getUserAgent(this, "Troubadour"),
        //         new SabrDataSource()  // Custom data source for SABR
        // );

        return new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(uri);
    }

    /**
     * Releases the ExoPlayer resources.
     */
    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Util.SDK_INT >= 24) {
            initializePlayer(Uri.parse("")); // Would pass actual URI
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer(Uri.parse(""));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Util.SDK_INT < 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Util.SDK_INT >= 24) {
            releasePlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        Logger.i("ExoPlayerVideoActivity destroyed");
    }
}