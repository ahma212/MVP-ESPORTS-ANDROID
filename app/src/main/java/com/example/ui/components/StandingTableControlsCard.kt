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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.platform.android.LocalVideoPickerActivity
import com.example.platform.android.VideoUriValidator
import com.example.core.model.StandingTableControlsState
import com.example.services.streaming.StandingTableControlsManager

@Composable
fun StandingTableControlsCard(
    state: StandingTableControlsState,
    modifier: Modifier = Modifier
) {
    var bannerTextState by remember(state.customBannerText) { mutableStateOf(state.customBannerText) }
    var tickerTextState by remember(state.tickerCustomText) { mutableStateOf(state.tickerCustomText) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141416))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STANDING TABLE & OVERLAY CONTROLS",
                color = Color(0xFFFF6600),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            StatusBadge(
                label = "LOCAL",
                value = "ACTIVE"
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlToggleRow(
                title = "SCOREBOARD OVERLAY",
                enabled = state.scoreboardEnabled,
                onToggle = { StandingTableControlsManager.toggleScoreboard() }
            )
            ControlToggleRow(
                title = "TOP-3 MODE",
                enabled = state.top3ModeEnabled,
                onToggle = { StandingTableControlsManager.toggleTop3Mode() }
            )
            ControlToggleRow(
                title = "FULL STANDINGS (16 TEAMS)",
                enabled = state.fullStandingsEnabled,
                onToggle = { StandingTableControlsManager.toggleFullStandings() }
            )
            ControlToggleRow(
                title = "BOTTOM LIVE TICKER",
                enabled = state.bottomTickerEnabled,
                onToggle = { StandingTableControlsManager.toggleBottomTicker() }
            )

            // Dynamic Ticker Text Input
            if (state.bottomTickerEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "BOTTOM TICKER CUSTOM TEXT",
                        color = Color(0xFFFF9900),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = tickerTextState,
                        onValueChange = {
                            tickerTextState = it
                            StandingTableControlsManager.setTickerCustomText(it)
                        },
                        placeholder = {
                            Text(
                                text = "Default: MVP ESPORTS • LIVE PUBG MOBILE TOURNAMENT...",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6600),
                            unfocusedBorderColor = Color(0xFF3F3F46),
                            focusedContainerColor = Color(0xFF141418),
                            unfocusedContainerColor = Color(0xFF141418)
                        )
                    )
                }
            }

            ControlToggleRow(
                title = "ELIMINATION KILL CARD",
                enabled = state.killCardEnabled,
                onToggle = { StandingTableControlsManager.toggleKillCard() }
            )
            ControlToggleRow(
                title = "VIP MILESTONE CARD",
                enabled = state.milestoneCardEnabled,
                onToggle = { StandingTableControlsManager.toggleMilestoneCard() }
            )
            ControlToggleRow(
                title = "CUSTOM GRAPHICS MASTER",
                enabled = state.customGraphicsEnabled,
                onToggle = { StandingTableControlsManager.toggleCustomGraphics() }
            )
            ControlToggleRow(
                title = "LOGO OVERLAY",
                enabled = state.logoOverlayEnabled,
                onToggle = { StandingTableControlsManager.toggleLogoOverlay() }
            )
            ControlToggleRow(
                title = "CUSTOM TEXT OVERLAY",
                enabled = state.textOverlayEnabled,
                onToggle = { StandingTableControlsManager.toggleTextOverlay() }
            )

            // Dynamic Custom Banner Text & Position Editor
            if (state.customGraphicsEnabled && state.textOverlayEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "LIVE BROADCAST BANNER TEXT",
                        color = Color(0xFFFF9900),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value = bannerTextState,
                        onValueChange = {
                            bannerTextState = it
                            StandingTableControlsManager.setCustomBannerText(it)
                        },
                        placeholder = {
                            Text(
                                text = "Enter broadcast banner text (e.g. GRAND FINALS MATCH 3)",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6600),
                            unfocusedBorderColor = Color(0xFF3F3F46),
                            focusedContainerColor = Color(0xFF141418),
                            unfocusedContainerColor = Color(0xFF141418)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BANNER POSITION:",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Top", "Center", "Bottom").forEach { pos ->
                                val isSelected = state.customBannerPosition.equals(pos, ignoreCase = true)
                                Button(
                                    onClick = { StandingTableControlsManager.setCustomBannerPosition(pos) },
                                    modifier = Modifier.height(26.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) Color(0xFFFF6600) else Color(0xFF2E2E36)
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = pos.uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ControlToggleRow(
                title = "MATCH INFO OVERLAY",
                enabled = state.matchInfoOverlayEnabled,
                onToggle = { StandingTableControlsManager.toggleMatchInfoOverlay() }
            )
            ControlToggleRow(
                title = "TEAM/PLAYER OVERLAY",
                enabled = state.teamPlayerOverlayEnabled,
                onToggle = { StandingTableControlsManager.toggleTeamPlayerOverlay() }
            )

            // Match Format Selector (Solo / Duo / Squad)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MATCH FORMAT",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Solo", "Duo", "Squad").forEach { format ->
                        Button(
                            onClick = { com.example.services.streaming.LocalLiveRuntimeManager.setMatchFormat(format) },
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF27272A)
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = format.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFF9900)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlToggleRow(
    title: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E24), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Button(
            onClick = onToggle,
            modifier = Modifier.height(30.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) Color(0xFF16A34A) else Color(0xFFDC2626)
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (enabled) "ON" else "OFF",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
