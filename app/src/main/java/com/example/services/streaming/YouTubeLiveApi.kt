package com.example.services.streaming

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// --- YouTube API Data Models ---

data class YTChannelListResponse(
    val items: List<YTChannelItem>?
)

data class YTChannelItem(
    val id: String,
    val snippet: YTChannelSnippet?
)

data class YTChannelSnippet(
    val title: String,
    val description: String?,
    val thumbnails: YTHitThumbnails?
)

data class YTHitThumbnails(
    val default: YTHitThumbnail?
)

data class YTHitThumbnail(
    val url: String?
)

// --- LiveBroadcast Models ---

data class YTBroadcastRequest(
    val snippet: YTBroadcastSnippetRequest,
    val status: YTBroadcastStatusRequest
)

data class YTBroadcastSnippetRequest(
    val title: String,
    val scheduledStartTime: String,
    val description: String? = null
)

data class YTBroadcastStatusRequest(
    val privacyStatus: String = "public", // public, unlisted, private
    val selfDeclaredMadeForKids: Boolean = false
)

data class YTBroadcastResponse(
    val id: String,
    val snippet: YTBroadcastSnippetResponse?,
    val status: YTBroadcastStatusResponse?,
    val contentDetails: YTBroadcastContentDetails?
)

data class YTBroadcastSnippetResponse(
    val title: String?,
    val description: String?,
    val publishedAt: String?,
    val liveChatId: String?
)

data class YTBroadcastStatusResponse(
    val lifeCycleStatus: String?, // created, ready, testing, live, complete
    val privacyStatus: String?
)

data class YTBroadcastContentDetails(
    val boundStreamId: String?
)

data class YTBroadcastListResponse(
    val items: List<YTBroadcastResponse>?
)

// --- LiveStream Models ---

data class YTStreamRequest(
    val snippet: YTStreamSnippetRequest,
    val cdn: YTStreamCdnRequest
)

data class YTStreamSnippetRequest(
    val title: String
)

data class YTStreamCdnRequest(
    val frameRate: String = "60fps", // 30fps, 60fps, variable
    val ingestionType: String = "rtmp", // rtmp, hls
    val resolution: String = "1080p" // 1080p, 720p, 480p, 360p, 240p, variable
)

data class YTStreamResponse(
    val id: String,
    val snippet: YTStreamSnippetResponse?,
    val cdn: YTStreamCdnResponse?,
    val status: YTStreamStatusResponse?
)

data class YTStreamSnippetResponse(
    val title: String?
)

data class YTStreamCdnResponse(
    val ingestionInfo: YTIngestionInfo?
)

data class YTIngestionInfo(
    val streamName: String?,
    val ingestionAddress: String?,
    val backupIngestionAddress: String?
)

data class YTStreamStatusResponse(
    val streamStatus: String? // active, inactive, error
)

data class YTStreamListResponse(
    val items: List<YTStreamResponse>?
)

// --- Retrofit Service Interface ---

interface YouTubeLiveApi {

    @GET("youtube/v3/channels?part=snippet&mine=true")
    suspend fun getMyChannel(
        @Header("Authorization") authHeader: String
    ): Response<YTChannelListResponse>

    @POST("youtube/v3/liveBroadcasts?part=id,snippet,status,contentDetails")
    suspend fun createLiveBroadcast(
        @Header("Authorization") authHeader: String,
        @Body request: YTBroadcastRequest
    ): Response<YTBroadcastResponse>

    @POST("youtube/v3/liveStreams?part=id,snippet,cdn,status")
    suspend fun createLiveStream(
        @Header("Authorization") authHeader: String,
        @Body request: YTStreamRequest
    ): Response<YTStreamResponse>

    @POST("youtube/v3/liveBroadcasts/bind?part=id,snippet,status,contentDetails")
    suspend fun bindBroadcastToStream(
        @Header("Authorization") authHeader: String,
        @Query("id") broadcastId: String,
        @Query("streamId") streamId: String
    ): Response<YTBroadcastResponse>

    @POST("youtube/v3/liveBroadcasts/transition?part=id,snippet,status")
    suspend fun transitionBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("broadcastStatus") broadcastStatus: String, // "testing", "live", "complete"
        @Query("id") broadcastId: String
    ): Response<YTBroadcastResponse>

    @GET("youtube/v3/liveBroadcasts?part=id,snippet,status")
    suspend fun getLiveBroadcast(
        @Header("Authorization") authHeader: String,
        @Query("id") broadcastId: String
    ): Response<YTBroadcastListResponse>

    @GET("youtube/v3/liveStreams?part=id,snippet,cdn,status")
    suspend fun getLiveStream(
        @Header("Authorization") authHeader: String,
        @Query("id") streamId: String
    ): Response<YTStreamListResponse>

    @GET("youtube/v3/liveBroadcasts?part=id,snippet,status,contentDetails")
    suspend fun getLiveBroadcastDetails(
        @Header("Authorization") authHeader: String,
        @Query("id") broadcastId: String
    ): Response<YTBroadcastListResponse>

    @GET("youtube/v3/liveChat/messages?part=id,snippet,authorDetails")
    suspend fun getLiveChatMessages(
        @Header("Authorization") authHeader: String,
        @Query("liveChatId") liveChatId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<YTChatMessageListResponse>

    @POST("youtube/v3/liveChat/messages?part=snippet")
    suspend fun sendLiveChatMessage(
        @Header("Authorization") authHeader: String,
        @Body request: YTChatMessageInsertRequest
    ): Response<YTChatMessageItem>
}

// --- Live Chat Data Models ---

data class YTChatMessageListResponse(
    val items: List<YTChatMessageItem>?,
    val nextPageToken: String?,
    val pollingIntervalMillis: Long?
)

data class YTChatMessageItem(
    val id: String,
    val snippet: YTChatMessageSnippet?,
    val authorDetails: YTChatAuthorDetails?
)

data class YTChatMessageSnippet(
    val liveChatId: String?,
    val authorChannelId: String?,
    val publishedAt: String?,
    val displayMessage: String?,
    val textMessageDetails: YTChatTextDetails?
)

data class YTChatTextDetails(
    val messageText: String?
)

data class YTChatAuthorDetails(
    val channelId: String?,
    val displayName: String?,
    val profileImageUrl: String?,
    val isChatModerator: Boolean?,
    val isChatOwner: Boolean?
)

data class YTChatMessageInsertRequest(
    val snippet: YTChatMessageInsertSnippet
)

data class YTChatMessageInsertSnippet(
    val liveChatId: String,
    val type: String = "textMessageEvent",
    val textMessageDetails: YTChatTextMessageDetails
)

data class YTChatTextMessageDetails(
    val messageText: String
)

