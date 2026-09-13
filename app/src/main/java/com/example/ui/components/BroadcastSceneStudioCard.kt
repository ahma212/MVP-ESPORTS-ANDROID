package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.AudioMixerState
import com.example.core.model.EsportsScene
import com.example.core.model.SceneEngineState
import com.example.services.audio.AudioMixerManager
import com.example.services.scene.EsportsSceneEngine

@Composable
fun BroadcastSceneStudioCard(
    sceneState: SceneEngineState,
    audioState: AudioMixerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141416))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BROADCAST SCENE & AUDIO STUDIO",
                color = Color(0xFFFF6600),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            StatusBadge(
                label = "SCENE",
                value = sceneState.currentScene.name
            )
        }

        // Scene Transition Buttons
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SCENE STATE MACHINE",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SceneButton(
                    title = "COUNTDOWN",
                    active = sceneState.currentScene == EsportsScene.STARTING_COUNTDOWN,
                    modifier = Modifier.weight(1f)
                ) {
                    EsportsSceneEngine.transitionTo(EsportsScene.STARTING_COUNTDOWN)
                }
                SceneButton(
                    title = "LIVE MATCH",
                    active = sceneState.currentScene == EsportsScene.LIVE_MATCH,
                    modifier = Modifier.weight(1f)
                ) {
                    EsportsSceneEngine.transitionTo(EsportsScene.LIVE_MATCH)
                }
                SceneButton(
                    title = "BREAK",
                    active = sceneState.currentScene == EsportsScene.BREAK,
                    modifier = Modifier.weight(1f)
                ) {
                    EsportsSceneEngine.transitionTo(EsportsScene.BREAK)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SceneButton(
                    title = "ENDING",
                    active = sceneState.currentScene == EsportsScene.ENDING,
                    modifier = Modifier.weight(1f)
                ) {
                    EsportsSceneEngine.transitionTo(EsportsScene.ENDING)
                }
                SceneButton(
                    title = "STOPPED",
                    active = sceneState.currentScene == EsportsScene.STOPPED,
                    modifier = Modifier.weight(1f)
                ) {
                    EsportsSceneEngine.transitionTo(EsportsScene.STOPPED)
                }
            }
        }

        // Timer Display if running
        if (sceneState.isRunning) {
            val remainingMin = (sceneState.remainingCountdownSeconds + sceneState.remainingBreakSeconds + sceneState.remainingEndingSeconds) / 60
            val remainingSec = (sceneState.remainingCountdownSeconds + sceneState.remainingBreakSeconds + sceneState.remainingEndingSeconds) % 60
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${sceneState.currentScene.name} REMAINING",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format("%02d:%02d", remainingMin, remainingSec),
                    color = Color(0xFFFF6600),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Audio Mixer Section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "AUDIO MIXER CHANNELS & PIPELINE",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            
            AudioChannelRow(
                channelName = "GAME AUDIO",
                volume = audioState.gameVolume,
                isMuted = audioState.gameMuted,
                onVolumeChanged = { AudioMixerManager.setGameVolume(it) },
                onToggleMute = { AudioMixerManager.toggleGameMute() }
            )
            AudioChannelRow(
                channelName = "MIC / COMMENTARY",
                volume = audioState.micVolume,
                isMuted = audioState.micMuted,
                onVolumeChanged = { AudioMixerManager.setMicVolume(it) },
                onToggleMute = { AudioMixerManager.toggleMicMute() }
            )
            AudioChannelRow(
                channelName = "MUSIC / BGM",
                volume = audioState.musicVolume,
                isMuted = audioState.musicMuted,
                onVolumeChanged = { AudioMixerManager.setMusicVolume(it) },
                onToggleMute = { AudioMixerManager.toggleMusicMute() }
            )

            val micState = com.example.services.audio.MicrophoneCommentaryManager.micState.collectAsState().value
            val musicState = com.example.services.audio.LocalMusicPlayerManager.musicState.collectAsState().value
            val internalAudioState = com.example.services.audio.InternalAudioCaptureManager.internalAudioState.collectAsState().value

            Spacer(modifier = Modifier.height(6.dp))

            // Microphone Status & Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MIC: ${if (micState.isRecording) "LIVE (RMS: ${(micState.peakVolume * 100).toInt()}%)" else "STOPPED"}",
                        color = if (micState.isRecording) Color.Green else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = micState.statusMessage,
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = {
                        if (micState.isRecording) {
                            com.example.services.audio.MicrophoneCommentaryManager.stopMicrophone()
                        } else {
                            com.example.services.audio.MicrophoneCommentaryManager.startMicrophone(context)
                        }
                    },
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (micState.isRecording) Color.Red else Color(0xFFFF6600)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (micState.isRecording) "STOP MIC" else "START MIC",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Music Player Status & Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MUSIC: ${musicState.songTitle}",
                        color = if (musicState.isPlaying) Color(0xFFFF6600) else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Text(
                        text = "Looping: ${musicState.isLooping}",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = {
                            if (musicState.isPlaying) {
                                com.example.services.audio.LocalMusicPlayerManager.pause()
                            } else {
                                com.example.services.audio.LocalMusicPlayerManager.resume()
                            }
                        },
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E2E38)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = if (musicState.isPlaying) "PAUSE" else "PLAY", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { com.example.services.audio.LocalMusicPlayerManager.stop() },
                        modifier = Modifier.height(30.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E1E1E)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = "STOP", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Internal Audio Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "INTERNAL GAME AUDIO CAPTURE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = internalAudioState.limitationMessage,
                        color = if (internalAudioState.isSupported) Color.Green else Color.Yellow,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SceneButton(
    title: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFFFF6600) else Color(0xFF1E1E22),
            contentColor = if (active) Color.Black else Color.White
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
        )
    }
}

@Composable
private fun AudioChannelRow(
    channelName: String,
    volume: Float,
    isMuted: Boolean,
    onVolumeChanged: (Float) -> Unit,
    onToggleMute: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = null,
                tint = if (isMuted) Color.Red else Color(0xFFFF6600),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = channelName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(110.dp)
        )
        Slider(
            value = if (isMuted) 0f else volume,
            onValueChange = onVolumeChanged,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6600),
                activeTrackColor = Color(0xFFFF6600)
            )
        )
        Text(
            text = "${((if (isMuted) 0f else volume) * 100).toInt()}%",
            color = Color.Gray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(36.dp)
        )
    }
}
