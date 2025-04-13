# Android TV File Server (FBS)

An Android TV application that serves files over the network.

## Automated GitHub Release Workflow

This repository includes a GitHub Actions workflow to automate the building and publishing of APKs when a new release is created.

### How to create a release

1. Click on the "Releases" tab in your GitHub repository
2. Click "Create a new release" or "Draft a new release"
3. Enter a tag version (e.g., v1.0.0)
4. Enter a release title and description
5. Click "Publish release"

The workflow will automatically:
1. Build a signed release APK
2. Rename the APK to include the version tag (e.g., fbs-v1.0.0.apk)
3. Attach the APK to the GitHub release

### Setting up signing for the automated build

Before creating your first release, you need to set up APK signing in GitHub secrets:

1. Generate a keystore file if you don't already have one:
   ```bash
   keytool -genkey -v -keystore keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias key0
   ```

2. Convert your keystore to base64:
   ```bash
   base64 keystore.jks | tr -d '\n' | pbcopy  # This will copy to clipboard on macOS
   # For Linux, use: base64 keystore.jks | tr -d '\n'
   ```

3. Add the following secrets in your GitHub repository:
   - `KEYSTORE_BASE64`: The base64-encoded keystore
   - `KEYSTORE_PASSWORD`: Your keystore password
   - `KEY_ALIAS`: Your key alias (e.g., key0)
   - `KEY_PASSWORD`: Your key password

To add secrets:
1. Go to your repository on GitHub
2. Click on "Settings" → "Secrets and variables" → "Actions"
3. Click "New repository secret" to add each of the required secrets

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