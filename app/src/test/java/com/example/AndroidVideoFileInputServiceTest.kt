package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.platform.android.AndroidVideoFileInputService
import com.example.services.video.VideoCaptureState
import com.example.services.video.VideoSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidVideoFileInputServiceTest {

    private lateinit var context: Context
    private lateinit var testUri: Uri
    private lateinit var videoService: AndroidVideoFileInputService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testUri = Uri.parse("content://media/external/video/media/101")
        videoService = AndroidVideoFileInputService(context, testUri)
    }

    @Test
    fun testActiveSourceType() {
        assertEquals(VideoSourceType.VIDEO_FILE_FEED, videoService.activeSource)
    }

    @Test
    fun testInitialCaptureState() {
        val state = videoService.captureState.value
        assertTrue(state is VideoCaptureState.Idle)
    }

    @Test
    fun testStartAndStopCaptureLifecycle() = runBlocking {
        val startResult = videoService.startCapture()
        assertTrue(startResult.isSuccess)

        val activeState = videoService.captureState.value
        assertTrue(activeState is VideoCaptureState.Capturing)
        val capturing = activeState as VideoCaptureState.Capturing
        assertEquals(VideoSourceType.VIDEO_FILE_FEED, capturing.sourceType)

        // Test playback pause and resume
        videoService.pausePlayback()
        videoService.resumePlayback()
        videoService.seekTo(1500L)

        // Stop capture
        val stopResult = videoService.stopCapture()
        assertTrue(stopResult.isSuccess)
        assertEquals(VideoCaptureState.Idle, videoService.captureState.value)
    }

    @Test
    fun testReleaseCleanup() {
        videoService.release()
        assertEquals(VideoCaptureState.Idle, videoService.captureState.value)
    }
}
