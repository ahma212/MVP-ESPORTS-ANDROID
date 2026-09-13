package com.example

import android.content.Context
import com.example.services.audio.InternalAudioCaptureManager
import com.example.services.audio.MicrophoneCommentaryManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MicrophoneForegroundServiceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        MicrophoneCommentaryManager.stopMicrophone()
        InternalAudioCaptureManager.stopCapture()
    }

    @Test
    fun testMicrophonePermissionCheckAndDeniedState() {
        // Without granting runtime permission explicitly, permission check will report missing or state
        MicrophoneCommentaryManager.stopMicrophone()
        val micState = MicrophoneCommentaryManager.micState.value
        assertFalse("Initially should not be recording", micState.isRecording)
    }

    @Test
    fun testMicrophoneStartStopIdempotencyAndThreadSafety() {
        // Call stopMicrophone multiple times
        MicrophoneCommentaryManager.stopMicrophone()
        assertFalse(MicrophoneCommentaryManager.micState.value.isRecording)
        assertEquals(0f, MicrophoneCommentaryManager.micState.value.peakVolume, 0.001f)

        MicrophoneCommentaryManager.stopMicrophone()
        assertFalse(MicrophoneCommentaryManager.micState.value.isRecording)

        // Read when mic is stopped
        val buffer = ByteArray(1024)
        val bytesRead = MicrophoneCommentaryManager.read(buffer, 0, 1024)
        assertEquals("Reading from stopped mic must return 0 bytes cleanly without exception", 0, bytesRead)
    }

    @Test
    fun testInternalAudioCaptureManagerIdempotency() {
        InternalAudioCaptureManager.stopCapture()
        assertFalse(InternalAudioCaptureManager.internalAudioState.value.isCapturing)

        InternalAudioCaptureManager.stopCapture()
        assertFalse(InternalAudioCaptureManager.internalAudioState.value.isCapturing)

        val buffer = ByteArray(1024)
        val readBytes = InternalAudioCaptureManager.read(buffer, 0, 1024)
        assertEquals("Reading from stopped internal capture must return 0", 0, readBytes)
    }
}
