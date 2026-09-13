package com.example.platform

import android.content.Context
import android.net.Uri
import com.example.platform.android.AndroidScreenCaptureService
import com.example.platform.android.AndroidVideoFileInputService
import com.example.platform.desktop.DesktopScreenCapturePlaceholder
import com.example.services.video.IVideoInputService

/**
 * Factory providing the correct video capture service for the running platform.
 */
object VideoInputFactory {
    fun createVideoInputService(context: Context): IVideoInputService {
        return when (PlatformDetector.getCurrentPlatform()) {
            PlatformType.ANDROID -> AndroidScreenCaptureService(context.applicationContext)
            PlatformType.WINDOWS_DESKTOP -> DesktopScreenCapturePlaceholder()
            else -> AndroidScreenCaptureService(context.applicationContext)
        }
    }

    fun createVideoFileInputService(context: Context, videoUri: Uri): IVideoInputService {
        return AndroidVideoFileInputService(context.applicationContext, videoUri)
    }
}
