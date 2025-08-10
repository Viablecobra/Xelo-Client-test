package com.origin.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.framing.Framedata;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Handler;
import android.os.Looper;
import java.util.Random;

public class DiscordRPC {
    private static final String TAG = "DiscordRPC";
    private static final String CLIENT_ID = "1403634750559752296";
    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";
    
    private static final String PREFS_NAME = "discord_rpc_prefs";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_USER_ID = "user_id";
    
    private Context context;
    private SharedPreferences prefs;
    private WebSocketClient webSocket;
    private ExecutorService executor;
    private ScheduledExecutorService heartbeatExecutor;
    private Handler mainHandler;
    private DiscordRPCCallback callback;
    
    // RPC state
    private AtomicBoolean connected = new AtomicBoolean(false);
    private AtomicLong sequence = new AtomicLong(0);
    private AtomicLong heartbeatInterval = new AtomicLong(41250); // Default interval
    private String sessionId;
    private String currentActivity = "";
    private String currentDetails = "";
    private long startTime;
    
    // Heartbeat tracking
    private volatile boolean heartbeatAcknowledged = true;
    private volatile long lastHeartbeat = 0;
    
    public interface DiscordRPCCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onPresenceUpdated();
    }
    
    public DiscordRPC(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.startTime = System.currentTimeMillis();
    }
    
    public void setCallback(DiscordRPCCallback callback) {
        this.callback = callback;
    }
    
    public boolean isConnected() {
        return connected.get();
    }
    
    public void connect() {
        if (connected.get()) {
            Log.d(TAG, "Already connected to Discord RPC");
            return;
        }
        
        String accessToken = prefs.getString(KEY_ACCESS_TOKEN, null);
        if (accessToken == null) {
            Log.e(TAG, "No access token available for RPC connection");
            if (callback != null) {
                mainHandler.post(() -> callback.onError("No access token available"));
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Connecting to Discord Gateway...");
                
                webSocket = new WebSocketClient(new URI(GATEWAY_URL)) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        Log.d(TAG, "WebSocket connection opened");
                        connected.set(true);
                        
                        // Send identify payload
                        sendIdentify(accessToken);
                    }
                    
                    @Override
                    public void onMessage(String message) {
                        Log.d(TAG, "Received message: " + message);
                        handleMessage(message);
                    }
                    
                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        Log.d(TAG, "WebSocket connection closed: " + code + " - " + reason);
                        connected.set(false);
                        heartbeatAcknowledged = true;
                        
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onDisconnected();
                            }
                        });
                    }
                    
                    @Override
                    public void onError(Exception ex) {
                        Log.e(TAG, "WebSocket error", ex);
                        connected.set(false);
                        
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onError("WebSocket error: " + ex.getMessage());
                            }
                        });
                    }
                };
                
                webSocket.connect();
                
            } catch (Exception e) {
                Log.e(TAG, "Error connecting to Discord RPC", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError("Connection error: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    private void sendIdentify(String accessToken) {
        try {
            JSONObject identify = new JSONObject();
            identify.put("op", 2); // Identify opcode
            
            JSONObject data = new JSONObject();
            data.put("token", accessToken);
            data.put("properties", new JSONObject()
                .put("os", "Android")
                .put("browser", "XeloClient")
                .put("device", "XeloClient"));
            
            data.put("presence", new JSONObject()
                .put("status", "online")
                .put("since", 0)
                .put("activities", new JSONArray())
                .put("afk", false));
            
            identify.put("d", data);
            
            Log.d(TAG, "Sending identify payload: " + identify.toString());
            webSocket.send(identify.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending identify payload", e);
        }
    }
    
    private void handleMessage(String message) {
        try {
            JSONObject payload = new JSONObject(message);
            int op = payload.getInt("op");
            
            switch (op) {
                case 10: // Hello
                    handleHello(payload);
                    break;
                case 11: // Heartbeat ACK
                    handleHeartbeatAck();
                    break;
                case 0: // Dispatch
                    handleDispatch(payload);
                    break;
                default:
                    Log.d(TAG, "Unhandled opcode: " + op);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling message", e);
        }
    }
    
    private void handleHello(JSONObject payload) {
        try {
            JSONObject data = payload.getJSONObject("d");
            long interval = data.getLong("heartbeat_interval");
            heartbeatInterval.set(interval);
            
            Log.d(TAG, "Received hello, heartbeat interval: " + interval);
            
            // Start heartbeat
            startHeartbeat();
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling hello", e);
        }
    }
    
    private void handleHeartbeatAck() {
        Log.d(TAG, "Heartbeat acknowledged");
        heartbeatAcknowledged = true;
    }
    
    private void handleDispatch(JSONObject payload) {
        try {
            String event = payload.getString("t");
            JSONObject data = payload.getJSONObject("d");
            
            if (payload.has("s") && !payload.isNull("s")) {
                sequence.set(payload.getLong("s"));
            }
            
            switch (event) {
                case "READY":
                    handleReady(data);
                    break;
                case "PRESENCE_UPDATE":
                    handlePresenceUpdate(data);
                    break;
                default:
                    Log.d(TAG, "Unhandled dispatch event: " + event);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling dispatch", e);
        }
    }
    
    private void handleReady(JSONObject data) {
        try {
            sessionId = data.getString("session_id");
            Log.d(TAG, "Discord RPC ready, session ID: " + sessionId);
            
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onConnected();
                }
            });
            
            // Set initial presence
            updatePresence("Using Xelo Client", "Just connected");
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling ready", e);
        }
    }
    
    private void handlePresenceUpdate(JSONObject data) {
        Log.d(TAG, "Presence update received: " + data.toString());
    }
    
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (connected.get() && webSocket != null && webSocket.isOpen()) {
                try {
                    if (!heartbeatAcknowledged) {
                        Log.w(TAG, "Previous heartbeat not acknowledged, reconnecting...");
                        reconnect();
                        return;
                    }
                    
                    JSONObject heartbeat = new JSONObject();
                    heartbeat.put("op", 1); // Heartbeat opcode
                    heartbeat.put("d", sequence.get());
                    
                    Log.d(TAG, "Sending heartbeat, sequence: " + sequence.get());
                    webSocket.send(heartbeat.toString());
                    
                    heartbeatAcknowledged = false;
                    lastHeartbeat = System.currentTimeMillis();
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error sending heartbeat", e);
                }
            }
        }, 0, heartbeatInterval.get(), TimeUnit.MILLISECONDS);
    }
    
    public void updatePresence(String activity, String details) {
        if (!connected.get() || webSocket == null || !webSocket.isOpen()) {
            Log.d(TAG, "Cannot update presence: not connected");
            return;
        }
        
        currentActivity = activity != null ? activity : "";
        currentDetails = details != null ? details : "";
        
        executor.execute(() -> {
            try {
                JSONObject presence = new JSONObject();
                presence.put("op", 3); // Update Presence opcode
                
                JSONObject data = new JSONObject();
                data.put("since", startTime);
                data.put("status", "online");
                data.put("afk", false);
                
                JSONArray activities = new JSONArray();
                if (!currentActivity.isEmpty() || !currentDetails.isEmpty()) {
                    JSONObject activityObj = new JSONObject();
                    activityObj.put("name", currentActivity.isEmpty() ? "Xelo Client" : currentActivity);
                    activityObj.put("type", 0); // Playing
                    
                    if (!currentDetails.isEmpty()) {
                        activityObj.put("details", currentDetails);
                    }
                    
                    JSONObject timestamps = new JSONObject();
                    timestamps.put("start", startTime);
                    activityObj.put("timestamps", timestamps);
                    
                    activities.put(activityObj);
                }
                
                data.put("activities", activities);
                presence.put("d", data);
                
                Log.d(TAG, "Updating presence: " + presence.toString());
                webSocket.send(presence.toString());
                
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onPresenceUpdated();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error updating presence", e);
            }
        });
    }
    
    private void reconnect() {
        Log.d(TAG, "Reconnecting to Discord RPC...");
        disconnect();
        
        // Wait a bit before reconnecting
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        connect();
    }
    
    public void disconnect() {
        Log.d(TAG, "Disconnecting from Discord RPC...");
        
        connected.set(false);
        heartbeatAcknowledged = true;
        
        if (webSocket != null) {
            webSocket.close();
            webSocket = null;
        }
        
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
            try {
                if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    heartbeatExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onDisconnected();
            }
        });
    }
    
    public void destroy() {
        disconnect();
        
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
    
    public void setAccessToken(String accessToken) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
    }
    
    public void setUserId(String userId) {
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }
    
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }
    
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }
}