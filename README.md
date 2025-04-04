# MIDlet Store

## Overview
MIDlet Store is a Java ME (J2ME) application that brings an app store experience to feature phones and other J2ME-capable devices. It provides a modern, intuitive interface for discovering, downloading, and managing mobile applications, inspired by contemporary app stores like Apple's App Store.

## Features
- **Modern UI**: Clean, touch-friendly interface with smooth transitions and visual feedback
- **App Discovery**: Browse featured apps, search for specific apps, and explore top charts by category
- **App Details**: View comprehensive information about apps including description, compatibility, and user ratings
- **Voting System**: Vote apps up or down to help other users find quality applications
- **Version Checking**: Automatic update notifications when a new version is available
- **Compatibility Checking**: Shows whether apps are compatible with your specific device
- **Adaptive Design**: Automatically adjusts to different screen sizes and device capabilities
- **Legacy Support**: Includes a simplified interface for devices with limited capabilities

## System Requirements
- J2ME-compatible device running MIDP 2.0 and CLDC 1.0 or higher
- Recommended: Support for JSR 226 (SVG) for enhanced visuals
- Network connectivity for downloading app information and updates

## Installation
1. Transfer the MIDletStore.jar file to your mobile device
2. Install the MIDlet through your device's application manager
3. Grant network permissions when prompted

## Usage
### Main Screen
- **Featured Apps**: Swipe or use left/right keys to browse featured applications
- **All Apps**: Scroll down to see all available applications
- **Search**: Select the "Search" command to find specific apps
- **Top Charts**: Select "Top Charts" to view popular apps by category

### Navigation
- **Selection**: Use directional keys to navigate, center/OK key to select
- **Back**: Press back key or select "Back" command to return to previous screen
- **Touch**: If your device supports touch, you can tap items to select and swipe to scroll

### App Details
- View full app information including description, size, and compatibility
- Select "Install" to download and install the app
- Use "Upvote" or "Downvote" to rate the application

### Search
- Enter search terms using your device's keypad
- Results update as you type (on supported devices)
- Select any result to view detailed information

### Top Charts
- Use number keys 1-0 to quickly switch between categories
- Or use left/right navigation to move between categories
- Charts show the most popular apps in each category

## Legacy Mode
For devices with limited capabilities, MIDlet Store automatically switches to a simplified interface that:
- Uses standard MIDP UI components instead of custom Canvas-based UI
- Requires less processing power and memory
- Maintains all core functionality despite the simpler interface

## Development Information
- Developed for J2ME platform (MIDP 2.0/CLDC 1.0)
- Server API URL: http://localhost:3000/storeapi (configurable)
- Client Version: 1.0

## Troubleshooting
- If the app fails to load, check your network connection
- For display issues, try the legacy mode manually by launching LegacyStore.jar
- If an app shows as incompatible, your device may not support required features for that application

## License
This software is provided for educational purposes only. Use at your own risk.
