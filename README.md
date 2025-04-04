# Hack MIDlet Store

## Overview
MIDlet Store is a Java ME (J2ME) application that brings an app store experience to feature phones and other J2ME-capable devices. It provides a modern, intuitive interface for discovering, downloading, and managing mobile applications, inspired by contemporary app stores like Apple's App Store.

Global Hosted Store and the Legacy Version will be availible as soon as @nora gives me access to the submissions api

## Features
- **Modern UI**: Clean, almost feels too clean with stunning ✨✨✨ transitions and visual feedback
- **App Discovery**: Browse featured apps, and fill your limited phone storage with stuff you never knew you needed
- **App Details**: View comprehensive information about apps including description, compatibility, and user ratings, reddit style
- **Voting System**: Vote apps up or down to help other users find quality applications
- **Version Checking**: You might get lucky and see this screen, one day...
- **Compatibility Checking**: Estimates how much you need to pray before running the app
- **Adaptive Design**: Automatically adjusts to different screen sizes and device capabilities 
> (With how *diverse* this dang ecosystem is I wouldn't be so sure)
- **Legacy Support** (To be Implemented): Includes a simplified interface for devices with limited capabilities 
> (This is less legacy and more that JSR226 is barely supported by any devices)

## System Requirements
- J2ME-compatible device running MIDP 2.1 and CLDC 1.1 or higher
- Has dangerous levels of SVGs (JSR226) for enhanced visuals
- Uses the interwebs

## Installation
### Offline Install
1. Transfer the MIDletStore.jar file to your mobile device
2. Install the MIDlet through your device's application manager
3. Grant network permissions when prompted

### Online Install (TBD)

### Server
Server implementation is available at [MIDlet-Store-API](https://github.com/Subhi-Dev/MIDlet-Store-API)
## Usage
### Main Screen
- **Featured Apps**: Swipe or use left/right keys to browse featured applications
- **All Apps**: Scroll down to see all available applications
- **Search**: Select the "Search" command to find specific apps
- **Top Charts**: Select "Top Charts" to view popular apps by category

### Navigation
- **Selection**: Use directional keys to navigate, center/OK key to select
- **Back**: Press back key or select "Back" command to return to previous screen

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

## Legacy Mode (TBD)
For devices with limited capabilities, MIDlet Store automatically switches to a simplified interface that:
- Uses standard MIDP UI components instead of custom Canvas-based UI
- Requires less processing power and memory
- Maintains all core functionality despite the simpler interface

## Development Information
- Developed for J2ME platform (MIDP 2.1/CLDC 1.1)
- Server API URL: http://localhost:3000/storeapi (configurable)
- Client Version: 1.0

## Troubleshooting
- If the app fails to load, check your network connection
- For display issues, try the legacy mode manually by launching LegacyStore.jar
- If an app shows as incompatible, your device may not support required features for that application

## License
This software is provided for educational purposes only. Use at your own risk.
