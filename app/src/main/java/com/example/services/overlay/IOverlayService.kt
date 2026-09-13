package com.example.services.overlay

import com.example.core.model.EsportsDetectedEvent
import com.example.core.model.LeaderboardEntry

/**
 * Data model for the custom esports broadcast stream overlay.
 * Independent of PRISM, usable on both Android mobile overlays and Windows OBS/Desktop overlays.
 */
data class OverlayState(
    val title: String = "MVP ESPORTS PK",
    val matchInfo: String = "MATCH 1 - ERANGEL",
    val isOverlayEnabled: Boolean = false,
    val showKillFeed: Boolean = true,
    val showLeaderboard: Boolean = true,
    val showMatchInfo: Boolean = true,
    val showPlayerStats: Boolean = true,
    val activeBannerText: String? = null,
    val aliveTeams: Int = 16,
    val alivePlayers: Int = 64,
    val recentKillFeed: List<EsportsDetectedEvent> = emptyList(),
    val topLeaderboard: List<LeaderboardEntry> = emptyList()
)

/**
 * Abstract interface for the esports broadcast overlay generator.
 */
interface IOverlayService {
    val overlayState: kotlinx.coroutines.flow.StateFlow<OverlayState>

    fun setOverlayEnabled(enabled: Boolean)
    fun setBannerText(text: String?)
    fun updateMatchInfo(info: String)
    fun renderOverlayLayer(): Any?
}
