package com.example.core.model

enum class EsportsScene {
    IDLE,
    STARTING_COUNTDOWN,
    LIVE_MATCH,
    MATCH_ENDED,
    BREAK,
    NEXT_MATCH_COUNTDOWN,
    ENDING,
    STOPPED
}

enum class StreamControlStateEnum {
    STREAM_OFF,
    STREAM_STARTING,
    STREAM_LIVE,
    STREAM_PAUSED,
    STREAM_ERROR,
    STREAM_STOPPING,
    STREAM_STOPPED
}

data class StandingTableControlsState(
    val scoreboardEnabled: Boolean = true,
    val top3ModeEnabled: Boolean = false,
    val fullStandingsEnabled: Boolean = true,
    val bottomTickerEnabled: Boolean = true,
    val killCardEnabled: Boolean = true,
    val milestoneCardEnabled: Boolean = true,
    val customGraphicsEnabled: Boolean = true,
    val logoOverlayEnabled: Boolean = true,
    val textOverlayEnabled: Boolean = true,
    val matchInfoOverlayEnabled: Boolean = true,
    val teamPlayerOverlayEnabled: Boolean = true,
    val tickerSpeedMultiplier: Float = 1.0f,
    val standingSize: String = "Medium",
    val standingPosition: String = "Top Right",
    val tickerSize: String = "Medium",
    val tickerYOffset: Float = 0.94f,
    val tickerCustomText: String = "",
    val tickerLoopVideoUri: String? = null,
    val tickerLoopVideoEnabled: Boolean = false,
    val customBannerText: String = "",
    val customBannerPosition: String = "Top",
    val customBannerScale: Float = 1.0f,
    val customBannerTextColor: String = "#FFFFFF",
    val customBannerBorderColor: String = "#FF6600",
    val customBannerBgColor: String = "#F00F0F14"
)

data class SceneEngineState(
    val currentScene: EsportsScene = EsportsScene.IDLE,
    val countdownMinutes: Int = 15,
    val remainingCountdownSeconds: Int = 15 * 60,
    val breakMinutes: Int = 15,
    val remainingBreakSeconds: Int = 15 * 60,
    val endingMinutes: Int = 15,
    val remainingEndingSeconds: Int = 15 * 60,
    val isRunning: Boolean = false,
    val loopVideoUri: String? = null,
    val backgroundMusicEnabled: Boolean = true,
    val customMessage: String = "",
    val streamState: StreamControlStateEnum = StreamControlStateEnum.STREAM_OFF
)

data class TickerConfig(
    val text: String = "MVP ESPORTS • LIVE • PUBG MOBILE TOURNAMENT • OFFICIAL BROADCAST",
    val speed: Float = 1.0f, // multiplier
    val enabled: Boolean = true
)

data class CustomTextOverlayItem(
    val id: String = "text_1",
    val text: String = "",
    val isVisible: Boolean = false,
    val durationSeconds: Int = 10,
    val xPosition: Float = 0.5f, // 0.0 to 1.0
    val yPosition: Float = 0.1f, // top banner
    val fontSizeSp: Int = 18,
    val textColorHex: String = "#FF6600",
    val enabled: Boolean = true
)

data class VideoOverlayState(
    val mediaUri: String? = null,
    val isPlaying: Boolean = false,
    val widthDp: Int = 320,
    val heightDp: Int = 180,
    val xPosition: Float = 0.75f,
    val yPosition: Float = 0.75f,
    val autoHideOnEnd: Boolean = true
)

data class AudioMixerState(
    val gameVolume: Float = 0.8f, // 0.0 to 1.0 (0-100%)
    val micVolume: Float = 1.0f,
    val musicVolume: Float = 0.5f,
    val gameMuted: Boolean = false,
    val micMuted: Boolean = false,
    val musicMuted: Boolean = false
)

