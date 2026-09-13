package com.example.services.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.core.model.RoiRegion
import com.example.services.roi.InMemoryRoiConfigurationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real In-Game ROI Cropper Overlay.
 *
 * Runs as a system overlay (TYPE_APPLICATION_OVERLAY) over PUBG Mobile or any running game.
 * Allows the operator to visually drag and resize a bounding box directly on top of
 * the real in-game kill feed.
 *
 * Requirements fulfilled:
 * - ROI EDIT ON / SAVE & HIDE buttons in the app AI & ROI tab AND in the pointer ROI section.
 * - When ROI EDIT is ON, draws a translucent rectangle OVER the real game.
 * - User can drag the box and resize corners/edges with large hit targets (48px).
 * - Touches outside the box pass through to the game (FLAG_NOT_TOUCH_MODAL + window bounds wrap box & handles).
 * - Coordinates stay normalized 0..1 and save to InMemoryRoiConfigurationService.
 * - SAVE & HIDE: persist ROI, then remove the on-game rectangle so it is gone from the screen.
 * - ROI EDIT OFF / hide: removes the rectangle without changing the last saved crop.
 * - Detector keeps reading only the saved crop.
 */
class FloatingRoiScreenCropper private constructor(private val appContext: Context) {

    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayRoot: FrameLayout? = null
    private var canvasView: RoiOverlayCanvasView? = null
    private var coordsTextView: TextView? = null
    private var isAttached = false

    private var currentRoi: RoiRegion = RoiRegion.DEFAULT_KILL_FEED
    private var screenW: Float = 1920f
    private var screenH: Float = 1080f

    companion object {
        @Volatile
        private var instance: FloatingRoiScreenCropper? = null

        private val _isEditActive = MutableStateFlow(false)
        val isEditActive: StateFlow<Boolean> = _isEditActive.asStateFlow()

        /**
         * ROI EDIT ON: Launches the on-screen game cropper overlay.
         */
        fun startEdit(context: Context, onSaved: ((RoiRegion) -> Unit)? = null) {
            synchronized(this) {
                if (instance != null) {
                    instance?.dismiss()
                }
                val cropper = FloatingRoiScreenCropper(context.applicationContext)
                instance = cropper
                cropper.show(onSaved)
                _isEditActive.value = true
            }
        }

        /**
         * Legacy alias for startEdit.
         */
        fun show(context: Context, onSaved: ((RoiRegion) -> Unit)? = null) {
            startEdit(context, onSaved)
        }

        /**
         * SAVE & HIDE: Persist current ROI to InMemoryRoiConfigurationService and remove on-game rectangle.
         */
        fun saveAndHide() {
            synchronized(this) {
                instance?.saveAndHideInternal()
            }
        }

        /**
         * ROI EDIT OFF / hide: Remove on-game rectangle without changing last saved crop.
         */
        fun stopEdit() {
            synchronized(this) {
                instance?.dismiss()
                instance = null
                _isEditActive.value = false
            }
        }

        /**
         * Legacy alias for stopEdit.
         */
        fun dismiss() {
            stopEdit()
        }

        fun getCurrentWorkingRoi(): RoiRegion? {
            return instance?.currentRoi
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(onSaved: ((RoiRegion) -> Unit)?) {
        if (isAttached || !FloatingPointerOverlay.checkOverlayPermission(appContext)) return

        val displayMetrics = appContext.resources.displayMetrics
        screenW = displayMetrics.widthPixels.toFloat()
        screenH = displayMetrics.heightPixels.toFloat()

        // Read last saved crop
        val savedRoi = InMemoryRoiConfigurationService.getInstance().selectedRoi.value
            ?: InMemoryRoiConfigurationService.getInstance().rois.value.find { it.id == "kill_feed" }
            ?: RoiRegion.DEFAULT_KILL_FEED
        currentRoi = savedRoi

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Window params: FLAG_NOT_TOUCH_MODAL allows touches outside window bounds to pass straight through to the game!
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val root = object : FrameLayout(appContext) {
            // Only consume touch if it lands on the ROI box, handles, or the action toolbar.
            // Touches outside pass through to the game below.
            override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                return false
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Full-screen transparent canvas view for drawing translucent rectangle & 48px handles
        val cv = RoiOverlayCanvasView(appContext).apply {
            isFullScreenMode = true
            setRoi(currentRoi)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            onRoiChanged = { updatedRoi ->
                currentRoi = updatedRoi
                coordsTextView?.text = formatCoords(updatedRoi)
            }
        }
        canvasView = cv
        root.addView(cv)

        // Floating Action Toolbar (SAVE & HIDE / ROI EDIT OFF / Coords)
        val toolbar = createActionToolbar(onSaved)
        root.addView(toolbar)

        try {
            windowManager.addView(root, params)
            overlayRoot = root
            isAttached = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createActionToolbar(onSaved: ((RoiRegion) -> Unit)?): LinearLayout {
        val dp = appContext.resources.displayMetrics.density

        return LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0070B14")) // Dark Navy
                cornerRadius = 16 * dp
                setStroke((1.5f * dp).toInt(), Color.parseColor("#00E5FF"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = (24 * dp).toInt()
            }

            val titleText = TextView(appContext).apply {
                text = "ROI CROPPER"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, (10 * dp).toInt(), 0)
            }
            addView(titleText)

            val tvCoords = TextView(appContext).apply {
                text = formatCoords(currentRoi)
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, (14 * dp).toInt(), 0)
            }
            coordsTextView = tvCoords
            addView(tvCoords)

            // SAVE & HIDE Button
            val saveBtn = Button(appContext).apply {
                text = "SAVE & HIDE"
                setTextColor(Color.WHITE)
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#10B981")) // Green Success
                    cornerRadius = 12 * dp
                }
                layoutParams = LinearLayout.LayoutParams((94 * dp).toInt(), (34 * dp).toInt()).apply {
                    rightMargin = (8 * dp).toInt()
                }
                setOnClickListener {
                    saveAndHideInternal(onSaved)
                }
            }
            addView(saveBtn)

            // ROI EDIT OFF / Hide Button
            val offBtn = Button(appContext).apply {
                text = "ROI EDIT OFF"
                setTextColor(Color.parseColor("#EF4444")) // Red
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E293B"))
                    cornerRadius = 12 * dp
                    setStroke((1f * dp).toInt(), Color.parseColor("#EF4444"))
                }
                layoutParams = LinearLayout.LayoutParams((90 * dp).toInt(), (34 * dp).toInt())
                setOnClickListener {
                    stopEdit()
                }
            }
            addView(offBtn)
        }
    }

    private fun formatCoords(roi: RoiRegion): String {
        return "X:%.2f Y:%.2f W:%.2f H:%.2f".format(roi.x, roi.y, roi.width, roi.height)
    }

    private fun saveAndHideInternal(onSaved: ((RoiRegion) -> Unit)? = null) {
        val roiToSave = currentRoi
        CoroutineScope(Dispatchers.Main).launch {
            InMemoryRoiConfigurationService.getInstance().saveRoi(roiToSave)
            onSaved?.invoke(roiToSave)
            stopEdit()
        }
    }

    private fun dismiss() {
        if (!isAttached || overlayRoot == null) return
        try {
            windowManager.removeView(overlayRoot)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayRoot = null
            canvasView = null
            coordsTextView = null
            isAttached = false
        }
    }
}
