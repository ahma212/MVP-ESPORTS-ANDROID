package com.example.services.video

/**
 * Abstract source types supported across platforms (Android, Windows, Video feed, Camera, PRISM).
 */
enum class VideoSourceType(val displayName: String) {
    ANDROID_MEDIA_PROJECTION("Android Screen Capture"),
    WINDOWS_DESKTOP_DUPLICATION("Windows Desktop Capture"),
    VIDEO_FILE_FEED("Video File / Test Stream"),
    CAMERA_INPUT("External Camera / HDMI"),
    PRISM_STREAM_INPUT("PRISM / External Stream Protocol"),
    NONE("Not connected")
}
