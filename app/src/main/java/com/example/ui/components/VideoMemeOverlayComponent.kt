package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.model.VideoOverlayState

/**
 * Foundation component for temporary local video / meme overlays.
 * Uses local Android VideoView, supporting play, stop, auto-hide on end, and replay.
 */
@Composable
fun VideoMemeOverlayComponent(
    state: VideoOverlayState,
    onPlaybackEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isPlaying || state.mediaUri.isNullOrBlank()) return

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .width(state.widthDp.dp)
                    .height(state.heightDp.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(2.dp, Color(0xFFFF6600), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoURI(Uri.parse(state.mediaUri))
                            setOnCompletionListener {
                                if (state.autoHideOnEnd) {
                                    onPlaybackEnded()
                                } else {
                                    start() // loop replay
                                }
                            }
                            start()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
