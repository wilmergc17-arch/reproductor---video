package com.wilmer.reproductor.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wilmer.reproductor.classify.VideoKind
import com.wilmer.reproductor.data.Playlist
import com.wilmer.reproductor.data.PlaylistEntry
import com.wilmer.reproductor.data.Thumbnails
import com.wilmer.reproductor.data.Video
import java.text.Normalizer
import java.util.Locale

/** Un video para la cola de reproducción. */
data class QueueItem(val uri: Uri, val title: String)

data class PlaybackRequest(val items: List<QueueItem>, val startIndex: Int = 0, val shuffle: Boolean = false)

fun Video.displayTitle(): String = when (val k = kind) {
    is VideoKind.Episode -> if (k.episode > 0) "T${k.season} E${k.episode} · ${k.title}" else k.title
    is VideoKind.Movie -> if (k.year != null) "${k.title} (${k.year})" else k.title
    is VideoKind.Reel -> k.title
    is VideoKind.Other -> k.title
}

fun Video.toQueueItem() = QueueItem(uri, displayTitle())
fun Video.toEntry() = PlaylistEntry(uri.toString(), displayTitle())
fun List<Video>.toQueue() = map { it.toQueueItem() }

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.0f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
}

private val ACCENTS = Regex("\\p{Mn}+")

/** Búsqueda sin distinguir mayúsculas ni tildes. */
fun String.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()
    return norm(this).contains(norm(query.trim()))
}

fun Video.matchesQuery(query: String) =
    displayTitle().matchesQuery(query) || fileName.matchesQuery(query) ||
        ((kind as? VideoKind.Episode)?.show?.matchesQuery(query) ?: false)

@Composable
fun VideoThumbnail(uri: Uri, modifier: Modifier = Modifier, durationMs: Long = 0) {
    val context = LocalContext.current
    val bitmap by produceState(Thumbnails.cached(uri), uri) {
        if (value == null) value = Thumbnails.load(context, uri)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF263238)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
        }
        if (durationMs > 0) {
            Text(
                text = formatDuration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** Videos marcados para hacer algo con todos a la vez (crear una lista, reproducirlos). */
class Selection {
    /** Direcciones de los videos marcados, en el orden en que se marcaron. */
    var uris by mutableStateOf(emptySet<String>())
        private set

    /** Videos que muestra la pantalla actual, para "Seleccionar todo". */
    var visible: List<Video> = emptyList()

    val active: Boolean get() = uris.isNotEmpty()

    fun toggle(video: Video) {
        val key = video.uri.toString()
        uris = if (key in uris) uris - key else uris + key
    }

    fun selectAll() {
        uris = uris + visible.map { it.uri.toString() }
    }

    fun clear() {
        uris = emptySet()
    }
}

val LocalSelection = staticCompositionLocalOf<Selection?> { null }

/** Pide confirmación para borrar videos del teléfono (la pantalla principal muestra el diálogo). */
val LocalDeleteRequest = staticCompositionLocalOf<((List<Video>) -> Unit)?> { null }

/** Fila de un video con miniatura, título y menú de acciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(
    uri: Uri,
    title: String,
    subtitle: String,
    durationMs: Long,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = if (selectionMode) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            VideoThumbnail(uri, Modifier.width(128.dp).height(72.dp), durationMs)
            if (selectionMode) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (selected) "Seleccionado" else "Sin seleccionar",
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!selectionMode) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu { menuOpen = false }
                }
            }
        }
    }
}

@Composable
fun LibraryVideoRow(
    video: Video,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    subtitle: String = "${video.folder} · ${formatSize(video.sizeBytes)}",
) {
    // Mantener presionado marca el video; con algo marcado, tocar marca o desmarca
    val selection = LocalSelection.current
    val deleteRequest = LocalDeleteRequest.current
    val context = LocalContext.current
    val selectionMode = selection?.active == true
    VideoRow(
        uri = video.uri,
        title = video.displayTitle(),
        subtitle = subtitle,
        durationMs = video.durationMs,
        onClick = { if (selection != null && selection.active) selection.toggle(video) else onClick() },
        onLongClick = selection?.let { s -> { s.toggle(video) } },
        selectionMode = selectionMode,
        selected = selection?.uris?.contains(video.uri.toString()) == true,
    ) { dismiss ->
        DropdownMenuItem(text = { Text("Reproducir") }, onClick = { dismiss(); onClick() })
        DropdownMenuItem(text = { Text("Agregar a lista") }, onClick = { dismiss(); onAddToPlaylist() })
        DropdownMenuItem(text = { Text("Compartir") }, onClick = { dismiss(); shareVideos(context, listOf(video.uri)) })
        if (selection != null) {
            DropdownMenuItem(text = { Text("Seleccionar varios") }, onClick = { dismiss(); selection.toggle(video) })
        }
        deleteRequest?.let { request ->
            DropdownMenuItem(text = { Text("Eliminar del teléfono") }, onClick = { dismiss(); request(listOf(video)) })
        }
    }
}

/** Diálogo para agregar videos a una lista existente o a una nueva. */
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAdd: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Nueva lista" else "Agregar a lista") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la lista") },
                    singleLine = true,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("Nueva lista…")
                        }
                    }
                    items(playlists, key = { it.id }) { p ->
                        Text(
                            "${p.name} (${p.entries.size})",
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(p) }.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name) }) { Text("Crear") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Diálogo simple para escribir un nombre (crear o renombrar listas). */
@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
