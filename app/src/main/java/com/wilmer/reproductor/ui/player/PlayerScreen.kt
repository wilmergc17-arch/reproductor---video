package com.wilmer.reproductor.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.wilmer.reproductor.data.ResumeStore
import com.wilmer.reproductor.ui.PlaybackRequest
import com.wilmer.reproductor.ui.QueueItem
import com.wilmer.reproductor.ui.formatDuration
import com.wilmer.reproductor.ui.shareVideos
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formas de ajustar la imagen a la pantalla. */
enum class AspectMode(val label: String, val resizeMode: Int) {
    AJUSTAR("Ajustar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    RECORTAR("Llenar pantalla", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ESTIRAR("Estirar", AspectRatioFrameLayout.RESIZE_MODE_FILL),
}

/** Orientación de la pantalla durante la reproducción. */
enum class RotationMode(val label: String) {
    VERTICAL("Vertical"),
    HORIZONTAL("Horizontal"),
    SEGUIR_VIDEO("Según el video"),
    LIBRE("Girar libre"),
}

private enum class DragMode { NONE, SEEK, VOLUME, BRIGHTNESS }

/** Ancho completo de la pantalla al deslizar = 100 segundos de avance o retroceso. */
private const val SEEK_MS_PER_SCREEN = 100_000L
private const val CONTROLS_TIMEOUT_MS = 3_500L

val PLAYBACK_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)

@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    inPip: Boolean,
    onPlayingChanged: (Boolean) -> Unit,
    onVideoSize: (width: Int, height: Int) -> Unit,
    onEnterPip: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val resume = remember { ResumeStore(context) }
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val currentOnPlayingChanged by rememberUpdatedState(onPlayingChanged)
    val currentOnVideoSize by rememberUpdatedState(onVideoSize)

    var osd by remember { mutableStateOf<String?>(null) }
    var osdTick by remember { mutableIntStateOf(0) }
    fun showOsd(text: String) {
        osd = text
        osdTick++
    }

    val player = remember(request) {
        val start = if (request.shuffle) request.items.indices.random() else request.startIndex
        val startUri = request.items[start].uri.toString()
        val startPosition = resume.get(startUri)
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setMediaItems(request.items.map { it.toMediaItem() }, start, startPosition)
                shuffleModeEnabled = request.shuffle
                prepare()
                playWhenReady = true
                if (startPosition > 0) showOsd("Continuando desde ${formatDuration(startPosition)}")
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var title by remember { mutableStateOf(request.items.getOrNull(request.startIndex)?.title.orEmpty()) }
    var hasPrevious by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var error by remember { mutableStateOf<String?>(null) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var shuffle by remember { mutableStateOf(request.shuffle) }
    var speed by remember { mutableFloatStateOf(1f) }
    var videoAspect by remember { mutableFloatStateOf(0f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableStateOf(AspectMode.AJUSTAR) }
    // Por defecto en vertical; el botón de rotación cambia a horizontal, según el video o libre
    var rotationMode by remember { mutableStateOf(RotationMode.VERTICAL) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }

    fun poke() {
        controlsVisible = true
        interactionTick++
    }

    // Para guardar la posición del video anterior cuando cambia la cola
    var lastId by remember { mutableStateOf<String?>(null) }
    var lastPosition by remember { mutableLongStateOf(0L) }
    var lastDuration by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                currentOnPlayingChanged(playing)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
                error = null
                val id = mediaItem?.mediaId ?: return
                val previous = lastId
                if (previous == id) return
                if (previous != null) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) resume.clear(previous)
                    else resume.save(previous, lastPosition, lastDuration)
                }
                lastId = id
                if (previous != null) {
                    val saved = resume.get(id)
                    if (saved > 0) {
                        player.seekTo(saved)
                        showOsd("Continuando desde ${formatDuration(saved)}")
                    }
                }
            }

            override fun onEvents(p: Player, events: Player.Events) {
                hasPrevious = p.hasPreviousMediaItem()
                hasNext = p.hasNextMediaItem()
                repeatMode = p.repeatMode
                shuffle = p.shuffleModeEnabled
                speed = p.playbackParameters.speed
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val w = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
                    videoAspect = w.toFloat() / videoSize.height
                    currentOnVideoSize(w, videoSize.height)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    player.currentMediaItem?.mediaId?.let(resume::clear)
                    controlsVisible = true
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = "No se pudo reproducir este video (${e.errorCodeName})."
                controlsVisible = true
            }
        }
        player.addListener(listener)
        lastId = player.currentMediaItem?.mediaId
        onDispose {
            player.removeListener(listener)
            player.currentMediaItem?.mediaId?.let { resume.save(it, player.currentPosition, player.duration) }
            player.release()
            currentOnPlayingChanged(false)
        }
    }

    // Pausa al salir de la app (en ventana flotante sigue reproduciendo)
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.currentMediaItem?.mediaId?.let { resume.save(it, player.currentPosition, player.duration) }
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Progreso de la barra
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0)
            bufferedMs = player.bufferedPosition
            if (player.currentMediaItem?.mediaId == lastId) {
                lastPosition = positionMs
                lastDuration = durationMs
            }
            delay(250)
        }
    }

    // Oculta los controles tras unos segundos sin tocar la pantalla
    LaunchedEffect(controlsVisible, interactionTick, isPlaying, dialog, seekPreviewMs) {
        if (controlsVisible && isPlaying && dialog == null && seekPreviewMs == null) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(osdTick) {
        if (osd != null) {
            delay(1_200)
            osd = null
        }
    }

    // Pantalla completa: oculta las barras del sistema mientras se ve el video
    val view = LocalView.current
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    LaunchedEffect(rotationMode, videoAspect) {
        activity?.requestedOrientation = when (rotationMode) {
            RotationMode.SEGUIR_VIDEO -> when {
                videoAspect == 0f -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                videoAspect >= 1f -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            RotationMode.LIBRE -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            RotationMode.HORIZONTAL -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            RotationMode.VERTICAL -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    BackHandler {
        when {
            dialog != null -> dialog = null
            locked -> { controlsVisible = true; showOsd("Pantalla bloqueada: toca el candado para desbloquear") }
            else -> onClose()
        }
    }

    val pickSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            player.addExternalSubtitle(uri, context)
            showOsd("Subtítulos cargados")
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = player
                }
            },
            update = {
                it.player = player
                it.resizeMode = aspectMode.resizeMode
                it.keepScreenOn = isPlaying
            },
        )

        if (!inPip) {
            // Capa de gestos: toques, doble toque y deslizamientos
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(locked) {
                        detectTapGestures(
                            onTap = {
                                if (controlsVisible) controlsVisible = false else poke()
                            },
                            onDoubleTap = { offset ->
                                if (locked) return@detectTapGestures
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> { player.seekBack(); showOsd("−10 s") }
                                    offset.x > third * 2 -> { player.seekForward(); showOsd("+10 s") }
                                    else -> player.togglePlayPause()
                                }
                            },
                        )
                    }
                    .pointerInput(locked) {
                        if (locked) return@pointerInput
                        var mode = DragMode.NONE
                        var total = Offset.Zero
                        var startPos = 0L
                        var startVolume = 0
                        var startBrightness = 0.5f
                        var startX = 0f
                        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        detectDragGestures(
                            onDragStart = { start ->
                                mode = DragMode.NONE
                                total = Offset.Zero
                                startX = start.x
                                startPos = player.currentPosition
                                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                startBrightness = activity?.currentBrightness() ?: 0.5f
                            },
                            onDragEnd = {
                                if (mode == DragMode.SEEK) seekPreviewMs?.let { player.seekTo(it) }
                                seekPreviewMs = null
                                mode = DragMode.NONE
                            },
                            onDragCancel = {
                                seekPreviewMs = null
                                mode = DragMode.NONE
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                total += amount
                                if (mode == DragMode.NONE) {
                                    mode = when {
                                        abs(total.x) > abs(total.y) -> DragMode.SEEK
                                        startX < size.width / 2f -> DragMode.BRIGHTNESS
                                        else -> DragMode.VOLUME
                                    }
                                }
                                when (mode) {
                                    DragMode.SEEK -> {
                                        val delta = (total.x / size.width * SEEK_MS_PER_SCREEN).toLong()
                                        val target = (startPos + delta).coerceIn(0L, durationMs.coerceAtLeast(0L))
                                        seekPreviewMs = target
                                        val sign = if (delta >= 0) "+" else "−"
                                        showOsd("$sign${formatDuration(abs(delta))}  (${formatDuration(target)})")
                                    }
                                    DragMode.VOLUME -> {
                                        val delta = -total.y / size.height * maxVolume * 1.5f
                                        val volume = (startVolume + delta).roundToInt().coerceIn(0, maxVolume)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                        showOsd("Volumen ${volume * 100 / maxVolume.coerceAtLeast(1)} %")
                                    }
                                    DragMode.BRIGHTNESS -> {
                                        val value = (startBrightness - total.y / size.height * 1.5f).coerceIn(0.01f, 1f)
                                        activity?.window?.let { w ->
                                            w.attributes = w.attributes.apply { screenBrightness = value }
                                        }
                                        showOsd("Brillo ${(value * 100).roundToInt()} %")
                                    }
                                    DragMode.NONE -> Unit
                                }
                            },
                        )
                    }
            )

            PlayerControls(
                visible = controlsVisible,
                locked = locked,
                title = title,
                isPlaying = isPlaying,
                positionMs = seekPreviewMs ?: positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                repeatMode = repeatMode,
                shuffle = shuffle,
                speed = speed,
                aspectMode = aspectMode,
                rotationMode = rotationMode,
                error = error,
                osd = osd,
                onInteraction = ::poke,
                onBack = onClose,
                onPlayPause = { player.togglePlayPause() },
                onPrevious = { player.seekToPrevious() },
                onNext = { player.seekToNext() },
                onSeekBack = { player.seekBack() },
                onSeekForward = { player.seekForward() },
                onSeekPreview = { seekPreviewMs = it },
                onSeekFinished = {
                    seekPreviewMs?.let { player.seekTo(it) }
                    seekPreviewMs = null
                },
                onLockToggle = {
                    locked = !locked
                    showOsd(if (locked) "Pantalla bloqueada" else "Pantalla desbloqueada")
                },
                onAspect = {
                    aspectMode = AspectMode.entries[(aspectMode.ordinal + 1) % AspectMode.entries.size]
                    showOsd(aspectMode.label)
                },
                onRotation = {
                    rotationMode = RotationMode.entries[(rotationMode.ordinal + 1) % RotationMode.entries.size]
                    showOsd("Rotación: ${rotationMode.label}")
                },
                onRepeat = {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    showOsd(
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_ALL -> "Repetir todo"
                            Player.REPEAT_MODE_ONE -> "Repetir este video"
                            else -> "Sin repetición"
                        }
                    )
                },
                onShuffle = {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    showOsd(if (player.shuffleModeEnabled) "Aleatorio activado" else "Aleatorio desactivado")
                },
                onSpeed = { dialog = PlayerDialog.SPEED },
                onAudio = { dialog = PlayerDialog.AUDIO },
                onSubtitles = { dialog = PlayerDialog.SUBTITLES },
                onPip = onEnterPip,
                onShare = {
                    player.currentMediaItem?.localConfiguration?.uri?.let { shareVideos(context, listOf(it)) }
                },
                onErrorNext = { player.seekToNext(); player.prepare() },
            )
        }
    }

    when (dialog) {
        PlayerDialog.SPEED -> SpeedDialog(
            current = speed,
            onDismiss = { dialog = null },
            onSelect = {
                player.setPlaybackSpeed(it)
                dialog = null
                showOsd("Velocidad ${speedLabel(it)}")
            },
        )
        PlayerDialog.AUDIO -> TrackDialog(
            title = "Pista de audio",
            options = trackOptions(tracks, C.TRACK_TYPE_AUDIO),
            offSelected = false,
            offLabel = null,
            extraAction = null,
            onDismiss = { dialog = null },
            onSelect = { player.selectTrack(it); dialog = null },
            onOff = {},
        )
        PlayerDialog.SUBTITLES -> TrackDialog(
            title = "Subtítulos",
            options = trackOptions(tracks, C.TRACK_TYPE_TEXT),
            offSelected = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
                trackOptions(tracks, C.TRACK_TYPE_TEXT).none { it.selected },
            offLabel = "Desactivados",
            extraAction = "Cargar archivo de subtítulos…" to {
                dialog = null
                pickSubtitle.launch(arrayOf("*/*"))
            },
            onDismiss = { dialog = null },
            onSelect = { player.selectTrack(it); dialog = null },
            onOff = {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                dialog = null
            },
        )
        null -> Unit
    }
}

enum class PlayerDialog { SPEED, AUDIO, SUBTITLES }

fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

private fun QueueItem.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uri.toString())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

private fun Player.togglePlayPause() {
    if (playbackState == Player.STATE_ENDED) {
        seekTo(0)
        play()
    } else if (isPlaying) {
        pause()
    } else {
        if (playbackState == Player.STATE_IDLE) prepare()
        play()
    }
}

/** Una pista de audio o subtítulos que se puede elegir. */
data class TrackOption(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> {
    var n = 0
    return tracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { i ->
            n++
            val format = group.getTrackFormat(i)
            val language = format.language
                ?.takeUnless { it == C.LANGUAGE_UNDETERMINED }
                ?.let { java.util.Locale.forLanguageTag(it).displayLanguage.replaceFirstChar { c -> c.uppercase() } }
            val parts = listOfNotNull(
                format.label,
                language?.takeIf { format.label == null || !format.label!!.contains(it, ignoreCase = true) },
                if (type == C.TRACK_TYPE_AUDIO && format.channelCount > 0) {
                    when (format.channelCount) {
                        1 -> "mono"
                        2 -> "estéreo"
                        6 -> "5.1"
                        8 -> "7.1"
                        else -> "${format.channelCount} canales"
                    }
                } else null,
            )
            TrackOption(parts.joinToString(" · ").ifEmpty { "Pista $n" }, group, i, group.isTrackSelected(i))
        }
    }
}

private fun Player.selectTrack(option: TrackOption) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(option.group.type, false)
        .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        .build()
}

/** Agrega un archivo de subtítulos externo (srt, vtt, ass, ttml) al video actual. */
private fun Player.addExternalSubtitle(subtitleUri: Uri, context: Context) {
    val item = currentMediaItem ?: return
    val name = context.displayName(subtitleUri) ?: subtitleUri.lastPathSegment.orEmpty()
    val mime = when (name.substringAfterLast('.', "").lowercase()) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
    val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
        .setMimeType(mime)
        .setLabel(name.substringBeforeLast('.'))
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
    val index = currentMediaItemIndex
    val position = currentPosition
    val wasPlaying = playWhenReady
    replaceMediaItem(index, item.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build())
    seekTo(index, position)
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .build()
    playWhenReady = wasPlaying
}

private fun Context.displayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

private fun Activity.currentBrightness(): Float {
    val current = window.attributes.screenBrightness
    if (current >= 0f) return current
    return runCatching {
        Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
