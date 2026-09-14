package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.LiveAnalyzerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.services.streaming.StandingTableControlsManager
import android.view.WindowManager

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    StandingTableControlsManager.context = applicationContext
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        LiveAnalyzerScreen()
      }
    }
  }
}


