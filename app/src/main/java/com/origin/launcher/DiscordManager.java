package com.origin.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
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
import java.net.URLDecoder;

public class DiscordManager {
    private static final String TAG = "DiscordManager";
    private static final String CLIENT_ID = "1403634750559752296"; // Corrected Discord Application ID
    private static final String REDIRECT_URI = "https://xelo-client.github.io/discord-callback.html"; // No trailing slash - cleaner approach
    private static final String SCOPE = "identify rpc";
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
    private DiscordRPC discordRPC;
    
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
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.discordRPC = new DiscordRPC(context);
        this.discordRPC.setCallback(new DiscordRPC.DiscordRPCCallback() {
            @Override
            public void onConnected() {
                rpcConnected = true;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onRPCConnected();
                    }
                });
            }
            
            @Override
            public void onDisconnected() {
                rpcConnected = false;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onRPCDisconnected();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Discord RPC error: " + error);
                rpcConnected = false;
            }
            
            @Override
            public void onPresenceUpdated() {
                Log.d(TAG, "Discord presence updated");
            }
        });
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
        Log.d(TAG, "Starting Discord login process");
        
        if (!(context instanceof Activity)) {
            Log.e(TAG, "Context is not an Activity, cannot start login activity");
            if (callback != null) {
                mainHandler.post(() -> callback.onLoginError("Invalid context for login"));
            }
            return;
        }
        
        Activity activity = (Activity) context;
        
        try {
            // Start the dedicated Discord login activity
            Intent intent = new Intent(activity, DiscordLoginActivity.class);
            Log.d(TAG, "Starting DiscordLoginActivity with intent: " + intent);
            activity.startActivityForResult(intent, 1001); // Request code for Discord login
        } catch (Exception e) {
            Log.e(TAG, "Error starting Discord login activity", e);
            if (callback != null) {
                mainHandler.post(() -> callback.onLoginError("Error starting login: " + e.getMessage()));
            }
        }
    }
    
    public void handleLoginResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String accessToken = data.getStringExtra("access_token");
                String userId = data.getStringExtra("user_id");
                String username = data.getStringExtra("username");
                String discriminator = data.getStringExtra("discriminator");
                String avatar = data.getStringExtra("avatar");
                
                if (accessToken != null && userId != null && username != null) {
                    // Save user info
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(KEY_ACCESS_TOKEN, accessToken);
                    editor.putString(KEY_USER_ID, userId);
                    editor.putString(KEY_USERNAME, username);
                    editor.putString(KEY_DISCRIMINATOR, discriminator != null ? discriminator : "0");
                    editor.putString(KEY_AVATAR, avatar != null ? avatar : "");
                    editor.apply();
                    
                    // Set access token for RPC
                    discordRPC.setAccessToken(accessToken);
                    discordRPC.setUserId(userId);
                    
                    DiscordUser user = new DiscordUser(userId, username, discriminator, avatar);
                    
                    if (callback != null) {
                        callback.onLoginSuccess(user);
                    }
                    
                    // Start RPC connection after successful login
                    startRPC();
                    
                    Log.i(TAG, "Discord login successful for user: " + user.displayName);
                } else {
                    if (callback != null) {
                        callback.onLoginError("Invalid login response");
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                String error = data != null ? data.getStringExtra("error") : "Login cancelled";
                if (callback != null) {
                    callback.onLoginError(error);
                }
            }
        }
    }
    

    
    public void startRPC() {
        if (!isLoggedIn()) {
            Log.w(TAG, "Cannot start RPC: not logged in");
            return;
        }
        
        Log.d(TAG, "Starting Discord RPC connection");
        discordRPC.connect();
    }
    
    public void stopRPC() {
        Log.d(TAG, "Discord RPC disconnected");
        discordRPC.disconnect();
    }
    
    public void updatePresence(String activity, String details) {
        if (!isLoggedIn() || !rpcConnected) {
            Log.d(TAG, "Cannot update presence: not logged in or RPC not connected");
            return;
        }
        
        currentActivity = activity != null ? activity : "";
        currentDetails = details != null ? details : "";
        
        Log.d(TAG, "Updating Discord presence: " + activity + " - " + details);
        discordRPC.updatePresence(activity, details);
    }
    
    public void logout() {
        // Stop RPC first
        stopRPC();
        
        // Clear all stored data
        prefs.edit().clear().apply();
        
        Log.i(TAG, "Discord logout successful");
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onLogout();
            }
        });
    }
    

    
    public void destroy() {
        stopRPC();
        
        if (discordRPC != null) {
            discordRPC.destroy();
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
    }
    
    public DiscordRPC getDiscordRPC() {
        return discordRPC;
    }
}