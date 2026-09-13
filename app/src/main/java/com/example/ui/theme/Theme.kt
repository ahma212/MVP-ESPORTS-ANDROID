package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val HighDensityColorScheme = darkColorScheme(
  primary = MvpCyanPrimary,
  onPrimary = Color.Black,
  secondary = MvpBlueAccent,
  onSecondary = Color.White,
  tertiary = MvpBlueButton,
  background = MvpNavyBackground,
  onBackground = MvpTextTitle,
  surface = MvpCardGlass,
  onSurface = MvpTextTitle,
  surfaceVariant = MvpCardGlassVariant,
  onSurfaceVariant = MvpTextSubtitle,
  outline = MvpCyanBorder
)


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = HighDensityColorScheme,
    typography = Typography,
    content = content
  )
}
