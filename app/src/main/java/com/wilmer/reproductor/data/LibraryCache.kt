package com.wilmer.reproductor.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lo que quedó guardado del último escaneo. */
data class CachedLibrary(
    val videos: List<Video> = emptyList(),
    /** Cuáles de esos videos vinieron del escaneo de carpetas (no de la galería de Android). */
    val scannedUris: Set<String> = emptySet(),
    val lastScanMs: Long = 0,
    /** Ajustes de escaneo con los que se hizo, para saber si hay que repetirlo. */
    val scanKey: String = "",
)

/**
 * Guarda la biblioteca en un archivo para mostrarla al instante al abrir la app, sin esperar
 * a escanear otra vez. También guarda la duración de cada archivo, que es lo más lento de leer.
 */
class LibraryCache(context: Context) {
    private val file = File(context.filesDir, "biblioteca.json")

    suspend fun load(): CachedLibrary = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching CachedLibrary()
            val root = JSONObject(file.readText())
            val items = root.getJSONArray("videos")
            val scanned = mutableSetOf<String>()
            val videos = List(items.length()) { i ->
                val o = items.getJSONObject(i)
                val uri = o.getString("u")
                if (o.optBoolean("e")) scanned += uri
                val name = o.getString("n")
                val folderPath = o.optString("p")
                val duration = o.optLong("d")
                Video(
                    id = o.optLong("i"),
                    uri = Uri.parse(uri),
                    fileName = name,
                    folderPath = folderPath,
                    folder = o.optString("f"),
                    durationMs = duration,
                    sizeBytes = o.optLong("s"),
                    dateAddedSec = o.optLong("t"),
                    kind = classifySafely(name, folderPath, duration),
                )
            }
            CachedLibrary(videos, scanned, root.optLong("ultimoEscaneo"), root.optString("ajustes"))
        }.getOrDefault(CachedLibrary())
    }

    suspend fun save(library: CachedLibrary) = withContext(Dispatchers.IO) {
        runCatching {
            val items = JSONArray()
            library.videos.forEach { v ->
                items.put(
                    JSONObject()
                        .put("u", v.uri.toString())
                        .put("i", v.id)
                        .put("n", v.fileName)
                        .put("p", v.folderPath)
                        .put("f", v.folder)
                        .put("d", v.durationMs)
                        .put("s", v.sizeBytes)
                        .put("t", v.dateAddedSec)
                        .put("e", v.uri.toString() in library.scannedUris)
                )
            }
            val json = JSONObject()
                .put("videos", items)
                .put("ultimoEscaneo", library.lastScanMs)
                .put("ajustes", library.scanKey)
            // Se escribe aparte y se renombra para no dejar el archivo a medias
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.toString())
            tmp.renameTo(file)
        }
    }
}

/** Duración de un archivo leyendo sus metadatos; -1 si no se puede leer. */
fun readDurationMs(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1
    } catch (e: Exception) {
        -1
    } finally {
        runCatching { retriever.release() }
    }
}

/** Lee las duraciones de varios videos a la vez (unos pocos en paralelo). */
suspend fun readDurations(context: Context, videos: List<Video>): List<Video> = coroutineScope {
    videos.chunked(4).flatMap { chunk ->
        chunk.map { v ->
            async(Dispatchers.IO) { v.withDuration(readDurationMs(context, v.uri)) }
        }.awaitAll()
    }
}

fun Video.withDuration(duration: Long): Video =
    copy(durationMs = duration, kind = classifySafely(fileName, folderPath, duration))
