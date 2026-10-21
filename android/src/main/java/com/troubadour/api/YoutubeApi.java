package com.troubadour.api;

import android.content.Context;
import android.util.Log;

import com.troubadour.auth.YTAuth;
import com.troubadour.network.HttpClient;
import com.troubadour.utils.JsonUtils;
import com.troubadour.utils.Logger;
import com.troubadour.utils.StringsUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of YTApi: Interface to YouTube's InnerTube API.
 */
public class YoutubeApi {
    private static final String TAG = "YoutubeApi";
    private static final String API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"; // YTInnertubeKey
    private static final String BASE_URL = "https://www.youtube.com/youtubei/v1/";

    private static Context sContext;
    private static HttpClient sHttpClient;

    /**
     * Initialize with application context.
     * Should be called from Application.onCreate().
     */
    public static void init(Context context) {
        sContext = context.getApplicationContext();
        sHttpClient = new HttpClient(sContext);
        Logger.i("YoutubeApi initialized");
    }

    /**
     * Build the client context map for a given client type.
     * Mirrors the Objective‑C clientContext: method.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> clientContext(String client) {
        Map<String, Object> context = new HashMap<>();
        context.put("clientName", client);
        context.put("hl", StringsUtils.loc("en")); // TODO: proper hl from settings
        context.put("gl", StringsUtils.loc("US")); // TODO: proper gl from settings

        if ("TVHTML5".equals(client)) {
            context.put("clientVersion", "7.20250209.19.00"); // YTTvVersion
            context.put("platform", "TV");
            context.put("deviceMake", "Samsung");
            context.put("deviceModel", "SmartTV");
            context.put("osName", "Tizen");
            context.put("osVersion", "5.0");
        } else if ("WEB".equals(client)) {
            context.put("clientVersion", "2.20260430.08.00"); // YTWebVersion
            context.put("platform", "WEB");
            // WEB client does not need extra fields? Keep as is.
        } else if ("ANDROID".equals(client)) {
            context.put("clientVersion", "19.09.37"); // YTAndroidVersion
            context.put("platform", "MOBILE");
            context.put("osName", "Android");
            context.put("osVersion", "11");
            context.put("androidSdkVersion", 30);
            context.put("deviceMake", "Google");
            context.put("deviceModel", "Pixel 5");
        } else {
            // Fallback to WEB
            context.put("clientVersion", "2.20260430.08.00");
        }
        return context;
    }

    /**
     * Build the full payload for a request: merges body with client context and user info.
     */
    private static Map<String, Object> buildPayload(String endpoint, Map<String, Object> body,
                                                    String client, boolean authorize) {
        Map<String, Object> payload = new HashMap<>(body != null ? body : new HashMap<>());
        Map<String, Object> context = clientContext(client);
        payload.put("context", context);

        // Handle onBehalfOfUser if needed (simplified)
        if (authorize) {
            // TODO: implement proper onBehalfOfUser logic using YTAuth
            String behalf = ""; // placeholder
            if (!behalf.isEmpty()) {
                Map<String, Object> user = new HashMap<>();
                Map<String, Object> onBehalf = new HashMap<>();
                onBehalf.put("onBehalfOfUser", behalf);
                user.putAll(onBehalf);
                payload.put("user", user);
            }
        }
        return payload;
    }

    /**
     * Send a POST request to the InnerTube endpoint and return the parsed JSON response.
     * This method is synchronous and must be called off the UI thread.
     *
     * @param endpoint   API endpoint (e.g., "browse", "player")
     * @param body       Request body as a map (may be null)
     * @param client     Client type: "TVHTML5", "WEB", "ANDROID"
     * @param authorize  Whether to include Authorization header
     * @return Parsed JSON response as Map or List, or null on failure
     */
    public static Object post(String endpoint, Map<String, Object> body,
                              String client, boolean authorize) {
        if (sContext == null || sHttpClient == null) {
            Logger.e("YoutubeApi not initialized");
            return null;
        }

        Map<String, Object> payload = buildPayload(endpoint, body, client, authorize);

        // Convert payload to JSON string (simplified)
        String jsonBody = JsonUtils.encode(payload); // TODO: implement proper JSON encoding
        if (jsonBody == null) {
            jsonBody = "{}";
        }

        String url = BASE_URL + endpoint + "?key=" + API_KEY + "&prettyPrint=false";

        try {
            // Build headers map, filtering out null values
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", getUserAgent(client));
            headers.put("X-YouTube-Client-Name", getClientNumber(client));
            headers.put("X-YouTube-Client-Version", getClientVersion(client));
            headers.put("Content-Type", "application/json");
            String authHeader = authorize ? "Bearer " + YTAuth.getAccessToken() : null;
            if (authHeader != null) {
                headers.put("Authorization", authHeader);
            }
            String acceptLanguage = StringsUtils.loc("en") + "," + StringsUtils.loc("en") + ";q=0.9"; // TODO: proper hl
            if (acceptLanguage != null) {
                headers.put("Accept-Language", acceptLanguage);
            }

            String responseJson = sHttpClient.post(url, jsonBody, headers);
            if (responseJson == null) {
                return null;
            }
            return JsonUtils.parseAny(responseJson);
        } catch (Exception e) {
            Logger.e("YoutubeApi request failed: " + e.getMessage());
            return null;
        }
    }

    // ---------- Helper methods for headers ----------

    private static String getClientNumber(String client) {
        if ("TVHTML5".equals(client)) return "85";
        if ("ANDROID".equals(client)) return "3";
        if ("MWEB".equals(client)) return "2";
        return "1"; // WEB
    }

    private static String getClientVersion(String client) {
        if ("TVHTML5".equals(client)) return "7.20250209.19.00"; // YTTvVersion
        if ("ANDROID".equals(client)) return "19.09.37"; // YTAndroidVersion
        if ("MWEB".equals(client)) return "2.20260430.08.00"; // YTWebVersion (placeholder)
        return YTWebVersion;
    }

    private static String getUserAgent(String client) {
        if ("TVHTML5".equals(client)) {
            return "Mozilla/5.0 (SMART-TV; LINUX; Tizen 5.0) AppleWebKit/537.36 (KHTML, like Gecko) Version/5.0 TV Safari/537.36";
        }
        if ("ANDROID".equals(client)) {
            return "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip";
        }
        if ("MWEB".equals(client)) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        }
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    }

    // ---------- API methods ----------

    /**
     * Лента «Главной». `continuation` — токен следующей страницы либо nil
     * для первой.
     *
     * Возвращает словарь с ключами `items` (массив YTVideoItem) и
     * `continuation` (строка или nil).
     */
    public static Map<String, Object> homeFeed(String continuation) {
        Map<String, Object> body = new HashMap<>();
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For home feed, we need TVHTML5 client with authorization
        Object resp = post("browse", body, "TVHTML5", true);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Страница ролика: описание, автор, счётчики, похожие, токен комментариев.
     * Ключи: `title`, `channelTitle`, `channelId`, `channelThumbnail`,
     * `subscribers`, `views`, `published`, `likes`, `description`,
     * `related` (массив YTVideoItem), `commentsToken`.
     */
    public static Map<String, Object> videoDetails(String videoId) {
        Map<String, Object> body = new HashMap<>();
        body.put("videoId", videoId);
        // For video details, we can use WEB client (no auth needed) or TVHTML5?
        // According to comments, videoDetails uses WEB client.
        Object resp = post("player", body, "WEB", false);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Ответ `/player` — там лежат потоки.
     *
     * Отдаётся как есть, разбирают его YTStreams и YTDashProxy.
     */
    public static Map<String, Object> playerResponse(String videoId) {
        Map<String, Object> body = new HashMap<>();
        body.put("videoId", videoId);
        // For player response, we need TVHTML5 client with auth
        Object resp = post("player", body, "TVHTML5", true);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Поиск по запросу.
     * `continuation` — токен следующей страницы либо nil для первой.
     * `query` — поисковый запрос.
     *
     * Возвращает словарь с ключами `contents` (содержит результаты поиска) и
     * `continuation` (строка или nil).
     */
    public static Map<String, Object> search(String query, String continuation) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For search, we use WEB client (no auth needed)
        Object resp = post("search", body, "WEB", false);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Лента подписок. `continuation` — токен следующей страницы либо nil
     * для первой.
     *
     * Возвращает словарь с ключами (потребуется уточнение структуры).
     */
    public static Map<String, Object> subscriptionsFeed(String continuation) {
        Map<String, Object> body = new HashMap<>();
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For subscriptions feed, we need TVHTML5 client with authorization
        Object resp = post("subscriptions", body, "TVHTML5", true);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * История просмотра. `continuation` — токен следующей страницы либо nil
     * для первой.
     *
     * Возвращает словарь с ключами (потребуется уточнение структуры).
     */
    public static Map<String, Object> history(String continuation) {
        Map<String, Object> body = new HashMap<>();
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For history, we need TVHTML5 client with authorization
        Object resp = post("history", body, "TVHTML5", true);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Канал по идентификатору. `continuation` — токен следующей страницы либо nil
     * для первой.
     *
     * Возвращает словарь с ключами: `title`, `description`, `subscribers`,
     * `avatar`, `banner`, `items` (массив видео), `continuation`.
     */
    public static Map<String, Object> channel(String channelId, String continuation) {
        Map<String, Object> body = new HashMap<>();
        body.put("channelId", channelId);
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For channel, we use TVHTML5 client with authorization
        Object resp = post("channel", body, "TVHTML5", true);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Плейлист по идентификатору. `continuation` — токен следующей страницы либо nil
     * для первой.
     *
     * Возвращает словарь с ключами: `title`, `description`, `itemCount`,
     * `thumbnail`, `items` (массив видео), `continuation`.
     */
    public static Map<String, Object> playlist(String playlistId, String continuation) {
        Map<String, Object> body = new HashMap<>();
        body.put("playlistId", playlistId);
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For playlist, we use WEB client (no auth needed)
        Object resp = post("playlist", body, "WEB", false);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Страница подборки (mix) по идентификатору.
     *
     * Возвращает словарь с ключами, аналогичными плейлисту.
     */
    public static Map<String, Object> mix(String mixId, String continuation) {
        Map<String, Object> body = new HashMap<>();
        body.put("playlistId", mixId); // Mixes use playlistId in API
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For mixes, we use WEB client (no auth needed)
        Object resp = post("playlist", body, "WEB", false);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    /**
     * Страница Shorts. `continuation` — токен следующей страницы либо nil
     * для первой.
     *
     * Возвращает словарь с ключами `contents` (содержит Shorts видео) и
     * `continuation` (строка или nil).
     */
    public static Map<String, Object> shorts(String continuation) {
        Map<String, Object> body = new HashMap<>();
        if (continuation != null && !continuation.isEmpty()) {
            body.put("continuation", continuation);
        }
        // For Shorts, we use specific client logic (simplified as WEB for now)
        Object resp = post("shorts", body, "WEB", false);
        if (resp instanceof Map) {
            return (Map<String, Object>) resp;
        }
        return null;
    }

    // TODO: Implement other API methods as needed (comments, likes, dislikes, etc.)
}