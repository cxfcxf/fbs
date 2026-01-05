---
description: Build and test the Android TV file server app on emulator
---

# Dev Workflow for Android TV File Server

// turbo-all

## Prerequisites
Ensure JDK 21 and Android SDK are installed.

## Steps

### 1. Set environment
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_SDK_ROOT=~/Library/Android/sdk
export PATH=$PATH:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator
```

### 2. Build the debug APK
```bash
cd /Users/xuefengchen/Repos/fbs
./gradlew assembleDebug
```

### 3. Start the Android TV emulator
```bash
emulator -avd Android_TV_API_34 -no-snapshot-load &
```
Wait for the emulator to fully boot (about 30-60 seconds).

### 4. Install the app
```bash
adb install -r app/build/outputs/apk/debug/fbs.apk
```

### 5. Launch the app
```bash
adb shell am start -n com.cxfcxf.androidtvfileserver/.MainActivity
```

### 6. Get emulator IP for browser testing
The app displays the server URL on screen. Alternatively:
```bash
adb shell ip addr show eth0 | grep 'inet '
```

### 7. Test upload from browser
- Open browser on host to `http://<emulator-ip>:8080`
- Drag a file to upload

### 8. Watch logs
```bash
adb logcat -s WebServer
```
