package com.example.services.streaming

import kotlinx.coroutines.flow.StateFlow

/**
 * Authoritative states for YouTube Live session state machine.
 */
enum class LiveSessionState {
    IDLE,
    AUTHORIZING,
    READY,
    STARTING,
    LIVE,
    RECONNECTING,
    STOPPING,
    STOPPED,
    ERROR
}

/**
 * YouTube Channel Metadata
 */
data class YouTubeChannelInfo(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val thumbnailUrl: String? = null
)

/**
 * YouTube Live Chat Message
 */
data class YouTubeChatMessage(
    val messageId: String,
    val authorName: String,
    val messageText: String,
    val publishedAt: String,
    val isModerator: Boolean = false,
    val isOwner: Boolean = false
)

/**
 * Live Broadcast + Stream details
 */
data class YouTubeLiveBroadcastInfo(
    val broadcastId: String,
    val streamId: String? = null,
    val title: String,
    val rtmpIngestUrl: String? = null,
    val streamName: String? = null,
    val lifeCycleStatus: String = "created", // created, ready, testing, live, complete
    val streamStatus: String = "inactive"   // active, inactive, error
)

/**
 * Platform-independent abstraction for YouTube Live Engine.
 * Supports official YouTube Data API v3 OAuth flows, stream creation, binding, transition, state polling.
 */
interface IYouTubeLiveService {
    val sessionState: StateFlow<LiveSessionState>
    val channelInfo: StateFlow<YouTubeChannelInfo?>
    val currentBroadcast: StateFlow<YouTubeLiveBroadcastInfo?>
    val errorMessage: StateFlow<String?>
    val isAuthorized: StateFlow<Boolean>
    val lastPollingIntervalMs: Long

    /**
     * Checks if a valid non-expired OAuth token is stored.
     */
    suspend fun checkAuthorization(): Boolean

    /**
     * Connects/authorizes YouTube account using an OAuth token string.
     */
    suspend fun authorizeWithToken(accessToken: String, refreshToken: String? = null, expiresInSeconds: Long = 3600): Result<YouTubeChannelInfo>

    /**
     * Creates a YouTube Live Broadcast.
     */
    suspend fun createLiveBroadcast(title: String, description: String, privacyStatus: String = "public"): Result<YouTubeLiveBroadcastInfo>

    /**
     * Creates a YouTube Live Stream ingest point.
     */
    suspend fun createLiveStream(title: String, frameRate: String = "60fps", resolution: String = "1080p"): Result<String>

    /**
     * Binds a created broadcast to a created live stream.
     */
    suspend fun bindBroadcastToStream(broadcastId: String, streamId: String): Result<YouTubeLiveBroadcastInfo>

    /**
     * Starts the broadcast transition to LIVE status on YouTube.
     */
    suspend fun startBroadcast(broadcastId: String): Result<Unit>

    /**
     * Transitions broadcast state on YouTube to complete/stopped.
     */
    suspend fun stopBroadcast(broadcastId: String? = null): Result<Unit>

    /**
     * Queries latest broadcast state from YouTube API.
     */
    suspend fun queryBroadcastState(broadcastId: String): Result<YouTubeLiveBroadcastInfo>

    /**
     * Queries latest stream health/ingestion status from YouTube API.
     */
    suspend fun queryStreamState(streamId: String): Result<String>

    /**
     * Fetches liveChatId associated with the active broadcast.
     */
    suspend fun getLiveChatId(broadcastId: String? = null): Result<String?>

    /**
     * Fetches recent messages from YouTube liveChat.
     */
    suspend fun fetchLiveChatMessages(liveChatId: String): Result<List<YouTubeChatMessage>>

    /**
     * Sends a message to YouTube liveChat.
     */
    suspend fun sendLiveChatMessage(liveChatId: String, messageText: String): Result<Unit>

    /**
     * Disconnects current authorized account and clears stored tokens.
     */

    suspend fun disconnect()

    /**
     * Reconnects live session using stored refresh token or authorization.
     */
    suspend fun reconnect(): Result<Boolean>
}
