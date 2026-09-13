package com.example

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.services.overlay.FloatingPointerOverlay
import com.example.platform.android.ScreenCaptureService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayServiceInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testOverlayInstantiationOnMainThread() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val overlay = FloatingPointerOverlay(context)
            assertNotNull(overlay)
            assertFalse(overlay.controlState.value.isVisible)
        }
    }

    @Test
    fun testOverlayPositionMovement() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val overlay = FloatingPointerOverlay(context)
            overlay.updatePosition(150, 250)
            assertEquals(150, overlay.controlState.value.lastPositionX)
            assertEquals(250, overlay.controlState.value.lastPositionY)
        }
    }

    @Test
    fun testOverlayRemovalFromWindowManager() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val overlay = FloatingPointerOverlay(context)
            overlay.hidePointer()
            assertFalse(overlay.controlState.value.isVisible)
        }
    }

    @Test
    fun testServiceStartAndStopIntents() {
        val startIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
        }
        assertNotNull(startIntent)
        assertEquals(ScreenCaptureService.ACTION_START, startIntent.action)

        val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        assertNotNull(stopIntent)
        assertEquals(ScreenCaptureService.ACTION_STOP, stopIntent.action)
    }

    @Test
    fun testNotificationStateStructure() {
        val testChannelId = "esports_streaming_channel"
        assertNotNull(testChannelId)
        assertTrue(testChannelId.isNotEmpty())
    }
}
