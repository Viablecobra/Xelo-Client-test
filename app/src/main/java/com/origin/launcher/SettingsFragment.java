package com.origin.launcher;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import android.app.ActivityManager;
import android.content.Context;
import android.util.DisplayMetrics;


public class SettingsFragment extends Fragment {

    private EditText packageNameEdit;
    private TextView deviceModelText;
    private TextView androidVersionText;
    private TextView buildNumberText;
    private TextView deviceManufacturerText;
    private TextView deviceArchitectureText;
    private TextView screenResolutionText;
    private TextView totalMemoryText;
    private static final String PREF_PACKAGE_NAME = "mc_package_name";
    private static final String DEFAULT_PACKAGE_NAME = "com.mojang.minecraftpe";

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
        
        // Load device information
        loadDeviceInformation();
        
        return view;
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
    
    @Override
    public void onPause() {
        super.onPause();
        savePackageName();
    }
}