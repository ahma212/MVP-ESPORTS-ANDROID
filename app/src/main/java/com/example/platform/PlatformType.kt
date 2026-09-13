package com.example.platform

/**
 * Supported execution platforms.
 */
enum class PlatformType(val displayName: String) {
    ANDROID("Android Mobile"),
    WINDOWS_DESKTOP("Windows PC / Desktop"),
    LINUX_DESKTOP("Linux Desktop"),
    MACOS_DESKTOP("macOS Desktop"),
    WEB_DASHBOARD("Web / Admin Client")
}

/**
 * Runtime platform detector and hardware capabilities inspector.
 */
object PlatformDetector {
    /**
     * Determines runtime platform environment.
     */
    fun getCurrentPlatform(): PlatformType {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""
        return when {
            osName.contains("linux") && isAndroidRuntime() -> PlatformType.ANDROID
            osName.contains("windows") -> PlatformType.WINDOWS_DESKTOP
            osName.contains("mac") -> PlatformType.MACOS_DESKTOP
            osName.contains("linux") -> PlatformType.LINUX_DESKTOP
            else -> PlatformType.ANDROID
        }
    }

    private fun isAndroidRuntime(): Boolean {
        return try {
            Class.forName("android.os.Build")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    fun isScreenCaptureSupported(): Boolean {
        return true // Supported on Android via MediaProjection, and Windows via Desktop Duplication
    }
}
