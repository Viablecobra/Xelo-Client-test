package com.origin.launcher;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Discord RPC
        DiscordRPCManager.initializeRPC();
        
        // Set initial Discord presence
        DiscordRPCManager.updatePresence(
            "Using Xelo Client",
            "Browsing home screen",
            "untitled224_20250729210331", // Your uploaded Discord asset
            "Xelo Client v1.3"
        );

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String presenceDetails = "";
            
            if (item.getItemId() == R.id.navigation_home) {
                selectedFragment = new HomeFragment();
                presenceDetails = "Browsing home screen";
            } else if (item.getItemId() == R.id.navigation_dashboard) {
                selectedFragment = new DashboardFragment();
                presenceDetails = "Viewing dashboard";
            } else if (item.getItemId() == R.id.navigation_settings) {
                selectedFragment = new SettingsFragment();
                presenceDetails = "Configuring settings";
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit();
                
                // Update Discord presence based on current screen
                DiscordRPCManager.updatePresence(
                    "Using Xelo Client",
                    presenceDetails,
                    "untitled224_20250729210331",
                    "Xelo Client v1.3"
                );
                
                return true;
            }
            return false;
        });

        // Set default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update presence when app comes to foreground
        if (DiscordRPCManager.isInitialized()) {
            DiscordRPCManager.updatePresence(
                "Using Xelo Client",
                "Active in launcher",
                "untitled224_20250729210331",
                "Xelo Client v1.3"
            );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Update presence when app goes to background
        if (DiscordRPCManager.isInitialized()) {
            DiscordRPCManager.updatePresence(
                "Xelo Client",
                "Running in background",
                "untitled224_20250729210331",
                "Xelo Client v1.3"
            );
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown Discord RPC when app is destroyed
        DiscordRPCManager.shutdownRPC();
    }
}