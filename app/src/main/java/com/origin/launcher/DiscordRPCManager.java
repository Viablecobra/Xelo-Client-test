package com.origin.launcher;

import android.util.Log;
import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;

public class DiscordRPCManager {
    private static final String TAG = "DiscordRPC";
    // Your Discord Application ID
    private static final String APPLICATION_ID = "1403634750557752296";
    private static boolean isInitialized = false;
    private static Thread callbackThread;
    private static boolean shouldRunCallbacks = true;

    public static void initializeRPC() {
        if (isInitialized) {
            Log.d(TAG, "Discord RPC already initialized");
            return;
        }

        try {
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            handlers.ready = (user) -> Log.i(TAG, "Discord RPC Ready! User: " + user.username + "#" + user.discriminator);
            handlers.errored = (errorCode, message) -> Log.e(TAG, "Discord RPC Error: " + errorCode + " - " + message);
            handlers.disconnected = (errorCode, message) -> Log.w(TAG, "Discord RPC Disconnected: " + errorCode + " - " + message);

            DiscordRPC.INSTANCE.Discord_Initialize(APPLICATION_ID, handlers, true, "");
            isInitialized = true;
            shouldRunCallbacks = true;

            // Start the callback thread
            callbackThread = new Thread(() -> {
                while (shouldRunCallbacks && !Thread.currentThread().isInterrupted()) {
                    try {
                        DiscordRPC.INSTANCE.Discord_RunCallbacks();
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Callback thread interrupted");
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error in Discord RPC callbacks", e);
                    }
                }
            }, "Discord-RPC-Callback-Handler");
            callbackThread.start();

            Log.i(TAG, "Discord RPC initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Discord RPC", e);
            isInitialized = false;
        }
    }

    public static void updatePresence(String state, String details, String largeImageKey, String largeImageText) {
        if (!isInitialized) {
            Log.w(TAG, "Discord RPC not initialized, initializing now...");
            initializeRPC();
        }

        try {
            DiscordRichPresence presence = new DiscordRichPresence();
            presence.startTimestamp = System.currentTimeMillis() / 1000;
            presence.details = details;
            presence.state = state;
            
            if (largeImageKey != null && !largeImageKey.isEmpty()) {
                presence.largeImageKey = largeImageKey;
                presence.largeImageText = largeImageText;
            }

            DiscordRPC.INSTANCE.Discord_UpdatePresence(presence);
            
            Log.d(TAG, "Discord presence updated - State: " + state + ", Details: " + details);
        } catch (Exception e) {
            Log.e(TAG, "Failed to update Discord presence", e);
        }
    }

    public static void updatePresence(String state, String details) {
        updatePresence(state, details, null, null);
    }

    public static void clearPresence() {
        if (!isInitialized) {
            return;
        }

        try {
            DiscordRPC.INSTANCE.Discord_ClearPresence();
            Log.d(TAG, "Discord presence cleared");
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear Discord presence", e);
        }
    }

    public static void shutdownRPC() {
        if (!isInitialized) {
            return;
        }

        try {
            shouldRunCallbacks = false;
            
            if (callbackThread != null && callbackThread.isAlive()) {
                callbackThread.interrupt();
                try {
                    callbackThread.join(1000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for callback thread to finish");
                }
            }

            DiscordRPC.INSTANCE.Discord_Shutdown();
            isInitialized = false;
            
            Log.i(TAG, "Discord RPC shutdown successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error during Discord RPC shutdown", e);
        }
    }

    public static boolean isInitialized() {
        return isInitialized;
    }
}