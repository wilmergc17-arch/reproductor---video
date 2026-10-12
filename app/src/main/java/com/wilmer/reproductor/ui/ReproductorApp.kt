package com.wilmer.reproductor.ui

import android.Manifest
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import com.wilmer.reproductor.ui.theme.BrandGradientHorizontal
import androidx.compose.runtime.rememberCoroutineScope
import com.wilmer.reproductor.data.DeleteResult
import kotlinx.coroutines.launch
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.wilmer.reproductor.data.ScanSettings
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wilmer.reproductor.data.PlaylistEntry
import com.wilmer.reproductor.data.Video
import com.wilmer.reproductor.ui.player.PlayerScreen

// Video público de prueba (Big Buck Bunny), sirve para comprobar que el reproductor funciona
private const val SAMPLE_VIDEO_URL =
    "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

enum class Tab(val label: String, val icon: ImageVector) {
    VIDEOS("Videos", Icons.Default.VideoLibrary),
    CARPETAS("Carpetas", Icons.Default.Folder),
    PELICULAS("Películas", Icons.Default.Movie),
    SERIES("Series", Icons.Default.Tv),
    REELS("Reels", Icons.Default.Slideshow),
    LISTAS("Listas", Icons.Default.PlaylistPlay),
}

enum class SortOrder(val label: String) {
    RECIENTES("Más recientes"),
    NOMBRE_AZ("Nombre (A-Z)"),
    NOMBRE_ZA("Nombre (Z-A)"),
    DURACION_MAYOR("Duración (más largos)"),
    DURACION_MENOR("Duración (más cortos)"),
    TAMANO_MAYOR("Tamaño (más grandes)"),
    TAMANO_MENOR("Tamaño (más pequeños)"),
}

/** Pantalla de detalle abierta dentro de una pestaña. */
sealed interface Detail {
    data class FolderDetail(val path: String) : Detail
    data class ShowDetail(val key: String) : Detail
    data class PlaylistDetail(val id: String) : Detail
}

// En Android 14+ el usuario puede dar acceso solo a algunos videos (READ_MEDIA_VISUAL_USER_SELECTED)
private val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    // Android 9 o menos: escribir hace falta para poder eliminar videos
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.isGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun hasMediaPermission(context: Context) =
    mediaPermissions.any { context.isGranted(it) } || hasAllFilesAccess(context)

/** Permiso para recorrer todo el almacenamiento archivo por archivo. */
private fun hasAllFilesAccess(context: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
    else context.isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)

@Composable
fun ReproductorApp(
    library: LibraryViewModel,
    playback: PlaybackRequest?,
    inPip: Boolean,
    onPlay: (PlaybackRequest) -> Unit,
    onClose: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
    onVideoSize: (Int, Int) -> Unit,
    onEnterPip: () -> Unit,
) {
    // La biblioteca sigue debajo del reproductor para conservar la pestaña y la posición
    Box(Modifier.fillMaxSize()) {
        LibraryScreen(library, onPlay)
        if (playback != null) {
            // key: una nueva cola crea un reproductor nuevo
            key(playback) {
                PlayerScreen(
                    request = playback,
                    inPip = inPip,
                    onPlayingChanged = onPlayingChanged,
                    onVideoSize = onVideoSize,
                    onEnterPip = onEnterPip,
                    onClose = onClose,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onPlay: (PlaybackRequest) -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val playlists by viewModel.playlists.playlists.collectAsState()

    val settings by viewModel.scanSettings.collectAsState()
    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    var notice by remember { mutableStateOf<String?>(null) }
    var sourcesDialog by remember { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasMediaPermission(context)
        if (!granted) {
            notice = "Sin ese permiso puedes usar \"Escanear todo el teléfono\" o \"Elegir carpetas\". " +
                "También puedes activarlo en Ajustes > Apps > Repropower > Permisos."
        }
    }
    LaunchedEffect(granted) { if (granted) viewModel.onPermissionGranted() }

    // Al volver de Ajustes, revisa si cambió algún permiso
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasMediaPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allFilesSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasAllFilesAccess(context)) {
            granted = hasMediaPermission(context)
            viewModel.setFullScan(true)
        } else {
            notice = "Para escanear todo el teléfono activa \"Permitir acceso para administrar todos los archivos\". " +
                "Si prefieres no hacerlo, usa \"Elegir carpetas\"."
        }
    }
    val legacyStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            granted = hasMediaPermission(context)
            viewModel.setFullScan(true)
        } else {
            notice = "Sin permiso de almacenamiento no puedo escanear el teléfono."
        }
    }
    val requestFullScan: () -> Unit = {
        when {
            hasAllFilesAccess(context) -> viewModel.setFullScan(true)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val appScreen = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                try {
                    allFilesSettings.launch(appScreen)
                } catch (e: ActivityNotFoundException) {
                    allFilesSettings.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            else -> legacyStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Guarda el permiso para poder volver a leer la carpeta al abrir la app otra vez
            // Lectura y escritura (para poder eliminar); si no se concede escritura, solo lectura
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            viewModel.addFolder(uri)
        }
    }
    val canShowLibrary = granted || settings.fullScan || settings.folders.isNotEmpty()

    var tab by rememberSaveable { mutableStateOf(Tab.VIDEOS) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortOrder.RECIENTES) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf<List<PlaylistEntry>?>(null) }

    // Filtro de videos parecidos (peso o duración); se quita al cambiar de pestaña o de carpeta
    var filter by remember { mutableStateOf<SimilarFilter?>(null) }
    LaunchedEffect(tab, detail) { filter = null }

    BackHandler(enabled = detail != null || searching || filter != null) {
        when {
            filter != null -> filter = null
            detail != null -> detail = null
            else -> { searching = false; query = "" }
        }
    }
    // Registrado después: con videos marcados, Atrás primero quita la selección
    val selection = remember { Selection() }
    BackHandler(enabled = selection.active) { selection.clear() }
    val selectedVideos: () -> List<Video> = { selection.uris.mapNotNull { state.byUri[it] } }

    // Videos sobre los que se puede filtrar en la pantalla actual (null = sin filtros aquí)
    val filterScope: List<Video>? = when (tab) {
        Tab.VIDEOS -> state.videos
        Tab.PELICULAS -> state.movies
        Tab.REELS -> state.reels
        Tab.CARPETAS -> (detail as? Detail.FolderDetail)?.let { d -> state.folders.firstOrNull { it.path == d.path }?.videos }
        Tab.SERIES -> (detail as? Detail.ShowDetail)?.let { d -> state.shows.firstOrNull { it.key == d.key }?.episodes }
        Tab.LISTAS -> null
    }?.takeIf { canShowLibrary && state.loaded && state.videos.isNotEmpty() }
    val requestSimilar: (Video, Metric) -> Unit = { video, metric ->
        filter = SimilarFilter(metric, video, filter?.tolerance ?: 0.10)
    }

    // Eliminar: primero nuestra confirmación; si Android pide la suya, se muestra después
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<List<Video>?>(null) }
    var deleteResult by remember { mutableStateOf<DeleteResult?>(null) }
    var pendingDelete by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val systemDelete = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingDelete
        pendingDelete = emptyList()
        if (result.resultCode == Activity.RESULT_OK && pending.isNotEmpty()) {
            scope.launch { deleteResult = viewModel.deleteConfirmed(pending) }
        }
    }
    LaunchedEffect(deleteResult) {
        val r = deleteResult ?: return@LaunchedEffect
        deleteResult = null
        val confirm = r.confirm
        if (confirm != null) {
            pendingDelete = r.pending
            systemDelete.launch(IntentSenderRequest.Builder(confirm).build())
        }
        if (r.failed > 0) {
            notice = (if (r.failed == 1) "No se pudo eliminar 1 video." else "No se pudieron eliminar ${r.failed} videos.") +
                " Si están en una carpeta elegida, quítala y vuelve a agregarla en ⋮ > Carpetas a escanear para dar permiso de escritura," +
                " o activa \"Escanear todo el teléfono\"."
        }
    }
    val requestDelete: (List<Video>) -> Unit = { list -> if (list.isNotEmpty()) toDelete = list }

    // Abre cualquier archivo de video, aunque no esté en la biblioteca (mkv, carpetas ocultas…)
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPlay(PlaybackRequest(listOf(QueueItem(it, it.lastPathSegment ?: "Video")))) }
    }

    val play: (List<Video>, Int) -> Unit = { list, index -> onPlay(PlaybackRequest(list.toQueue(), index)) }
    val askAdd: (List<Video>) -> Unit = { list -> addToPlaylist = list.map { it.toEntry() } }

    CompositionLocalProvider(
        LocalSelection provides selection,
        LocalDeleteRequest provides requestDelete,
        LocalSimilarRequest provides requestSimilar,
    ) {
    Scaffold(
        topBar = {
            if (selection.active) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                navigationIcon = {
                    IconButton(onClick = { selection.clear() }) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar selección")
                    }
                },
                title = {
                    val n = selection.uris.size
                    Text(if (n == 1) "1 seleccionado" else "$n seleccionados")
                },
                actions = {
                    IconButton(onClick = { selection.selectAll() }) {
                        Icon(Icons.Default.SelectAll, contentDescription = "Seleccionar todo")
                    }
                    IconButton(onClick = {
                        val list = selectedVideos()
                        selection.clear()
                        if (list.isNotEmpty()) play(list, 0)
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir seleccionados")
                    }
                    IconButton(onClick = { askAdd(selectedVideos()) }) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Agregar seleccionados a una lista")
                    }
                    IconButton(onClick = { shareVideos(context, selectedVideos().map { it.uri }) }) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir seleccionados")
                    }
                    IconButton(onClick = { requestDelete(selectedVideos()) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar seleccionados del teléfono")
                    }
                },
            ) else Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (detail != null) {
                        IconButton(onClick = { detail = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar en ${tab.label.lowercase()}…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                        )
                    } else {
                        val title = detailTitle(detail, state, playlists.associate { it.id to it.name })
                        if (title != null) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            BrandTitle()
                        }
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda")
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    }
                    val sortable = tab == Tab.VIDEOS || tab == Tab.PELICULAS || tab == Tab.REELS ||
                        (tab == Tab.CARPETAS && detail != null)
                    if (sortable) {
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Ordenar")
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SortOrder.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(if (s == sort) "✓ ${s.label}" else s.label) },
                                        onClick = { sort = s; sortMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Abrir archivo…") },
                                onClick = { menuOpen = false; openFile.launch(arrayOf("video/*")) },
                            )
                            DropdownMenuItem(
                                text = { Text("Abrir dirección de red (URL)…") },
                                onClick = { menuOpen = false; urlDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Video de ejemplo") },
                                onClick = {
                                    menuOpen = false
                                    onPlay(PlaybackRequest(listOf(QueueItem(Uri.parse(SAMPLE_VIDEO_URL), "Big Buck Bunny"))))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Carpetas a escanear…") },
                                onClick = { menuOpen = false; sourcesDialog = true },
                            )
                            if (canShowLibrary) {
                                DropdownMenuItem(
                                    text = { Text("Actualizar biblioteca") },
                                    onClick = { menuOpen = false; viewModel.refresh(forceScan = true) },
                                )
                            }
                        }
                    }
                },
            )
            if (filterScope != null) FilterRow(filter) { filter = it }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t; detail = null },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    trackColor = Color.Transparent,
                )
            }
            when {
                tab == Tab.LISTAS -> PlaylistsTab(
                    playlists = playlists,
                    library = state,
                    store = viewModel.playlists,
                    query = query,
                    openId = (detail as? Detail.PlaylistDetail)?.id,
                    onOpen = { detail = Detail.PlaylistDetail(it) },
                    onClose = { detail = null },
                    onPlay = onPlay,
                )
                !canShowLibrary -> VideoSources(
                    message = "Elige cómo encontrar tus videos. Puedes usar varias opciones a la vez.",
                    showMediaButton = true,
                    onMedia = { requestPermission.launch(mediaPermissions) },
                    onFullScan = requestFullScan,
                    onPickFolder = { pickFolder.launch(null) },
                    onOpenFile = { openFile.launch(arrayOf("video/*")) },
                )
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.videos.isEmpty() -> VideoSources(
                    message = buildString {
                        append("No encontré videos")
                        append(" (biblioteca de Android: ${state.fromAndroid}, escaneo de carpetas: ${state.fromScan}).")
                        state.error?.let { append("\n\n").append(it) }
                        append("\n\nPrueba a escanear todo el teléfono o elige las carpetas donde guardas tus videos.")
                    },
                    showMediaButton = !granted,
                    onMedia = { requestPermission.launch(mediaPermissions) },
                    onFullScan = requestFullScan,
                    onPickFolder = { pickFolder.launch(null) },
                    onOpenFile = { openFile.launch(arrayOf("video/*")) },
                )
                filter != null && filterScope != null -> SimilarResults(
                    filter = filter!!,
                    videos = filterScope.filter { it.matchesQuery(query) },
                    play = play,
                    askAdd = askAdd,
                    onClearReference = { filter = filter?.copy(reference = null) },
                )
                else -> when (tab) {
                    Tab.VIDEOS -> VideosTab(
                        videos = state.videos, sort = sort, query = query, play = play, askAdd = askAdd,
                    )
                    Tab.CARPETAS -> FoldersTab(
                        state = state,
                        sort = sort,
                        query = query,
                        openPath = (detail as? Detail.FolderDetail)?.path,
                        onOpen = { detail = Detail.FolderDetail(it) },
                        play = play,
                        askAdd = askAdd,
                    )
                    Tab.PELICULAS -> VideoGrid(
                        videos = state.movies.filter { it.matchesQuery(query) }.sortedFor(sort),
                        empty = "No hay películas: aquí aparecen los videos de una hora o más.",
                        columns = 2,
                        aspectRatio = 16f / 9f,
                        play = play,
                        askAdd = askAdd,
                    )
                    Tab.SERIES -> SeriesTab(
                        state = state,
                        query = query,
                        openKey = (detail as? Detail.ShowDetail)?.key,
                        onOpen = { detail = Detail.ShowDetail(it) },
                        play = play,
                        askAdd = askAdd,
                        onCreatePlaylist = { show ->
                            viewModel.playlists.create(show.name, show.episodes.map { it.toEntry() })
                            tab = Tab.LISTAS
                            detail = null
                        },
                    )
                    Tab.REELS -> VideoGrid(
                        videos = state.reels.filter { it.matchesQuery(query) }.sortedFor(sort),
                        empty = "No hay reels: aquí aparecen los videos de menos de 10 minutos.",
                        columns = 3,
                        aspectRatio = 9f / 16f,
                        play = play,
                        askAdd = askAdd,
                    )
                    Tab.LISTAS -> Unit
                }
            }
        }
    }
    }

    toDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = {
                Text(if (list.size == 1) "¿Eliminar este video del teléfono?" else "¿Eliminar ${list.size} videos del teléfono?")
            },
            text = {
                val names = list.take(3).joinToString("\n") { "• ${it.fileName}" } +
                    if (list.size > 3) "\n• y ${list.size - 3} más" else ""
                Text("$names\n\nSe borran los archivos del almacenamiento y no se pueden recuperar.")
            },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    selection.clear()
                    scope.launch { deleteResult = viewModel.delete(list.map { it.uri }) }
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }

    if (sourcesDialog) {
        ScanSourcesDialog(
            settings = settings,
            mediaGranted = granted,
            folderName = viewModel::folderName,
            onDismiss = { sourcesDialog = false },
            onMedia = { requestPermission.launch(mediaPermissions) },
            onFullScanChange = { enabled -> if (enabled) requestFullScan() else viewModel.setFullScan(false) },
            onAddFolder = { pickFolder.launch(null) },
            onRemoveFolder = viewModel::removeFolder,
        )
    }

    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { notice = null }) { Text("Entendido") } },
        )
    }

    if (urlDialog) {
        UrlDialog(
            onDismiss = { urlDialog = false },
            onOpen = { url ->
                urlDialog = false
                onPlay(PlaybackRequest(listOf(QueueItem(Uri.parse(url), url))))
            },
        )
    }

    addToPlaylist?.let { entries ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { addToPlaylist = null },
            onAdd = { p -> viewModel.playlists.add(p.id, entries); addToPlaylist = null; selection.clear() },
            onCreate = { name -> viewModel.playlists.create(name, entries); addToPlaylist = null; selection.clear() },
        )
    }
}

private fun detailTitle(detail: Detail?, state: LibraryState, playlistNames: Map<String, String>): String? =
    when (detail) {
        is Detail.FolderDetail -> state.folders.firstOrNull { it.path == detail.path }?.name
        is Detail.ShowDetail -> state.shows.firstOrNull { it.key == detail.key }?.name
        is Detail.PlaylistDetail -> playlistNames[detail.id]
        null -> null
    }

@Composable
private fun VideoSources(
    message: String,
    showMediaButton: Boolean,
    onMedia: () -> Unit,
    onFullScan: () -> Unit,
    onPickFolder: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandLogo(size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(brandName(), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        if (showMediaButton) {
            GradientButton(
                "Usar la galería de Android",
                onClick = onMedia,
                icon = Icons.Default.VideoLibrary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SourceButton("Escanear todo el teléfono", Icons.Default.PhoneAndroid, onFullScan)
        } else {
            GradientButton(
                "Escanear todo el teléfono",
                onClick = onFullScan,
                icon = Icons.Default.PhoneAndroid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        SourceButton("Elegir carpetas para escanear", Icons.Default.Folder, onPickFolder)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onOpenFile) { Text("Abrir un solo archivo") }
    }
}

@Composable
private fun SourceButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/** Ajustes de dónde buscar videos: galería de Android, todo el teléfono y carpetas elegidas. */
@Composable
private fun ScanSourcesDialog(
    settings: ScanSettings,
    mediaGranted: Boolean,
    folderName: (String) -> String,
    onDismiss: () -> Unit,
    onMedia: () -> Unit,
    onFullScanChange: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dónde buscar videos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Galería de Android", Modifier.weight(1f))
                    if (mediaGranted) Text("Activada", color = MaterialTheme.colorScheme.primary)
                    else TextButton(onClick = onMedia) { Text("Permitir") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Escanear todo el teléfono")
                        Text(
                            "Revisa memoria interna y tarjeta SD, incluidas carpetas ocultas a la galería.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.fullScan, onCheckedChange = onFullScanChange)
                }
                Spacer(Modifier.height(12.dp))
                Text("Carpetas elegidas", style = MaterialTheme.typography.titleSmall)
                if (settings.folders.isEmpty()) {
                    Text(
                        "Ninguna todavía.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                settings.folders.forEach { tree ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Text(
                            folderName(tree),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            maxLines = 2,
                        )
                        IconButton(onClick = { onRemoveFolder(tree) }) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar carpeta")
                        }
                    }
                }
                TextButton(onClick = onAddFolder) { Text("Agregar carpeta…") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir dirección de red") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("http://, https://, rtsp://, .m3u8…") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = url.isNotBlank(), onClick = { onOpen(url.trim()) }) { Text("Reproducir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
