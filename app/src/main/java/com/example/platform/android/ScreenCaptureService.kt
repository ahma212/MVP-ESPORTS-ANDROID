package com.example.platform.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.core.model.DetectionFrame
import com.example.services.video.VideoCaptureState
import com.example.services.video.VideoSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

import android.app.PendingIntent
import com.example.MainActivity
import com.example.services.overlay.FloatingPointerOverlay

/**
 * Real Android Foreground Service executing MediaProjection -> VirtualDisplay -> ImageReader pipeline.
 * Fully compliant with Android 14+ foreground service types and lifecycle constraints.
 * Serves as the long-running owner of background screen capture, floating overlay pointer, and live notifications.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.platform.android.ACTION_START"
        const val ACTION_STOP = "com.example.platform.android.ACTION_STOP"
        const val ACTION_UPDATE_LIVE_STATUS = "com.example.platform.android.ACTION_UPDATE_LIVE_STATUS"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_DENSITY_DPI = "extra_density_dpi"
        const val EXTRA_LIVE_STATUS = "extra_live_status"
        const val ACTION_TOGGLE_POINTER = "action_toggle_pointer"

        private const val NOTIFICATION_CHANNEL_ID = "esports_screen_capture_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "MVP ESPORTS LIVE Service"
        private const val NOTIFICATION_ID = 2001

        private val _captureState = MutableStateFlow<VideoCaptureState>(VideoCaptureState.Idle)
        val captureState: StateFlow<VideoCaptureState> = _captureState.asStateFlow()

        private val _frameFlow = MutableSharedFlow<DetectionFrame>(extraBufferCapacity = 64)
        val frameFlow: SharedFlow<DetectionFrame> = _frameFlow.asSharedFlow()

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isPointerEnabled: Boolean = false

        @Volatile
        var activeInstance: ScreenCaptureService? = null
            private set

        fun stopService(context: Context) {
            val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(stopIntent)
        }

        fun updateLiveStatus(context: Context, statusText: String) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_UPDATE_LIVE_STATUS
                putExtra(EXTRA_LIVE_STATUS, statusText)
            }
            context.startService(intent)
        }

        fun setPointerEnabled(context: Context, enabled: Boolean) {
            isPointerEnabled = enabled
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ACTION_TOGGLE_POINTER
                putExtra("extra_pointer_enabled", enabled)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var floatingPointerOverlay: FloatingPointerOverlay? = null

    private var frameCounter = 0L
    private var captureStartTime = 0L
    private var lastFpsTimestamp = 0L
    private var frameCountInWindow = 0
    private var currentFps = 0
    private var currentLiveStatusText = "Capture: ACTIVE | YouTube: READY"

    private var captureWidth = 1080
    private var captureHeight = 2400
    private var droppedFrames = 0L
    private var lastFrameTimestamp = 0L
    private var lastError: String? = null

    // Ring buffer pool of preallocated byte arrays to prevent per-frame GC allocations
    private val bufferPoolSize = 4
    private var bufferPool: Array<ByteArray>? = null
    private var bufferPoolWidth = 0
    private var bufferPoolHeight = 0
    private var bufferPoolIndex = 0

    private fun getPooledBuffer(width: Int, height: Int, requiredSize: Int): ByteArray {
        val size = maxOf(requiredSize, width * height * 4)
        var pool = bufferPool
        if (pool == null || bufferPoolWidth != width || bufferPoolHeight != height || pool[0].size < size) {
            pool = Array(bufferPoolSize) { ByteArray(size) }
            bufferPool = pool
            bufferPoolWidth = width
            bufferPoolHeight = height
            bufferPoolIndex = 0
        }
        val buffer = pool[bufferPoolIndex]
        bufferPoolIndex = (bufferPoolIndex + 1) % bufferPoolSize
        return buffer
    }

    private val _latestFrame = MutableStateFlow<DetectionFrame?>(null)
    val latestFrame: StateFlow<DetectionFrame?> = _latestFrame.asStateFlow()

    fun getWidth(): Int = captureWidth
    fun getHeight(): Int = captureHeight
    fun getDroppedFrames(): Long = droppedFrames
    fun getLastFrameTimestamp(): Long = lastFrameTimestamp
    fun getCaptureStartTime(): Long = captureStartTime
    fun getLastError(): String? = lastError

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapturePipeline()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    fun getFrameCount(): Long = frameCounter
    fun getFps(): Int = currentFps

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopCapturePipeline()
            return START_NOT_STICKY
        }

        if (action == ACTION_UPDATE_LIVE_STATUS && intent != null) {
            val status = intent.getStringExtra(EXTRA_LIVE_STATUS)
            if (!status.isNullOrBlank()) {
                currentLiveStatusText = status
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, buildNotification())
                floatingPointerOverlay?.updateStatus(status)
            }
            return START_NOT_STICKY
        }

        if (action == ACTION_TOGGLE_POINTER && intent != null) {
            val enabled = intent.getBooleanExtra("extra_pointer_enabled", false)
            if (enabled) {
                showFloatingPointer()
            } else {
                hideFloatingPointer()
            }
            return START_NOT_STICKY
        }

        if (action == ACTION_START && intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            val width = intent.getIntExtra(EXTRA_WIDTH, 1080)
            val height = intent.getIntExtra(EXTRA_HEIGHT, 1920)
            val densityDpi = intent.getIntExtra(EXTRA_DENSITY_DPI, 320)

            if (resultData != null && resultCode != 0) {
                startForegroundCapture(resultCode, resultData, width, height, densityDpi)
            } else {
                _captureState.value = VideoCaptureState.Error("Missing MediaProjection permission intent data")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when real-time screen capture and YouTube Live session is active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("MVP ESPORTS LIVE")
            .setContentText(currentLiveStatusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_preferences, "OPEN CONTROLS", openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP LIVE", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundCapture(
        resultCode: Int,
        resultData: Intent,
        width: Int,
        height: Int,
        densityDpi: Int
    ) {
        captureWidth = width
        captureHeight = height
        droppedFrames = 0L
        lastError = null
        try {
            val notification = buildNotification()

            // Under Android 14+ (API 34), startForeground MUST be called before getMediaProjection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    serviceType
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager == null) {
                _captureState.value = VideoCaptureState.Error("MediaProjectionManager not available")
                stopSelf()
                return
            }

            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            if (mediaProjection == null) {
                _captureState.value = VideoCaptureState.Error("Failed to obtain MediaProjection handle")
                stopSelf()
                return
            }

            // Dedicated background handler for frame delivery
            val thread = HandlerThread("RealScreenCaptureThread").apply { start() }
            handlerThread = thread
            val handler = Handler(thread.looper)
            backgroundHandler = handler

            mediaProjection?.registerCallback(mediaProjectionCallback, handler)

            // Create ImageReader for RGBA_8888 frames
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                handleImageAvailable(ir, width, height)
            }, handler)

            // Create VirtualDisplay piping screen output to ImageReader Surface
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "EsportsLiveCaptureDisplay",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            frameCounter = 0L
            captureStartTime = System.currentTimeMillis()
            lastFpsTimestamp = captureStartTime
            frameCountInWindow = 0
            currentFps = 0
            isRunning = true

            _captureState.value = VideoCaptureState.Capturing(
                sourceType = VideoSourceType.ANDROID_MEDIA_PROJECTION,
                width = width,
                height = height,
                fps = 0,
                framesCaptured = 0L,
                startTimeMs = captureStartTime
            )

            // Display MVP Floating Pointer Overlay over third-party apps ONLY IF pointer is enabled
            try {
                if (isPointerEnabled) {
                    showFloatingPointer()
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            lastError = e.localizedMessage
            _captureState.value = VideoCaptureState.Error("Failed to start MediaProjection capture: ${e.localizedMessage}", e)
            try {
                stopCapturePipeline()
            } catch (_: Exception) {}
        }
    }

    private fun handleImageAvailable(reader: ImageReader, width: Int, height: Int) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val plane = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val expectedSize = width * height * 4
            val pixelData = getPooledBuffer(width, height, expectedSize)

            if (rowPadding == 0) {
                val bytesToRead = minOf(buffer.remaining(), pixelData.size)
                buffer.get(pixelData, 0, bytesToRead)
            } else {
                var offset = 0
                val rowBytes = width * 4
                for (i in 0 until height) {
                    val rowStart = i * rowStride
                    if (rowStart < buffer.capacity()) {
                        buffer.position(rowStart)
                        val bytesToRead = minOf(rowBytes, buffer.remaining())
                        buffer.get(pixelData, offset, bytesToRead)
                    }
                    offset += rowBytes
                }
            }

            val now = System.currentTimeMillis()
            frameCounter++
            frameCountInWindow++

            if (now - lastFpsTimestamp >= 1000L) {
                val elapsed = now - lastFpsTimestamp
                currentFps = ((frameCountInWindow * 1000L) / elapsed).toInt()
                frameCountInWindow = 0
                lastFpsTimestamp = now

                // Emit low-frequency capture state updates only at 1Hz interval
                if (isRunning) {
                    _captureState.value = VideoCaptureState.Capturing(
                        sourceType = VideoSourceType.ANDROID_MEDIA_PROJECTION,
                        width = width,
                        height = height,
                        fps = currentFps,
                        framesCaptured = frameCounter,
                        startTimeMs = captureStartTime
                    )
                    AndroidBackgroundCaptureService.getInstance(applicationContext).updateMetrics(
                        fps = currentFps,
                        count = frameCounter,
                        dropped = droppedFrames,
                        lastTs = now,
                        error = lastError
                    )
                }
            }

            val frame = DetectionFrame(
                frameId = frameCounter,
                timestampMs = now,
                width = width,
                height = height,
                format = "RGBA_8888",
                buffer = pixelData,
                sourceIdentifier = "android_screen_capture"
            )

            lastFrameTimestamp = now
            _latestFrame.value = frame
            if (!_frameFlow.tryEmit(frame)) {
                droppedFrames++
            }
        } catch (_: Exception) {
            // Closed or recycled frame
        } finally {
            try {
                image.close()
            } catch (_: Exception) {}
        }
    }

    private fun stopCapturePipeline() {
        isRunning = false

        try {
            floatingPointerOverlay?.hideOverlay()
        } catch (_: Exception) {}
        floatingPointerOverlay = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(mediaProjectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        try {
            handlerThread?.quitSafely()
        } catch (_: Exception) {}
        handlerThread = null
        backgroundHandler = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        _captureState.value = VideoCaptureState.Idle
        stopSelf()
    }

    private fun showFloatingPointer() {
        try {
            if (floatingPointerOverlay == null && FloatingPointerOverlay.checkOverlayPermission(this)) {
                val overlay = FloatingPointerOverlay(this)
                overlay.onStopSessionRequested = {
                    try {
                        stopCapturePipeline()
                    } catch (_: Exception) {}
                }
                overlay.showOverlay("LIVE")
                floatingPointerOverlay = overlay
            }
        } catch (_: Exception) {}
    }

    private fun hideFloatingPointer() {
        try {
            floatingPointerOverlay?.hideOverlay()
            floatingPointerOverlay = null
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopCapturePipeline()
        activeInstance = null
        super.onDestroy()
    }
}
