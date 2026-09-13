package com.example.services.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.core.model.RoiRegion

/**
 * Reusable ROI Overlay Canvas View.
 *
 * Can be used both as:
 * 1. An in-game full-screen / bounded overlay over the real game (TYPE_APPLICATION_OVERLAY).
 * 2. An in-panel preview inside FloatingPointerOverlay or RoiEditorCard.
 *
 * Features:
 * - 48px hit targets for 4 corners (TL, TR, BL, BR) and 4 edges (T, B, L, R).
 * - Full-box dragging.
 * - Normalized (0.0 to 1.0) coordinates.
 * - Translucent cyan box with high-visibility handles.
 */
class RoiOverlayCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val HANDLE_HIT_PX = 48f
        const val MIN_BOX_SIZE_NORM = 0.04f
    }

    enum class DragMode {
        NONE,
        MOVE_BOX,
        RESIZE_TOP_LEFT,
        RESIZE_TOP_RIGHT,
        RESIZE_BOTTOM_LEFT,
        RESIZE_BOTTOM_RIGHT,
        RESIZE_TOP,
        RESIZE_BOTTOM,
        RESIZE_LEFT,
        RESIZE_RIGHT
    }

    var currentRoi: RoiRegion = RoiRegion.DEFAULT_KILL_FEED

    var isFullScreenMode: Boolean = false
    private var frameBitmap: Bitmap? = null

    var onRoiChanged: ((RoiRegion) -> Unit)? = null
    var onDragStateChanged: ((isDragging: Boolean) -> Unit)? = null

    // Drawing paints
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // MVP Cyan
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2600E5FF") // 15% opacity cyan
        style = Paint.Style.FILL
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0F172A")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF94A3B8")
        textSize = 24f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9070B14")
        style = Paint.Style.FILL
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var activeDragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    fun updateFrameAndRoi(bitmap: Bitmap?, roi: RoiRegion) {
        this.frameBitmap = bitmap
        this.currentRoi = roi
        postInvalidate()
    }

    fun setRoi(roi: RoiRegion) {
        this.currentRoi = roi
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (!isFullScreenMode) {
            val bmp = frameBitmap
            if (bmp != null && !bmp.isRecycled) {
                val src = Rect(0, 0, bmp.width, bmp.height)
                val dst = RectF(0f, 0f, w, h)
                canvas.drawBitmap(bmp, src, dst, null)
            } else {
                canvas.drawRect(0f, 0f, w, h, bgPaint)
                canvas.drawText("LIVE SCREEN CAPTURE FRAME", w / 2f, h / 2f + 8f, textPaint)
            }
        }

        val rx = currentRoi.x * w
        val ry = currentRoi.y * h
        val rw = currentRoi.width * w
        val rh = currentRoi.height * h
        val rRight = rx + rw
        val rBottom = ry + rh

        // Draw translucent selection rectangle
        val rectF = RectF(rx, ry, rRight, rBottom)
        canvas.drawRoundRect(rectF, 6f, 6f, fillPaint)
        canvas.drawRoundRect(rectF, 6f, 6f, strokePaint)

        // Draw corner handles
        val cornerRadius = if (isFullScreenMode) 20f else 12f
        drawHandle(canvas, rx, ry, cornerRadius)
        drawHandle(canvas, rRight, ry, cornerRadius)
        drawHandle(canvas, rx, rBottom, cornerRadius)
        drawHandle(canvas, rRight, rBottom, cornerRadius)

        // In full-screen mode, also draw edge middle indicators
        if (isFullScreenMode) {
            val edgeRadius = 14f
            drawHandle(canvas, rx + rw / 2f, ry, edgeRadius)
            drawHandle(canvas, rx + rw / 2f, rBottom, edgeRadius)
            drawHandle(canvas, rx, ry + rh / 2f, edgeRadius)
            drawHandle(canvas, rRight, ry + rh / 2f, edgeRadius)

            // Draw coordinate pill above the crop box
            val label = "X:%.2f Y:%.2f W:%.2f H:%.2f".format(currentRoi.x, currentRoi.y, currentRoi.width, currentRoi.height)
            val textWidth = badgeTextPaint.measureText(label)
            val badgeX = rx.coerceAtLeast(16f)
            val badgeY = (ry - 16f).coerceAtLeast(40f)
            val badgeRect = RectF(badgeX - 8f, badgeY - 32f, badgeX + textWidth + 16f, badgeY + 10f)
            canvas.drawRoundRect(badgeRect, 8f, 8f, badgeBgPaint)
            canvas.drawText(label, badgeX, badgeY, badgeTextPaint)
        }
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, handlePaint)
        canvas.drawCircle(cx, cy, radius, handleStrokePaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return super.onTouchEvent(event)

        val touchX = event.x
        val touchY = event.y

        val rx = currentRoi.x * w
        val ry = currentRoi.y * h
        val rw = currentRoi.width * w
        val rh = currentRoi.height * h
        val rRight = rx + rw
        val rBottom = ry + rh

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeDragMode = determineDragMode(touchX, touchY, rx, ry, rRight, rBottom)
                if (activeDragMode == DragMode.NONE) {
                    // Touches outside the box pass through!
                    return false
                }
                lastTouchX = touchX
                lastTouchY = touchY
                parent?.requestDisallowInterceptTouchEvent(true)
                onDragStateChanged?.invoke(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeDragMode == DragMode.NONE) return false

                val dx = (touchX - lastTouchX) / w
                val dy = (touchY - lastTouchY) / h

                applyGestureDelta(dx, dy)

                lastTouchX = touchX
                lastTouchY = touchY
                onRoiChanged?.invoke(currentRoi)
                postInvalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeDragMode != DragMode.NONE) {
                    activeDragMode = DragMode.NONE
                    parent?.requestDisallowInterceptTouchEvent(false)
                    onDragStateChanged?.invoke(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Determines whether the touch lands on a 48px corner handle, edge handle, or inside the box.
     */
    private fun determineDragMode(tx: Float, ty: Float, left: Float, top: Float, right: Float, bottom: Float): DragMode {
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f
        val hit = HANDLE_HIT_PX

        // 1. Corners (Priority)
        if (Math.hypot((tx - left).toDouble(), (ty - top).toDouble()) <= hit) return DragMode.RESIZE_TOP_LEFT
        if (Math.hypot((tx - right).toDouble(), (ty - top).toDouble()) <= hit) return DragMode.RESIZE_TOP_RIGHT
        if (Math.hypot((tx - left).toDouble(), (ty - bottom).toDouble()) <= hit) return DragMode.RESIZE_BOTTOM_LEFT
        if (Math.hypot((tx - right).toDouble(), (ty - bottom).toDouble()) <= hit) return DragMode.RESIZE_BOTTOM_RIGHT

        // 2. Edges
        if (isFullScreenMode) {
            if (Math.abs(ty - top) <= hit && tx in (left - hit)..(right + hit)) return DragMode.RESIZE_TOP
            if (Math.abs(ty - bottom) <= hit && tx in (left - hit)..(right + hit)) return DragMode.RESIZE_BOTTOM
            if (Math.abs(tx - left) <= hit && ty in (top - hit)..(bottom + hit)) return DragMode.RESIZE_LEFT
            if (Math.abs(tx - right) <= hit && ty in (top - hit)..(bottom + hit)) return DragMode.RESIZE_RIGHT
        }

        // 3. Inside box
        if (tx in left..right && ty in top..bottom) {
            return DragMode.MOVE_BOX
        }

        return DragMode.NONE
    }

    private fun applyGestureDelta(dx: Float, dy: Float) {
        var x = currentRoi.x
        var y = currentRoi.y
        var rw = currentRoi.width
        var rh = currentRoi.height

        when (activeDragMode) {
            DragMode.MOVE_BOX -> {
                x = (x + dx).coerceIn(0.0f, 1.0f - rw)
                y = (y + dy).coerceIn(0.0f, 1.0f - rh)
            }
            DragMode.RESIZE_BOTTOM_RIGHT -> {
                rw = (rw + dx).coerceIn(MIN_BOX_SIZE_NORM, 1.0f - x)
                rh = (rh + dy).coerceIn(MIN_BOX_SIZE_NORM, 1.0f - y)
            }
            DragMode.RESIZE_TOP_LEFT -> {
                val newX = (x + dx).coerceIn(0.0f, x + rw - MIN_BOX_SIZE_NORM)
                val newY = (y + dy).coerceIn(0.0f, y + rh - MIN_BOX_SIZE_NORM)
                rw += (x - newX)
                rh += (y - newY)
                x = newX
                y = newY
            }
            DragMode.RESIZE_TOP_RIGHT -> {
                rw = (rw + dx).coerceIn(MIN_BOX_SIZE_NORM, 1.0f - x)
                val newY = (y + dy).coerceIn(0.0f, y + rh - MIN_BOX_SIZE_NORM)
                rh += (y - newY)
                y = newY
            }
            DragMode.RESIZE_BOTTOM_LEFT -> {
                val newX = (x + dx).coerceIn(0.0f, x + rw - MIN_BOX_SIZE_NORM)
                rw += (x - newX)
                x = newX
                rh = (rh + dy).coerceIn(MIN_BOX_SIZE_NORM, 1.0f - y)
            }
            DragMode.RESIZE_TOP -> {
                val newY = (y + dy).coerceIn(0.0f, y + rh - MIN_BOX_SIZE_NORM)
                rh += (y - newY)
                y = newY
            }
            DragMode.RESIZE_BOTTOM -> {
                rh = (rh + dy).coerceIn(MIN_BOX_SIZE_NORM, 1.0f - y)
            }
            DragMode.RESIZE_LEFT -> {
                val newX = (x + dx).coerceIn(0.0f, x + rw - MIN_BOX_SIZE_NORM)
                rw += (x - newX)
                x = newX
            }
            DragMode.RESIZE_RIGHT -> {
                rw = (rw + dx).coerceIn(MIN_BOX_SIZE_NORM, 1.0f - x)
            }
            DragMode.NONE -> {}
        }

        currentRoi = currentRoi.clamped(newX = x, newY = y, newW = rw, newH = rh)
    }
}
