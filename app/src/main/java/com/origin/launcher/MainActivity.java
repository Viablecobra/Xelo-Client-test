package com.origin.launcher;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private DiscordManager discordManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Discord Manager
        discordManager = new DiscordManager(this);
        
        // Update Discord presence if logged in
        if (discordManager.isLoggedIn()) {
            discordManager.updatePresence("Using Xelo Client", "Browsing home screen");
        }

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
                
                // Update Discord presence if logged in
                if (discordManager.isLoggedIn()) {
                    discordManager.updatePresence("Using Xelo Client", presenceDetails);
                }
                
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
        if (discordManager != null && discordManager.isLoggedIn()) {
            discordManager.updatePresence("Using Xelo Client", "Active in launcher");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Update presence when app goes to background
        if (discordManager != null && discordManager.isLoggedIn()) {
            discordManager.updatePresence("Xelo Client", "Running in background");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up Discord manager
        if (discordManager != null) {
            discordManager.destroy();
        }
    }
}