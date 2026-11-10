package com.wilmer.reproductor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.wilmer.reproductor.ui.theme.BrandGradient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wilmer.reproductor.classify.VideoKind
import com.wilmer.reproductor.data.Playlist
import com.wilmer.reproductor.data.PlaylistStore
import com.wilmer.reproductor.data.Video
import androidx.core.net.toUri

@Composable
fun VideoList(
    videos: List<Video>,
    empty: String,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val selection = LocalSelection.current
    SideEffect { selection?.visible = videos }
    if (videos.isEmpty() && header == null) {
        EmptyMessage(empty)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        header?.let { item { it() } }
        itemsIndexed(videos, key = { _, v -> v.uri.toString() }) { index, video ->
            LibraryVideoRow(
                video = video,
                onClick = { play(videos, index) },
                onAddToPlaylist = { askAdd(listOf(video)) },
            )
        }
    }
}

/** Cuadrícula de tarjetas: 2 columnas para películas, 3 verticales para reels. */
@Composable
fun VideoGrid(
    videos: List<Video>,
    empty: String,
    columns: Int,
    aspectRatio: Float,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
) {
    val selection = LocalSelection.current
    SideEffect { selection?.visible = videos }
    if (videos.isEmpty()) {
        EmptyMessage(empty)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 7.dp, end = 7.dp, top = 4.dp, bottom = 16.dp),
    ) {
        gridItemsIndexed(videos, key = { _, v -> v.uri.toString() }) { index, video ->
            LibraryVideoCard(
                video = video,
                aspectRatio = aspectRatio,
                onClick = { play(videos, index) },
                onAddToPlaylist = { askAdd(listOf(video)) },
            )
        }
    }
}

/** Resumen arriba de la lista: cuántos videos y cuánto ocupan. */
@Composable
private fun LibrarySummary(videos: List<Video>) {
    val bytes = videos.sumOf { it.sizeBytes }
    Text(
        "${videos.size} ${if (videos.size == 1) "video" else "videos"} · ${formatSize(bytes)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
    )
}

@Composable
fun VideosTab(
    videos: List<Video>,
    sort: SortOrder,
    query: String,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
) {
    val sorted = remember(videos, sort, query) { videos.filter { it.matchesQuery(query) }.sortedFor(sort) }
    VideoList(sorted, "No hay videos en el teléfono.", play, askAdd, header = { LibrarySummary(sorted) })
}

fun List<Video>.sortedFor(order: SortOrder): List<Video> = when (order) {
    SortOrder.RECIENTES -> sortedByDescending { it.dateAddedSec }
    SortOrder.NOMBRE_AZ -> sortedBy { it.displayTitle().lowercase() }
    SortOrder.NOMBRE_ZA -> sortedByDescending { it.displayTitle().lowercase() }
    SortOrder.DURACION_MAYOR -> sortedByDescending { it.durationMs }
    SortOrder.DURACION_MENOR -> sortedBy { if (it.durationMs > 0) it.durationMs else Long.MAX_VALUE }
    SortOrder.TAMANO_MAYOR -> sortedByDescending { it.sizeBytes }
    SortOrder.TAMANO_MENOR -> sortedBy { it.sizeBytes }
}

@Composable
fun FoldersTab(
    state: LibraryState,
    sort: SortOrder,
    query: String,
    openPath: String?,
    onOpen: (String) -> Unit,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
) {
    val folder = openPath?.let { p -> state.folders.firstOrNull { it.path == p } }
    if (folder != null) {
        val videos = folder.videos.filter { it.matchesQuery(query) }.sortedFor(sort)
        VideoList(videos, "Carpeta vacía.", play, askAdd, header = {
            PlayAllBar(onPlay = { play(videos, 0) }, onAdd = { askAdd(videos) }, count = videos.size)
        })
        return
    }
    val folders = state.folders.filter { it.name.matchesQuery(query) }
    if (folders.isEmpty()) {
        EmptyMessage("No hay carpetas con videos.", icon = Icons.Default.Folder)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpen(f.path) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${f.videos.size} videos · ${formatSize(f.videos.sumOf { it.sizeBytes })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SeriesTab(
    state: LibraryState,
    query: String,
    openKey: String?,
    onOpen: (String) -> Unit,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
    onCreatePlaylist: (Show) -> Unit,
) {
    val show = openKey?.let { k -> state.shows.firstOrNull { it.key == k } }
    if (show != null) {
        ShowDetail(show, query, play, askAdd, onCreatePlaylist)
        return
    }
    val shows = state.shows.filter { it.name.matchesQuery(query) }
    if (shows.isEmpty()) {
        EmptyMessage(
            "No encontré series: aquí aparecen los videos de 10 a 59 minutos, agrupados por nombre o carpeta.",
            icon = Icons.Default.Tv,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(shows, key = { it.key }) { s ->
            val first = s.episodes.first()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpen(s.key) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    VideoThumbnail(first.uri, Modifier.width(136.dp).height(78.dp))
                    Text(
                        "${s.episodes.size} ep.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val seasons = s.seasons.size
                    Text(
                        "$seasons ${if (seasons == 1) "temporada" else "temporadas"} · ${s.episodes.size} episodios",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowDetail(
    show: Show,
    query: String,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
    onCreatePlaylist: (Show) -> Unit,
) {
    val all = show.episodes
    val selection = LocalSelection.current
    SideEffect { selection?.visible = all.filter { it.matchesQuery(query) } }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradientButton("Reproducir todo", onClick = { play(all, 0) }, icon = Icons.Default.PlayArrow)
                OutlinedButton(onClick = { onCreatePlaylist(show) }, modifier = Modifier.height(46.dp)) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Crear lista")
                }
            }
        }
        show.seasons.forEach { (season, episodes) ->
            val visible = episodes.filter { it.matchesQuery(query) }
            if (visible.isEmpty()) return@forEach
            item(key = "t$season") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Temporada $season",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { play(episodes, 0) }) { Text("Reproducir") }
                    IconButton(onClick = { askAdd(episodes) }) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Agregar temporada a lista")
                    }
                }
            }
            items(visible, key = { it.uri.toString() }) { video ->
                val ep = video.kind as VideoKind.Episode
                LibraryVideoRow(
                    video = video,
                    onClick = { play(episodes, episodes.indexOf(video)) },
                    onAddToPlaylist = { askAdd(listOf(video)) },
                    subtitle = "Episodio ${ep.episode} · ${formatSize(video.sizeBytes)}",
                    showBadge = false,
                )
            }
        }
    }
}

@Composable
private fun PlayAllBar(onPlay: () -> Unit, onAdd: () -> Unit, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientButton("Reproducir todo", onClick = onPlay, icon = Icons.Default.PlayArrow, enabled = count > 0)
        OutlinedButton(onClick = onAdd, enabled = count > 0, modifier = Modifier.height(46.dp)) {
            Icon(Icons.Default.PlaylistAdd, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("A una lista")
        }
    }
}

@Composable
fun PlaylistsTab(
    playlists: List<Playlist>,
    library: LibraryState,
    store: PlaylistStore,
    query: String,
    openId: String?,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    val open = openId?.let { id -> playlists.firstOrNull { it.id == id } }
    if (open != null) {
        PlaylistDetail(open, library, store, onPlay, onDelete = { deleting = open })
    } else {
        Box(Modifier.fillMaxSize()) {
            val visible = playlists.filter { it.name.matchesQuery(query) }
            if (visible.isEmpty()) {
                EmptyMessage(
                    "Aún no tienes listas. Crea una aquí o usa \"Agregar a lista\" en el menú de cualquier video.",
                    icon = Icons.Default.PlaylistPlay,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(visible, key = { it.id }) { p ->
                        PlaylistRow(
                            playlist = p,
                            onClick = { onOpen(p.id) },
                            onPlay = { playPlaylist(p, library, onPlay, shuffle = false) },
                            onRename = { renaming = p },
                            onDelete = { deleting = p },
                        )
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva lista") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    if (creating) {
        NameDialog("Nueva lista", "", onDismiss = { creating = false }) { name ->
            store.create(name)
            creating = false
        }
    }
    renaming?.let { p ->
        NameDialog("Renombrar lista", p.name, onDismiss = { renaming = null }) { name ->
            store.rename(p.id, name)
            renaming = null
        }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Borrar \"${p.name}\"?") },
            text = { Text("Solo se borra la lista; los videos siguen en el teléfono.") },
            confirmButton = {
                TextButton(onClick = {
                    if (openId == p.id) onClose()
                    store.delete(p.id)
                    deleting = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val first = playlist.entries.firstOrNull()
        if (first != null) {
            VideoThumbnail(first.uri.toUri(), Modifier.width(112.dp).height(64.dp))
        } else {
            Box(
                Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(12.dp)).background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.entries.size} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPlay, enabled = playlist.entries.isNotEmpty()) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir lista", tint = MaterialTheme.colorScheme.primary)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menuOpen = false; onRename() })
                DropdownMenuItem(text = { Text("Borrar") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}

private fun playPlaylist(
    playlist: Playlist,
    library: LibraryState,
    onPlay: (PlaybackRequest) -> Unit,
    shuffle: Boolean,
    start: Int = 0,
) {
    if (playlist.entries.isEmpty()) return
    val items = playlist.entries.map { e ->
        library.byUri[e.uri]?.toQueueItem() ?: QueueItem(e.uri.toUri(), e.title)
    }
    onPlay(PlaybackRequest(items, start, shuffle))
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    library: LibraryState,
    store: PlaylistStore,
    onPlay: (PlaybackRequest) -> Unit,
    onDelete: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GradientButton(
                    "Reproducir",
                    onClick = { playPlaylist(playlist, library, onPlay, shuffle = false) },
                    icon = Icons.Default.PlayArrow,
                    enabled = playlist.entries.isNotEmpty(),
                )
                FilledTonalButton(
                    modifier = Modifier.height(46.dp),
                    onClick = { playPlaylist(playlist, library, onPlay, shuffle = true) },
                    enabled = playlist.entries.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Aleatorio")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Borrar lista")
                }
            }
        }
        if (playlist.entries.isEmpty()) {
            item {
                EmptyMessage(
                    "Lista vacía. Agrega videos desde las pestañas Videos, Películas o Series con el menú ⋮ de cada video.",
                    Modifier.height(240.dp),
                )
            }
        }
        itemsIndexed(playlist.entries, key = { _, e -> e.uri }) { index, entry ->
            val video = library.byUri[entry.uri]
            VideoRow(
                uri = entry.uri.toUri(),
                title = video?.displayTitle() ?: entry.title,
                subtitle = if (video == null && library.loaded && entry.uri.startsWith("content://media")) {
                    "No disponible en el teléfono"
                } else {
                    video?.folder.orEmpty()
                },
                durationMs = video?.durationMs ?: 0,
                onClick = { playPlaylist(playlist, library, onPlay, shuffle = false, start = index) },
            ) { dismiss ->
                if (index > 0) {
                    DropdownMenuItem(
                        text = { Text("Subir") },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                        onClick = { dismiss(); store.move(playlist.id, index, index - 1) },
                    )
                }
                if (index < playlist.entries.lastIndex) {
                    DropdownMenuItem(
                        text = { Text("Bajar") },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                        onClick = { dismiss(); store.move(playlist.id, index, index + 1) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Quitar de la lista") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { dismiss(); store.remove(playlist.id, entry.uri) },
                )
            }
        }
    }
}
