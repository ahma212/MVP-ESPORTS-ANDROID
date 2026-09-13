package com.example.services.streaming

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class YouTubeLiveService private constructor(
    private val context: Context,
    private val tokenStore: SecureTokenStore = SecureTokenStore(context)
) : IYouTubeLiveService {

    companion object {
        @Volatile
        private var INSTANCE: YouTubeLiveService? = null

        fun getInstance(context: Context): YouTubeLiveService {
            return INSTANCE ?: synchronized(this) {
                val instance = YouTubeLiveService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    private val chatPageTokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    override var lastPollingIntervalMs: Long = 10000L
        private set

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { 
            level = HttpLoggingInterceptor.Level.HEADERS
            redactHeader("Authorization")
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val api: YouTubeLiveApi = retrofit.create(YouTubeLiveApi::class.java)

    init {
        // Load initial state from secure storage
        serviceScope.launch {
            checkAuthorization()
        }
    }

    private fun getAuthHeader(): String {
        val token = tokenStore.getAccessToken() ?: throw IllegalStateException("OAuth access token missing")
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    override suspend fun checkAuthorization(): Boolean {
        val token = tokenStore.getAccessToken()
        if (token.isNullOrBlank()) {
            _isAuthorized.value = false
            if (_sessionState.value != LiveSessionState.ERROR) {
                _sessionState.value = LiveSessionState.IDLE
            }
            return false
        }

        val channelTitle = tokenStore.getChannelTitle()
        val channelId = tokenStore.getChannelId()
        if (!channelTitle.isNullOrBlank() && !channelId.isNullOrBlank()) {
            _channelInfo.value = YouTubeChannelInfo(channelId, channelTitle)
            _isAuthorized.value = true
            if (_sessionState.value == LiveSessionState.IDLE || _sessionState.value == LiveSessionState.AUTHORIZING) {
                _sessionState.value = LiveSessionState.READY
            }
            return true
        }

        // Fetch channel details via API
        return try {
            val response = api.getMyChannel(getAuthHeader())
            if (response.isSuccessful && response.body()?.items?.isNotEmpty() == true) {
                val item = response.body()!!.items!![0]
                val info = YouTubeChannelInfo(
                    channelId = item.id,
                    title = item.snippet?.title ?: "YouTube Channel",
                    description = item.snippet?.description,
                    thumbnailUrl = item.snippet?.thumbnails?.default?.url
                )
                _channelInfo.value = info
                tokenStore.saveTokens(token, channelTitle = info.title, channelId = info.channelId)
                _isAuthorized.value = true
                _sessionState.value = LiveSessionState.READY
                true
            } else {
                _isAuthorized.value = false
                _sessionState.value = LiveSessionState.IDLE
                false
            }
        } catch (_: Exception) {
            _isAuthorized.value = false
            _sessionState.value = LiveSessionState.IDLE
            false
        }
    }

    override suspend fun authorizeWithToken(
        accessToken: String,
        refreshToken: String?,
        expiresInSeconds: Long
    ): Result<YouTubeChannelInfo> {
        _sessionState.value = LiveSessionState.AUTHORIZING
        _errorMessage.value = null

        return try {
            val authHeader = if (accessToken.startsWith("Bearer ")) accessToken else "Bearer $accessToken"
            val response = api.getMyChannel(authHeader)

            if (response.isSuccessful && response.body()?.items?.isNotEmpty() == true) {
                val item = response.body()!!.items!![0]
                val info = YouTubeChannelInfo(
                    channelId = item.id,
                    title = item.snippet?.title ?: "YouTube Channel",
                    description = item.snippet?.description,
                    thumbnailUrl = item.snippet?.thumbnails?.default?.url
                )

                tokenStore.saveTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = expiresInSeconds,
                    channelTitle = info.title,
                    channelId = info.channelId
                )

                _channelInfo.value = info
                _isAuthorized.value = true
                _sessionState.value = LiveSessionState.READY
                Result.success(info)
            } else {
                val err = "YouTube Authorization failed: HTTP ${response.code()} ${response.message()}"
                _errorMessage.value = err
                _sessionState.value = LiveSessionState.ERROR
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            val err = "YouTube Authorization error: ${e.localizedMessage}"
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            Result.failure(e)
        }
    }

    override suspend fun createLiveBroadcast(
        title: String,
        description: String,
        privacyStatus: String
    ): Result<YouTubeLiveBroadcastInfo> {
        _errorMessage.value = null
        if (!_isAuthorized.value || tokenStore.getAccessToken().isNullOrBlank()) {
            val err = "YouTube account is not authorized. Please connect YouTube first."
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            return Result.failure(IllegalStateException(err))
        }

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val nowIso = sdf.format(java.util.Date(System.currentTimeMillis() + 10000L))

        val req = YTBroadcastRequest(
            snippet = YTBroadcastSnippetRequest(
                title = title,
                scheduledStartTime = nowIso,
                description = description
            ),
            status = YTBroadcastStatusRequest(
                privacyStatus = privacyStatus,
                selfDeclaredMadeForKids = false
            )
        )

        return try {
            val response = api.createLiveBroadcast(getAuthHeader(), req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val info = YouTubeLiveBroadcastInfo(
                    broadcastId = body.id,
                    streamId = body.contentDetails?.boundStreamId,
                    title = body.snippet?.title ?: title,
                    lifeCycleStatus = body.status?.lifeCycleStatus ?: "created"
                )
                _currentBroadcast.value = info
                Result.success(info)
            } else {
                val err = "Failed to create broadcast: HTTP ${response.code()} ${response.message()}"
                _errorMessage.value = err
                _sessionState.value = LiveSessionState.ERROR
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            val err = "Broadcast creation error: ${e.localizedMessage}"
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            Result.failure(e)
        }
    }

    override suspend fun createLiveStream(
        title: String,
        frameRate: String,
        resolution: String
    ): Result<String> {
        _errorMessage.value = null
        if (!_isAuthorized.value || tokenStore.getAccessToken().isNullOrBlank()) {
            val err = "YouTube account is not authorized. Please connect YouTube first."
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            return Result.failure(IllegalStateException(err))
        }

        val req = YTStreamRequest(
            snippet = YTStreamSnippetRequest(title = title),
            cdn = YTStreamCdnRequest(frameRate = frameRate, ingestionType = "rtmp", resolution = resolution)
        )

        return try {
            val response = api.createLiveStream(getAuthHeader(), req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val streamId = body.id
                val ingestAddr = body.cdn?.ingestionInfo?.ingestionAddress
                val streamName = body.cdn?.ingestionInfo?.streamName

                val fullRtmpUrl = if (!ingestAddr.isNullOrBlank() && !streamName.isNullOrBlank()) {
                    "$ingestAddr/$streamName"
                } else null

                val cur = _currentBroadcast.value
                if (cur != null) {
                    _currentBroadcast.value = cur.copy(
                        streamId = streamId,
                        rtmpIngestUrl = fullRtmpUrl,
                        streamName = streamName
                    )
                }
                Result.success(streamId)
            } else {
                val err = "Failed to create stream: HTTP ${response.code()} ${response.message()}"
                _errorMessage.value = err
                _sessionState.value = LiveSessionState.ERROR
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            val err = "Stream creation error: ${e.localizedMessage}"
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            Result.failure(e)
        }
    }

    override suspend fun bindBroadcastToStream(
        broadcastId: String,
        streamId: String
    ): Result<YouTubeLiveBroadcastInfo> {
        _errorMessage.value = null
        if (!_isAuthorized.value || tokenStore.getAccessToken().isNullOrBlank()) {
            val err = "YouTube account is not authorized. Please connect YouTube first."
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            return Result.failure(IllegalStateException(err))
        }

        return try {
            val response = api.bindBroadcastToStream(getAuthHeader(), broadcastId, streamId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val cur = _currentBroadcast.value
                val updated = YouTubeLiveBroadcastInfo(
                    broadcastId = body.id,
                    streamId = streamId,
                    title = body.snippet?.title ?: (cur?.title ?: "MVP ESPORTS LIVE"),
                    rtmpIngestUrl = cur?.rtmpIngestUrl,
                    streamName = cur?.streamName,
                    lifeCycleStatus = body.status?.lifeCycleStatus ?: "ready"
                )
                _currentBroadcast.value = updated
                _sessionState.value = LiveSessionState.READY
                Result.success(updated)
            } else {
                val err = "Failed to bind stream: HTTP ${response.code()} ${response.message()}"
                _errorMessage.value = err
                _sessionState.value = LiveSessionState.ERROR
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            val err = "Stream binding error: ${e.localizedMessage}"
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            Result.failure(e)
        }
    }

    override suspend fun startBroadcast(broadcastId: String): Result<Unit> {
        _sessionState.value = LiveSessionState.STARTING
        _errorMessage.value = null
        if (!_isAuthorized.value || tokenStore.getAccessToken().isNullOrBlank()) {
            val err = "YouTube account is not authorized. Please connect YouTube first."
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            return Result.failure(IllegalStateException(err))
        }

        return try {
            // First transition to live or query
            val response = api.transitionBroadcast(getAuthHeader(), "live", broadcastId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val cur = _currentBroadcast.value
                _currentBroadcast.value = (cur ?: YouTubeLiveBroadcastInfo(broadcastId = broadcastId, title = "MVP ESPORTS LIVE"))
                    .copy(lifeCycleStatus = body.status?.lifeCycleStatus ?: "live")

                _sessionState.value = LiveSessionState.LIVE
                Result.success(Unit)
            } else {
                // If direct transition fails because stream is testing, try testing then live or update status
                val err = "YouTube start broadcast failed: HTTP ${response.code()} ${response.message()}"
                _errorMessage.value = err
                _sessionState.value = LiveSessionState.ERROR
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            val err = "Start broadcast error: ${e.localizedMessage}"
            _errorMessage.value = err
            _sessionState.value = LiveSessionState.ERROR
            Result.failure(e)
        }
    }

    override suspend fun stopBroadcast(broadcastId: String?): Result<Unit> {
        val targetId = broadcastId ?: _currentBroadcast.value?.broadcastId ?: return Result.success(Unit)
        _sessionState.value = LiveSessionState.STOPPING
        _errorMessage.value = null

        return try {
            val response = api.transitionBroadcast(getAuthHeader(), "complete", targetId)
            if (response.isSuccessful) {
                val cur = _currentBroadcast.value
                if (cur != null) {
                    _currentBroadcast.value = cur.copy(lifeCycleStatus = "complete")
                }
                _sessionState.value = LiveSessionState.STOPPED
                Result.success(Unit)
            } else {
                _sessionState.value = LiveSessionState.STOPPED
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _sessionState.value = LiveSessionState.STOPPED
            Result.failure(e)
        }
    }

    override suspend fun queryBroadcastState(broadcastId: String): Result<YouTubeLiveBroadcastInfo> {
        return try {
            val response = api.getLiveBroadcast(getAuthHeader(), broadcastId)
            if (response.isSuccessful && response.body()?.items?.isNotEmpty() == true) {
                val item = response.body()!!.items!![0]
                val cur = _currentBroadcast.value
                val info = YouTubeLiveBroadcastInfo(
                    broadcastId = item.id,
                    streamId = item.contentDetails?.boundStreamId ?: cur?.streamId,
                    title = item.snippet?.title ?: (cur?.title ?: "MVP ESPORTS LIVE"),
                    rtmpIngestUrl = cur?.rtmpIngestUrl,
                    streamName = cur?.streamName,
                    lifeCycleStatus = item.status?.lifeCycleStatus ?: "unknown"
                )
                _currentBroadcast.value = info
                if (info.lifeCycleStatus == "live") {
                    _sessionState.value = LiveSessionState.LIVE
                }
                Result.success(info)
            } else {
                Result.failure(Exception("Broadcast query failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun queryStreamState(streamId: String): Result<String> {
        return try {
            val response = api.getLiveStream(getAuthHeader(), streamId)
            if (response.isSuccessful && response.body()?.items?.isNotEmpty() == true) {
                val status = response.body()!!.items!![0].status?.streamStatus ?: "unknown"
                Result.success(status)
            } else {
                Result.failure(Exception("Stream query failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLiveChatId(broadcastId: String?): Result<String?> {
        val targetId = broadcastId ?: _currentBroadcast.value?.broadcastId ?: return Result.failure(Exception("No active broadcast ID"))
        return try {
            val response = api.getLiveBroadcastDetails(getAuthHeader(), targetId)
            if (response.isSuccessful && response.body()?.items?.isNotEmpty() == true) {
                val item = response.body()!!.items!![0]
                val chatId = item.snippet?.liveChatId
                Result.success(chatId)
            } else {
                Result.failure(Exception("Failed to get broadcast details for chat"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fetchLiveChatMessages(liveChatId: String): Result<List<YouTubeChatMessage>> {
        return try {
            val pageToken = chatPageTokens[liveChatId]
            val response = api.getLiveChatMessages(getAuthHeader(), liveChatId, pageToken = pageToken)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val items = body.items ?: emptyList()

                // Save next page token to ensure dynamic pagination
                body.nextPageToken?.let { token ->
                    chatPageTokens[liveChatId] = token
                }

                // Save polling interval
                body.pollingIntervalMillis?.let { interval ->
                    lastPollingIntervalMs = interval
                }

                val messages = items.map { item ->
                    YouTubeChatMessage(
                        messageId = item.id,
                        authorName = item.authorDetails?.displayName ?: "Viewer",
                        messageText = item.snippet?.displayMessage ?: item.snippet?.textMessageDetails?.messageText ?: "",
                        publishedAt = item.snippet?.publishedAt ?: "",
                        isModerator = item.authorDetails?.isChatModerator == true,
                        isOwner = item.authorDetails?.isChatOwner == true
                    )
                }
                Result.success(messages)
            } else {
                Result.failure(Exception("Failed to fetch chat messages: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendLiveChatMessage(liveChatId: String, messageText: String): Result<Unit> {
        return try {
            val req = YTChatMessageInsertRequest(
                snippet = YTChatMessageInsertSnippet(
                    liveChatId = liveChatId,
                    textMessageDetails = YTChatTextMessageDetails(messageText = messageText)
                )
            )
            val response = api.sendLiveChatMessage(getAuthHeader(), req)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send chat message: HTTP ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        tokenStore.clear()
        _isAuthorized.value = false
        _channelInfo.value = null
        _currentBroadcast.value = null
        _sessionState.value = LiveSessionState.IDLE
        _errorMessage.value = null
    }

    override suspend fun reconnect(): Result<Boolean> {
        _sessionState.value = LiveSessionState.RECONNECTING
        val ok = checkAuthorization()
        return if (ok) Result.success(true) else Result.failure(Exception("Reconnect failed"))
    }
}
