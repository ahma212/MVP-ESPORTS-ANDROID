package com.example.services.streaming

import com.example.core.model.StandingTableControlsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Local runtime manager for Standing Table and Overlay controls (Part 26).
 * Manages scoreboard, top-3 mode, full standings, bottom ticker, and kill card visibility locally.
 */
object StandingTableControlsManager {
    var context: android.content.Context? = null

    private val _controlsState = MutableStateFlow(StandingTableControlsState())
    val controlsState: StateFlow<StandingTableControlsState> = _controlsState.asStateFlow()

    fun toggleScoreboard() {
        _controlsState.update { it.copy(scoreboardEnabled = !it.scoreboardEnabled) }
    }

    fun toggleTop3Mode() {
        _controlsState.update { 
            val newTop3 = !it.top3ModeEnabled
            it.copy(
                top3ModeEnabled = newTop3,
                fullStandingsEnabled = !newTop3
            )
        }
    }

    fun toggleFullStandings() {
        _controlsState.update { 
            val newFull = !it.fullStandingsEnabled
            it.copy(
                fullStandingsEnabled = newFull,
                top3ModeEnabled = !newFull
            )
        }
    }

    fun toggleBottomTicker() {
        _controlsState.update { it.copy(bottomTickerEnabled = !it.bottomTickerEnabled) }
    }

    fun setTickerSpeed(speed: Float) {
        _controlsState.update { it.copy(tickerSpeedMultiplier = speed) }
    }

    fun toggleKillCard() {
        _controlsState.update { it.copy(killCardEnabled = !it.killCardEnabled) }
    }

    fun toggleMilestoneCard() {
        _controlsState.update { it.copy(milestoneCardEnabled = !it.milestoneCardEnabled) }
    }

    fun toggleCustomGraphics() {
        _controlsState.update { it.copy(customGraphicsEnabled = !it.customGraphicsEnabled) }
    }

    fun toggleLogoOverlay() {
        _controlsState.update { it.copy(logoOverlayEnabled = !it.logoOverlayEnabled) }
    }

    fun toggleTextOverlay() {
        _controlsState.update { it.copy(textOverlayEnabled = !it.textOverlayEnabled) }
    }

    fun toggleMatchInfoOverlay() {
        _controlsState.update { it.copy(matchInfoOverlayEnabled = !it.matchInfoOverlayEnabled) }
    }

    fun toggleTeamPlayerOverlay() {
        _controlsState.update { it.copy(teamPlayerOverlayEnabled = !it.teamPlayerOverlayEnabled) }
    }

    fun setStandingSize(size: String) {
        _controlsState.update { it.copy(standingSize = size) }
    }

    fun setStandingPosition(position: String) {
        _controlsState.update { it.copy(standingPosition = position) }
    }

    fun setTickerSize(size: String) {
        _controlsState.update { it.copy(tickerSize = size) }
    }

    fun setTickerYOffset(offset: Float) {
        _controlsState.update { it.copy(tickerYOffset = offset) }
    }

    fun setTickerCustomText(text: String) {
        _controlsState.update { it.copy(tickerCustomText = text) }
    }

    fun setTickerLoopVideo(uri: String?, enabled: Boolean) {
        _controlsState.update { it.copy(tickerLoopVideoUri = uri, tickerLoopVideoEnabled = enabled) }
    }

    fun setCustomBannerText(text: String) {
        _controlsState.update { it.copy(customBannerText = text) }
    }

    fun setCustomBannerPosition(position: String) {
        _controlsState.update { it.copy(customBannerPosition = position) }
    }

    fun setCustomBannerScale(scale: Float) {
        _controlsState.update { it.copy(customBannerScale = scale) }
    }

    fun setCustomBannerColors(textColor: String, bgColor: String, borderColor: String) {
        _controlsState.update {
            it.copy(
                customBannerTextColor = textColor,
                customBannerBgColor = bgColor,
                customBannerBorderColor = borderColor
            )
        }
    }
}
