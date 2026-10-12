package com.wilmer.reproductor.ui

import androidx.compose.foundation.clickable
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

@Composable
fun VideosTab(
    videos: List<Video>,
    sort: SortOrder,
    query: String,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
) {
    val sorted = remember(videos, sort, query) {
        val filtered = videos.filter { it.matchesQuery(query) }
        when (sort) {
            SortOrder.RECIENTES -> filtered.sortedByDescending { it.dateAddedSec }
            SortOrder.NOMBRE -> filtered.sortedBy { it.fileName.lowercase() }
            SortOrder.DURACION -> filtered.sortedByDescending { it.durationMs }
            SortOrder.TAMANO -> filtered.sortedByDescending { it.sizeBytes }
        }
    }
    VideoList(sorted, "No hay videos en el teléfono.", play, askAdd)
}

@Composable
fun FoldersTab(
    state: LibraryState,
    query: String,
    openPath: String?,
    onOpen: (String) -> Unit,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
) {
    val folder = openPath?.let { p -> state.folders.firstOrNull { it.path == p } }
    if (folder != null) {
        val videos = folder.videos.filter { it.matchesQuery(query) }
        VideoList(videos, "Carpeta vacía.", play, askAdd, header = {
            PlayAllBar(onPlay = { play(videos, 0) }, onAdd = { askAdd(videos) }, count = videos.size)
        })
        return
    }
    val folders = state.folders.filter { it.name.matchesQuery(query) }
    if (folders.isEmpty()) {
        EmptyMessage("No hay carpetas con videos.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { f ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(f.path) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${f.videos.size} videos · ${f.path}",
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
        EmptyMessage("No encontré series. Se reconocen por nombres como \"Serie S01E02\", \"1x02\" o \"Temporada 1 Capítulo 2\".")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(shows, key = { it.key }) { s ->
            val first = s.episodes.first()
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(s.key) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VideoThumbnail(first.uri, Modifier.width(128.dp).height(72.dp))
                Spacer(Modifier.width(12.dp))
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
            ) {
                Button(onClick = { play(all, 0) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Reproducir todo")
                }
                OutlinedButton(onClick = { onCreatePlaylist(show) }) {
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
        Button(onClick = onPlay, enabled = count > 0) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Reproducir todo")
        }
        OutlinedButton(onClick = onAdd, enabled = count > 0) {
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
                    "Aún no tienes listas. Crea una aquí o usa \"Agregar a lista\" en el menú de cualquier video."
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val first = playlist.entries.firstOrNull()
        if (first != null) {
            VideoThumbnail(first.uri.toUri(), Modifier.width(96.dp).height(54.dp))
        } else {
            Box(Modifier.width(96.dp).height(54.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlaylistPlay, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${playlist.entries.size} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPlay, enabled = playlist.entries.isNotEmpty()) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir lista")
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
            ) {
                Button(
                    onClick = { playPlaylist(playlist, library, onPlay, shuffle = false) },
                    enabled = playlist.entries.isNotEmpty(),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Reproducir")
                }
                FilledTonalButton(
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
