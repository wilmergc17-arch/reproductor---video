package com.wilmer.reproductor.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.wilmer.reproductor.data.Similarity
import com.wilmer.reproductor.data.Video
import kotlin.math.roundToInt

/** Qué se compara para encontrar videos parecidos. */
enum class Metric(val label: String, val icon: ImageVector) {
    PESO("Peso similar", Icons.Default.Storage),
    DURACION("Duración similar", Icons.Default.Timer);

    fun of(video: Video): Long = if (this == PESO) video.sizeBytes else video.durationMs

    fun format(value: Long): String = if (this == PESO) formatSize(value) else formatDuration(value)
}

/**
 * Filtro activo. Con [reference] muestra los parecidos a ese video;
 * sin referencia agrupa los videos parecidos entre sí (sirve para encontrar copias repetidas).
 */
data class SimilarFilter(val metric: Metric, val reference: Video? = null, val tolerance: Double = 0.10)

val TOLERANCES = listOf(0.05, 0.10, 0.20)

private fun percent(tolerance: Double) = "±${(tolerance * 100).roundToInt()}%"

/** "Ver de peso/duración similar" desde el menú de un video. */
val LocalSimilarRequest = staticCompositionLocalOf<((Video, Metric) -> Unit)?> { null }

/** Fila de filtros debajo de la barra superior. Tocar el filtro activo lo quita. */
@Composable
fun FilterRow(filter: SimilarFilter?, onChange: (SimilarFilter?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Icon(
                Icons.Default.FilterList,
                contentDescription = "Filtros",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        items(Metric.entries) { m ->
            Pill(m.label, m.icon, selected = filter?.metric == m) {
                onChange(
                    if (filter?.metric == m) null
                    else SimilarFilter(m, filter?.reference, filter?.tolerance ?: 0.10)
                )
            }
        }
        if (filter != null) {
            items(TOLERANCES) { t ->
                Pill(percent(t), null, selected = filter.tolerance == t) { onChange(filter.copy(tolerance = t)) }
            }
        }
    }
}

/** Resultados del filtro sobre los videos de la pestaña actual. */
@Composable
fun SimilarResults(
    filter: SimilarFilter,
    videos: List<Video>,
    play: (List<Video>, Int) -> Unit,
    askAdd: (List<Video>) -> Unit,
    onClearReference: () -> Unit,
) {
    val selection = LocalSelection.current
    val metric = filter.metric
    val reference = filter.reference
    if (reference != null) {
        val refValue = metric.of(reference)
        val matches = remember(filter, videos) {
            Similarity.similarTo(videos.filter { it.uri != reference.uri }, refValue, filter.tolerance, metric::of)
        }
        SideEffect { selection?.visible = matches }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${metric.label} (${percent(filter.tolerance)}) a:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearReference) { Text("Ver grupos") }
                }
            }
            item(key = "ref") {
                LibraryVideoRow(
                    video = reference,
                    onClick = { play(listOf(reference) + matches, 0) },
                    onAddToPlaylist = { askAdd(listOf(reference)) },
                    subtitle = metric.format(refValue),
                )
            }
            item {
                Text(
                    if (matches.isEmpty()) "No hay otros videos parecidos aquí. Prueba con ±20%."
                    else "${matches.size} ${if (matches.size == 1) "parecido" else "parecidos"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(matches, key = { _, v -> v.uri.toString() }) { index, video ->
                val value = metric.of(video)
                LibraryVideoRow(
                    video = video,
                    onClick = { play(matches, index) },
                    onAddToPlaylist = { askAdd(listOf(video)) },
                    subtitle = "${metric.format(value)} · ${difference(value, refValue)}",
                )
            }
        }
        return
    }

    val groups = remember(filter, videos) { Similarity.groups(videos, filter.tolerance, metric::of) }
    SideEffect { selection?.visible = groups.flatten() }
    if (groups.isEmpty()) {
        EmptyMessage(
            "No encontré videos con ${metric.label.lowercase()} entre sí (${percent(filter.tolerance)}).",
            icon = Icons.Default.FilterList,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                "${groups.size} ${if (groups.size == 1) "grupo" else "grupos"} · ${groups.sumOf { it.size }} videos. " +
                    "Sirve para encontrar copias repetidas; mantén presionado para marcar y eliminar.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
        groups.forEachIndexed { groupIndex, group ->
            item(key = "grupo$groupIndex") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val low = metric.format(metric.of(group.first()))
                    val high = metric.format(metric.of(group.last()))
                    Text(
                        "Grupo ${groupIndex + 1} · ${if (low == high) low else "$low – $high"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { play(group, 0) }) { Text("Reproducir") }
                }
            }
            itemsIndexed(group, key = { _, v -> v.uri.toString() }) { index, video ->
                LibraryVideoRow(
                    video = video,
                    onClick = { play(group, index) },
                    onAddToPlaylist = { askAdd(listOf(video)) },
                    subtitle = "${metric.format(metric.of(video))} · ${video.folder}",
                )
            }
        }
    }
}

private fun difference(value: Long, reference: Long): String {
    val pct = ((value - reference) * 100.0 / reference).roundToInt()
    return when {
        pct == 0 -> "casi igual"
        pct > 0 -> "+$pct%"
        else -> "$pct%"
    }
}

