package com.example.platform.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Lightweight translucent Activity that launches the Android Storage Access Framework (SAF) local video picker.
 * Can be invoked directly from services or overlay floating views.
 */
class LocalVideoPickerActivity : ComponentActivity() {

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            VideoUriValidator.processAndSelectVideoUri(applicationContext, uri)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            videoPickerLauncher.launch(arrayOf("video/*"))
        } catch (_: Exception) {
            // Fallback to ACTION_GET_CONTENT if ACTION_OPEN_DOCUMENT is unsupported
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_PICK_VIDEO)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_VIDEO && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                VideoUriValidator.processAndSelectVideoUri(applicationContext, uri)
            }
        }
        finish()
    }

    companion object {
        private const val REQUEST_CODE_PICK_VIDEO = 2001

        fun launch(context: Context) {
            val intent = Intent(context, LocalVideoPickerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
