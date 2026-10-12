package com.wilmer.reproductor.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import com.wilmer.reproductor.R
import com.wilmer.reproductor.ui.theme.BrandGradient
import com.wilmer.reproductor.ui.theme.BrandGradientHorizontal
import com.wilmer.reproductor.ui.theme.EpisodeColor
import com.wilmer.reproductor.ui.theme.MovieColor
import com.wilmer.reproductor.ui.theme.ReelColor
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
fun VideoThumbnail(uri: Uri, modifier: Modifier = Modifier, durationMs: Long = 0, corner: Dp = 12.dp) {
    val context = LocalContext.current
    val bitmap by produceState(Thumbnails.cached(uri), uri) {
        if (value == null) value = Thumbnails.load(context, uri)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
        if (durationMs > 0) {
            // Sombra suave abajo para que la duración se lea sobre cualquier imagen
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
            )
            Text(
                text = formatDuration(durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** Nombre de la app con "power" en el degradado de la marca. */
@OptIn(ExperimentalTextApi::class)
fun brandName(): AnnotatedString = buildAnnotatedString {
    append("Repro")
    withStyle(SpanStyle(brush = BrandGradientHorizontal)) { append("power") }
}

/** Logo de Repropower: el mismo dibujo que el ícono de la app, en un círculo. */
@Composable
fun BrandLogo(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        // El ícono adaptable mide 108 y su zona visible 66: se agranda y el círculo recorta el resto
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * (108f / 66f)),
        )
    }
}

@Composable
fun BrandTitle(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Text(brandName(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

/** Botón principal con el degradado de la marca. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(
                if (enabled) BrandGradientHorizontal else SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            icon?.let {
                Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

/** Etiqueta de color con el tipo del video (Película, Serie, Reel). */
data class KindLabel(val text: String, val color: Color)

fun Video.kindLabel(): KindLabel? = when (kind) {
    is VideoKind.Movie -> KindLabel("Película", MovieColor)
    is VideoKind.Episode -> KindLabel("Serie", EpisodeColor)
    is VideoKind.Reel -> KindLabel("Reel", ReelColor)
    is VideoKind.Other -> null
}

@Composable
fun KindBadge(label: KindLabel, modifier: Modifier = Modifier) {
    Text(
        label.text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = label.color,
        maxLines = 1,
        modifier = modifier
            .background(label.color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Círculo de selección sobre la miniatura. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) BrandGradient else SolidColor(Color.Black.copy(alpha = 0.45f)))
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Seleccionado", tint = Color.White, modifier = Modifier.size(16.dp))
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
    badge: KindLabel? = null,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.14f) else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, primary.copy(alpha = 0.6f), shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = if (selectionMode) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            VideoThumbnail(uri, Modifier.width(136.dp).height(78.dp), durationMs)
            if (selectionMode) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (badge != null || subtitle.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    badge?.let {
                        KindBadge(it)
                        Spacer(Modifier.width(8.dp))
                    }
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
            }
        }
        if (!selectionMode) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menu { menuOpen = false }
                }
            }
        }
    }
}

/** Acciones de un video de la biblioteca (menú ⋮ de filas y tarjetas). */
@Composable
fun LibraryVideoMenu(video: Video, dismiss: () -> Unit, onPlay: () -> Unit, onAddToPlaylist: () -> Unit) {
    val selection = LocalSelection.current
    val deleteRequest = LocalDeleteRequest.current
    val context = LocalContext.current
    MenuItem("Reproducir", Icons.Default.PlayArrow) { dismiss(); onPlay() }
    MenuItem("Agregar a lista", Icons.Default.PlaylistAdd) { dismiss(); onAddToPlaylist() }
    MenuItem("Compartir", Icons.Default.Share) { dismiss(); shareVideos(context, listOf(video.uri)) }
    if (selection != null) {
        MenuItem("Seleccionar varios", Icons.Default.Check) { dismiss(); selection.toggle(video) }
    }
    deleteRequest?.let { request ->
        MenuItem("Eliminar del teléfono", Icons.Default.Delete, MaterialTheme.colorScheme.error) {
            dismiss(); request(listOf(video))
        }
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector, color: Color = Color.Unspecified, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
            )
        },
        onClick = onClick,
    )
}

@Composable
fun LibraryVideoRow(
    video: Video,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    subtitle: String = "${video.folder} · ${formatSize(video.sizeBytes)}",
    showBadge: Boolean = true,
) {
    // Mantener presionado marca el video; con algo marcado, tocar marca o desmarca
    val selection = LocalSelection.current
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
        badge = if (showBadge) video.kindLabel() else null,
    ) { dismiss ->
        LibraryVideoMenu(video, dismiss, onPlay = onClick, onAddToPlaylist = onAddToPlaylist)
    }
}

/** Tarjeta de un video para las cuadrículas de Películas y Reels. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryVideoCard(video: Video, aspectRatio: Float, onClick: () -> Unit, onAddToPlaylist: () -> Unit) {
    val selection = LocalSelection.current
    val selectionMode = selection?.active == true
    val selected = selection?.uris?.contains(video.uri.toString()) == true
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .padding(5.dp)
            .clip(shape)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(
                onClick = { if (selection != null && selection.active) selection.toggle(video) else onClick() },
                onLongClick = selection?.let { s -> { s.toggle(video) } },
            ),
    ) {
        Box {
            VideoThumbnail(
                video.uri,
                Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                video.durationMs,
                corner = 18.dp,
            )
            if (selectionMode) {
                SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(8.dp))
            } else {
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.padding(4.dp).size(32.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        LibraryVideoMenu(video, { menuOpen = false }, onPlay = onClick, onAddToPlaylist = onAddToPlaylist)
                    }
                }
            }
        }
        Text(
            video.displayTitle(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        Text(
            formatSize(video.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
        )
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
fun EmptyMessage(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.VideoLibrary) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
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
