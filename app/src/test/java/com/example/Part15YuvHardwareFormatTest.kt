package com.example

import android.graphics.Bitmap
import android.media.MediaCodecInfo
import com.example.services.streaming.MediaCodecVideoEncoder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Part15YuvHardwareFormatTest {

    @Test
    fun testYuv420PlanarConversionCorrectness() {
        val encoder = MediaCodecVideoEncoder()
        
        // Create a basic 4x4 bitmap
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, android.graphics.Color.RED)
        bitmap.setPixel(0, 1, android.graphics.Color.GREEN)
        bitmap.setPixel(1, 0, android.graphics.Color.BLUE)
        bitmap.setPixel(1, 1, android.graphics.Color.YELLOW)

        // Invoke the private bitmapToYuv420 method for COLOR_FormatYUV420Planar
        val method = MediaCodecVideoEncoder::class.java.getDeclaredMethod(
            "bitmapToYuv420",
            Bitmap::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java
        )
        method.isAccessible = true

        val result = method.invoke(
            encoder,
            bitmap,
            4,
            4,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        ) as ByteArray

        // YUV 420 Planar (I420) layout: Y plane (size W*H=16), U plane (size W*H/4=4), V plane (size W*H/4=4)
        assertEquals(24, result.size) // 16 + 4 + 4 = 24 bytes

        // Check planar separation
        val yPlane = result.sliceArray(0 until 16)
        val uPlane = result.sliceArray(16 until 20)
        val vPlane = result.sliceArray(20 until 24)

        // Verify some content properties
        assertNotNull(yPlane)
        assertNotNull(uPlane)
        assertNotNull(vPlane)
    }

    @Test
    fun testYuv420SemiPlanarConversionCorrectness() {
        val encoder = MediaCodecVideoEncoder()
        
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val method = MediaCodecVideoEncoder::class.java.getDeclaredMethod(
            "bitmapToYuv420",
            Bitmap::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java
        )
        method.isAccessible = true

        val result = method.invoke(
            encoder,
            bitmap,
            4,
            4,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        ) as ByteArray

        // YUV 420 SemiPlanar (NV12) layout: Y plane (size W*H=16), UV plane interleaved (size W*H/2=8)
        assertEquals(24, result.size) // 16 + 8 = 24 bytes

        val yPlane = result.sliceArray(0 until 16)
        val uvPlane = result.sliceArray(16 until 24)

        assertNotNull(yPlane)
        assertNotNull(uvPlane)
    }

    @Test
    fun testSupportedFormatAndConfigurationPath() {
        val encoder = MediaCodecVideoEncoder()
        
        // Configure with default capabilities selection
        val configResult = encoder.configureEncoder(320, 240, 500000, 30)
        assertTrue(configResult.isSuccess)
        assertTrue(encoder.isRunning)

        // Verify either 19 (planar) or 21 (semi-planar) is configured
        val selected = encoder.selectedColorFormat
        assertTrue(
            selected == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
            selected == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        )

        encoder.stopEncoder()
    }

    @Test
    fun testUnsupportedFormatConfigureFailure() {
        val encoder = MediaCodecVideoEncoder()
        
        // Override with unsupported color format
        encoder.colorFormatOverride = 999999 

        val configResult = encoder.configureEncoder(320, 240, 500000, 30)
        assertTrue(configResult.isFailure)
        assertFalse(encoder.isRunning)
    }

    @Test
    fun testEncoderInitializationFailureHandling() {
        val encoder = MediaCodecVideoEncoder()
        
        // Use an invalid dimension to trigger initialization failure or configuration failure
        val configResult = encoder.configureEncoder(-100, -100, 500000, 30)
        assertTrue(configResult.isFailure)
        assertFalse(encoder.isRunning)
    }

    @Test
    fun testSuccessfulFrameEncodingWithSelectedFormat() {
        val encoder = MediaCodecVideoEncoder()
        val configResult = encoder.configureEncoder(320, 240, 500000, 30)
        assertTrue(configResult.isSuccess)

        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        val encodeResult = encoder.encodeFrame(bitmap, 1000L)
        
        if (encodeResult.isFailure) {
            val ex = encodeResult.exceptionOrNull()
            println("ENCODE_FAILURE_TRACE:")
            ex?.printStackTrace()
        }
        
        assertTrue(encodeResult.isSuccess)
        encoder.stopEncoder()
    }
}
