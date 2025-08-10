package com.origin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

public class DiscordLoginActivity extends AppCompatActivity {
    private static final String TAG = "DiscordLoginActivity";
    
    private WebView webView;
    private ProgressBar progressBar;
    private ExtendedFloatingActionButton backButton;
    private ExecutorService executor;
    private Handler mainHandler;
    private boolean isTokenExtractionInProgress = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discord_login);
        
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        setupToolbar();
        initializeViews();
        setupWebView();
        loadDiscordLogin();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Login to Discord");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }
    
    private void initializeViews() {
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        backButton = findViewById(R.id.back_button);
        
        if (webView == null) {
            Log.e(TAG, "WebView not found in layout");
            finishWithError("WebView initialization failed");
            return;
        }
        
        if (backButton != null) {
            backButton.setOnClickListener(v -> onBackPressed());
        }
    }
    
    private void setupWebView() {
        try {
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            // Removed deprecated setAppCacheEnabled method
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setUserAgentString(settings.getUserAgentString() + " XeloClient/1.0");
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting up WebView", e);
            finishWithError("WebView setup failed: " + e.getMessage());
            return;
        }
        
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "shouldOverrideUrlLoading: " + url);
                
                // Check if user reached Discord's main app page (indicates successful login)
                if (url != null && url.endsWith("/app") && !isTokenExtractionInProgress) {
                    Log.d(TAG, "User reached Discord app page, extracting token...");
                    isTokenExtractionInProgress = true;
                    webView.stopLoading();
                    extractTokenAndFinish();
                    return true;
                }
                
                return false;
            }
            
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "onPageStarted: " + url);
                progressBar.setVisibility(View.VISIBLE);
                
                // Also check here for app page
                if (url != null && url.endsWith("/app") && !isTokenExtractionInProgress) {
                    Log.d(TAG, "User reached Discord app page (onPageStarted), extracting token...");
                    isTokenExtractionInProgress = true;
                    extractTokenAndFinish();
                }
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "onPageFinished: " + url);
                progressBar.setVisibility(View.GONE);
                
                // Final check for app page
                if (url != null && url.endsWith("/app") && !isTokenExtractionInProgress) {
                    Log.d(TAG, "User reached Discord app page (onPageFinished), extracting token...");
                    isTokenExtractionInProgress = true;
                    extractTokenAndFinish();
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl + " (Code: " + errorCode + ")");
                progressBar.setVisibility(View.GONE);
                
                Toast.makeText(DiscordLoginActivity.this, 
                    "Error loading page: " + description, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void loadDiscordLogin() {
        try {
            Log.d(TAG, "Loading Discord login page");
            webView.loadUrl("https://discord.com/login");
        } catch (Exception e) {
            Log.e(TAG, "Error loading Discord login URL", e);
            finishWithError("Error loading Discord login: " + e.getMessage());
        }
    }
    
    private void extractTokenAndFinish() {
        mainHandler.post(() -> {
            progressBar.setVisibility(View.VISIBLE);
            if (backButton != null) {
                backButton.setText("Extracting token...");
                backButton.setEnabled(false);
            }
        });
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Starting token extraction process");
                
                // Wait a moment for the WebView to save data
                Thread.sleep(2000);
                
                String token = extractTokenFromStorage();
                
                if (token != null && !token.isEmpty()) {
                    Log.d(TAG, "Token extracted successfully, validating...");
                    validateTokenAndGetUserInfo(token);
                } else {
                    Log.e(TAG, "Failed to extract token from storage");
                    mainHandler.post(() -> finishWithError("Failed to extract Discord token"));
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error during token extraction", e);
                mainHandler.post(() -> finishWithError("Error extracting token: " + e.getMessage()));
            }
        });
    }
    
    private String extractTokenFromStorage() {
        try {
            // Path to WebView's local storage (similar to your friend's implementation)
            File webViewDir = new File(getFilesDir().getParentFile(), "app_webview/Default/Local Storage/leveldb");
            
            if (!webViewDir.exists()) {
                Log.w(TAG, "WebView storage directory not found");
                return null;
            }
            
            File[] logFiles = webViewDir.listFiles((dir, name) -> name.endsWith(".log"));
            
            if (logFiles == null || logFiles.length == 0) {
                Log.w(TAG, "No log files found in WebView storage");
                return null;
            }
            
            // Read the first log file (usually contains the token)
            try (BufferedReader reader = new BufferedReader(new FileReader(logFiles[0]))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("token")) {
                        // Extract token from the line
                        int tokenStart = line.indexOf("token") + 5;
                        String substring = line.substring(tokenStart);
                        int quoteStart = substring.indexOf("\"") + 1;
                        String tokenPart = substring.substring(quoteStart);
                        int quoteEnd = tokenPart.indexOf("\"");
                        
                        if (quoteEnd > 0) {
                            String token = tokenPart.substring(0, quoteEnd);
                            Log.d(TAG, "Found token in storage");
                            return token;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error reading WebView storage", e);
        }
        
        return null;
    }
    
    private void validateTokenAndGetUserInfo(String token) {
        // Similar to your friend's validation approach - test the token with Discord Gateway
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                .url("wss://gateway.discord.gg/?v=10&encoding=json")
                .build();
            
            WebSocket webSocket = client.newWebSocket(request, new WebSocketListener() {
                private boolean identificationSent = false;
                
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    Log.d(TAG, "Gateway WebSocket opened for token validation");
                }
                
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    try {
                        JSONObject json = new JSONObject(text);
                        int op = json.getInt("op");
                        
                        switch (op) {
                            case 10: // Hello
                                if (!identificationSent) {
                                    sendIdentify(webSocket, token);
                                    identificationSent = true;
                                }
                                break;
                            case 0: // Dispatch
                                String eventType = json.getString("t");
                                if ("READY".equals(eventType)) {
                                    JSONObject data = json.getJSONObject("d");
                                    JSONObject user = data.getJSONObject("user");
                                    
                                    String userId = user.getString("id");
                                    String username = user.getString("username");
                                    String discriminator = user.optString("discriminator", "0");
                                    String avatarHash = user.optString("avatar", "");
                                    
                                    String avatarUrl;
                                    if (avatarHash.isEmpty()) {
                                        int defaultAvatar = Integer.parseInt(discriminator) % 5;
                                        avatarUrl = "https://cdn.discordapp.com/embed/avatars/" + defaultAvatar + ".png";
                                    } else {
                                        avatarUrl = "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + ".png";
                                    }
                                    
                                    Log.d(TAG, "Token validation successful for user: " + username);
                                    
                                    mainHandler.post(() -> 
                                        finishWithSuccess(token, userId, username, discriminator, avatarUrl)
                                    );
                                    
                                    webSocket.close(1000, "Validation complete");
                                }
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing gateway message", e);
                        mainHandler.post(() -> finishWithError("Token validation failed"));
                        webSocket.close(1000, "Error");
                    }
                }
                
                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    Log.e(TAG, "Gateway WebSocket failed during validation", t);
                    mainHandler.post(() -> finishWithError("Failed to validate token"));
                }
            });
            
            client.dispatcher().executorService().shutdown();
            
        } catch (Exception e) {
            Log.e(TAG, "Error validating token", e);
            mainHandler.post(() -> finishWithError("Error validating token: " + e.getMessage()));
        }
    }
    
    private void sendIdentify(WebSocket webSocket, String token) {
        try {
            JSONObject properties = new JSONObject();
            properties.put("os", "Android");
            properties.put("browser", "XeloClient");
            properties.put("device", "XeloClient");
            
            JSONObject data = new JSONObject();
            data.put("token", token);
            data.put("intents", 0);
            data.put("properties", properties);
            
            JSONObject payload = new JSONObject();
            payload.put("op", 2);
            payload.put("d", data);
            
            webSocket.send(payload.toString());
            Log.d(TAG, "Identify payload sent for token validation");
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending identify payload", e);
        }
    }
    
    private void finishWithSuccess(String token, String userId, String username, String discriminator, String avatarUrl) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("access_token", token);
        resultIntent.putExtra("user_id", userId);
        resultIntent.putExtra("username", username);
        resultIntent.putExtra("discriminator", discriminator);
        resultIntent.putExtra("avatar", avatarUrl);
        
        Log.d(TAG, "Setting result OK with user data for: " + username);
        setResult(RESULT_OK, resultIntent);
        finish();
    }
    
    private void finishWithError(String error) {
        Intent errorIntent = new Intent();
        errorIntent.putExtra("error", error);
        Log.d(TAG, "Setting result CANCELED with error: " + error);
        setResult(RESULT_CANCELED, errorIntent);
        finish();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    @Override
    public void onBackPressed() {
        finishWithError("Login cancelled by user");
        super.onBackPressed();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}