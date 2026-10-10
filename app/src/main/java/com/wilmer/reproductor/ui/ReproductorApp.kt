package com.wilmer.reproductor.ui

import android.Manifest
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    LISTAS("Listas", Icons.Default.PlaylistPlay),
}

enum class SortOrder(val label: String) {
    RECIENTES("Más recientes"),
    NOMBRE("Nombre"),
    DURACION("Duración"),
    TAMANO("Tamaño"),
}

/** Pantalla de detalle abierta dentro de una pestaña. */
sealed interface Detail {
    data class FolderDetail(val path: String) : Detail
    data class ShowDetail(val key: String) : Detail
    data class PlaylistDetail(val id: String) : Detail
}

private val videoPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

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

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, videoPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    LaunchedEffect(granted) { if (granted) viewModel.onPermissionGranted() }

    var tab by rememberSaveable { mutableStateOf(Tab.VIDEOS) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortOrder.RECIENTES) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf<List<PlaylistEntry>?>(null) }

    BackHandler(enabled = detail != null || searching) {
        if (detail != null) detail = null else { searching = false; query = "" }
    }

    // Abre cualquier archivo de video, aunque no esté en la biblioteca (mkv, carpetas ocultas…)
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPlay(PlaybackRequest(listOf(QueueItem(it, it.lastPathSegment ?: "Video")))) }
    }

    val play: (List<Video>, Int) -> Unit = { list, index -> onPlay(PlaybackRequest(list.toQueue(), index)) }
    val askAdd: (List<Video>) -> Unit = { list -> addToPlaylist = list.map { it.toEntry() } }

    Scaffold(
        topBar = {
            TopAppBar(
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
                            placeholder = { Text("Buscar…") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(detailTitle(detail, state, playlists.associate { it.id to it.name }) ?: tab.label)
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
                    if (tab == Tab.VIDEOS && detail == null) {
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
                            if (granted) {
                                DropdownMenuItem(
                                    text = { Text("Actualizar biblioteca") },
                                    onClick = { menuOpen = false; viewModel.refresh() },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t; detail = null },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                !granted -> PermissionRequest(
                    onRequest = { requestPermission.launch(videoPermission) },
                    onOpenFile = { openFile.launch(arrayOf("video/*")) },
                )
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> when (tab) {
                    Tab.VIDEOS -> VideosTab(
                        videos = state.videos, sort = sort, query = query, play = play, askAdd = askAdd,
                    )
                    Tab.CARPETAS -> FoldersTab(
                        state = state,
                        query = query,
                        openPath = (detail as? Detail.FolderDetail)?.path,
                        onOpen = { detail = Detail.FolderDetail(it) },
                        play = play,
                        askAdd = askAdd,
                    )
                    Tab.PELICULAS -> VideoList(
                        videos = state.movies.filter { it.matchesQuery(query) },
                        empty = "No encontré películas. Se reconocen por el año o las etiquetas del nombre (p. ej. \"Coco (2017).mp4\").",
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
                    Tab.LISTAS -> Unit
                }
            }
        }
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
            onAdd = { p -> viewModel.playlists.add(p.id, entries); addToPlaylist = null },
            onCreate = { name -> viewModel.playlists.create(name, entries); addToPlaylist = null },
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
private fun PermissionRequest(onRequest: () -> Unit, onOpenFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Para mostrar tu biblioteca, la app necesita permiso para ver los videos del teléfono.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Permitir acceso a videos") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenFile) { Text("Abrir un archivo sin dar permiso") }
    }
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
