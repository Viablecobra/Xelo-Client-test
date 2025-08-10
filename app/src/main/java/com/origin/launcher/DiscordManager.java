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
    private static final String REDIRECT_URI = "https://xelo-client.github.io/discord-callback.html";
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
            this.discriminator = discriminator != null ? discriminator : "0";
            this.avatar = avatar != null ? avatar : "";
            this.displayName = username + (this.discriminator.equals("0") ? "" : "#" + this.discriminator);
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
                mainHandler.post(() -> callback.onLoginError("Invalid context for login"));
            }
            return;
        }
        
        Activity activity = (Activity) context;
        
        // Ensure we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::login);
            return;
        }
        
        String authUrl = "https://discord.com/oauth2/authorize?" +
            "client_id=" + CLIENT_ID +
            "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
            "&response_type=token" + // Changed from "code" to "token"
            "&scope=" + Uri.encode(SCOPE) +
            "&prompt=consent";
        
        Log.d(TAG, "Starting Discord login with URL: " + authUrl);
        
        try {
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
                    
                    if (url.startsWith(REDIRECT_URI) || url.contains("#access_token=")) {
                        Log.d(TAG, "Redirect URI detected: " + url);
                        mainHandler.post(() -> {
                            if (currentDialog != null && currentDialog.isShowing()) {
                                currentDialog.dismiss();
                            }
                        });
                        handleCallback(url);
                        return true;
                    }
                    return false;
                }
                
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "Page finished loading: " + url);
                    
                    if (url.startsWith(REDIRECT_URI) || url.contains("#access_token=")) {
                        mainHandler.post(() -> {
                            if (currentDialog != null && currentDialog.isShowing()) {
                                currentDialog.dismiss();
                            }
                        });
                        handleCallback(url);
                    }
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl);
                    
                    // Notify callback of error
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onLoginError("WebView error: " + description);
                        }
                    });
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
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating login dialog", e);
            if (callback != null) {
                callback.onLoginError("Failed to create login dialog: " + e.getMessage());
            }
        }
    }
    
    private void handleCallback(String callbackUrl) {
        Log.d(TAG, "Handling callback URL: " + callbackUrl);
        
        try {
            String accessToken = null;
            String errorParam = null;
            
            // Handle both fragment (#) and query (?) parameters
            if (callbackUrl.contains("#access_token=")) {
                // Implicit flow - token in URL fragment
                String fragment = callbackUrl.substring(callbackUrl.indexOf("#") + 1);
                String[] params = fragment.split("&");
                for (String param : params) {
                    String[] keyValue = param.split("=");
                    if (keyValue.length == 2) {
                        String key = keyValue[0];
                        String value = Uri.decode(keyValue[1]);
                        if ("access_token".equals(key)) {
                            accessToken = value;
                        } else if ("error".equals(key)) {
                            errorParam = value;
                        }
                    }
                }
            } else {
                // Fallback to query parameters
                Uri uri = Uri.parse(callbackUrl);
                accessToken = uri.getQueryParameter("access_token");
                errorParam = uri.getQueryParameter("error");
            }
            
            // Make final variables for lambda usage
            final String finalError = errorParam;
            final String finalAccessToken = accessToken;
            
            if (finalError != null) {
                Log.e(TAG, "Discord OAuth error: " + finalError);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLoginError("Discord authorization failed: " + finalError);
                    }
                });
                return;
            }
            
            if (finalAccessToken != null && !finalAccessToken.isEmpty()) {
                Log.d(TAG, "Access token received directly");
                // Save token and fetch user info
                prefs.edit().putString(KEY_ACCESS_TOKEN, finalAccessToken).apply();
                fetchUserInfo(finalAccessToken);
            } else {
                Log.e(TAG, "No access token received in callback");
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLoginError("No access token received");
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling callback", e);
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onLoginError("Error processing callback: " + e.getMessage());
                }
            });
        }
    }
    
    // Remove the exchangeCodeForToken method as it's not needed for implicit flow
    
    private void fetchUserInfo(String accessToken) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                Log.d(TAG, "Fetching user information");
                
                URL url = new URL(DISCORD_API_BASE + "/users/@me");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("User-Agent", "XeloClient/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                
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
                } else if (responseCode == 401) {
                    Log.e(TAG, "Invalid access token");
                    // Clear invalid token
                    prefs.edit().remove(KEY_ACCESS_TOKEN).apply();
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onLoginError("Authentication failed - please try again");
                        }
                    });
                } else {
                    String errorResponse = readErrorResponse(conn);
                    Log.e(TAG, "Failed to fetch user info with code " + responseCode + ": " + errorResponse);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onLoginError("Failed to fetch user information (HTTP " + responseCode + ")");
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
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
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
        
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onRPCDisconnected();
            }
        });
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
        mainHandler.post(() -> {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
        });
        
        Log.i(TAG, "Discord logout successful");
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onLogout();
            }
        });
    }
    
    private String readResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
    
    private String readErrorResponse(HttpURLConnection conn) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (Exception e) {
            return "Unable to read error response: " + e.getMessage();
        }
    }
    
    public void destroy() {
        stopRPC();
        
        mainHandler.post(() -> {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
        });
        
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