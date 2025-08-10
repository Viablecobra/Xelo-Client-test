# Discord RPC Improvements for Xelo Client

## Overview
This document outlines the improvements made to the Discord RPC functionality in the Xelo Client Android app to address the issues with the WebView dialog size and implement proper Discord Rich Presence.

## Issues Fixed

### 1. WebView Dialog Size Problem
**Problem**: The Discord login WebView was too small and users couldn't scroll or interact properly with the login form.

**Solution**: 
- Created a dedicated `DiscordLoginActivity` with a full-screen WebView
- Removed the problematic dialog-based approach
- Implemented proper activity result handling for login flow

### 2. Discord RPC Not Working
**Problem**: Discord Rich Presence wasn't actually connecting to Discord's servers or updating presence properly.

**Solution**:
- Implemented proper Discord Gateway WebSocket connection using `DiscordRPC.java`
- Added real-time presence updates with proper Discord API protocol
- Implemented heartbeat mechanism to maintain connection
- Added proper error handling and reconnection logic

## New Files Created

### 1. `DiscordLoginActivity.java`
- Full-screen activity for Discord OAuth login
- Proper WebView configuration for mobile login
- Activity result handling for login success/failure

### 2. `DiscordRPC.java`
- WebSocket-based Discord Gateway connection
- Real Discord Rich Presence implementation
- Heartbeat mechanism and connection management
- Proper presence update protocol

### 3. `activity_discord_login.xml`
- Layout for the Discord login activity
- Full-screen WebView with progress bar
- Material Design toolbar

## Updated Files

### 1. `DiscordManager.java`
- Removed dialog-based login approach
- Integrated with new `DiscordLoginActivity`
- Added proper activity result handling
- Integrated with new `DiscordRPC` for real presence updates

### 2. `DiscordRPCHelper.java`
- Enhanced to work with both old and new RPC systems
- Added support for direct DiscordRPC instance
- Improved presence update logic

### 3. `SettingsFragment.java`
- Added `onActivityResult` handling for Discord login
- Updated to work with new login flow
- Enhanced Discord RPC initialization

### 4. `build.gradle`
- Added WebSocket library: `org.java-websocket:Java-WebSocket:1.5.3`
- Added HTTP client: `com.squareup.okhttp3:okhttp:4.12.0`
- Added logging interceptor: `com.squareup.okhttp3:logging-interceptor:4.12.0`

### 5. `AndroidManifest.xml`
- Added `DiscordLoginActivity` declaration
- Proper activity configuration for WebView

## Features

### Discord Login
- Full-screen WebView for better user experience
- Proper OAuth flow with Discord
- Automatic user info retrieval
- Error handling and user feedback

### Discord Rich Presence
- Real-time connection to Discord Gateway
- Automatic presence updates
- Heartbeat mechanism for connection stability
- Proper reconnection on network issues
- Support for activity details and timestamps

### Presence Updates
- "Using Xelo Client" - Default state
- "In Menu" - When navigating app menus
- "Playing Minecraft" - When game is active
- "Idle" - When app is in background

## Technical Details

### WebSocket Connection
- Connects to `wss://gateway.discord.gg/?v=10&encoding=json`
- Implements Discord Gateway protocol
- Handles identify, heartbeat, and presence update operations

### OAuth Flow
- Uses Discord OAuth2 implicit flow
- Redirects to `https://xelo-client.github.io/discord-callback.html`
- Retrieves access token and user information

### Presence Protocol
- Sends proper Discord presence update payloads
- Includes activity name, details, timestamps
- Maintains connection with periodic heartbeats

## Usage

1. **Login**: User clicks "Login with Discord" in settings
2. **OAuth**: Full-screen WebView opens for Discord authorization
3. **Connection**: After successful login, RPC automatically connects
4. **Presence**: App automatically updates Discord presence based on user activity
5. **Logout**: User can logout which disconnects RPC and clears stored data

## Dependencies

```gradle
implementation 'org.java-websocket:Java-WebSocket:1.5.3'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
```

## Permissions

The following permissions are required:
- `INTERNET` - For Discord API and WebSocket connections
- `ACCESS_NETWORK_STATE` - For network connectivity checks
- `ACCESS_WIFI_STATE` - For WebView functionality
- `CHANGE_NETWORK_STATE` - For WebView functionality

## Testing

To test the implementation:
1. Build and install the app
2. Go to Settings → Discord Integration
3. Click "Login with Discord"
4. Complete OAuth flow in full-screen WebView
5. Verify Rich Presence appears in Discord
6. Navigate through app to see presence updates
7. Test logout functionality

## Future Enhancements

- Add support for custom presence images/artwork
- Implement presence buttons (Join, Spectate)
- Add party system for multiplayer games
- Support for multiple Discord applications
- Enhanced error reporting and user feedback