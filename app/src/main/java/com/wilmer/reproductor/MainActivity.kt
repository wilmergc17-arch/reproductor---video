package com.wilmer.reproductor

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wilmer.reproductor.ui.LibraryViewModel
import com.wilmer.reproductor.ui.PlaybackRequest
import com.wilmer.reproductor.ui.QueueItem
import com.wilmer.reproductor.ui.ReproductorApp
import com.wilmer.reproductor.ui.theme.ReproductorTheme

class MainActivity : ComponentActivity() {

    private val library: LibraryViewModel by viewModels()

    // Lo que se está reproduciendo (null = biblioteca)
    private var playback by mutableStateOf<PlaybackRequest?>(null)
    private var inPip by mutableStateOf(false)
    private var playing = false
    private var videoRatio = Rational(16, 9)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            playback = intent.playbackOrNull()
        }
        setContent {
            ReproductorTheme {
                ReproductorApp(
                    library = library,
                    playback = playback,
                    inPip = inPip,
                    onPlay = { playback = it },
                    onClose = { playback = null },
                    onPlayingChanged = { playing = it },
                    onVideoSize = { w, h -> videoRatio = safeRatio(w, h) },
                    onEnterPip = ::enterPip,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.playbackOrNull()?.let { playback = it }
    }

    // Al pulsar Inicio mientras se ve un video, sigue en una ventana flotante como VLC
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playback != null && playing) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    private fun enterPip() {
        if (!packageManager.hasSystemFeature("android.software.picture_in_picture")) return
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(videoRatio).build())
        }
    }

    // Android solo acepta proporciones entre 1:2.39 y 2.39:1 para la ventana flotante
    private fun safeRatio(w: Int, h: Int): Rational {
        val ratio = (w.toFloat() / h).coerceIn(1 / 2.39f, 2.39f)
        return Rational((ratio * 1000).toInt(), 1000)
    }

    private fun Intent.playbackOrNull(): PlaybackRequest? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        return PlaybackRequest(listOf(QueueItem(uri, displayName(uri))))
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
            }
        }
        return uri.lastPathSegment ?: uri.toString()
    }
}
