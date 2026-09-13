package com.example.services.detection.pubg

import android.graphics.Rect
import com.example.core.model.DetectionEvidence

/**
 * Segments the kill-feed ROI into multiple independent feed lines.
 *
 * Implements Requirements 1 & 5 of K4-A:
 * - Detects each visible kill-feed line independently.
 * - If multiple kill-feed lines are visible simultaneously (stacked vertically in the ROI),
 *   segments and provides an independent slice/context for each line.
 */
object PUBGKillFeedLineSegmenter {

    data class KillFeedLineSlice(
        val lineIndex: Int,
        val rawText: String,
        val subRect: Rect?,
        val subBuffer: ByteArray? = null,
        val subWidth: Int = 0,
        val subHeight: Int = 0,
        val metadata: Map<String, Any> = emptyMap()
    )

    /**
     * Segments evidence into individual lines based on text lines, metadata, or vertical pixel slicing.
     */
    fun segmentLines(evidence: DetectionEvidence): List<KillFeedLineSlice> {
        val meta = evidence.metadata

        // 1. Explicit multi-line list in metadata
        val rawLinesList = meta["feed_lines"] as? List<*>
        if (!rawLinesList.isNullOrEmpty()) {
            return rawLinesList.mapIndexedNotNull { index, item ->
                val text = item?.toString()?.trim()
                if (text.isNullOrBlank()) null
                else KillFeedLineSlice(
                    lineIndex = index,
                    rawText = text,
                    subRect = null,
                    metadata = meta + mapOf("line_index" to index)
                )
            }
        }

        // 2. Multiline feed text in metadata (separated by newline or delimiter)
        val multiLineTextCandidate = (meta["feed_line"] as? String)
            ?: (meta["raw_ocr_text"] as? String)
            ?: (meta["feed_text"] as? String)
            ?: (meta["ocr_text"] as? String)
            ?: (meta["text"] as? String)

        if (!multiLineTextCandidate.isNullOrBlank() && (multiLineTextCandidate.contains("\n") || multiLineTextCandidate.contains(";;"))) {
            val lines = multiLineTextCandidate
                .split(Regex("[\r\n]+|;;"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (lines.size > 1) {
                return lines.mapIndexed { index, lineText ->
                    KillFeedLineSlice(
                        lineIndex = index,
                        rawText = lineText,
                        subRect = null,
                        metadata = meta + mapOf("line_index" to index)
                    )
                }
            }
        }

        // 3. Structured lines in metadata
        val structuredLines = meta["lines"] as? List<*>
        if (!structuredLines.isNullOrEmpty()) {
            return structuredLines.mapIndexedNotNull { index, item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val map = item as Map<String, Any>
                    val text = (map["raw_text"] ?: map["feed_line"] ?: "${map["left_text"]} -> ${map["right_text"]}").toString()
                    KillFeedLineSlice(
                        lineIndex = index,
                        rawText = text,
                        subRect = null,
                        metadata = meta + map
                    )
                } else null
            }
        }

        // 4. Pixel buffer vertical band slicing (if multiple lines are visually present)
        val buffer = evidence.croppedBuffer ?: evidence.buffer
        val width = evidence.croppedWidth.takeIf { it > 0 } ?: 0
        val height = evidence.croppedHeight.takeIf { it > 0 } ?: 0

        // If height indicates multiple stacked lines (e.g. height >= 80px, each line is ~28-36px)
        // and no multi-line metadata was provided, check if line_count is explicitly requested or slice into vertical slots
        val explicitLineCount = (meta["line_count"] as? Number)?.toInt() ?: 1
        if (explicitLineCount > 1 && height > 0) {
            val lineHeight = height / explicitLineCount
            val slices = mutableListOf<KillFeedLineSlice>()
            for (i in 0 until explicitLineCount) {
                val top = i * lineHeight
                val bottom = (i + 1) * lineHeight
                val sliceRect = Rect(0, top, width, bottom)
                val lineSliceBuffer = sliceVerticalBuffer(buffer, width, height, top, bottom)
                slices.add(
                    KillFeedLineSlice(
                        lineIndex = i,
                        rawText = (meta["feed_line_line_$i"] as? String) ?: (meta["feed_line"] as? String) ?: "",
                        subRect = sliceRect,
                        subBuffer = lineSliceBuffer,
                        subWidth = width,
                        subHeight = lineHeight,
                        metadata = meta + mapOf("line_index" to i)
                    )
                )
            }
            return slices
        }

        // 5. Single line or multi-line text fallback
        val singleRawText = (meta["feed_line"] as? String)
            ?: (meta["raw_ocr_text"] as? String)
            ?: run {
                val left = meta["left_text"] as? String
                val right = meta["right_text"] as? String
                if (!left.isNullOrBlank() || !right.isNullOrBlank()) {
                    "${left ?: ""} -> ${right ?: ""}".trim()
                } else ""
            }

        if (singleRawText.contains("\n") || singleRawText.contains(";;")) {
            val lines = singleRawText.split(Regex("[\r\n]+|;;")).map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size > 1) {
                return lines.mapIndexed { index, lineText ->
                    KillFeedLineSlice(
                        lineIndex = index,
                        rawText = lineText,
                        subRect = if (width > 0 && height > 0) Rect(0, 0, width, height) else null,
                        subBuffer = buffer,
                        subWidth = width,
                        subHeight = height,
                        metadata = meta + mapOf("line_index" to index)
                    )
                }
            }
        }

        return listOf(
            KillFeedLineSlice(
                lineIndex = 0,
                rawText = singleRawText,
                subRect = if (width > 0 && height > 0) Rect(0, 0, width, height) else null,
                subBuffer = buffer,
                subWidth = width,
                subHeight = height,
                metadata = meta + mapOf("line_index" to 0)
            )
        )
    }

    private fun sliceVerticalBuffer(
        source: ByteArray?,
        width: Int,
        height: Int,
        topY: Int,
        bottomY: Int
    ): ByteArray? {
        if (source == null || width <= 0 || height <= 0) return null
        val safeTop = topY.coerceIn(0, height)
        val safeBottom = bottomY.coerceIn(safeTop, height)
        val sliceH = safeBottom - safeTop
        if (sliceH <= 0) return null

        val bytesPerPixel = 4 // RGBA_8888
        val lineByteCount = width * bytesPerPixel
        val sliceByteCount = sliceH * lineByteCount
        val out = ByteArray(sliceByteCount)

        val srcStart = safeTop * lineByteCount
        if (srcStart + sliceByteCount <= source.size) {
            System.arraycopy(source, srcStart, out, 0, sliceByteCount)
            return out
        }
        return null
    }
}
