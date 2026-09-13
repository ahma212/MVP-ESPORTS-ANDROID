package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Modern Clean Sans Typography for MVP Esports (mvpesports.online)
val Typography =
  Typography(
    displayLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Black,
      fontSize = 28.sp,
      lineHeight = 34.sp,
      letterSpacing = 0.5.sp,
      color = MvpTextTitle
    ),
    titleLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 20.sp,
      lineHeight = 26.sp,
      letterSpacing = 0.15.sp,
      color = MvpTextTitle
    ),
    titleMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 16.sp,
      lineHeight = 22.sp,
      letterSpacing = 0.15.sp,
      color = MvpTextTitle
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Normal,
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.25.sp,
      color = MvpTextSubtitle
    ),
    bodyMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Normal,
      fontSize = 13.sp,
      lineHeight = 18.sp,
      letterSpacing = 0.25.sp,
      color = MvpTextSubtitle
    ),
    labelLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      lineHeight = 18.sp,
      letterSpacing = 0.5.sp,
      color = MvpTextTitle
    ),
    labelMedium = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.SemiBold,
      fontSize = 11.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.5.sp,
      color = MvpTextSubtitle
    ),
    labelSmall = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Medium,
      fontSize = 10.sp,
      lineHeight = 14.sp,
      letterSpacing = 0.5.sp,
      color = MvpTextMuted
    )
  )

