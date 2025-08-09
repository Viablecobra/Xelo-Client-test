package com.origin.launcher;

import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;
import android.util.Log;

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
            DiscordEventHandlers handlers = new DiscordEventHandlers.Builder()
                .setReadyEventHandler((user) -> {
                    Log.i(TAG, "Discord RPC Ready! User: " + user.username + "#" + user.discriminator);
                })
                .setErroredEventHandler((errorCode, message) -> {
                    Log.e(TAG, "Discord RPC Error: " + errorCode + " - " + message);
                })
                .setDisconnectedEventHandler((errorCode, message) -> {
                    Log.w(TAG, "Discord RPC Disconnected: " + errorCode + " - " + message);
                })
                .setJoinGameEventHandler((secret) -> {
                    Log.i(TAG, "Join Game: " + secret);
                })
                .setSpectateGameEventHandler((secret) -> {
                    Log.i(TAG, "Spectate Game: " + secret);
                })
                .setJoinRequestEventHandler((request) -> {
                    Log.i(TAG, "Join Request: " + request.username + "#" + request.discriminator);
                })
                .build();

            DiscordRPC.discordInitialize(APPLICATION_ID, handlers, true);
            isInitialized = true;
            shouldRunCallbacks = true;

            // Start the callback thread
            callbackThread = new Thread(() -> {
                while (shouldRunCallbacks && !Thread.currentThread().isInterrupted()) {
                    try {
                        DiscordRPC.discordRunCallbacks();
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
            DiscordRichPresence.Builder presenceBuilder = new DiscordRichPresence.Builder(state)
                .setDetails(details)
                .setStartTimestamps(System.currentTimeMillis());

            if (largeImageKey != null && !largeImageKey.isEmpty()) {
                presenceBuilder.setLargeImage(largeImageKey, largeImageText);
            }

            DiscordRichPresence presence = presenceBuilder.build();
            DiscordRPC.discordUpdatePresence(presence);
            
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
            DiscordRPC.discordClearPresence();
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
                    callbackThread.join(1000); // Wait up to 1 second for thread to finish
                } catch (InterruptedException e) {
                    Log.w(TAG, "Interrupted while waiting for callback thread to finish");
                }
            }

            DiscordRPC.discordShutdown();
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