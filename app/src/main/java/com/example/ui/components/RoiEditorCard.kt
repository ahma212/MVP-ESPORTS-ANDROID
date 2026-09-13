package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.RoiRegion
import com.example.services.overlay.FloatingRoiScreenCropper
import com.example.ui.theme.HighDensityBorder
import com.example.ui.theme.HighDensityOrange
import com.example.ui.theme.HighDensityOrangeLight
import com.example.ui.theme.HighDensitySuccess
import com.example.ui.theme.HighDensitySurface
import com.example.ui.theme.HighDensitySurfaceVariant
import com.example.ui.theme.HighDensityTextMuted
import com.example.ui.theme.HighDensityTextPrimary
import com.example.ui.theme.HighDensityTextSecondary
import java.util.Locale

/**
 * Interactive manipulation modes for ROI dragging and corner resizing.
 */
private enum class RoiInteractionMode {
    NONE,
    MOVE,
    RESIZE_N,       // Top edge
    RESIZE_S,       // Bottom edge
    RESIZE_E,       // Right edge
    RESIZE_W,       // Left edge
    RESIZE_NE,      // Top-Right corner
    RESIZE_NW,      // Top-Left corner
    RESIZE_SE,      // Bottom-Right corner
    RESIZE_SW       // Bottom-Left corner
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoiEditorCard(
    rois: List<RoiRegion>,
    selectedRoi: RoiRegion?,
    frameWidth: Int,
    frameHeight: Int,
    frameBitmap: ImageBitmap?,
    croppedBitmap: ImageBitmap?,
    framesReceived: Long,
    framesSampled: Long,
    captureFps: Int,
    previewFps: Double,
    analysisFps: Double,
    targetSampleFps: Double,
    isAiAssistEnabled: Boolean = false,
    isAiAvailable: Boolean = false,
    aiStatusText: String = "OFF (OCR only)",
    onTargetSampleFpsChanged: (Double) -> Unit,
    onSelectRoi: (String) -> Unit,
    onUpdateRoi: (RoiRegion) -> Unit,
    onToggleRoiEnabled: (String) -> Unit,
    onDeleteRoi: (String) -> Unit,
    onResetDefaults: () -> Unit,
    onAddNewRoi: (String) -> Unit,
    onToggleAiAssist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeRoi = selectedRoi ?: RoiRegion.DEFAULT_KILL_FEED
    val safeFrameWidth = if (frameWidth > 0) frameWidth else 1080
    val safeFrameHeight = if (frameHeight > 0) frameHeight else 1920
    val pixelRect = activeRoi.toPixelRect(safeFrameWidth, safeFrameHeight)

    var currentInteractionMode by remember { mutableStateOf(RoiInteractionMode.NONE) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(HighDensitySurface)
            .border(1.dp, HighDensityBorder, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = null,
                    tint = HighDensityOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "FRAME ANALYSIS & ROI EDITOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            Text(
                text = "PHASE 4B // DUAL STREAM",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = HighDensityOrangeLight
            )
        }

        // Real AI ASSIST ON / OFF Switch Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (isAiAssistEnabled) HighDensitySurfaceVariant else HighDensitySurfaceVariant.copy(alpha = 0.6f))
                .border(
                    1.dp,
                    if (isAiAssistEnabled) HighDensityOrange.copy(alpha = 0.8f) else HighDensityBorder,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (isAiAssistEnabled) HighDensityOrange else HighDensityTextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "AI ASSIST (VISION + OCR):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = HighDensityTextPrimary
                        )
                        Text(
                            text = if (isAiAssistEnabled) "ON" else "OFF",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = if (isAiAssistEnabled) HighDensitySuccess else HighDensityTextMuted
                        )
                    }
                    Text(
                        text = if (!isAiAssistEnabled) {
                            "OFF // Standard OCR & icon detection only. No extra AI work."
                        } else if (!isAiAvailable) {
                            "AI offline, OCR only // Local crop sharpening & normalizer active"
                        } else {
                            "ACTIVE // PUBGVisualAIAssistant & MLKit vision sharpening crop & clarifying names"
                        },
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isAiAssistEnabled) HighDensityTextSecondary else HighDensityTextMuted
                    )
                }
            }

            Switch(
                checked = isAiAssistEnabled,
                onCheckedChange = { onToggleAiAssist() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HighDensityOrange,
                    uncheckedThumbColor = HighDensityTextMuted,
                    uncheckedTrackColor = HighDensitySurface
                )
            )
        }

        // Distinct Three-Tier Independent Telemetry: CAPTURE FPS, PREVIEW FPS, ANALYSIS FPS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HighDensitySurfaceVariant)
                .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "CAPTURE FPS",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
                Text(
                    text = "$captureFps FPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensitySuccess
                )
            }
            Column {
                Text(
                    text = "PREVIEW FPS",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
                Text(
                    text = String.format(Locale.US, "%.1f", previewFps),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64B5F6)
                )
            }
            Column {
                Text(
                    text = "ANALYSIS FPS",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
                Text(
                    text = String.format(Locale.US, "%.1f", analysisFps),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOrange
                )
            }
            Column {
                Text(
                    text = "SAMPLED",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )
                Text(
                    text = "$framesSampled",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOrangeLight
                )
            }
        }

        // Configurable Analysis Rate Slider (Independent from Smooth Preview)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target Analysis Sampling Rate:",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextSecondary
                )
                Text(
                    text = "${targetSampleFps.toInt()} FPS (Decoupled)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOrange
                )
            }
            Slider(
                value = targetSampleFps.toFloat(),
                onValueChange = { onTargetSampleFpsChanged(it.toDouble()) },
                valueRange = 1f..30f,
                steps = 28,
                colors = SliderDefaults.colors(
                    thumbColor = HighDensityOrange,
                    activeTrackColor = HighDensityOrange,
                    inactiveTrackColor = HighDensityBorder
                ),
                modifier = Modifier.height(24.dp)
            )
        }

        // Named ROI Selector Chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SELECT ACTIVE ROI:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )

                IconButton(
                    onClick = onResetDefaults,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset ROIs",
                        tint = HighDensityTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rois.forEach { roi ->
                    val isSelected = roi.id == activeRoi.id
                    val chipBorderColor = when {
                        isSelected -> HighDensityOrange
                        roi.enabled -> HighDensityBorder
                        else -> HighDensityBorder.copy(alpha = 0.4f)
                    }
                    val chipBg = when {
                        isSelected -> Color(0xFF26180E)
                        else -> HighDensitySurfaceVariant
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorderColor, RoundedCornerShape(4.dp))
                            .clickable { onSelectRoi(roi.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (roi.enabled) HighDensitySuccess else HighDensityTextMuted)
                        )
                        Text(
                            text = roi.name,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = if (isSelected) HighDensityOrange else HighDensityTextPrimary
                        )
                    }
                }
            }
        }

        // Header with Live Interaction Feedback
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FULL-FRAME VIEWPORT & REAL TOUCH ROI",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = HighDensityTextMuted
            )

            if (currentInteractionMode != RoiInteractionMode.NONE) {
                val actionLabel = when (currentInteractionMode) {
                    RoiInteractionMode.MOVE -> "↔ DRAGGING ROI"
                    RoiInteractionMode.RESIZE_NW -> "↖ RESIZING TOP-LEFT"
                    RoiInteractionMode.RESIZE_NE -> "↗ RESIZING TOP-RIGHT"
                    RoiInteractionMode.RESIZE_SW -> "↙ RESIZING BOTTOM-LEFT"
                    RoiInteractionMode.RESIZE_SE -> "↘ RESIZING BOTTOM-RIGHT"
                    RoiInteractionMode.RESIZE_N -> "↕ RESIZING TOP EDGE"
                    RoiInteractionMode.RESIZE_S -> "↕ RESIZING BOTTOM EDGE"
                    RoiInteractionMode.RESIZE_W -> "↔ RESIZING LEFT EDGE"
                    RoiInteractionMode.RESIZE_E -> "↔ RESIZING RIGHT EDGE"
                    RoiInteractionMode.NONE -> ""
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF331E10))
                        .border(1.dp, HighDensityOrange, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityOrangeLight
                    )
                }
            }
        }

        // Interactive Viewport with Accurate ContentScale.Fit Geometry & Continuous Touch Gestures
        val density = LocalDensity.current
        val isPortraitFrame = safeFrameHeight > safeFrameWidth
        val frameAspectRatio = if (safeFrameHeight > 0) safeFrameWidth.toFloat() / safeFrameHeight.toFloat() else 16f / 9f

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val viewportModifier = if (isPortraitFrame) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF070709))
                    .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(frameAspectRatio.coerceIn(1.33f, 2.33f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF070709))
                    .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
            }

            BoxWithConstraints(
                modifier = viewportModifier
            ) {
            val viewWidthPx = constraints.maxWidth.toFloat()
            val viewHeightPx = constraints.maxHeight.toFloat()

            // Calculate precise ContentScale.Crop displayed image bounds
            val srcW = safeFrameWidth.toFloat()
            val srcH = safeFrameHeight.toFloat()
            val cropScale = maxOf(viewWidthPx / srcW, viewHeightPx / srcH)
            val displayedImgWidth = srcW * cropScale
            val displayedImgHeight = srcH * cropScale
            val imgLeft = (viewWidthPx - displayedImgWidth) / 2f
            val imgTop = (viewHeightPx - displayedImgHeight) / 2f

            // Map Normalized ROI -> Viewport Screen Pixels
            // In Crop mapping mode, the cropped area fills the card, so the box boundaries are the image boundaries.
            val boxLeft = imgLeft
            val boxTop = imgTop
            val boxWidth = displayedImgWidth
            val boxHeight = displayedImgHeight
            val boxRight = boxLeft + boxWidth
            val boxBottom = boxTop + boxHeight

            // State holders for stable non-restarting gestures
            val currentRoiState = rememberUpdatedState(activeRoi)
            val currentOnUpdateRoiState = rememberUpdatedState(onUpdateRoi)
            val currentImgLeft = rememberUpdatedState(imgLeft)
            val currentImgTop = rememberUpdatedState(imgTop)
            val currentImgW = rememberUpdatedState(displayedImgWidth)
            val currentImgH = rememberUpdatedState(displayedImgHeight)

            // ROI editor uses the frame only for ROI geometry/interaction.
// Do NOT render another live video surface here.
// The dedicated cropped ROI preview below is the only video preview
// retained for the AI/ROI section.

Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0A0A0C)),
    contentAlignment = Alignment.Center
) {
    Text(
        text = "ROI AREA • CROPPED PREVIEW BELOW",
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = HighDensityTextMuted
    )
}

            // Draw ROI Box, Letterbox Dimming & Resize Handles
            Canvas(modifier = Modifier.fillMaxSize()) {

                // Clear / highlight the active ROI inside the image
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight)
                )

                // ROI Outline Stroke
                val strokeColor = if (activeRoi.enabled) HighDensityOrange else HighDensityTextMuted
                drawRect(
                    color = strokeColor,
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxWidth, boxHeight),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Draw Grid lines inside ROI (1/3 rule)
                val gridStroke = Stroke(width = 0.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
                drawLine(
                    color = strokeColor.copy(alpha = 0.4f),
                    start = Offset(boxLeft + boxWidth / 3f, boxTop),
                    end = Offset(boxLeft + boxWidth / 3f, boxBottom),
                    strokeWidth = 0.5.dp.toPx()
                )
                drawLine(
                    color = strokeColor.copy(alpha = 0.4f),
                    start = Offset(boxLeft + 2 * boxWidth / 3f, boxTop),
                    end = Offset(boxLeft + 2 * boxWidth / 3f, boxBottom),
                    strokeWidth = 0.5.dp.toPx()
                )
                drawLine(
                    color = strokeColor.copy(alpha = 0.4f),
                    start = Offset(boxLeft, boxTop + boxHeight / 2f),
                    end = Offset(boxRight, boxTop + boxHeight / 2f),
                    strokeWidth = 0.5.dp.toPx()
                )

                // Draw 4 Prominent Corner Resize Handles
                val handleRadius = 6.dp.toPx()
                val handleOuterRadius = 9.dp.toPx()

                fun drawCornerHandle(center: Offset, isHighlighted: Boolean) {
                    drawCircle(
                        color = Color.Black,
                        radius = handleOuterRadius,
                        center = center
                    )
                    drawCircle(
                        color = if (isHighlighted) Color.White else HighDensityOrangeLight,
                        radius = handleRadius,
                        center = center
                    )
                    drawCircle(
                        color = if (isHighlighted) HighDensityOrange else Color(0xFF1E1610),
                        radius = handleRadius * 0.4f,
                        center = center
                    )
                }

                drawCornerHandle(
                    Offset(boxLeft, boxTop),
                    currentInteractionMode == RoiInteractionMode.RESIZE_NW
                )
                drawCornerHandle(
                    Offset(boxRight, boxTop),
                    currentInteractionMode == RoiInteractionMode.RESIZE_NE
                )
                drawCornerHandle(
                    Offset(boxLeft, boxBottom),
                    currentInteractionMode == RoiInteractionMode.RESIZE_SW
                )
                drawCornerHandle(
                    Offset(boxRight, boxBottom),
                    currentInteractionMode == RoiInteractionMode.RESIZE_SE
                )

                // Draw 4 Prominent Edge Resize Handles
                fun drawEdgeHandle(center: Offset, isHighlighted: Boolean) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(center.x - 7.dp.toPx(), center.y - 7.dp.toPx()),
                        size = Size(14.dp.toPx(), 14.dp.toPx())
                    )
                    drawRect(
                        color = if (isHighlighted) Color.White else HighDensityOrangeLight,
                        topLeft = Offset(center.x - 5.dp.toPx(), center.y - 5.dp.toPx()),
                        size = Size(10.dp.toPx(), 10.dp.toPx())
                    )
                    drawRect(
                        color = if (isHighlighted) HighDensityOrange else Color(0xFF1E1610),
                        topLeft = Offset(center.x - 2.dp.toPx(), center.y - 2.dp.toPx()),
                        size = Size(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Middle of Top Edge (N)
                drawEdgeHandle(
                    Offset(boxLeft + boxWidth / 2f, boxTop),
                    currentInteractionMode == RoiInteractionMode.RESIZE_N
                )
                // Middle of Bottom Edge (S)
                drawEdgeHandle(
                    Offset(boxLeft + boxWidth / 2f, boxBottom),
                    currentInteractionMode == RoiInteractionMode.RESIZE_S
                )
                // Middle of Left Edge (W)
                drawEdgeHandle(
                    Offset(boxLeft, boxTop + boxHeight / 2f),
                    currentInteractionMode == RoiInteractionMode.RESIZE_W
                )
                // Middle of Right Edge (E)
                drawEdgeHandle(
                    Offset(boxRight, boxTop + boxHeight / 2f),
                    currentInteractionMode == RoiInteractionMode.RESIZE_E
                )
            }

            // Real Drag & Multi-Corner/Edge Resize Gesture Detector Layer using raw pointer events on Initial pass
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                                val downChange = downEvent.changes.firstOrNull { it.pressed } ?: continue
                                val pointerId = downChange.id
                                val downPos = downChange.position

                                val iLeft = currentImgLeft.value
                                val iTop = currentImgTop.value
                                val iW = currentImgW.value
                                val iH = currentImgH.value
                                val roi = currentRoiState.value

                                if (iW <= 0f || iH <= 0f) continue

                                val bLeft = iLeft
                                val bTop = iTop
                                val bRight = iLeft + iW
                                val bBottom = iTop + iH

                                // Large hit targets: corners 48px, edges 48px
                                val cornerHitRadius = 48f
                                val edgeHitMargin = 48f

                                val dNW = (downPos - Offset(bLeft, bTop)).getDistance()
                                val dNE = (downPos - Offset(bRight, bTop)).getDistance()
                                val dSW = (downPos - Offset(bLeft, bBottom)).getDistance()
                                val dSE = (downPos - Offset(bRight, bBottom)).getDistance()

                                val minCornerDist = minOf(dNW, dNE, dSW, dSE)
                                val hitMode = if (minCornerDist <= cornerHitRadius) {
                                    when (minCornerDist) {
                                        dNW -> RoiInteractionMode.RESIZE_NW
                                        dNE -> RoiInteractionMode.RESIZE_NE
                                        dSW -> RoiInteractionMode.RESIZE_SW
                                        else -> RoiInteractionMode.RESIZE_SE
                                    }
                                } else {
                                    // 2. Edge resize handles
                                    val inXSpan = downPos.x in (bLeft - edgeHitMargin)..(bRight + edgeHitMargin)
                                    val inYSpan = downPos.y in (bTop - edgeHitMargin)..(bBottom + edgeHitMargin)

                                    val distTop = kotlin.math.abs(downPos.y - bTop)
                                    val distBottom = kotlin.math.abs(downPos.y - bBottom)
                                    val distLeft = kotlin.math.abs(downPos.x - bLeft)
                                    val distRight = kotlin.math.abs(downPos.x - bRight)

                                    when {
                                        distTop <= edgeHitMargin && inXSpan -> RoiInteractionMode.RESIZE_N
                                        distBottom <= edgeHitMargin && inXSpan -> RoiInteractionMode.RESIZE_S
                                        distLeft <= edgeHitMargin && inYSpan -> RoiInteractionMode.RESIZE_W
                                        distRight <= edgeHitMargin && inYSpan -> RoiInteractionMode.RESIZE_E
                                        // 3. ROI Body Move
                                        downPos.x in bLeft..bRight && downPos.y in bTop..bBottom -> RoiInteractionMode.MOVE
                                        else -> RoiInteractionMode.NONE
                                    }
                                }

                                if (hitMode == RoiInteractionMode.NONE) {
                                    continue
                                }

                                currentInteractionMode = hitMode
                                downChange.consume()

                                val startRoi = roi
                                val startPos = downPos
                                val minSize = 0.04f

                                var isDragging = true
                                while (isDragging) {
                                    val moveEvent = awaitPointerEvent(PointerEventPass.Initial)
                                    val pointerChange = moveEvent.changes.firstOrNull { it.id == pointerId }

                                    if (pointerChange == null || !pointerChange.pressed) {
                                        isDragging = false
                                        currentInteractionMode = RoiInteractionMode.NONE
                                    } else {
                                        pointerChange.consume()
                                        val currentPos = pointerChange.position
                                        val deltaX = currentPos.x - startPos.x
                                        val deltaY = currentPos.y - startPos.y

                                        val onUpdate = currentOnUpdateRoiState.value

                                         when (hitMode) {
                                            RoiInteractionMode.MOVE -> {
                                                val roiDeltaX = (deltaX / iW) * startRoi.width
                                                val roiDeltaY = (deltaY / iH) * startRoi.height
                                                val newNormX = (startRoi.x + roiDeltaX).coerceIn(0.0f, (1.0f - startRoi.width).coerceAtLeast(0.0f))
                                                val newNormY = (startRoi.y + roiDeltaY).coerceIn(0.0f, (1.0f - startRoi.height).coerceAtLeast(0.0f))
                                                onUpdate(startRoi.copy(x = newNormX, y = newNormY, updatedAtMs = System.currentTimeMillis()))
                                            }

                                            RoiInteractionMode.RESIZE_NW -> {
                                                val fixedRight = startRoi.x + startRoi.width
                                                val fixedBottom = startRoi.y + startRoi.height
                                                val roiDeltaX = (deltaX / iW) * startRoi.width
                                                val roiDeltaY = (deltaY / iH) * startRoi.height
                                                val desiredLeft = (startRoi.x + roiDeltaX).coerceIn(0.0f, fixedRight - minSize)
                                                val desiredTop = (startRoi.y + roiDeltaY).coerceIn(0.0f, fixedBottom - minSize)
                                                onUpdate(startRoi.copy(
                                                    x = desiredLeft,
                                                    y = desiredTop,
                                                    width = fixedRight - desiredLeft,
                                                    height = fixedBottom - desiredTop,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.RESIZE_NE -> {
                                                val fixedLeft = startRoi.x
                                                val fixedBottom = startRoi.y + startRoi.height
                                                val roiDeltaX = (deltaX / iW) * startRoi.width
                                                val roiDeltaY = (deltaY / iH) * startRoi.height
                                                val desiredRight = (startRoi.x + startRoi.width + roiDeltaX).coerceIn(fixedLeft + minSize, 1.0f)
                                                val desiredTop = (startRoi.y + roiDeltaY).coerceIn(0.0f, fixedBottom - minSize)
                                                onUpdate(startRoi.copy(
                                                    x = fixedLeft,
                                                    y = desiredTop,
                                                    width = desiredRight - fixedLeft,
                                                    height = fixedBottom - desiredTop,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.RESIZE_SW -> {
                                                val fixedRight = startRoi.x + startRoi.width
                                                val fixedTop = startRoi.y
                                                val roiDeltaX = (deltaX / iW) * startRoi.width
                                                val roiDeltaY = (deltaY / iH) * startRoi.height
                                                val desiredLeft = (startRoi.x + roiDeltaX).coerceIn(0.0f, fixedRight - minSize)
                                                val desiredBottom = (startRoi.y + startRoi.height + roiDeltaY).coerceIn(fixedTop + minSize, 1.0f)
                                                onUpdate(startRoi.copy(
                                                    x = desiredLeft,
                                                    y = fixedTop,
                                                    width = fixedRight - desiredLeft,
                                                    height = desiredBottom - fixedTop,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.RESIZE_SE -> {
                                                val fixedLeft = startRoi.x
                                                val fixedTop = startRoi.y
                                                val roiDeltaX = (deltaX / iW) * startRoi.width
                                                val roiDeltaY = (deltaY / iH) * startRoi.height
                                                val desiredRight = (startRoi.x + startRoi.width + roiDeltaX).coerceIn(fixedLeft + minSize, 1.0f)
                                                val desiredBottom = (startRoi.y + startRoi.height + roiDeltaY).coerceIn(fixedTop + minSize, 1.0f)
                                                onUpdate(startRoi.copy(
                                                    x = fixedLeft,
                                                    y = fixedTop,
                                                    width = desiredRight - fixedLeft,
                                                    height = desiredBottom - fixedTop,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.RESIZE_N -> {
                                                val fixedBottom = startRoi.y + startRoi.height
                                                val roiDeltaY = (deltaY / iH) * startRoi.height
                                                val desiredTop = (startRoi.y + roiDeltaY).coerceIn(0.0f, fixedBottom - minSize)
                                                onUpdate(startRoi.copy(
                                                    y = desiredTop,
                                                    height = fixedBottom - desiredTop,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.RESIZE_S -> {
                                                val fixedTop = startRoi.y
                                                val roiDeltaY = (deltaY / iH) * startRoi.height
                                                val desiredBottom = (startRoi.y + startRoi.height + roiDeltaY).coerceIn(fixedTop + minSize, 1.0f)
                                                onUpdate(startRoi.copy(
                                                    height = desiredBottom - fixedTop,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.RESIZE_W -> {
                                                val fixedRight = startRoi.x + startRoi.width
                                                val roiDeltaX = (deltaX / iW) * startRoi.width
                                                val desiredLeft = (startRoi.x + roiDeltaX).coerceIn(0.0f, fixedRight - minSize)
                                                onUpdate(startRoi.copy(
                                                    x = desiredLeft,
                                                    width = fixedRight - desiredLeft,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.RESIZE_E -> {
                                                val fixedLeft = startRoi.x
                                                val roiDeltaX = (deltaX / iW) * startRoi.width
                                                val desiredRight = (startRoi.x + startRoi.width + roiDeltaX).coerceIn(fixedLeft + minSize, 1.0f)
                                                onUpdate(startRoi.copy(
                                                    width = desiredRight - fixedLeft,
                                                    updatedAtMs = System.currentTimeMillis()
                                                ))
                                            }

                                            RoiInteractionMode.NONE -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

        // Fine Step Adjustment & Coordinates Readout
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(HighDensitySurfaceVariant)
                .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ROI: ${activeRoi.name}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityOrange
                )

                val context = LocalContext.current

                val isEditActive by FloatingRoiScreenCropper.isEditActive.collectAsState()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            FloatingRoiScreenCropper.startEdit(context) { savedRoi ->
                                onUpdateRoi(savedRoi)
                            }
                        },
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEditActive) "EDITING..." else "ROI EDIT ON",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isEditActive) {
                        androidx.compose.material3.Button(
                            onClick = {
                                FloatingRoiScreenCropper.saveAndHide()
                            },
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SAVE & HIDE",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        androidx.compose.material3.Button(
                            onClick = {
                                FloatingRoiScreenCropper.stopEdit()
                            },
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ROI EDIT OFF",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onToggleRoiEnabled(activeRoi.id) },
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (activeRoi.enabled) HighDensitySuccess else HighDensityTextMuted
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = if (activeRoi.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (activeRoi.enabled) "ACTIVE" else "DISABLED",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Coordinates Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "NORMALIZED [0.000 - 1.000]",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextMuted
                    )
                    Text(
                        text = "X: ${String.format(Locale.US, "%.3f", activeRoi.x)} | Y: ${String.format(Locale.US, "%.3f", activeRoi.y)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextPrimary
                    )
                    Text(
                        text = "W: ${String.format(Locale.US, "%.3f", activeRoi.width)} | H: ${String.format(Locale.US, "%.3f", activeRoi.height)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextPrimary
                    )
                }

                Column {
                    Text(
                        text = "SOURCE RESOLUTION (${safeFrameWidth}x${safeFrameHeight})",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextMuted
                    )
                    Text(
                        text = "X: ${pixelRect.left}px | Y: ${pixelRect.top}px",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextSecondary
                    )
                    Text(
                        text = "W: ${pixelRect.width}px | H: ${pixelRect.height}px",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityTextSecondary
                    )
                }
            }

            // Step Nudge Controls for Fine Adjustment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { onUpdateRoi(activeRoi.clamped(newX = activeRoi.x - 0.01f)) },
                    modifier = Modifier.weight(1f).height(26.dp),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("← X-", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = { onUpdateRoi(activeRoi.clamped(newX = activeRoi.x + 0.01f)) },
                    modifier = Modifier.weight(1f).height(26.dp),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("X+ →", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = { onUpdateRoi(activeRoi.clamped(newY = activeRoi.y - 0.01f)) },
                    modifier = Modifier.weight(1f).height(26.dp),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("↑ Y-", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = { onUpdateRoi(activeRoi.clamped(newY = activeRoi.y + 0.01f)) },
                    modifier = Modifier.weight(1f).height(26.dp),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("Y+ ↓", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = { onUpdateRoi(activeRoi.clamped(newW = activeRoi.width - 0.01f)) },
                    modifier = Modifier.weight(1f).height(26.dp),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("W-", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                OutlinedButton(
                    onClick = { onUpdateRoi(activeRoi.clamped(newW = activeRoi.width + 0.01f)) },
                    modifier = Modifier.weight(1f).height(26.dp),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text("W+", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Enlarged & Highly Readable Cropped ROI Inspection Viewer
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CROPPED ROI INSPECTOR (PIPELINE DATA)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = HighDensityTextMuted
                )

                if (croppedBitmap != null) {
                    val cropW = croppedBitmap.width
                    val cropH = croppedBitmap.height
                    Text(
                        text = "${cropW}x${cropH} px",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = HighDensityOrangeLight
                    )
                }
            }

            if (croppedBitmap != null) {
                val cropAspect = if (croppedBitmap.height > 0) {
                    croppedBitmap.width.toFloat() / croppedBitmap.height.toFloat()
                } else 2.5f

                val cropBoxModifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(cropAspect.coerceIn(0.5f, 5.0f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF0A0A0C))
                    .border(1.dp, HighDensityOrange, RoundedCornerShape(4.dp))

                Box(
                    modifier = cropBoxModifier,
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = croppedBitmap,
                        contentDescription = "Cropped ROI Inspection",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0A0A0C))
                        .border(1.dp, HighDensityBorder, RoundedCornerShape(4.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "NO CROPPED FRAME READY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = HighDensityTextMuted
                        )
                        Text(
                            text = "Start screen capture to stream real cropped ROI data",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HighDensityTextMuted.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
