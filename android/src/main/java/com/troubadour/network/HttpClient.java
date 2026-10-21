package com.troubadour.network;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.Map;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Simple HTTP client wrapper using OkHttp.
 * To be enhanced with caching, certificate pinning, request deduplication, etc.
 */
public class HttpClient {
    private static final String TAG = "HttpClient";
    private static final int DEFAULT_TIMEOUT = 30; // seconds
    private static final long HTTP_RESPONSE_CACHE_SIZE = 10 * 1024 * 1024; // 10 MB

    private final OkHttpClient client;
    private final Context context;

    public HttpClient(Context context) {
        this.context = context.getApplicationContext();
        // Configure OkHttp client with cache
        Cache cache = new Cache(context.getCacheDir(), HTTP_RESPONSE_CACHE_SIZE);
        this.client = new OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Execute a GET request synchronously.
     * NOTE: This method should not be called on the UI thread.
     *
     * @param URL to fetch
     * @return Response body as string, or null on failure
     * @throws IOException if an I/O error occurs
     */
    public String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "Unexpected HTTP " + response.code());
                return null;
            }
            return response.body().string();
        }
    }

    /**
     * Execute a POST request synchronously.
     * NOTE: This method should not be called on the UI thread.
     *
     * @param url URL to fetch
     * @param jsonBody JSON string body (may be null or empty)
     * @param headers Map of header names to values (may be null)
     * @return Response body as string, or null on failure
     * @throws IOException if an I/O error occurs
     */
    public String post(String url, String jsonBody, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonBody == null ? "" : jsonBody, JSON);
        builder.post(body);
        Request request = builder.build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "Unexpected HTTP " + response.code());
                return null;
            }
            return response.body().string();
        }
    }

    /**
     * Execute a GET request asynchronously.
     *
     * @param url URL to fetch
     * @param callback Callback to receive the response
     */
    public void getAsync(String url, final HttpCallback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Request failed", e);
                callback.onFailure(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure(new IOException("Unexpected HTTP " + response.code()));
                    return;
                }
                String body = response.body().string();
                callback.onSuccess(body);
            }
        });
    }

    /**
     * Callback for asynchronous HTTP requests.
     */
    public interface HttpCallback {
        void onSuccess(String responseBody);
        void onFailure(Exception e);
    }
}