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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.ui.SubtitleView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import com.wilmer.reproductor.PlaybackService
import com.wilmer.reproductor.data.AppSettings
import com.wilmer.reproductor.data.QuietMode
import com.wilmer.reproductor.data.TapMode
import com.wilmer.reproductor.data.ResumeStore
import com.wilmer.reproductor.ui.PlaybackRequest
import com.wilmer.reproductor.ui.QueueItem
import com.wilmer.reproductor.ui.formatDuration
import com.wilmer.reproductor.ui.shareVideos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/** La app y el reproductor abren en vertical; al cerrar el video se vuelve a esto. */
private const val APP_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

/** IGNORED: el dedo empezó en un borde (gestos de Android) o ese gesto está apagado en Ajustes. */
private enum class DragMode { NONE, IGNORED, SEEK, VOLUME, BRIGHTNESS, REEL }

/** En Reels: parte de la altura de la pantalla que hay que deslizar para cambiar de video. */
private const val REEL_SWIPE_FRACTION = 0.18f

/** Opciones del temporizador para apagar: minutos (0 = al terminar el video actual). */
val SLEEP_OPTIONS = listOf(15, 30, 45, 60, 0)

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
    val quiet = remember { QuietMode(context) }
    val appSettings = remember { AppSettings.get(context) }
    val settings by appSettings.state.collectAsState()
    val currentSettings by rememberUpdatedState(settings)
    val currentInPip by rememberUpdatedState(inPip)
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val currentOnPlayingChanged by rememberUpdatedState(onPlayingChanged)
    val currentOnVideoSize by rememberUpdatedState(onVideoSize)

    var osd by remember { mutableStateOf<String?>(null) }
    var osdTick by remember { mutableIntStateOf(0) }
    fun showOsd(text: String) {
        osd = text
        osdTick++
    }

    val reels = request.reels
    // Recordar posición: apagado en Ajustes o en Reels (los reels empiezan siempre desde el inicio)
    fun canResume() = !reels && currentSettings.rememberPosition
    val seekStepMs = settings.seekStepSec * 1_000L

    val player = remember(request) {
        val initial = appSettings.current
        val start = if (request.shuffle) request.items.indices.random() else request.startIndex
        val startUri = request.items[start].uri.toString()
        val startPosition = if (!reels && initial.rememberPosition) resume.get(startUri) else 0L
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(initial.seekStepSec * 1_000L)
            .setSeekForwardIncrementMs(initial.seekStepSec * 1_000L)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            // Sigue sonando con la pantalla apagada si está activado el audio en segundo plano
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                setMediaItems(request.items.map { it.toMediaItem() }, start, startPosition)
                shuffleModeEnabled = request.shuffle
                if (reels && initial.loopReels) repeatMode = Player.REPEAT_MODE_ONE
                pauseAtEndOfMediaItems = !initial.autoNext
                if (initial.defaultSpeed != 1f) setPlaybackSpeed(initial.defaultSpeed)
                prepare()
                playWhenReady = true
                when {
                    startPosition > 0 -> showOsd("Continuando desde ${formatDuration(startPosition)}")
                    reels -> showOsd("Desliza arriba o abajo para cambiar de reel")
                }
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
    var aspectMode by remember {
        mutableStateOf(AspectMode.entries.firstOrNull { it.name == settings.defaultAspect } ?: AspectMode.AJUSTAR)
    }
    // Animación de Reels: la imagen sigue al dedo y sale por arriba o por abajo
    val reelOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Por defecto en vertical; el botón de rotación cambia a horizontal, según el video o libre
    var rotationMode by remember { mutableStateOf(RotationMode.VERTICAL) }
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }
    val tapMode = settings.tapMode
    // En pausa aparecen los botones de Android (atrás, inicio, multitarea) y se apagan los gestos
    var paused by remember { mutableStateOf(false) }
    // Mantener presionado: 2x mientras se sostiene el dedo
    var boostFrom by remember { mutableStateOf<Float?>(null) }
    // Temporizador para apagar: hora de parada, o "al terminar este video"
    var sleepAt by remember { mutableStateOf<Long?>(null) }
    var sleepAtEnd by remember { mutableStateOf(false) }
    // La ventana flotante se cerró (no solo se ocultó la app): entonces sí se pausa
    var pipSession by remember { mutableStateOf(false) }
    // No molestar: se recuerda entre videos; se activa solo mientras el reproductor está a la vista
    var quietOn by remember { mutableStateOf(quiet.wanted && quiet.hasAccess) }
    var awaitingQuietAccess by remember { mutableStateOf(false) }
    var appVisible by remember { mutableStateOf(true) }

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
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || !canResume()) resume.clear(previous)
                    else resume.save(previous, lastPosition, lastDuration)
                }
                lastId = id
                if (previous != null && canResume()) {
                    val saved = resume.get(id)
                    if (saved > 0) {
                        player.seekTo(saved)
                        showOsd("Continuando desde ${formatDuration(saved)}")
                    }
                }
            }

            override fun onEvents(p: Player, events: Player.Events) {
                paused = !p.playWhenReady || p.playbackState == Player.STATE_ENDED
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

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAtEnd) {
                    sleepAtEnd = false
                    player.pauseAtEndOfMediaItems = !currentSettings.autoNext
                    showOsd("Temporizador: video pausado")
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
            if (canResume()) player.currentMediaItem?.mediaId?.let { resume.save(it, player.currentPosition, player.duration) }
            player.release()
            currentOnPlayingChanged(false)
        }
    }

    // Pausa al salir de la app (en ventana flotante sigue reproduciendo)
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (canResume()) player.currentMediaItem?.mediaId?.let { resume.save(it, player.currentPosition, player.duration) }
                    // Con audio en segundo plano sigue sonando (pantalla apagada u otra app),
                    // salvo que se haya cerrado la ventana flotante
                    if (!currentSettings.backgroundAudio || pipSession || currentInPip) player.pause()
                    pipSession = false
                    appVisible = false
                }
                Lifecycle.Event.ON_START -> appVisible = true
                Lifecycle.Event.ON_RESUME -> {
                    pipSession = false
                    // De vuelta de Ajustes: si ya dio el permiso, se activa No molestar
                    if (awaitingQuietAccess) {
                        awaitingQuietAccess = false
                        if (quiet.hasAccess) {
                            quietOn = true
                            quiet.wanted = true
                            showOsd("No molestar activado")
                        } else {
                            showOsd("Sin permiso no se puede activar No molestar")
                        }
                    }
                }
                else -> Unit
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

    // No molestar solo mientras se ve el video; al salir de la app o cerrar el video se restaura
    DisposableEffect(quietOn, appVisible) {
        if (quietOn && appVisible) quiet.start()
        onDispose { quiet.stop() }
    }

    LaunchedEffect(inPip) { if (inPip) pipSession = true }

    // Reels: los videos verticales llenan la pantalla y los horizontales se ven completos
    LaunchedEffect(videoAspect) {
        if (reels && videoAspect > 0f) aspectMode = if (videoAspect < 1f) AspectMode.RECORTAR else AspectMode.AJUSTAR
    }

    // Los botones de volumen del teléfono siempre controlan el sonido del video
    DisposableEffect(activity) {
        val before = activity?.volumeControlStream
        activity?.volumeControlStream = AudioManager.STREAM_MUSIC
        onDispose { before?.let { activity?.volumeControlStream = it } }
    }

    // Audio en segundo plano: un aviso fijo mantiene viva la app mientras suena
    DisposableEffect(settings.backgroundAudio, isPlaying, title) {
        if (settings.backgroundAudio && isPlaying) PlaybackService.start(context, title)
        else PlaybackService.stop(context)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { PlaybackService.stop(context) } }

    // Temporizador para apagar
    LaunchedEffect(sleepAt) {
        val at = sleepAt ?: return@LaunchedEffect
        delay((at - System.currentTimeMillis()).coerceAtLeast(0))
        player.pause()
        sleepAt = null
        controlsVisible = true
        showOsd("Temporizador: video pausado")
    }

    LaunchedEffect(osdTick) {
        if (osd != null) {
            delay(1_200)
            osd = null
        }
    }

    // Pantalla completa mientras se reproduce; en pausa se muestran las barras de Android
    val view = LocalView.current
    val barsController = remember(activity) {
        activity?.window?.let { WindowCompat.getInsetsController(it, view) }?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    LaunchedEffect(paused, inPip) {
        if (paused || inPip) barsController?.show(WindowInsetsCompat.Type.systemBars())
        else barsController?.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = barsController
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = APP_ORIENTATION
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // Se aplica apenas se compone (no después del primer cuadro) para que nunca abra girado
    DisposableEffect(rotationMode, videoAspect) {
        activity?.requestedOrientation = when (rotationMode) {
            RotationMode.SEGUIR_VIDEO -> when {
                videoAspect == 0f -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                videoAspect >= 1f -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            RotationMode.LIBRE -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            RotationMode.HORIZONTAL -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            RotationMode.VERTICAL -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose { }
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
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = reelOffset.value },
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
                it.subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * settings.subtitleScale)
                it.keepScreenOn = isPlaying
            },
        )

        if (!inPip) {
            // Capa de gestos: toques, doble toque y deslizamientos
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(locked, tapMode, settings.holdForSpeed, seekStepMs) {
                        detectTapGestures(
                            onPress = {
                                tryAwaitRelease()
                                boostFrom?.let { before ->
                                    player.setPlaybackSpeed(before)
                                    boostFrom = null
                                    osd = null
                                }
                            },
                            onLongPress = {
                                if (!locked && settings.holdForSpeed && player.isPlaying && boostFrom == null) {
                                    boostFrom = player.playbackParameters.speed
                                    player.setPlaybackSpeed(2f)
                                    osd = "2x ▶▶  suelta para volver"
                                }
                            },
                            onTap = {
                                when {
                                    // Reels: un toque pausa o reanuda, como en TikTok
                                    reels && !locked -> {
                                        player.togglePlayPause()
                                        if (player.playWhenReady) controlsVisible = false else poke()
                                    }
                                    controlsVisible -> controlsVisible = false
                                    else -> poke()
                                }
                            },
                            onDoubleTap = { offset ->
                                if (locked) return@detectTapGestures
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> when {
                                        tapMode == TapMode.SALTAR -> { player.seekBack(); showOsd("−${seekStepMs / 1000} s") }
                                        player.hasPreviousMediaItem() -> {
                                            player.seekToPreviousMediaItem()
                                            showOsd("⏮ Video anterior")
                                        }
                                        else -> { player.seekTo(0); showOsd("Desde el inicio") }
                                    }
                                    offset.x > third * 2 -> when {
                                        tapMode == TapMode.SALTAR -> { player.seekForward(); showOsd("+${seekStepMs / 1000} s") }
                                        player.hasNextMediaItem() -> {
                                            player.seekToNextMediaItem()
                                            showOsd("Video siguiente ⏭")
                                        }
                                        else -> showOsd("No hay más videos en la lista")
                                    }
                                    else -> player.togglePlayPause()
                                }
                            },
                        )
                    }
                    .pointerInput(locked, paused && !reels, settings) {
                        // En Reels se puede deslizar aunque el video esté en pausa
                        if (locked || (paused && !reels)) return@pointerInput
                        // Zonas de los bordes reservadas a los gestos de Android (inicio, atrás, multitarea)
                        val sideEdge = 36.dp.toPx()
                        val topEdge = 56.dp.toPx()
                        val bottomEdge = 72.dp.toPx()
                        var mode = DragMode.NONE
                        var total = Offset.Zero
                        var startPos = 0L
                        var startVolume = 0
                        var startBrightness = 0.5f
                        var startX = 0f
                        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        detectDragGestures(
                            onDragStart = { start ->
                                val atEdge = start.x < sideEdge || start.x > size.width - sideEdge ||
                                    start.y < topEdge || start.y > size.height - bottomEdge
                                mode = if (atEdge) DragMode.IGNORED else DragMode.NONE
                                total = Offset.Zero
                                startX = start.x
                                startPos = player.currentPosition
                                startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                startBrightness = activity?.currentBrightness() ?: 0.5f
                            },
                            onDragEnd = {
                                if (mode == DragMode.SEEK) seekPreviewMs?.let { player.seekTo(it) }
                                if (mode == DragMode.REEL) {
                                    val height = size.height.toFloat()
                                    val dy = total.y
                                    scope.launch { finishReelSwipe(player, reelOffset, dy, height, ::showOsd) }
                                }
                                seekPreviewMs = null
                                mode = DragMode.NONE
                            },
                            onDragCancel = {
                                if (mode == DragMode.REEL) scope.launch { reelOffset.animateTo(0f, spring()) }
                                seekPreviewMs = null
                                mode = DragMode.NONE
                            },
                            onDrag = { change, amount ->
                                if (mode == DragMode.IGNORED) return@detectDragGestures
                                change.consume()
                                total += amount
                                if (mode == DragMode.NONE) {
                                    val wanted = when {
                                        abs(total.x) > abs(total.y) -> DragMode.SEEK
                                        // Reels: arriba o abajo cambia de video; volumen y brillo con el teléfono
                                        reels -> DragMode.REEL
                                        startX < size.width / 2f -> DragMode.BRIGHTNESS
                                        else -> DragMode.VOLUME
                                    }
                                    val enabled = when (wanted) {
                                        DragMode.SEEK -> settings.gestureSeek
                                        DragMode.REEL -> true
                                        DragMode.BRIGHTNESS -> settings.gestureBrightness
                                        else -> settings.gestureVolume
                                    }
                                    mode = if (enabled) wanted else DragMode.IGNORED
                                    if (!enabled) return@detectDragGestures
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
                                    DragMode.REEL -> {
                                        val y = total.y
                                        // Sin video siguiente o anterior se resiste (se mueve menos)
                                        val canGo = if (y < 0) player.hasNextMediaItem() else player.hasPreviousMediaItem()
                                        scope.launch { reelOffset.snapTo(if (canGo) y else y / 4f) }
                                    }
                                    DragMode.NONE, DragMode.IGNORED -> Unit
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
                quietOn = quietOn,
                onQuiet = {
                    when {
                        quietOn -> {
                            quietOn = false
                            quiet.wanted = false
                            showOsd("No molestar desactivado")
                        }
                        quiet.hasAccess -> {
                            quietOn = true
                            quiet.wanted = true
                            showOsd("No molestar activado")
                        }
                        else -> dialog = PlayerDialog.QUIET_ACCESS
                    }
                },
                tapMode = tapMode,
                onTapMode = {
                    val next = TapMode.entries[(tapMode.ordinal + 1) % TapMode.entries.size]
                    appSettings.update { it.copy(tapMode = next) }
                    showOsd("Doble toque a los lados: ${next.label.lowercase()}")
                },
                sleepLabel = when {
                    sleepAtEnd -> "al terminar el video"
                    sleepAt != null -> "quedan ${((sleepAt!! - System.currentTimeMillis()) / 60_000 + 1)} min"
                    else -> "apagado"
                },
                onSleep = { dialog = PlayerDialog.SLEEP },
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
        PlayerDialog.SLEEP -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Temporizador para apagar") },
            text = {
                Column {
                    SLEEP_OPTIONS.forEach { minutes ->
                        TextButton(onClick = {
                            dialog = null
                            if (minutes == 0) {
                                sleepAt = null
                                sleepAtEnd = true
                                player.pauseAtEndOfMediaItems = true
                                showOsd("Se pausará al terminar este video")
                            } else {
                                sleepAtEnd = false
                                player.pauseAtEndOfMediaItems = !settings.autoNext
                                sleepAt = System.currentTimeMillis() + minutes * 60_000L
                                showOsd("Se pausará en $minutes min")
                            }
                        }) { Text(if (minutes == 0) "Al terminar este video" else "$minutes minutos") }
                    }
                    if (sleepAt != null || sleepAtEnd) {
                        TextButton(onClick = {
                            dialog = null
                            sleepAt = null
                            sleepAtEnd = false
                            player.pauseAtEndOfMediaItems = !settings.autoNext
                            showOsd("Temporizador apagado")
                        }) { Text("Apagar temporizador") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cerrar") } },
        )
        PlayerDialog.QUIET_ACCESS -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Permitir No molestar") },
            text = {
                Text(
                    "Para silenciar las notificaciones mientras ves un video, Android pide que des a " +
                        "Repropower el \"Acceso a No molestar\". En la pantalla que se abre, busca Repropower " +
                        "y actívalo; luego vuelve con Atrás. Al cerrar el video se restaura el modo que tenías."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    awaitingQuietAccess = true
                    runCatching { context.startActivity(QuietMode.accessSettingsIntent()) }
                        .onFailure {
                            awaitingQuietAccess = false
                            showOsd("No se pudo abrir los ajustes")
                        }
                }) { Text("Abrir ajustes") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } },
        )
        null -> Unit
    }
}

enum class PlayerDialog { SPEED, AUDIO, SUBTITLES, QUIET_ACCESS, SLEEP }

/** Al soltar el dedo en Reels: pasa al video siguiente o anterior, o vuelve a su sitio. */
private suspend fun finishReelSwipe(
    player: Player,
    offset: Animatable<Float, AnimationVector1D>,
    dy: Float,
    height: Float,
    showOsd: (String) -> Unit,
) {
    val goNext = dy < -height * REEL_SWIPE_FRACTION
    val goPrevious = dy > height * REEL_SWIPE_FRACTION
    when {
        goNext && player.hasNextMediaItem() -> {
            offset.animateTo(-height, tween(160))
            player.seekToNextMediaItem()
            player.play()
            offset.snapTo(height)
            offset.animateTo(0f, tween(220))
        }
        goPrevious && player.hasPreviousMediaItem() -> {
            offset.animateTo(height, tween(160))
            player.seekToPreviousMediaItem()
            player.play()
            offset.snapTo(-height)
            offset.animateTo(0f, tween(220))
        }
        else -> {
            if (goNext) showOsd("No hay más reels")
            if (goPrevious) showOsd("Es el primer reel")
            offset.animateTo(0f, spring())
        }
    }
}

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
