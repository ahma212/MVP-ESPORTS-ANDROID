package com.example.services.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real-time PCM Mixer that combines three independent sources of 16-bit PCM audio:
 * 1. Microphone (Mono or Stereo 16-bit PCM)
 * 2. Internal Device / Game Audio (Stereo 16-bit PCM)
 * 3. Music Audio (Stereo 16-bit PCM)
 * Handles independent volume control, muting, channel upmixing, frame alignment, and anti-clipping saturation.
 */
object PcmMixer {

    /**
     * Mixes three PCM buffers into a single standardized stereo 16-bit PCM buffer (4 bytes per frame).
     * @return Mixed stereo PCM buffer.
     */
    fun mix(
        micPcm: ByteArray?,
        micVol: Float,
        micMuted: Boolean,
        gamePcm: ByteArray?,
        gameVol: Float,
        gameMuted: Boolean,
        musicPcm: ByteArray?,
        musicVol: Float,
        musicMuted: Boolean
    ): ByteArray {
        val micGain = if (micMuted) 0f else micVol.coerceIn(0f, 1f)
        val gameGain = if (gameMuted) 0f else gameVol.coerceIn(0f, 1f)
        val musicGain = if (musicMuted) 0f else musicVol.coerceIn(0f, 1f)

        // Mic is typically mono (2 bytes per sample frame). Game & Music are stereo (4 bytes per sample frame).
        val micFrames = if (micPcm != null) micPcm.size / 2 else 0
        val gameFrames = if (gamePcm != null) gamePcm.size / 4 else 0
        val musicFrames = if (musicPcm != null) musicPcm.size / 4 else 0

        val maxFrames = maxOf(micFrames, maxOf(gameFrames, musicFrames))
        if (maxFrames == 0) return ByteArray(0)

        val outputBytes = ByteArray(maxFrames * 4) // Stereo 16-bit: 4 bytes per frame (L: 2 bytes, R: 2 bytes)

        for (f in 0 until maxFrames) {
            // 1. Mic sample (mono -> expand to Left and Right channels)
            var micLeft = 0
            var micRight = 0
            if (micPcm != null && micGain > 0f) {
                val byteIdx = f * 2
                if (byteIdx + 1 < micPcm.size) {
                    val sample = ((micPcm[byteIdx + 1].toInt() shl 8) or (micPcm[byteIdx].toInt() and 0xFF)).toShort().toInt()
                    micLeft = sample
                    micRight = sample
                } else if (micPcm.size >= 2) {
                    val sample = ((micPcm[micPcm.size - 1].toInt() shl 8) or (micPcm[micPcm.size - 2].toInt() and 0xFF)).toShort().toInt()
                    micLeft = sample
                    micRight = sample
                }
            }

            // 2. Game sample (stereo: 4 bytes per frame -> Left and Right channels)
            var gameLeft = 0
            var gameRight = 0
            if (gamePcm != null && gameGain > 0f) {
                val byteIdx = f * 4
                if (byteIdx + 3 < gamePcm.size) {
                    gameLeft = ((gamePcm[byteIdx + 1].toInt() shl 8) or (gamePcm[byteIdx].toInt() and 0xFF)).toShort().toInt()
                    gameRight = ((gamePcm[byteIdx + 3].toInt() shl 8) or (gamePcm[byteIdx + 2].toInt() and 0xFF)).toShort().toInt()
                }
            }

            // 3. Music sample (stereo: 4 bytes per frame -> Left and Right channels)
            var musicLeft = 0
            var musicRight = 0
            if (musicPcm != null && musicGain > 0f) {
                val byteIdx = f * 4
                if (byteIdx + 3 < musicPcm.size) {
                    musicLeft = ((musicPcm[byteIdx + 1].toInt() shl 8) or (musicPcm[byteIdx].toInt() and 0xFF)).toShort().toInt()
                    musicRight = ((musicPcm[byteIdx + 3].toInt() shl 8) or (musicPcm[byteIdx + 2].toInt() and 0xFF)).toShort().toInt()
                }
            }

            // Mix Left channel with independent volume gains and anti-clipping saturation
            val mixedLeft = (micLeft * micGain + gameLeft * gameGain + musicLeft * musicGain)
                .toInt()
                .coerceIn(-32768, 32767)

            // Mix Right channel with independent volume gains and anti-clipping saturation
            val mixedRight = (micRight * micGain + gameRight * gameGain + musicRight * musicGain)
                .toInt()
                .coerceIn(-32768, 32767)

            val outIdx = f * 4
            // Write Left (2 bytes)
            outputBytes[outIdx] = (mixedLeft and 0xFF).toByte()
            outputBytes[outIdx + 1] = ((mixedLeft shr 8) and 0xFF).toByte()
            // Write Right (2 bytes)
            outputBytes[outIdx + 2] = (mixedRight and 0xFF).toByte()
            outputBytes[outIdx + 3] = ((mixedRight shr 8) and 0xFF).toByte()
        }

        return outputBytes
    }
}

