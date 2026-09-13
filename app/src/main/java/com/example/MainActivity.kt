package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.LiveAnalyzerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.services.streaming.StandingTableControlsManager

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    StandingTableControlsManager.context = applicationContext
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        LiveAnalyzerScreen()
      }
    }
  }
}


