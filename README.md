# Android TV File Server (FBS)

An Android TV application that transforms your TV into a file server, allowing easy file management via a web browser.

English | [简体中文](README.zh-CN.md)

## Features

- **High-Density Grid Interface**: Optimized 10-column layout to display more files on large screens.
- **Web Management UI**: Full-featured browser interface to manage files from your computer or phone.
    - **Upload**: Drag & drop files directly to the current directory.
    - **Delete**: Remove files and folders remotely.
    - **Navigation**: Independent browsing history and directory persistence.
- **TV Controls**: 
    - **Optimized Navigation**: Smart D-pad support.
    - **Quick Actions**: Long-press to delete files directly on TV.
- **Built-in HTTP Server**: Robust server for file transfer.

## Usage

### TV Interface

1.  **Launch the App**: Grant storage permissions if prompted.
2.  **Browse Files**:
    - Use D-pad to navigate the 10-column grid.
    - **Click (Center)** to open a file or enter a folder.
    - **Long-Press (Center)** on a file/folder to delete it.
3.  **Start Server**:
    - Navigate to the **"Start"** button in the top-right corner.
    - Click to toggle the server ON/OFF.
    - The server URL (e.g., `http://192.168.1.x:8080`) will appear next to the button.

### Web Interface

1.  Make sure your computer/phone is on the same Wi-Fi network as the TV.
2.  Open the server URL shown on the TV in your web browser.
3.  **Manage Files**:
    - **Navigate**: Click folders to browse. The URL updates automatically, so you can bookmark folders.
    - **Upload**: Drag & drop files anywhere on the page, or use the "Select Files" button. Files upload to the *current* folder.
    - **Delete**: Click the trash icon (🗑️) next to any item to delete it.

## Development

### Requirements

- Android Studio Iguana or newer
- JDK 21
- Android API 34+ SDK

### Building

1.  Clone the repository.
2.  Open in Android Studio.
3.  Build using Gradle:
    ```bash
    ./gradlew assembleDebug
    ```
4.  Install on device via ADB:
    ```bash
    adb install -r app/build/outputs/apk/debug/fbs.apk
    ```

## License

This project is open source and available under the [MIT License](LICENSE).