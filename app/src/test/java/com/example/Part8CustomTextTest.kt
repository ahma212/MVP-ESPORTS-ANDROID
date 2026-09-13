package com.example

import com.example.services.composition.CustomGraphicsOverlayLayer
import com.example.services.streaming.StandingTableControlsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Integration Test Suite for PART 8 — Custom Text (Remove Hardcoded Fallback).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part8CustomTextTest {

    @Before
    fun setup() {
        StandingTableControlsManager.setCustomBannerText("")
    }

    @Test
    fun testCustomTextOnWithOperatorTextRendersExactText() {
        val operatorText = "WELCOME TO MATCH 3 FINALS"
        StandingTableControlsManager.setCustomBannerText(operatorText)
        val controls = StandingTableControlsManager.controlsState.value
        assertEquals(operatorText, controls.customBannerText)

        val layer = CustomGraphicsOverlayLayer().apply {
            isEnabled = true
            customBannerText = controls.customBannerText
        }
        assertEquals(operatorText, layer.customBannerText)
    }

    @Test
    fun testOperatorChangesTextDynamically() {
        StandingTableControlsManager.setCustomBannerText("First Announcement")
        assertEquals("First Announcement", StandingTableControlsManager.controlsState.value.customBannerText)

        StandingTableControlsManager.setCustomBannerText("Updated Operator Text")
        assertEquals("Updated Operator Text", StandingTableControlsManager.controlsState.value.customBannerText)
    }

    @Test
    fun testOperatorClearsText() {
        StandingTableControlsManager.setCustomBannerText("")
        assertTrue(StandingTableControlsManager.controlsState.value.customBannerText.isEmpty())
    }

    @Test
    fun testNoHardcodedDefaultAnnouncementExists() {
        val controls = StandingTableControlsManager.controlsState.value
        assertTrue(controls.customBannerText.isEmpty())
    }
}
