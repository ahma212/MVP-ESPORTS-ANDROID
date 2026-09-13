package com.example

import android.content.Context
import com.example.services.streaming.BroadcastController
import com.example.services.streaming.BroadcastLifecycleState
import com.example.services.streaming.LocalLiveRuntimeManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PartIBroadcastControllerTest {

    private lateinit var controller: BroadcastController
    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        controller = BroadcastController.getInstance(context)
        
        // Reset state for clean test since it's a singleton
        if (controller.broadcastState.value != BroadcastLifecycleState.IDLE) {
            controller.endBroadcast()
        }
    }

    @Test
    fun testBroadcastLifecycleStateTransitions() {
        // If it starts in COMPLETED from a previous test run, we can transition it to READY.
        if (controller.broadcastState.value == BroadcastLifecycleState.COMPLETED) {
            controller.prepareBroadcast()
            assertEquals(BroadcastLifecycleState.READY, controller.broadcastState.value)
        } else {
            // Initial state IDLE
            assertEquals(BroadcastLifecycleState.IDLE, controller.broadcastState.value)

            // IDLE -> READY
            controller.prepareBroadcast()
            assertEquals(BroadcastLifecycleState.READY, controller.broadcastState.value)
        }

        // READY -> LIVE
        val startResult = controller.startBroadcast()
        assertTrue(startResult.isSuccess)
        assertEquals(BroadcastLifecycleState.LIVE, controller.broadcastState.value)

        // LIVE -> PAUSED
        val pauseResult = controller.pauseBroadcast()
        assertTrue(pauseResult.isSuccess)
        assertEquals(BroadcastLifecycleState.PAUSED, controller.broadcastState.value)

        // PAUSED -> LIVE (Resume)
        val resumeResult = controller.resumeBroadcast()
        assertTrue(resumeResult.isSuccess)
        assertEquals(BroadcastLifecycleState.LIVE, controller.broadcastState.value)

        // LIVE -> COMPLETED (End)
        val endResult = controller.endBroadcast()
        assertTrue(endResult.isSuccess)
        assertEquals(BroadcastLifecycleState.COMPLETED, controller.broadcastState.value)
    }

    @Test
    fun testInvalidTransitions() {
        // Ensure starting from COMPLETED (since we use a singleton, reset to completed to test)
        controller.endBroadcast()
        
        // COMPLETED -> START directly should fail
        val startResult = controller.startBroadcast()
        assertTrue(startResult.isFailure)

        // Let's test PREPARE -> READY -> START
        controller.prepareBroadcast()
        assertEquals(BroadcastLifecycleState.READY, controller.broadcastState.value)
        controller.startBroadcast()
        assertEquals(BroadcastLifecycleState.LIVE, controller.broadcastState.value)
    }

    @Test
    fun testNextMatchFunctionality() {
        controller.prepareBroadcast()
        controller.startBroadcast()
        
        val initialMatch = LocalLiveRuntimeManager.broadcastState.value.currentMatchNumber

        // Move to Next Match
        val nextResult = controller.nextMatch()
        assertTrue(nextResult.isSuccess)

        val nextMatch = LocalLiveRuntimeManager.broadcastState.value.currentMatchNumber
        assertEquals(initialMatch + 1, nextMatch)

        controller.endBroadcast()
    }
}
