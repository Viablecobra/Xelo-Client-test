package com.origin.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.net.URLDecoder;

public class DiscordLoginActivity extends AppCompatActivity {
    private static final String TAG = "DiscordLoginActivity";
    private static final String CLIENT_ID = "1403634750559752296";
    private static final String REDIRECT_URI = "https://xelo-client.github.io/discord-callback.html";
    private static final String SCOPE = "identify rpc";
    
    private WebView webView;
    private ProgressBar progressBar;
    private DiscordLoginCallback callback;
    
    public interface DiscordLoginCallback {
        void onLoginSuccess(String accessToken, String userId, String username, String discriminator, String avatar);
        void onLoginError(String error);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discord_login);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("Login to Discord");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
        
        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progress_bar);
        
        if (webView == null) {
            Log.e(TAG, "WebView not found in layout");
            finish();
            return;
        }
        
        setupWebView();
        loadDiscordLogin();
    }
    
    private void setupWebView() {
        try {
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setUserAgentString(settings.getUserAgentString() + " XeloClient/1.0");
            settings.setTextZoom(100);
            settings.setSupportMultipleWindows(false);
            settings.setLoadsImagesAutomatically(true);
            settings.setBlockNetworkImage(false);
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
            settings.setSaveFormData(true);
            settings.setSavePassword(true);
            settings.setMinimumFontSize(14);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up WebView", e);
            Toast.makeText(this, "Error setting up WebView: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Enable scrolling
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);
        
        // Clear cache and cookies
        webView.clearCache(true);
        webView.clearHistory();
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "WebView shouldOverrideUrlLoading: " + url);
                
                if (url.startsWith(REDIRECT_URI) || url.contains("#access_token=")) {
                    Log.d(TAG, "Redirect detected, handling callback: " + url);
                    handleCallback(url);
                    return true;
                }
                
                return false;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "Page finished loading: " + url);
                
                if (url.startsWith(REDIRECT_URI) || url.contains("#access_token=")) {
                    Log.d(TAG, "Redirect detected in onPageFinished: " + url);
                    handleCallback(url);
                }
            }
            
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                Log.d(TAG, "Page started loading: " + url);
                
                if (url.startsWith(REDIRECT_URI) || url.contains("#access_token=")) {
                    Log.d(TAG, "Early redirect detection: " + url);
                    handleCallback(url);
                }
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl);
                
                if (!failingUrl.startsWith(REDIRECT_URI)) {
                    Toast.makeText(DiscordLoginActivity.this, 
                        "Error loading page: " + description, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    
    private void loadDiscordLogin() {
        try {
            String authUrl = "https://discord.com/oauth2/authorize?" +
                "client_id=" + CLIENT_ID +
                "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
                "&response_type=token" +
                "&scope=" + Uri.encode(SCOPE);
            
            Log.d(TAG, "Loading Discord login URL: " + authUrl);
            webView.loadUrl(authUrl);
        } catch (Exception e) {
            Log.e(TAG, "Error loading Discord login URL", e);
            Toast.makeText(this, "Error loading Discord login: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void handleCallback(String callbackUrl) {
        Log.d(TAG, "Handling callback URL: " + callbackUrl);
        
        try {
            String accessToken = null;
            String errorParam = null;
            
            // Parse URL for access token (implicit flow returns token in URL fragment)
            if (callbackUrl.contains("#")) {
                String fragment = callbackUrl.substring(callbackUrl.indexOf("#") + 1);
                Log.d(TAG, "URL fragment: " + fragment);
                
                String[] params = fragment.split("&");
                for (String param : params) {
                    if (param.contains("=")) {
                        String[] keyValue = param.split("=", 2);
                        if (keyValue.length == 2) {
                            String key = keyValue[0];
                            String value = URLDecoder.decode(keyValue[1], "UTF-8");
                            
                            Log.d(TAG, "Param: " + key + " = " + value);
                            
                            if ("access_token".equals(key)) {
                                accessToken = value;
                            } else if ("error".equals(key)) {
                                errorParam = value;
                            }
                        }
                    }
                }
            }
            
            // Also check query parameters as fallback
            if (accessToken == null) {
                Uri uri = Uri.parse(callbackUrl);
                accessToken = uri.getQueryParameter("access_token");
                if (errorParam == null) {
                    errorParam = uri.getQueryParameter("error");
                }
            }
            
            if (errorParam != null) {
                Log.e(TAG, "Discord OAuth error: " + errorParam);
                setResult(RESULT_CANCELED, new Intent().putExtra("error", "Discord authorization failed: " + errorParam));
                finish();
                return;
            }
            
            if (accessToken != null && !accessToken.isEmpty()) {
                Log.d(TAG, "Access token received, fetching user info");
                fetchUserInfo(accessToken);
            } else {
                Log.e(TAG, "No access token received in callback");
                setResult(RESULT_CANCELED, new Intent().putExtra("error", "No access token received"));
                finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling callback", e);
            setResult(RESULT_CANCELED, new Intent().putExtra("error", "Error processing callback: " + e.getMessage()));
            finish();
        }
    }
    
    private void fetchUserInfo(String accessToken) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Fetching user information");
                
                java.net.URL url = new java.net.URL("https://discord.com/api/v10/users/@me");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("User-Agent", "XeloClient/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "User info response code: " + responseCode);
                
                if (responseCode == 200) {
                    String response = readResponse(conn);
                    Log.d(TAG, "User info response: " + response);
                    
                    org.json.JSONObject userJson = new org.json.JSONObject(response);
                    
                    String id = userJson.getString("id");
                    String username = userJson.getString("username");
                    String discriminator = userJson.optString("discriminator", "0");
                    String avatar = userJson.optString("avatar", "");
                    
                    Log.d(TAG, "User info retrieved for: " + username);
                    
                    // Return success result
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("access_token", accessToken);
                    resultIntent.putExtra("user_id", id);
                    resultIntent.putExtra("username", username);
                    resultIntent.putExtra("discriminator", discriminator);
                    resultIntent.putExtra("avatar", avatar);
                    
                    runOnUiThread(() -> {
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });
                    
                } else {
                    String errorResponse = readErrorResponse(conn);
                    Log.e(TAG, "Failed to fetch user info with code " + responseCode + ": " + errorResponse);
                    runOnUiThread(() -> {
                        setResult(RESULT_CANCELED, new Intent().putExtra("error", 
                            "Failed to fetch user information (HTTP " + responseCode + ")"));
                        finish();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching user info", e);
                runOnUiThread(() -> {
                    setResult(RESULT_CANCELED, new Intent().putExtra("error", 
                        "Error fetching user information: " + e.getMessage()));
                    finish();
                });
            }
        }).start();
    }
    
    private String readResponse(java.net.HttpURLConnection conn) throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
    
    private String readErrorResponse(java.net.HttpURLConnection conn) throws Exception {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(
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
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED, new Intent().putExtra("error", "Login cancelled by user"));
        super.onBackPressed();
    }
}