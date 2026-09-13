package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.TeamLiveState
import com.example.ui.theme.*

/**
 * VIP Esports Standing Table:
 * - Rendered as a sleek esports side-panel
 * - Columns: # | TEAM | ALIVE | KILLS | PTS
 * - Gold / Silver / Bronze accents for Top 1/2/3
 * - Clean grey state for eliminated teams
 * - No player names on standing
 * - Clean modern sans typography
 */
@Composable
fun EsportsStandingTable(
    teams: List<TeamLiveState>,
    modifier: Modifier = Modifier,
    isStreamOverlay: Boolean = false
) {
    // Sort teams by rank or match points
    val sortedTeams = remember(teams) {
        teams.sortedWith(
            compareBy<TeamLiveState> { it.rank }
                .thenByDescending { it.currentMatchPoints }
                .thenByDescending { it.currentMatchKills }
        )
    }

    val totalAlive = remember(teams) {
        teams.sumOf { it.currentAlivePlayers }
    }
    val activeTeamsCount = remember(teams) {
        teams.count { !it.isEliminated && it.currentAlivePlayers > 0 }
    }

    Column(
    modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF101A31),
                    Color(0xFF08101F),
                    Color(0xFF050A14)
                )
            )
        )
        .border(
            1.dp,
            MvpCyanPrimary.copy(alpha = 0.55f),
            RoundedCornerShape(16.dp)
        )
        .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
        // --- 1. VIP Header & Stats Banner ---
       Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF111D38),
                    Color(0xFF0A1327),
                    Color(0xFF160D16)
                )
            )
        )
        .border(
            1.dp,
            MvpCyanPrimary.copy(alpha = 0.30f),
            RoundedCornerShape(12.dp)
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF1744))
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "MVP ESPORTS",
                color = Color(0xFFFFD54A),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif
            )

            Text(
                text = "VIP LIVE STANDINGS",
                color = MvpCyanPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            color = Color(0x3322C55E),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                1.dp,
                Color(0xFF22C55E).copy(alpha = 0.65f)
            )
        ) {
            Text(
                text = "$totalAlive ALIVE",
                color = Color(0xFF4ADE80),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 4.dp
                )
            )
        }

        Surface(
            color = Color(0x3329B6F6),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                1.dp,
                MvpCyanPrimary.copy(alpha = 0.65f)
            )
        ) {
            Text(
                text = "$activeTeamsCount TEAMS",
                color = Color(0xFF67E8F9),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 4.dp
                )
            )
        }
    }
}

        // --- 2. Table Header Row (# TEAM ALIVE KILLS PTS) ---
        Row(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(
            Brush.horizontalGradient(
                listOf(
                    Color(0xFF102A45),
                    Color(0xFF0B1A31),
                    Color(0xFF24111B)
                )
            )
        )
        .border(
            1.dp,
            MvpCyanPrimary.copy(alpha = 0.28f),
            RoundedCornerShape(8.dp)
        )
        .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
       Text(
                text = "#",
                color = Color(0xFF7DD3FC),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "TEAM",
                color = MvpTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "ALIVE",
                color = MvpTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(48.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "KILLS",
                color = MvpTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center
            )

            Text(
                text = "PTS",
                color = MvpTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center
            )
        }

        // --- 3. Teams Standing List ---
        if (sortedTeams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MvpCardGlassVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Awaiting tournament match data...",
                    color = MvpTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                   .heightIn(
    min = 120.dp,
    max = if (isStreamOverlay) 420.dp else 540.dp
),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sortedTeams, key = { it.teamNumber }) { team ->
                    EsportsStandingRow(team = team)
                }
            }
        }
    }
}

/**
 * Single VIP Esports Standing Row:
 * - Columns: # | TEAM | ALIVE | KILLS | PTS
 * - Top 1 Gold accent
 * - Top 2 Silver accent
 * - Top 3 Bronze accent
 * - Eliminated grey state
 */
@Composable
private fun EsportsStandingRow(
    team: TeamLiveState,
    modifier: Modifier = Modifier
) {
    val isTeamEliminated = team.isEliminated || team.currentAlivePlayers <= 0
    val aliveCount = team.currentAlivePlayers

    // Podium colors
    val rankAccentColor = when (team.rank) {
        1 -> MvpGoldRank
        2 -> MvpSilverRank
        3 -> MvpBronzeRank
        else -> MvpTextSubtitle
    }

    val rankBorder = when (team.rank) {
        1 -> MvpGoldRank.copy(alpha = 0.6f)
        2 -> MvpSilverRank.copy(alpha = 0.6f)
        3 -> MvpBronzeRank.copy(alpha = 0.6f)
        else -> MvpBorderSubtle
    }

    val rowBgColor = when {
        isTeamEliminated -> MvpEliminatedBg
        team.rank == 1 -> Color(0x35FFD700)
        team.rank == 2 -> Color(0x2529B6F6)
        team.rank == 3 -> Color(0x2522D3EE)
        else -> MvpCardGlassVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBgColor)
            .border(
                BorderStroke(
                    width = if (team.rank <= 3) 1.dp else 0.5.dp,
                    color = if (team.rank <= 3) rankBorder else MvpBorderSubtle
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // # Rank Badge
        Box(
            modifier = Modifier
                .width(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (team.rank) {
                        1 -> MvpGoldRank.copy(alpha = 0.2f)
                        2 -> MvpSilverRank.copy(alpha = 0.2f)
                        3 -> MvpBronzeRank.copy(alpha = 0.2f)
                        else -> Color(0x1A00E5FF)
                    }
                )
                .border(
                    0.5.dp,
                    if (team.rank <= 3) rankAccentColor else MvpBorderSubtle,
                    RoundedCornerShape(6.dp)
                )
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
           Text(
                text = "#${team.rank}",
                color = if (isTeamEliminated) MvpEliminatedGrey else rankAccentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // TEAM (T01 Name)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "T%02d".format(team.teamNumber),
                    color = if (isTeamEliminated) MvpEliminatedGrey else MvpCyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )

                Text(
                    text = team.teamName,
                    color = if (isTeamEliminated) MvpEliminatedGrey else MvpTextTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ALIVE
        val aliveColor = when {
            isTeamEliminated -> MvpEliminatedGrey
            aliveCount >= 3 -> MvpSuccess
            aliveCount == 2 -> MvpWarning
            else -> MvpDanger
        }

        Box(
            modifier = Modifier
                .width(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(aliveColor.copy(alpha = if (isTeamEliminated) 0.08f else 0.15f))
                .border(0.5.dp, aliveColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isTeamEliminated) "DEAD" else "$aliveCount",
                color = aliveColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // KILLS
        Box(
            modifier = Modifier.width(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${team.currentMatchKills}",
                color = if (isTeamEliminated) MvpEliminatedGrey else MvpTextTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }

        // PTS
        Box(
            modifier = Modifier.width(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${team.currentMatchPoints}",
                color = if (isTeamEliminated) MvpEliminatedGrey else MvpCyanPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }
    }
}
