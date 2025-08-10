package com.origin.launcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.Button;
import androidx.fragment.app.Fragment;
import android.app.ActivityManager;
import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.text.ParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

public class SettingsFragment extends Fragment implements DiscordManager.DiscordLoginCallback {

    private EditText packageNameEdit;
    private TextView deviceModelText;
    private TextView androidVersionText;
    private TextView buildNumberText;
    private TextView deviceManufacturerText;
    private TextView deviceArchitectureText;
    private TextView screenResolutionText;
    private TextView totalMemoryText;
    private LinearLayout commitsContainer;
    private LinearLayout githubButton;
    private LinearLayout discordButton;
    
    // Discord components
    private com.google.android.material.button.MaterialButton discordLoginButton;
    private TextView discordStatusText;
    private TextView discordUserText;
    private DiscordManager discordManager;
    
    private static final String PREF_PACKAGE_NAME = "mc_package_name";
    private static final String DEFAULT_PACKAGE_NAME = "com.mojang.minecraftpe";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/Xelo-Client/Xelo-Client/commits";
    private static final String GITHUB_URL = "https://github.com/Xelo-Client/Xelo-Client";
    private static final String DISCORD_URL = "https://discord.gg/CHUchrEWwc";
    private static final String TAG = "SettingsFragment";
    private ExecutorService executor;
    private Handler mainHandler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        
        packageNameEdit = view.findViewById(R.id.mc_pkgname);
        
        // Initialize device information TextViews
        deviceModelText = view.findViewById(R.id.device_model);
        androidVersionText = view.findViewById(R.id.android_version);
        buildNumberText = view.findViewById(R.id.build_number);
        deviceManufacturerText = view.findViewById(R.id.device_manufacturer);
        deviceArchitectureText = view.findViewById(R.id.device_architecture);
        screenResolutionText = view.findViewById(R.id.screen_resolution);
        totalMemoryText = view.findViewById(R.id.total_memory);
        
        // Initialize commits container
        commitsContainer = view.findViewById(R.id.commits_container);
        
        // Initialize social media buttons
        githubButton = view.findViewById(R.id.github_button);
        discordButton = view.findViewById(R.id.discord_button);
        
        // Initialize Discord components
        discordLoginButton = view.findViewById(R.id.discord_login_button);
        discordStatusText = view.findViewById(R.id.discord_status_text);
        discordUserText = view.findViewById(R.id.discord_user_text);
        
        // Initialize Discord manager
        discordManager = new DiscordManager(getActivity()); // Use getActivity() instead of getContext()
        discordManager.setCallback(this);
        
        // Initialize the global RPC helper
        DiscordRPCHelper.getInstance().initialize(discordManager);
        
        // Initialize executor and handler
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Load saved package name
        SharedPreferences prefs = requireContext().getSharedPreferences("settings", 0);
        String savedPackageName = prefs.getString(PREF_PACKAGE_NAME, DEFAULT_PACKAGE_NAME);
        packageNameEdit.setText(savedPackageName);
        
        // Save package name when text changes
        packageNameEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                savePackageName();
            }
        });
        
        // Set up button click listeners
        setupButtonListeners();
        
        // Setup Discord login button
        setupDiscordButton();
        
        // Update Discord UI
        updateDiscordUI();
        
        // If already logged in, start RPC
        if (discordManager.isLoggedIn()) {
            discordManager.startRPC();
        }
        
        // Load device information
        loadDeviceInformation();
        
        // Load commits
        loadCommits();
        
        return view;
    }
    
    private void setupButtonListeners() {
        githubButton.setOnClickListener(v -> openUrl(GITHUB_URL, "GitHub"));
        discordButton.setOnClickListener(v -> openUrl(DISCORD_URL, "Discord"));
    }
    
    private void setupDiscordButton() {
        discordLoginButton.setOnClickListener(v -> {
            if (discordManager.isLoggedIn()) {
                // Show logout confirmation
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                    .setTitle("Logout from Discord")
                    .setMessage("Are you sure you want to logout from Discord? This will also disconnect Rich Presence.")
                    .setPositiveButton("Logout", (dialog, which) -> {
                        discordManager.logout();
                        updateDiscordUI();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            } else {
                // Disable button during login process
                discordLoginButton.setEnabled(false);
                discordLoginButton.setText("Connecting...");
                discordManager.login();
            }
        });
    }
    
    private void updateDiscordUI() {
        if (discordManager.isLoggedIn()) {
            DiscordManager.DiscordUser user = discordManager.getCurrentUser();
            discordStatusText.setText("Connected");
            discordStatusText.setTextColor(0xFF4CAF50); // Green
            discordUserText.setText("Logged in as: " + user.displayName);
            discordUserText.setVisibility(View.VISIBLE);
            discordLoginButton.setText("Logout");
            discordLoginButton.setEnabled(true);
            
            // Set red color for logout button
            discordLoginButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336)); // Red for logout
        } else {
            discordStatusText.setText("Not connected");
            discordStatusText.setTextColor(0xFFF44336); // Red
            discordUserText.setVisibility(View.GONE);
            discordLoginButton.setText("Login with Discord");
            discordLoginButton.setEnabled(true);
            
            // Set Discord brand color for login
            discordLoginButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF5865F2)); // Discord blue
        }
    }
    
    // Discord callback methods
    @Override
    public void onLoginSuccess(DiscordManager.DiscordUser user) {
        if (isAdded()) {
            Toast.makeText(getContext(), "Successfully logged in as " + user.displayName, Toast.LENGTH_SHORT).show();
            updateDiscordUI();
            
            Log.i(TAG, "Discord login successful, updating presence");
        }
    }
    
    @Override
    public void onLoginError(String error) {
        if (isAdded()) {
            Toast.makeText(getContext(), "Discord login failed: " + error, Toast.LENGTH_LONG).show();
            updateDiscordUI();
            Log.e(TAG, "Discord login error: " + error);
        }
    }
    
    @Override
    public void onLogout() {
        if (isAdded()) {
            Toast.makeText(getContext(), "Logged out from Discord", Toast.LENGTH_SHORT).show();
            updateDiscordUI();
            Log.i(TAG, "Discord logout successful");
        }
    }
    
    @Override
    public void onRPCConnected() {
        if (isAdded()) {
            Log.i(TAG, "Discord RPC connected");
            // Show a subtle indication that RPC is working
            if (discordUserText.getVisibility() == View.VISIBLE) {
                String currentText = discordUserText.getText().toString();
                if (!currentText.contains("Rich Presence")) {
                    discordUserText.setText(currentText + " • Rich Presence Active");
                }
            }
            
            // Set initial presence when RPC connects
            discordManager.updatePresence("Browsing Settings", "Configuring Xelo Client");
        }
    }
    
    @Override
    public void onRPCDisconnected() {
        if (isAdded()) {
            Log.i(TAG, "Discord RPC disconnected");
            // Remove RPC indication from user text
            if (discordUserText.getVisibility() == View.VISIBLE) {
                String currentText = discordUserText.getText().toString();
                if (currentText.contains(" • Rich Presence Active")) {
                    discordUserText.setText(currentText.replace(" • Rich Presence Active", ""));
                }
            }
        }
    }
    
    private void openUrl(String url, String appName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening " + appName + " URL", e);
            if (isAdded()) {
                Toast.makeText(getContext(), "Unable to open " + appName, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void savePackageName() {
        String packageName = packageNameEdit.getText().toString().trim();
        if (!packageName.isEmpty()) {
            SharedPreferences prefs = requireContext().getSharedPreferences("settings", 0);
            prefs.edit().putString(PREF_PACKAGE_NAME, packageName).apply();
        }
    }
    
    private void loadDeviceInformation() {
        // Device Model
        String model = Build.MODEL;
        deviceModelText.setText("Model: " + model);
        
        // Android Version
        String androidVersion = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        androidVersionText.setText("Android Version: " + androidVersion);
        
        // Build Number
        String buildNumber = Build.DISPLAY;
        buildNumberText.setText("Build Number: " + buildNumber);
        
        // Device Manufacturer
        String manufacturer = Build.MANUFACTURER;
        deviceManufacturerText.setText("Manufacturer: " + manufacturer);
        
        // Device Architecture
        String architecture = Build.SUPPORTED_ABIS[0];
        deviceArchitectureText.setText("Architecture: " + architecture);
        
        // Screen Resolution
        DisplayMetrics displayMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        String resolution = displayMetrics.widthPixels + " x " + displayMetrics.heightPixels;
        screenResolutionText.setText("Screen Resolution: " + resolution);
        
        // Total Memory
        ActivityManager activityManager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long totalMemoryMB = memoryInfo.totalMem / (1024 * 1024);
        totalMemoryText.setText("Total Memory: " + totalMemoryMB + " MB");
    }
    
    private void loadCommits() {
        executor.execute(() -> {
            String result = fetchCommitsFromApi();
            mainHandler.post(() -> {
                if (result != null && isAdded()) {
                    parseAndDisplayCommits(result);
                } else if (isAdded()) {
                    displayErrorMessage();
                }
            });
        });
    }
    
    private String fetchCommitsFromApi() {
        try {
            URL url = new URL(GITHUB_API_URL + "?per_page=5");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                Log.e(TAG, "HTTP Error: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching commits", e);
            return null;
        }
    }
    
    private void parseAndDisplayCommits(String jsonResponse) {
        try {
            JSONArray commits = new JSONArray(jsonResponse);
            commitsContainer.removeAllViews();
            
            for (int i = 0; i < Math.min(commits.length(), 5); i++) {
                JSONObject commit = commits.getJSONObject(i);
                JSONObject commitData = commit.getJSONObject("commit");
                JSONObject author = commitData.getJSONObject("author");
                
                String message = commitData.getString("message");
                String authorName = author.getString("name");
                String sha = commit.getString("sha").substring(0, 7);
                String dateStr = author.getString("date");
                
                // Parse and format date
                String formattedDate = formatDate(dateStr);
                
                // Create commit view
                createCommitView(message, authorName, sha, formattedDate);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing commits", e);
            displayErrorMessage();
        }
    }
    
    private void createCommitView(String message, String author, String sha, String date) {
        // Create main container
        LinearLayout commitLayout = new LinearLayout(getContext());
        commitLayout.setOrientation(LinearLayout.VERTICAL);
        commitLayout.setPadding(0, 16, 0, 16);
        
        // Create header with author and commit info
        LinearLayout headerLayout = new LinearLayout(getContext());
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        // Author name
        TextView authorText = new TextView(getContext());
        authorText.setText(author);
        authorText.setTextSize(14);
        authorText.setTextColor(0xFFFFFFFF); // White text
        
        // Bullet separator
        TextView bulletText = new TextView(getContext());
        bulletText.setText(" • ");
        bulletText.setTextSize(14);
        bulletText.setTextColor(0xFF888888); // Gray text
        
        // SHA
        TextView shaText = new TextView(getContext());
        shaText.setText(sha);
        shaText.setTextSize(14);
        shaText.setTextColor(0xFF888888); // Gray text
        
        // Date
        TextView dateText = new TextView(getContext());
        dateText.setText(date);
        dateText.setTextSize(12);
        dateText.setTextColor(0xFF888888); // Gray text
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dateParams.weight = 1;
        dateParams.gravity = android.view.Gravity.END;
        dateText.setLayoutParams(dateParams);
        dateText.setGravity(android.view.Gravity.END);
        
        headerLayout.addView(authorText);
        headerLayout.addView(bulletText);
        headerLayout.addView(shaText);
        headerLayout.addView(dateText);
        
        // Commit message
        TextView messageText = new TextView(getContext());
        messageText.setText(message);
        messageText.setTextSize(16);
        messageText.setTextColor(0xFFFFFFFF); // White text
        messageText.setPadding(0, 8, 0, 0);
        
        commitLayout.addView(headerLayout);
        commitLayout.addView(messageText);
        
        commitsContainer.addView(commitLayout);
        
        // Add separator line (except for last item)
        View separator = new View(getContext());
        separator.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        separator.setBackgroundColor(0xFF333333); // Dark gray separator
        LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1);
        separatorParams.setMargins(0, 16, 0, 0);
        separator.setLayoutParams(separatorParams);
        commitsContainer.addView(separator);
    }
    
    private String formatDate(String isoDate) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yy", Locale.US);
            Date date = inputFormat.parse(isoDate);
            return outputFormat.format(date);
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing date: " + isoDate, e);
            return "Unknown";
        }
    }
    
    private void displayErrorMessage() {
        commitsContainer.removeAllViews();
        TextView errorText = new TextView(getContext());
        errorText.setText("Unable to load commits. Check your internet connection.");
        errorText.setTextColor(0xFF888888);
        errorText.setTextSize(14);
        errorText.setPadding(0, 16, 0, 16);
        commitsContainer.addView(errorText);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Update Discord RPC when fragment resumes
        DiscordRPCHelper.getInstance().updateMenuPresence("Settings");
    }
    
    @Override
    public void onPause() {
        super.onPause();
        savePackageName();
        
        // Update Discord RPC when leaving settings
        DiscordRPCHelper.getInstance().updateIdlePresence();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        if (discordManager != null) {
            discordManager.destroy();
        }
    }
    
    // Helper method to update Discord presence from other activities
    public void updateDiscordPresence(String activity, String details) {
        if (discordManager != null && discordManager.isRPCConnected()) {
            discordManager.updatePresence(activity, details);
        }
    }
    
    // Method to get the Discord manager instance for use in other fragments/activities
    public DiscordManager getDiscordManager() {
        return discordManager;
    }
}