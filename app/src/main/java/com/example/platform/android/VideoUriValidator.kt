package com.example.platform.android

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.services.streaming.StandingTableControlsManager

sealed class VideoValidationResult {
    data class Valid(
        val uri: Uri,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val fileName: String?
    ) : VideoValidationResult()

    data class Invalid(val reason: String) : VideoValidationResult()
}

/**
 * Validates user-selected local video files before activating broadcast video/meme overlays.
 * Ensures accessibility, valid media stream type, and extracts basic metadata safely without crashing.
 */
object VideoUriValidator {

    fun validateVideoUri(context: Context, uri: Uri): VideoValidationResult {
        // Attempt to request/persist URI read permission if supported by storage framework
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Ignore if URI does not support persistable permissions
        }

        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "content" || uri.scheme == "file") {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(uri.toString())
            }

            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val width = widthStr?.toIntOrNull() ?: 0
            val height = heightStr?.toIntOrNull() ?: 0

            // Verify that metadata indicates a valid video stream or positive dimensions/duration
            if (hasVideo == "yes" || durationMs > 0 || (width > 0 && height > 0)) {
                var fileName: String? = null
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex)
                            }
                        }
                    }
                } catch (_: Exception) { }

                VideoValidationResult.Valid(
                    uri = uri,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                    fileName = fileName ?: uri.lastPathSegment ?: "Selected Video"
                )
            } else {
                VideoValidationResult.Invalid("Selected file does not contain a valid video stream.")
            }
        } catch (e: Exception) {
            VideoValidationResult.Invalid("Inaccessible or unsupported video file: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) { }
        }
    }

    fun processAndSelectVideoUri(context: Context, uri: Uri): Boolean {
        return when (val result = validateVideoUri(context, uri)) {
            is VideoValidationResult.Valid -> {
                StandingTableControlsManager.setTickerLoopVideo(result.uri.toString(), true)
                true
            }
            is VideoValidationResult.Invalid -> {
                // If invalid, clear video overlay state and disable overlay
                StandingTableControlsManager.setTickerLoopVideo(null, false)
                false
            }
        }
    }
}
