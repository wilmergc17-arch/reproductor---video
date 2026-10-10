package com.wilmer.reproductor.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wilmer.reproductor.R

// Video público de prueba (Big Buck Bunny), sirve para comprobar que el reproductor funciona
private const val SAMPLE_VIDEO_URL =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

@Composable
fun ReproductorApp(
    videoUri: Uri?,
    onVideoSelected: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    if (videoUri == null) {
        HomeScreen(onVideoSelected)
    } else {
        BackHandler(onBack = onClose)
        PlayerScreen(uri = videoUri)
    }
}

@Composable
private fun HomeScreen(onVideoSelected: (Uri) -> Unit) {
    // El selector de fotos/videos del sistema no necesita permisos de almacenamiento
    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onVideoSelected) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.welcome),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = {
                pickVideo.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }) {
                Text(stringResource(R.string.pick_video))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onVideoSelected(Uri.parse(SAMPLE_VIDEO_URL)) }) {
                Text(stringResource(R.string.sample_video))
            }
        }
    }
}
