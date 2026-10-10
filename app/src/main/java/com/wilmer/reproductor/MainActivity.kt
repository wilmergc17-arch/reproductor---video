package com.wilmer.reproductor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wilmer.reproductor.ui.ReproductorApp
import com.wilmer.reproductor.ui.theme.ReproductorTheme

class MainActivity : ComponentActivity() {

    // Video que se está reproduciendo (null = pantalla de inicio)
    private var videoUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            videoUri = intent.videoUriOrNull()
        }
        setContent {
            ReproductorTheme {
                ReproductorApp(
                    videoUri = videoUri,
                    onVideoSelected = { videoUri = it },
                    onClose = { videoUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.videoUriOrNull()?.let { videoUri = it }
    }

    private fun Intent.videoUriOrNull(): Uri? =
        if (action == Intent.ACTION_VIEW) data else null
}
