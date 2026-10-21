package com.troubadour.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.troubadour.network.HttpClient;
import com.troubadour.utils.JsonUtils;
import com.troubadour.utils.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Port of YTAuth: Handles YouTube OAuth 2.0 device flow authentication.
 */
public class YTAuth {
    private static final String TAG = "YTAuth";
    private static final String PREF_REFRESH_TOKEN = "youtube_refresh_token";
    private static final String PREF_ACCESS_TOKEN = "youtube_access_token";
    private static final String PREF_TOKEN_EXPIRY = "youtube_token_expiry";
    private static final String PREF_LAST_POLL_TIME = "youtube_last_poll_time";

    // OAuth2 credentials for TV client (from YTAuth.m)
    private static final String CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube https://www.googleapis.com/auth/youtubepartner";

    private static Context sContext;
    private static SharedPreferences sPreferences;
    private static OkHttpClient sHttpClient;

    /**
     * Initialize with application context.
     * Should be called from Application.onCreate().
     */
    public static void init(Context context) {
        sContext = context.getApplicationContext();
        sPreferences = PreferenceManager.getDefaultSharedPreferences(sContext);
        sHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        Logger.i("YTAuth initialized");
    }

    /**
     * Check if the user is signed in (has a refresh token).
     */
    public static boolean isSignedIn() {
        if (sContext == null) return false;
        return sPreferences.getString(PREF_REFRESH_TOKEN, null) != null;
    }

    /**
     * Begin the device flow: request a device code and user code.
     * Returns a map containing "device_code", "user_code", "verification_uri", "expires_in", "interval".
     * Returns null on failure.
     */
    public static Map<String, Object> beginDeviceFlow() {
        if (sContext == null || sHttpClient == null) return null;
        RequestBody body = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE)
                .build();
        Request request = new Request.Builder()
                .url("https://oauth2.googleapis.com/device/code")
                .post(body)
                .build();
        try {
            Response response = sHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.e(TAG, "Device flow request failed: " + response.code());
                return null;
            }
            String json = response.body().string();
            Map<String, Object> result = JsonUtils.parseAny(json);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Device flow request exception", e);
            return null;
        }
    }

    /**
     * Poll for token exchange using the device code.
     * Returns:
     *   - Access token string if successful
     *   - null if still pending (authorization_pending)
     *   - Empty string on error
     *   - Specific error strings for other error cases
     */
    public static String pollDeviceFlow(String deviceCode) {
        if (sContext == null || sHttpClient == null) return "";
        RequestBody body = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("device_code", deviceCode)
                .add("grant_type", "http://oauth.net/grant_type/device/1.0")
                .build();
        Request request = new Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build();
        try {
            Response response = sHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.e(TAG, "Token poll failed: " + response.code());
                return ""; // error
            }
            String json = response.body().string();
            Map<String, Object> result = JsonUtils.parseAny(json);
            if (!(result instanceof Map)) {
                return "";
            }
            Map<String, Object> map = (Map<String, Object>) result;
            String error = (String) map.get("error");
            if (error != null) {
                if ("authorization_pending".equals(error)) {
                    return null; // still pending
                } else if ("slow_down".equals(error)) {
                    return "slow_down"; // special case
                } else {
                    Log.e(TAG, "Token poll error: " + error);
                    return ""; // other error
                }
            }
            // Success case
            String accessToken = (String) map.get("access_token");
            String refreshToken = (String) map.get("refresh_token");
            Integer expiresIn = (Integer) map.get("expires_in");
            if (accessToken != null && refreshToken != null && expiresIn != null) {
                storeTokens(refreshToken, accessToken, expiresIn);
                return accessToken;
            }
            return ""; // missing required fields
        } catch (IOException e) {
            Log.e(TAG, "Token poll exception", e);
            return "";
        }
    }

    /**
     * Get the current access token, refreshing if necessary.
     * Returns the access token string, or null if not signed in.
     */
    public static String getAccessToken() {
        if (sContext == null) return null;
        if (!isSignedIn()) {
            return null;
        }
        // Check if access token is still valid
        long expiryTime = sPreferences.getLong(PREF_TOKEN_EXPIRY, 0);
        long now = System.currentTimeMillis();
        if (now < expiryTime - 60000) { // 1 minute buffer
            String accessToken = sPreferences.getString(PREF_ACCESS_TOKEN, null);
            if (accessToken != null) {
                return accessToken;
            }
        }
        // Token expired or no access token stored, try to refresh
        String refreshToken = sPreferences.getString(PREF_REFRESH_TOKEN, null);
        if (refreshToken != null) {
            Map<String, Object> refreshed = refreshAccessToken(refreshToken);
            if (refreshed != null) {
                String newAccessToken = (String) refreshed.get("access_token");
                String newRefreshToken = (String) refreshed.get("refresh_token");
                Integer newExpiresIn = (Integer) refreshed.get("expires_in");
                if (newAccessToken != null && newRefreshToken != null && newExpiresIn != null) {
                    storeTokens(newRefreshToken, newAccessToken, newExpiresIn);
                    return newAccessToken;
                }
            }
        }
        // Refresh failed, fall back to return null (will trigger re-auth)
        return null;
    }

    /**
     * Refresh access token using refresh token.
     * Returns map with access_token, refresh_token, expires_in or null on failure.
     */
    private static Map<String, Object> refreshAccessToken(String refreshToken) {
        if (sContext == null || sHttpClient == null) return null;
        RequestBody body = new FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build();
        Request request = new Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(body)
                .build();
        try {
            Response response = sHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                Log.e(TAG, "Token refresh failed: " + response.code());
                return null;
            }
            String json = response.body().string();
            Map<String, Object> result = JsonUtils.parseAny(json);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Token refresh exception", e);
            return null;
        }
    }

    /**
     * Sign out: clear stored tokens.
     */
    public static void signOut() {
        if (sContext == null) return;
        SharedPreferences.Editor editor = sPreferences.edit();
        editor.remove(PREF_REFRESH_TOKEN);
        editor.remove(PREF_ACCESS_TOKEN);
        editor.remove(PREF_TOKEN_EXPIRY);
        editor.remove(PREF_LAST_POLL_TIME);
        editor.apply();
        Logger.i("User signed out");
    }

    /**
     * Store the refresh token (and optionally access token) obtained from token exchange.
     */
    public static void storeTokens(String refreshToken, String accessToken, long expiresIn) {
        if (sContext == null) return;
        SharedPreferences.Editor editor = sPreferences.edit();
        editor.putString(PREF_REFRESH_TOKEN, refreshToken);
        if (accessToken != null) {
            editor.putString(PREF_ACCESS_TOKEN, accessToken);
            long expiryTime = System.currentTimeMillis() + (expiresIn * 1000);
            editor.putLong(PREF_TOKEN_EXPIRY, expiryTime);
        }
        editor.apply();
        Logger.i("Tokens stored");
    }
}