# Android TV File Server

A file browser and HTTP upload server for Android TV. This app allows you to:

- Browse files on your Android TV
- Upload files to your TV via a web interface
- Delete files directly from your TV using the remote control
- Install APK files easily

## Features

- Simple and TV-friendly file browsing interface
- Built-in HTTP server with file upload capabilities
- Easy file deletion with the remote control (press RIGHT on a file)
- APK installation support
- Directory navigation
- Permissions handling for Android 10+

## Usage

### Installation

1. Download the APK file
2. Install on your Android TV device
3. Grant the necessary permissions when prompted

### File Browser

- Use the D-pad to navigate through files and folders
- Press RIGHT on a file to show the delete button
- Press OK on the delete button to confirm deletion

### File Upload Server

1. Start the server from the main screen
2. Note the server URL shown on the screen
3. Open the URL in a web browser on your computer or mobile device
4. Drag & drop files to upload them to the TV

## Development

### Requirements

- Android Studio Arctic Fox (2020.3.1) or newer
- JDK 11 or newer
- Android API 30+ SDK

### Building

1. Clone the repository
2. Open the project in Android Studio
3. Build using Gradle:
   ```
   ./gradlew assembleDebug
   ```
4. Install on a device:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## License

This project is open source and available under the [MIT License](LICENSE).

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. 