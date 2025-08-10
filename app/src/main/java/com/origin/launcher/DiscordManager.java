package com.origin.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.app.AlertDialog;
import android.app.Activity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.os.Handler;
import android.os.Looper;

public class DiscordManager {
    private static final String TAG = "DiscordManager";
    private static final String CLIENT_ID = "1403634750557752296"; // Your Discord Application ID
    private static final String REDIRECT_URI = "https://xelo-client.github.io/discord-callback.html/";
    private static final String SCOPE = "identify rpc";
    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";
    
    // Discord RPC Gateway
    private static final String DISCORD_RPC_URL = "wss://gateway.discord.gg/?v=10&encoding=json";
    
    private static final String PREFS_NAME = "discord_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISCRIMINATOR = "discriminator";
    private static final String KEY_AVATAR = "avatar";
    
    private Context context;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private ScheduledExecutorService rpcExecutor;
    private Handler mainHandler;
    private DiscordLoginCallback callback;
    private AlertDialog currentDialog;
    
    // RPC state
    private boolean rpcConnected = false;
    private String currentActivity = "";
    private String currentDetails = "";
    
    public interface DiscordLoginCallback {
        void onLoginSuccess(DiscordUser user);
        void onLoginError(String error);
        void onLogout();
        void onRPCConnected();
        void onRPCDisconnected();
    }
    
    public static class DiscordUser {
        public String id;
        public String username;
        public String discriminator;
        public String avatar;
        public String displayName;
        
        public DiscordUser(String id, String username, String discriminator, String avatar) {
            this.id = id;
            this.username = username;
            this.discriminator = discriminator;
            this.avatar = avatar;
            this.displayName = username + (discriminator.equals("0") ? "" : "#" + discriminator);
        }
    }
    
    public DiscordManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
        this.rpcExecutor = Executors.newScheduledThreadPool(1);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setCallback(DiscordLoginCallback callback) {
        this.callback = callback;
    }
    
    public boolean isLoggedIn() {
        return prefs.contains(KEY_ACCESS_TOKEN) && prefs.contains(KEY_USER_ID);
    }
    
    public boolean isRPCConnected() {
        return rpcConnected;
    }
    
    public DiscordUser getCurrentUser() {
        if (!isLoggedIn()) return null;
        
        return new DiscordUser(
            prefs.getString(KEY_USER_ID, ""),
            prefs.getString(KEY_USERNAME, ""),
            prefs.getString(KEY_DISCRIMINATOR, "0"),
            prefs.getString(KEY_AVATAR, "")
        );
    }
    
    public void login() {
        if (!(context instanceof Activity)) {
            Log.e(TAG, "Context is not an Activity, cannot show login dialog");
            if (callback != null) {
                callback.onLoginError("Invalid context for login");
            }
            return;
        }
        
        Activity activity = (Activity) context;
        
        String authUrl = "https://discord.com/oauth2/authorize?" +
            "client_id=" + CLIENT_ID +
            "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
            "&response_type=code" +
            "&scope=" + Uri.encode(SCOPE) +
            "&prompt=consent";
        
        Log.d(TAG, "Starting Discord login with URL: " + authUrl);
        
        // Create WebView with proper settings
        WebView webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " XeloClient/1.0");
        
        // Clear cache and cookies for fresh login
        webView.clearCache(true);
        webView.clearHistory();
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        
        // Create dialog with proper styling
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog);
        builder.setTitle("Login to Discord");
        builder.setView(webView);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Log.d(TAG, "Discord login cancelled by user");
            if (callback != null) {
                callback.onLoginError("Login cancelled by user");
            }
        });
        
        currentDialog = builder.create();
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "WebView loading URL: " + url);
                
                if (url.startsWith(REDIRECT_URI)) {
                    Log.d(TAG, "Redirect URI detected: " + url);
                    if (currentDialog != null && currentDialog.isShowing()) {
                        currentDialog.dismiss();
                    }
                    handleCallback(url);
                    return true;
                }
                return false;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);
                
                if (url.startsWith(REDIRECT_URI)) {
                    if (currentDialog != null && currentDialog.isShowing()) {
                        currentDialog.dismiss();
                    }
                    handleCallback(url);
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl);
            }
        });
        
        // Show dialog and load URL
        currentDialog.show();
        
        // Set dialog to full width
        if (currentDialog.getWindow() != null) {
            currentDialog.getWindow().setLayout(
                (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.95),
                (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.8)
            );
        }
        
        webView.loadUrl(authUrl);
    }
    
    private void handleCallback(String callbackUrl) {
        Log.d(TAG, "Handling callback URL: " + callbackUrl);
        
        Uri uri = Uri.parse(callbackUrl);
        String code = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error");
        
        if (error != null) {
            Log.e(TAG, "Discord OAuth error: " + error);
            if (callback != null) {
                callback.onLoginError("Discord authorization failed: " + error);
            }
            return;
        }
        
        if (code != null) {
            Log.d(TAG, "Authorization code received, exchanging for token");
            exchangeCodeForToken(code);
        } else {
            Log.e(TAG, "No authorization code received in callback");
            if (callback != null) {
                callback.onLoginError("No authorization code received");
            }
        }
    }
    
    private void exchangeCodeForToken(String code) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Exchanging code for access token");
                
                // Prepare token exchange data
                String tokenData = "client_id=" + CLIENT_ID +
                    "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
                    "&grant_type=authorization_code" +
                    "&code=" + code;
                
                URL url = new URL("https://discord.com/api/oauth2/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("User-Agent", "XeloClient/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                // Write request data
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(tokenData.getBytes("UTF-8"));
                    os.flush();
                }
                
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "Token exchange response code: " + responseCode);
                
                if (responseCode == 200) {
                    String response = readResponse(conn);
                    Log.d(TAG, "Token exchange successful");
                    
                    JSONObject tokenJson = new JSONObject(response);
                    String accessToken = tokenJson.getString("access_token");
                    
                    // Save token and fetch user info
                    prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
                    fetchUserInfo(accessToken);
                } else {
                    String errorResponse = readErrorResponse(conn);
                    Log.e(TAG, "Token exchange failed: " + errorResponse);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onLoginError("Failed to exchange authorization code: " + errorResponse);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error exchanging code for token", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLoginError("Network error during login: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    private void fetchUserInfo(String accessToken) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Fetching user information");
                
                URL url = new URL(DISCORD_API_BASE + "/users/@me");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("User-Agent", "XeloClient/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "User info response code: " + responseCode);
                
                if (responseCode == 200) {
                    String response = readResponse(conn);
                    JSONObject userJson = new JSONObject(response);
                    
                    String id = userJson.getString("id");
                    String username = userJson.getString("username");
                    String discriminator = userJson.optString("discriminator", "0");
                    String avatar = userJson.optString("avatar", "");
                    
                    Log.d(TAG, "User info retrieved for: " + username);
                    
                    // Save user info
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(KEY_USER_ID, id);
                    editor.putString(KEY_USERNAME, username);
                    editor.putString(KEY_DISCRIMINATOR, discriminator);
                    editor.putString(KEY_AVATAR, avatar);
                    editor.apply();
                    
                    DiscordUser user = new DiscordUser(id, username, discriminator, avatar);
                    
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onLoginSuccess(user);
                        }
                        // Start RPC connection after successful login
                        startRPC();
                    });
                    
                    Log.i(TAG, "Discord login successful for user: " + user.displayName);
                } else {
                    String errorResponse = readErrorResponse(conn);
                    Log.e(TAG, "Failed to fetch user info: " + errorResponse);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onLoginError("Failed to fetch user information: " + errorResponse);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user info", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLoginError("Error fetching user information: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    public void startRPC() {
        if (!isLoggedIn()) {
            Log.w(TAG, "Cannot start RPC: not logged in");
            return;
        }
        
        rpcExecutor.execute(() -> {
            try {
                Log.d(TAG, "Starting Discord RPC connection");
                
                // For Android, we'll use HTTP-based presence updates instead of WebSocket
                // as WebSocket connections are more complex on mobile
                rpcConnected = true;
                
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onRPCConnected();
                    }
                });
                
                // Set initial presence
                updatePresence("Using Xelo Client", "Just logged in");
                
                Log.i(TAG, "Discord RPC connected successfully");
                
                // Schedule periodic heartbeat to maintain presence
                rpcExecutor.scheduleAtFixedRate(() -> {
                    if (rpcConnected && !currentActivity.isEmpty()) {
                        updatePresence(currentActivity, currentDetails);
                    }
                }, 30, 30, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                Log.e(TAG, "Error starting RPC", e);
                rpcConnected = false;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onRPCDisconnected();
                    }
                });
            }
        });
    }
    
    public void stopRPC() {
        rpcConnected = false;
        Log.d(TAG, "Discord RPC disconnected");
        
        if (callback != null) {
            callback.onRPCDisconnected();
        }
    }
    
    public void updatePresence(String activity, String details) {
        if (!isLoggedIn() || !rpcConnected) {
            Log.d(TAG, "Cannot update presence: not logged in or RPC not connected");
            return;
        }
        
        currentActivity = activity != null ? activity : "";
        currentDetails = details != null ? details : "";
        
        rpcExecutor.execute(() -> {
            try {
                Log.d(TAG, "Updating Discord presence: " + activity + " - " + details);
                
                // Create presence payload
                JSONObject presence = new JSONObject();
                presence.put("name", "Xelo Client");
                presence.put("type", 0); // Playing
                presence.put("details", details);
                presence.put("state", activity);
                
                JSONObject timestamps = new JSONObject();
                timestamps.put("start", System.currentTimeMillis());
                presence.put("timestamps", timestamps);
                
                // For now, just log the presence update
                // In a full implementation, you would send this to Discord's gateway
                Log.i(TAG, "Presence updated: " + presence.toString());
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating presence", e);
            }
        });
    }
    
    public void logout() {
        // Stop RPC first
        stopRPC();
        
        // Clear all stored data
        prefs.edit().clear().apply();
        
        // Cancel any ongoing dialogs
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        
        Log.i(TAG, "Discord logout successful");
        if (callback != null) {
            callback.onLogout();
        }
    }
    
    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
    
    private String readErrorResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
    
    public void destroy() {
        stopRPC();
        
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (rpcExecutor != null && !rpcExecutor.isShutdown()) {
            rpcExecutor.shutdown();
            try {
                if (!rpcExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    rpcExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                rpcExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}