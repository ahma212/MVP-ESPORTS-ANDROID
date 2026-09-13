package com.example

import com.example.services.audio.AudioMixerManager
import com.example.services.audio.PcmMixer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit & Integration Test Suite for PART 6 — Audio Channel, Mixing & Independent Volume Control.
 */
class Part6AudioPipelineTest {

    @Before
    fun setup() {
        AudioMixerManager.setGameVolume(1.0f)
        AudioMixerManager.setMicVolume(1.0f)
        AudioMixerManager.setMusicVolume(1.0f)
    }

    @Test
    fun testMicrophoneOnOffContribution() {
        val micPcm = byteArrayOf(0x00, 0x40) // sample value 16384
        val mixedActive = PcmMixer.mix(micPcm, 1.0f, false, null, 1.0f, true, null, 1.0f, true)
        val mixedMuted = PcmMixer.mix(micPcm, 1.0f, true, null, 1.0f, true, null, 1.0f, true)

        val sampleActive = ((mixedActive[1].toInt() shl 8) or (mixedActive[0].toInt() and 0xFF)).toShort()
        assertEquals(16384, sampleActive.toInt())

        val sampleMuted = ((mixedMuted[1].toInt() shl 8) or (mixedMuted[0].toInt() and 0xFF)).toShort()
        assertEquals(0, sampleMuted.toInt())
    }

    @Test
    fun testInternalDeviceAudioOnOffContribution() {
        val gamePcm = ByteArray(4).apply {
            this[0] = 0xE8.toByte()
            this[1] = 0x03.toByte()
            this[2] = 0xD0.toByte()
            this[3] = 0x07.toByte()
        }

        val mixedActive = PcmMixer.mix(null, 1.0f, true, gamePcm, 1.0f, false, null, 1.0f, true)
        val leftActive = ((mixedActive[1].toInt() shl 8) or (mixedActive[0].toInt() and 0xFF)).toShort()
        val rightActive = ((mixedActive[3].toInt() shl 8) or (mixedActive[2].toInt() and 0xFF)).toShort()
        assertEquals(1000, leftActive.toInt())
        assertEquals(2000, rightActive.toInt())

        val mixedMuted = PcmMixer.mix(null, 1.0f, true, gamePcm, 1.0f, true, null, 1.0f, true)
        val leftMuted = ((mixedMuted[1].toInt() shl 8) or (mixedMuted[0].toInt() and 0xFF)).toShort()
        assertEquals(0, leftMuted.toInt())
    }

    @Test
    fun testMusicAudioOnOffContribution() {
        val musicPcm = ByteArray(4).apply {
            this[0] = 0x64.toByte() // 100
            this[1] = 0x00.toByte()
            this[2] = 0xC8.toByte() // 200
            this[3] = 0x00.toByte()
        }

        val mixedActive = PcmMixer.mix(null, 1.0f, true, null, 1.0f, true, musicPcm, 1.0f, false)
        val left = ((mixedActive[1].toInt() shl 8) or (mixedActive[0].toInt() and 0xFF)).toShort()
        assertEquals(100, left.toInt())

        val mixedMuted = PcmMixer.mix(null, 1.0f, true, null, 1.0f, true, musicPcm, 1.0f, true)
        val leftMuted = ((mixedMuted[1].toInt() shl 8) or (mixedMuted[0].toInt() and 0xFF)).toShort()
        assertEquals(0, leftMuted.toInt())
    }

    @Test
    fun testIndependentVolumeControlForThreeSources() {
        AudioMixerManager.setGameVolume(0.5f)
        AudioMixerManager.setMicVolume(0.8f)
        AudioMixerManager.setMusicVolume(0.2f)

        val state = AudioMixerManager.mixerState.value
        assertEquals(0.8f, state.micVolume)
        assertEquals(0.5f, state.gameVolume)
        assertEquals(0.2f, state.musicVolume)
    }

    @Test
    fun testVolumeScaling100And50And0Percent() {
        val sampleVal = 10000
        val micPcm = ByteArray(2).apply {
            this[0] = (sampleVal and 0xFF).toByte()
            this[1] = ((sampleVal shr 8) and 0xFF).toByte()
        }

        val mixed100 = PcmMixer.mix(micPcm, 1.0f, false, null, 1.0f, true, null, 1.0f, true)
        val s100 = ((mixed100[1].toInt() shl 8) or (mixed100[0].toInt() and 0xFF)).toShort().toInt()
        assertEquals(10000, s100)

        val mixed50 = PcmMixer.mix(micPcm, 0.5f, false, null, 1.0f, true, null, 1.0f, true)
        val s50 = ((mixed50[1].toInt() shl 8) or (mixed50[0].toInt() and 0xFF)).toShort().toInt()
        assertEquals(5000, s50)

        val mixed0 = PcmMixer.mix(micPcm, 0.0f, false, null, 1.0f, true, null, 1.0f, true)
        val s0 = ((mixed0[1].toInt() shl 8) or (mixed0[0].toInt() and 0xFF)).toShort().toInt()
        assertEquals(0, s0)
    }

    @Test
    fun testMonoMicrophonePlusStereoAudioMixing() {
        val micPcm = ByteArray(2).apply {
            this[0] = (1000 and 0xFF).toByte()
            this[1] = ((1000 shr 8) and 0xFF).toByte()
        }
        val gamePcm = ByteArray(4).apply {
            this[0] = (2000 and 0xFF).toByte()
            this[1] = (2000 shr 8).toByte()
            this[2] = (3000 and 0xFF).toByte()
            this[3] = (3000 shr 8).toByte()
        }

        val mixed = PcmMixer.mix(micPcm, 1.0f, false, gamePcm, 1.0f, false, null, 1.0f, true)
        val left = ((mixed[1].toInt() shl 8) or (mixed[0].toInt() and 0xFF)).toShort().toInt()
        val right = ((mixed[3].toInt() shl 8) or (mixed[2].toInt() and 0xFF)).toShort().toInt()

        assertEquals(3000, left)
        assertEquals(4000, right)
    }

    @Test
    fun testStereoPlusStereoMixing() {
        val gamePcm = ByteArray(4).apply {
            this[0] = 10; this[1] = 0; this[2] = 20; this[3] = 0
        }
        val musicPcm = ByteArray(4).apply {
            this[0] = 5; this[1] = 0; this[2] = 15; this[3] = 0
        }

        val mixed = PcmMixer.mix(null, 1.0f, true, gamePcm, 1.0f, false, musicPcm, 1.0f, false)
        val left = ((mixed[1].toInt() shl 8) or (mixed[0].toInt() and 0xFF)).toShort().toInt()
        val right = ((mixed[3].toInt() shl 8) or (mixed[2].toInt() and 0xFF)).toShort().toInt()

        assertEquals(15, left)
        assertEquals(35, right)
    }

    @Test
    fun testNoLeftRightChannelCorruption() {
        val gamePcm = ByteArray(4).apply {
            this[0] = 0x01; this[1] = 0x00; this[2] = 0x02; this[3] = 0x00
        }
        val musicPcm = ByteArray(4).apply {
            this[0] = 0x10; this[1] = 0x00; this[2] = 0x20; this[3] = 0x00
        }

        val mixed = PcmMixer.mix(null, 1.0f, true, gamePcm, 1.0f, false, musicPcm, 1.0f, false)
        val left = ((mixed[1].toInt() shl 8) or (mixed[0].toInt() and 0xFF)).toShort().toInt()
        val right = ((mixed[3].toInt() shl 8) or (mixed[2].toInt() and 0xFF)).toShort().toInt()

        assertEquals(17, left)
        assertEquals(34, right)
    }

    @Test
    fun testNoClippingOverflowDuringMixing() {
        val micPcm = ByteArray(2).apply { this[0] = 0xFF.toByte(); this[1] = 0x7F.toByte() }
        val gamePcm = ByteArray(4).apply {
            this[0] = 0xFF.toByte(); this[1] = 0x7F.toByte()
            this[2] = 0xFF.toByte(); this[3] = 0x7F.toByte()
        }
        val musicPcm = ByteArray(4).apply {
            this[0] = 0xFF.toByte(); this[1] = 0x7F.toByte()
            this[2] = 0xFF.toByte(); this[3] = 0x7F.toByte()
        }

        val mixed = PcmMixer.mix(micPcm, 1.0f, false, gamePcm, 1.0f, false, musicPcm, 1.0f, false)
        val left = ((mixed[1].toInt() shl 8) or (mixed[0].toInt() and 0xFF)).toShort().toInt()
        val right = ((mixed[3].toInt() shl 8) or (mixed[2].toInt() and 0xFF)).toShort().toInt()

        assertEquals(32767, left)
        assertEquals(32767, right)
    }

    @Test
    fun testDisabledSourceContributesZeroAmplitude() {
        val micPcm = ByteArray(2).apply { this[0] = 0x50; this[1] = 0x00 }
        val gamePcm = ByteArray(4).apply { this[0] = 0x60; this[1] = 0x00; this[2] = 0x70; this[3] = 0x00 }
        
        val mixed = PcmMixer.mix(micPcm, 1.0f, true, gamePcm, 1.0f, true, null, 1.0f, true)
        assertEquals(4, mixed.size)
        val left = ((mixed[1].toInt() shl 8) or (mixed[0].toInt() and 0xFF)).toShort().toInt()
        val right = ((mixed[3].toInt() shl 8) or (mixed[2].toInt() and 0xFF)).toShort().toInt()
        assertEquals(0, left)
        assertEquals(0, right)
    }

    @Test
    fun testAllThreeSourcesMixedTogetherCorrectly() {
        val micPcm = ByteArray(2).apply { this[0] = 10; this[1] = 0 }
        val gamePcm = ByteArray(4).apply { this[0] = 20; this[1] = 0; this[2] = 30; this[3] = 0 }
        val musicPcm = ByteArray(4).apply { this[0] = 5; this[1] = 0; this[2] = 5; this[3] = 0 }

        val mixed = PcmMixer.mix(micPcm, 1.0f, false, gamePcm, 1.0f, false, musicPcm, 1.0f, false)
        val left = ((mixed[1].toInt() shl 8) or (mixed[0].toInt() and 0xFF)).toShort().toInt()
        val right = ((mixed[3].toInt() shl 8) or (mixed[2].toInt() and 0xFF)).toShort().toInt()

        assertEquals(35, left)
        assertEquals(45, right)
    }
}
