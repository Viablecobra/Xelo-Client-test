package com.origin.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.AlertDialog;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class DiscordManager {
    private static final String TAG = "DiscordManager";
    private static final String CLIENT_ID = "1403634750557752296"; // Your Discord Application ID
    private static final String CLIENT_SECRET = ""; // Optional: Add your client secret here for more security
    private static final String REDIRECT_URI = "https://xelo-client.github.io/discord-callback.html/";
    private static final String SCOPE = "identify";
    private static final String DISCORD_API_BASE = "https://discord.com/api/v10";
    
    private static final String PREFS_NAME = "discord_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISCRIMINATOR = "discriminator";
    private static final String KEY_AVATAR = "avatar";
    
    private Context context;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;
    private DiscordLoginCallback callback;
    
    public interface DiscordLoginCallback {
        void onLoginSuccess(DiscordUser user);
        void onLoginError(String error);
        void onLogout();
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
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void setCallback(DiscordLoginCallback callback) {
        this.callback = callback;
    }
    
    public boolean isLoggedIn() {
        return prefs.contains(KEY_ACCESS_TOKEN) && prefs.contains(KEY_USER_ID);
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
        String authUrl = "https://discord.com/api/oauth2/authorize?" +
            "client_id=" + CLIENT_ID +
            "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
            "&response_type=code" +
            "&scope=" + Uri.encode(SCOPE);
        
        // Create WebView with better settings
        WebView webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        
        // Create full-screen dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        AlertDialog dialog = builder
            .setView(webView)
            .setNegativeButton("Cancel", (d, which) -> {
                if (callback != null) {
                    callback.onLoginError("Login cancelled by user");
                }
            })
            .create();
        
        // Make dialog full screen
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
            
            // Enable keyboard support
            dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            );
        }
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(REDIRECT_URI)) {
                    dialog.dismiss();
                    handleCallback(url);
                    return true;
                }
                return false;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.startsWith(REDIRECT_URI)) {
                    dialog.dismiss();
                    handleCallback(url);
                }
            }
            
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // Show loading indicator or update title
                if (dialog.getWindow() != null) {
                    dialog.setTitle("Loading Discord Login...");
                }
            }
        });
        
        webView.loadUrl(authUrl);
        dialog.show();
        
        // Final adjustment after dialog is shown
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
    }
    
    private void handleCallback(String callbackUrl) {
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
            exchangeCodeForToken(code);
        } else {
            Log.e(TAG, "No authorization code received");
            if (callback != null) {
                callback.onLoginError("No authorization code received");
            }
        }
    }
    
    private void exchangeCodeForToken(String code) {
        executor.execute(() -> {
            try {
                // Exchange code for access token
                String tokenData = "client_id=" + CLIENT_ID +
                    "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
                    "&grant_type=authorization_code" +
                    "&code=" + code;
                
                URL url = new URL("https://discord.com/api/oauth2/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                
                conn.getOutputStream().write(tokenData.getBytes());
                
                if (conn.getResponseCode() == 200) {
                    String response = readResponse(conn);
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
                            callback.onLoginError("Failed to exchange authorization code");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error exchanging code for token", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLoginError("Network error during login");
                    }
                });
            }
        });
    }
    
    private void fetchUserInfo(String accessToken) {
        executor.execute(() -> {
            try {
                URL url = new URL(DISCORD_API_BASE + "/users/@me");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                
                if (conn.getResponseCode() == 200) {
                    String response = readResponse(conn);
                    JSONObject userJson = new JSONObject(response);
                    
                    String id = userJson.getString("id");
                    String username = userJson.getString("username");
                    String discriminator = userJson.optString("discriminator", "0");
                    String avatar = userJson.optString("avatar", "");
                    
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
                    });
                    
                    Log.i(TAG, "Discord login successful for user: " + user.displayName);
                } else {
                    String errorResponse = readErrorResponse(conn);
                    Log.e(TAG, "Failed to fetch user info: " + errorResponse);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onLoginError("Failed to fetch user information");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user info", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onLoginError("Error fetching user information");
                    }
                });
            }
        });
    }
    
    public void logout() {
        // Clear all stored data
        prefs.edit().clear().apply();
        
        Log.i(TAG, "Discord logout successful");
        if (callback != null) {
            callback.onLogout();
        }
    }
    
    public void updatePresence(String activity, String details) {
        // This is a placeholder for future Discord Rich Presence implementation
        // For now, we just log the activity
        Log.d(TAG, "Would update presence: " + activity + " - " + details);
    }
    
    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
    
    private String readErrorResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
    
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}