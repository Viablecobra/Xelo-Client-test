package com.mojang.minecraftpe;

import android.app.NativeActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

public abstract class MainActivity extends NativeActivity implements View.OnKeyListener, FilePickerManagerHandler {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Proper implementation - no longer throwing stub exception
    }
    
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // Default implementation - can be overridden by subclasses
        return false;
    }
    
    @Override
    public void startPickerActivity(Intent intent, int i) {
        // Default implementation - can be overridden by subclasses
        if (intent != null) {
            startActivityForResult(intent, i);
        }
    }
}
