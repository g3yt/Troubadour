package com.troubadour.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
            android.widget.ImageView;
            android.widget.TextView;

import com.bumptech.glide.Glide;
import com.troubadour.app.R;
import com.troubadour.app.TroubadourApplication;
import com.troubadour.api.YoutubeApi;
import com.troubadour.player.MediaPlayerVideoActivity;
import com.troubadour.player.SabrProxyServer;
import com.troubadour.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Home feed activity displaying recommended videos.
 */
public class HomeFeedActivity extends AppCompatActivity {
    private static final String TAG = "HomeFeedActivity";
    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private List<Map<String, Object>> videoList = new ArrayList<>();
    private String continuationToken = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_feed);
        Logger.i("HomeFeedActivity created");

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter(videoList);
        recyclerView.setAdapter(adapter);

        // Load initial feed
        loadHomeFeed();
    }

    private void loadHomeFeed() {
        // In a real implementation, this would be done on a background thread
        Map<String, Object> response = YoutubeApi.homeFeed(continuationToken);
        if (response instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) response;
            Object contentsObj = map.get("contents");
            if (contentsObj instanceof List) {
                List<?> contents = (List<?>) contentsObj;
                // Extract video items from contents (simplified)
                for (Object item : contents) {
                    if (item instanceof Map) {
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        // Look for video renderer items
                        Object videoRenderer = itemMap.get("videoRenderer");
                        if (videoRenderer instanceof Map) {
                            Map<String, Object> video = (Map<String, Object>) videoRenderer;
                            videoList.add(video);
                        }
                    }
                }
                // Get continuation token
                continuationToken = (String) map.get("continuation");
                adapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Adapter for displaying video items in a RecyclerView.
     */
    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
        private List<Map<String, Object>> videos;

        VideoAdapter(List<Map<String, Object>> videos) {
            this.videos = videos;
        }

        @Override
        public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_video, parent, false);
            return new VideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(VideoViewHolder holder, int position) {
            Map<String, Object> video = videos.get(position);
            if (video != null) {
                holder.bind(video);
                holder.itemView.setOnClickListener(v -> {
                    // When a video item is clicked, launch the video player via our SABR proxy
                    // Extract video ID from the video item
                    String videoId = extractVideoId(video);
                    if (videoId != null) {
                        // Construct a URL pointing to our local SABR proxy
                        SabrProxyServer proxy = TroubadourApplication.getSabrProxyServer();
                        String proxyBaseUrl = proxy != null ? proxy.getProxyBaseUrl() : null;
                        Uri videoUri;
                        if (proxyBaseUrl != null) {
                            // Use our local proxy for SABR handling
                            videoUri = Uri.parse(proxyBaseUrl + "video/" + videoId);
                        } else {
                            // Fallback to direct YouTube URL if proxy isn't available
                            videoUri = Uri.parse("https://www.youtube.com/watch?v=" + videoId);
                        }
                        Intent intent = new Intent(HomeFeedActivity.this, MediaPlayerVideoActivity.class);
                        intent.setData(videoUri);
                        startActivity(intent);
                    }
                });
            }
        }

        private String extractVideoId(Map<String, Object> video) {
            // Extract video ID from the video item map
            // This is simplified - actual implementation would need to parse the video renderer properly
            Object videoIdObj = video.get("videoId");
            if (videoIdObj instanceof String) {
                return (String) videoIdObj;
            }
            return null;
        }

        @Override
        public int getItemCount() {
            return videos.size();
        }

        class VideoViewHolder extends RecyclerView.ViewHolder {
            private TextView titleView;
            private TextView channelView;
            private ImageView thumbnailView;

            VideoViewHolder(View itemView) {
                super(itemView);
                titleView = itemView.findViewById(R.id.title);
                channelView = itemView.findViewById(R.id.channel);
                thumbnailView = itemView.findViewById(R.id.thumbnail);
            }

            void bind(Map<String, Object> video) {
                // Extract title
                Object titleObj = video.get("title");
                String title = "";
                if (titleObj instanceof Map) {
                    Map<String, Object> titleMap = (Map<String, Object>) titleObj;
                    Object simpleText = titleMap.get("simpleText");
                    if (simpleText instanceof String) {
                        title = (String) simpleText;
                    }
                }
                titleView.setText(title);

                // Extract channel name
                Object channelObj = video.get("author");
                String channel = "";
                if (channelObj instanceof Map) {
                    Map<String, Object> authorMap = (Map<String, Object>) channelObj;
                    Object authorText = authorMap.get("simpleText");
                    if (authorText instanceof String) {
                        channel = (String) authorText;
                    }
                }
                channelView.setText(channel);

                // Extract thumbnail
                Object thumbnailObj = video.get("thumbnail");
                String thumbnailUrl = "";
                if (thumbnailObj instanceof Map) {
                    Map<String, Object> thumbnailMap = (Map<String, Object>) thumbnailObj;
                    Object thumbnails = thumbnailMap.get("thumbnails");
                    if (thumbnails instanceof List) {
                        List<?> thumbs = (List<?>) thumbnails;
                        if (!thumbs.isEmpty()) {
                            Object firstThumb = thumbs.get(0);
                            if (firstThumb instanceof Map) {
                                Map<String, Object> thumbMap = (Map<String, Object>) firstThumb;
                                Object url = thumbMap.get("url");
                                if (url instanceof String) {
                                    thumbnailUrl = (String) url;
                                }
                            }
                        }
                    }
                }
                if (!thumbnailUrl.isEmpty()) {
                    Glide.with(thumbnailView.getContext())
                            .load(thumbnailUrl)
                            .into(thumbnailView);
                }
            }
        }
    }
}