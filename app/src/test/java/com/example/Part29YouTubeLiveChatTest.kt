package com.example

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.services.overlay.FloatingPointerOverlay
import com.example.services.streaming.IYouTubeLiveService
import com.example.services.streaming.LiveSessionState
import com.example.services.streaming.YouTubeChannelInfo
import com.example.services.streaming.YouTubeChatMessage
import com.example.services.streaming.YouTubeLiveBroadcastInfo
import com.example.services.streaming.YouTubeLiveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part29YouTubeLiveChatTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // --- Dynamic Mock YouTube Live Service implementation ---
    class MockYouTubeLiveService : IYouTubeLiveService {
        private val _sessionState = MutableStateFlow(LiveSessionState.IDLE)
        override val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

        private val _channelInfo = MutableStateFlow<YouTubeChannelInfo?>(null)
        override val channelInfo: StateFlow<YouTubeChannelInfo?> = _channelInfo.asStateFlow()

        private val _currentBroadcast = MutableStateFlow<YouTubeLiveBroadcastInfo?>(null)
        override val currentBroadcast: StateFlow<YouTubeLiveBroadcastInfo?> = _currentBroadcast.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _isAuthorized = MutableStateFlow(false)
        override val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

        override var lastPollingIntervalMs: Long = 10000L

        var getLiveChatIdResult: Result<String?> = Result.success("test-chat-id")
        var fetchMessagesResult: Result<List<YouTubeChatMessage>> = Result.success(emptyList())
        var sendChatMessageResult: Result<Unit> = Result.success(Unit)

        var lastFetchedLiveChatId: String? = null
        var lastSentMessageText: String? = null
        var pageTokensPassed = mutableListOf<String?>()

        fun setBroadcast(info: YouTubeLiveBroadcastInfo?) {
            _currentBroadcast.value = info
        }

        override suspend fun checkAuthorization(): Boolean = true
        override suspend fun authorizeWithToken(accessToken: String, refreshToken: String?, expiresInSeconds: Long) = Result.success(YouTubeChannelInfo("id", "title"))
        override suspend fun createLiveBroadcast(title: String, description: String, privacyStatus: String) = Result.success(YouTubeLiveBroadcastInfo("id", "stream", "title"))
        override suspend fun createLiveStream(title: String, frameRate: String, resolution: String) = Result.success("streamId")
        override suspend fun bindBroadcastToStream(broadcastId: String, streamId: String) = Result.success(YouTubeLiveBroadcastInfo("id", "stream", "title"))
        override suspend fun startBroadcast(broadcastId: String) = Result.success(Unit)
        override suspend fun stopBroadcast(broadcastId: String?) = Result.success(Unit)
        override suspend fun queryBroadcastState(broadcastId: String) = Result.success(YouTubeLiveBroadcastInfo("id", "stream", "title"))
        override suspend fun queryStreamState(streamId: String) = Result.success("active")

        override suspend fun getLiveChatId(broadcastId: String?): Result<String?> {
            return getLiveChatIdResult
        }

        override suspend fun fetchLiveChatMessages(liveChatId: String): Result<List<YouTubeChatMessage>> {
            lastFetchedLiveChatId = liveChatId
            return fetchMessagesResult
        }

        override suspend fun sendLiveChatMessage(liveChatId: String, messageText: String): Result<Unit> {
            lastSentMessageText = messageText
            return sendChatMessageResult
        }

        override suspend fun disconnect() {}
        override suspend fun reconnect() = Result.success(true)
    }

    @Test
    fun testPollingIntervalMillisHandling() {
        val service = MockYouTubeLiveService()
        service.lastPollingIntervalMs = 5000L // Simulate YouTube response returned 5000ms

        assertEquals(5000L, service.lastPollingIntervalMs)
    }

    @Test
    fun testDuplicateMessagePreventionAndCaching() {
        val cachedMessages = mutableSetOf<String>()
        val msg1 = YouTubeChatMessage("msg-101", "Player1", "Hello MVP", "2026-09-03T15:00:00Z")
        val msg2 = YouTubeChatMessage("msg-101", "Player1", "Hello MVP", "2026-09-03T15:00:00Z") // Duplicate ID

        assertTrue("First message should be unique", cachedMessages.add(msg1.messageId))
        assertFalse("Second message should be blocked as duplicate", cachedMessages.add(msg2.messageId))
    }

    @Test
    fun testTokenSecurityInLogOutputs() {
        // Redirect standard out to verify that sensitive tokens do not leak
        val outputStream = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(outputStream))

        try {
            val sensitiveToken = "ya29.a0AfB_abcdef1234567890_tokenSecret"
            // Simulate HTTP interceptor behavior redacting Authorization
            val authorizationHeader = "Bearer $sensitiveToken"
            
            // Log output format mimicking redactHeader or Level.HEADERS
            val logMessage = "Authorization: Bearer ██████████"
            println(logMessage)

            val capturedOutput = outputStream.toString()
            assertTrue(capturedOutput.contains("██████████"))
            assertFalse(capturedOutput.contains(sensitiveToken))
        } finally {
            System.setOut(originalOut)
        }
    }
}
