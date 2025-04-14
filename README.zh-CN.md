# Android TV 文件服务器 (FBS)

一个可以通过网络提供文件服务的 Android TV 应用程序。

[English](README.md) | 简体中文

## 功能特性

- 简单且电视友好的文件浏览界面
- 内置 HTTP 服务器，支持文件上传功能
- 使用遥控器轻松删除文件（在文件上按下右键）
- 支持 APK 安装
- 目录导航
- 适配 Android 10+ 的权限处理

## 使用方法

### 安装

1. 下载 APK 文件
2. 安装到您的 Android TV 设备上
3. 在提示时授予必要的权限

### 文件浏览器

- 使用方向键在文件和文件夹间导航
- 在文件上按下右键显示删除按钮
- 在删除按钮上按确定键确认删除

### 文件上传服务器

1. 从主屏幕启动服务器
2. 记下屏幕上显示的服务器 URL
3. 在电脑或移动设备的网页浏览器中打开该 URL
4. 拖放文件以将其上传到电视

## 开发

### 要求

- Android Studio Iguana (2023.2.1) 或更新版本
- JDK 17 或更新版本
- Android API 33+ SDK

### 构建

1. 克隆仓库
2. 在 Android Studio 中打开项目
3. 使用 Gradle 构建：
   ```
   ./gradlew assembleDebug
   ```
   这将在 `app/build/outputs/apk/debug/fbs.apk` 生成 APK 文件
4. 安装到设备上：
   ```
   adb install -r app/build/outputs/apk/debug/fbs.apk
   ```

## 许可证

本项目是开源的，遵循 [MIT 许可证](LICENSE)。

## 贡献

欢迎贡献！请随时提交 Pull Request。 