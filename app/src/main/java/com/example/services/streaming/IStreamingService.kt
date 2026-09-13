package com.example.services.streaming

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * YouTube Live / RTMP streaming states.
 */
sealed class StreamingState {
    object NotConnected : StreamingState()
    object Authenticating : StreamingState()
    data class Authenticated(val channelTitle: String, val channelId: String) : StreamingState()
    data class BroadcastConfigured(val broadcastId: String, val streamTitle: String, val rtmpIngestUrl: String) : StreamingState()
    data class StreamingLive(
        val broadcastId: String,
        val viewersCount: Int = 0,
        val bitrateKbps: Int = 4500,
        val fps: Int = 60,
        val uptimeSeconds: Long = 0L
    ) : StreamingState()
    data class StreamError(val message: String) : StreamingState()
}

/**
 * Broadcast configuration for YouTube Live stream.
 */
data class BroadcastConfig(
    val title: String = "MVP ESPORTS PK LIVE TOURNAMENT",
    val description: String = "Live esports PUBG Mobile action analyzed by MVP ESPORTS PK.",
    val privacyStatus: String = "public", // "public", "unlisted", "private"
    val targetResolution: String = "1080p",
    val targetFps: Int = 60,
    val targetBitrateKbps: Int = 6000
)

/**
 * Abstract interface for YouTube Live streaming and encoder outputs.
 */
interface IStreamingService {
    val streamingState: StateFlow<StreamingState>

    suspend fun authenticateYouTube(authCredentials: String): Result<Unit>
    suspend fun createLiveBroadcast(config: BroadcastConfig): Result<String>
    suspend fun startLiveStream(context: Context, broadcastId: String): Result<Unit>
    suspend fun stopLiveStream(): Result<Unit>
    fun disconnect()
}
