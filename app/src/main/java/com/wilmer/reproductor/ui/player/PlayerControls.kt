package com.wilmer.reproductor.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import com.wilmer.reproductor.ui.theme.BrandGradient
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.wilmer.reproductor.ui.formatDuration

private val Scrim = Color.Black.copy(alpha = 0.55f)
private val Glass = Color.White.copy(alpha = 0.12f)
private val TopShade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))
private val BottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))

/** Controles superpuestos al video, al estilo de VLC. */
@Composable
fun PlayerControls(
    visible: Boolean,
    locked: Boolean,
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    repeatMode: Int,
    shuffle: Boolean,
    speed: Float,
    aspectMode: AspectMode,
    rotationMode: RotationMode,
    error: String?,
    osd: String?,
    onInteraction: () -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onLockToggle: () -> Unit,
    onAspect: () -> Unit,
    onRotation: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onSpeed: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onPip: () -> Unit,
    onShare: () -> Unit,
    onErrorNext: () -> Unit,
) {
    // Cada botón avisa de la interacción para que los controles no se oculten
    fun act(action: () -> Unit): () -> Unit = { onInteraction(); action() }

    Box(Modifier.fillMaxSize()) {
        osd?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 88.dp)
                    .background(Scrim, CircleShape)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        error?.let {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Scrim, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(it, color = Color.White)
                Row {
                    if (hasNext) TextButton(onClick = onErrorNext) { Text("Siguiente video") }
                    TextButton(onClick = onBack) { Text("Cerrar") }
                }
            }
        }

        AnimatedVisibility(visible = visible && locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                ControlIcon(
                    Icons.Default.Lock, "Desbloquear", act(onLockToggle),
                    Modifier.align(Alignment.CenterStart).padding(16.dp),
                    filled = true,
                )
            }
        }

        AnimatedVisibility(visible = visible && !locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Barra superior
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(TopShade)
                        .safeDrawingPadding()
                        .padding(start = 4.dp, end = 4.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIcon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onBack)
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    ControlIcon(Icons.Default.Audiotrack, "Pista de audio", act(onAudio))
                    ControlIcon(Icons.Default.Subtitles, "Subtítulos", act(onSubtitles))
                    TextButton(onClick = act(onSpeed), modifier = Modifier.background(Glass, CircleShape)) {
                        Icon(Icons.Default.Speed, contentDescription = "Velocidad", tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text(speedLabel(speed), color = Color.White)
                    }
                    ControlIcon(Icons.Default.Share, "Compartir", act(onShare))
                    ControlIcon(Icons.Default.PictureInPictureAlt, "Ventana flotante", onPip)
                }

                // Botones centrales
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ControlIcon(Icons.Default.SkipPrevious, "Anterior", act(onPrevious), size = 30, enabled = true)
                    ControlIcon(Icons.Default.Replay10, "Atrasar 10 segundos", act(onSeekBack), size = 30, filled = true)
                    IconButton(
                        onClick = act(onPlayPause),
                        modifier = Modifier.size(78.dp).background(BrandGradient, CircleShape),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausa" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                    ControlIcon(Icons.Default.Forward10, "Adelantar 10 segundos", act(onSeekForward), size = 30, filled = true)
                    ControlIcon(Icons.Default.SkipNext, "Siguiente", act(onNext), size = 30, enabled = hasNext)
                }

                // Barra inferior: progreso y opciones
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(BottomShade)
                        .safeDrawingPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { onInteraction(); onSeekPreview((it * durationMs).toLong()) },
                            onValueChangeFinished = onSeekFinished,
                            enabled = durationMs > 0,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = if (bufferedMs > positionMs) 0.4f else 0.25f),
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                        Text(formatDuration(durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlIcon(Icons.Default.LockOpen, "Bloquear pantalla", act(onLockToggle))
                        ControlIcon(Icons.Default.AspectRatio, "Ajuste: ${aspectMode.label}", act(onAspect))
                        ControlIcon(Icons.Default.ScreenRotation, "Rotación: ${rotationMode.label}", act(onRotation))
                        ControlIcon(
                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            "Repetir",
                            act(onRepeat),
                            highlighted = repeatMode != Player.REPEAT_MODE_OFF,
                        )
                        ControlIcon(Icons.Default.Shuffle, "Aleatorio", act(onShuffle), highlighted = shuffle)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 26,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    filled: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size((size + 22).dp)
            .then(if (filled) Modifier.background(Glass, CircleShape) else Modifier),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                !enabled -> Color.White.copy(alpha = 0.35f)
                highlighted -> MaterialTheme.colorScheme.primary
                else -> Color.White
            },
            modifier = Modifier.size(size.dp),
        )
    }
}

@Composable
fun SpeedDialog(current: Float, onDismiss: () -> Unit, onSelect: (Float) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Velocidad de reproducción") },
        text = {
            LazyColumn {
                items(PLAYBACK_SPEEDS) { s ->
                    OptionRow(label = speedLabel(s), selected = s == current, onClick = { onSelect(s) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun TrackDialog(
    title: String,
    options: List<TrackOption>,
    offSelected: Boolean,
    offLabel: String?,
    extraAction: Pair<String, () -> Unit>?,
    onDismiss: () -> Unit,
    onSelect: (TrackOption) -> Unit,
    onOff: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                if (offLabel != null) {
                    item { OptionRow(offLabel, offSelected, onOff) }
                }
                items(options) { o ->
                    OptionRow(o.label, o.selected && !offSelected, onClick = { onSelect(o) })
                }
                if (options.isEmpty() && offLabel == null) {
                    item { Text("Este video no tiene otras pistas.") }
                }
                extraAction?.let { (label, action) ->
                    item {
                        TextButton(onClick = action, modifier = Modifier.padding(top = 8.dp)) { Text(label) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
