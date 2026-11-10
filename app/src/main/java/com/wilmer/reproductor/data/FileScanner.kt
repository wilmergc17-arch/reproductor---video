package com.wilmer.reproductor.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Qué lugares, además de la biblioteca de Android, se revisan buscando videos. */
data class ScanSettings(
    /** Recorrer todo el almacenamiento del teléfono (y la tarjeta SD) archivo por archivo. */
    val fullScan: Boolean = false,
    /** Carpetas elegidas por el usuario (direcciones content:// con permiso guardado). */
    val folders: List<String> = emptyList(),
)

class ScanSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("escaneo", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ScanSettings> = _settings.asStateFlow()

    fun setFullScan(enabled: Boolean) = save(_settings.value.copy(fullScan = enabled))

    fun addFolder(uri: String) {
        val current = _settings.value
        if (uri !in current.folders) save(current.copy(folders = current.folders + uri))
    }

    fun removeFolder(uri: String) =
        save(_settings.value.copy(folders = _settings.value.folders - uri))

    private fun save(settings: ScanSettings) {
        _settings.value = settings
        prefs.edit()
            .putBoolean("todo", settings.fullScan)
            .putString("carpetas", settings.folders.joinToString("\n"))
            .apply()
    }

    private fun read() = ScanSettings(
        fullScan = prefs.getBoolean("todo", false),
        folders = prefs.getString("carpetas", "").orEmpty().split('\n').filter { it.isNotBlank() },
    )
}

/**
 * Busca videos recorriendo carpetas directamente, sin depender del índice de Android.
 * Así aparecen también los que Android no indexa (carpetas con .nomedia, apps de mensajería…).
 */
class FileScanner(private val context: Context) {

    /** Recorre todo el almacenamiento: memoria interna y tarjetas SD. */
    suspend fun scanStorage(): List<Video> = withContext(Dispatchers.IO) {
        val roots = buildList {
            @Suppress("DEPRECATION")
            add(Environment.getExternalStorageDirectory())
            // Las carpetas privadas de la app indican dónde está montada cada tarjeta SD
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                val path = dir.absolutePath
                if ("/Android/" in path) add(File(path.substringBefore("/Android/")))
            }
        }.distinctBy { it.absolutePath }

        val videos = mutableListOf<Video>()
        for (root in roots) {
            val stack = ArrayDeque<Pair<File, Int>>()
            stack.addLast(root to 0)
            while (stack.isNotEmpty()) {
                val (dir, depth) = stack.removeLast()
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    if (f.name.startsWith(".")) continue
                    if (f.isDirectory) {
                        val relative = f.absolutePath.removePrefix(root.absolutePath).trimStart('/')
                        if (depth < MAX_DEPTH && relative != "Android/data" && relative != "Android/obb") {
                            stack.addLast(f to depth + 1)
                        }
                    } else if (isVideoName(f.name) && f.length() > 0) {
                        val folderPath = f.parentFile?.absolutePath.orEmpty()
                            .removePrefix(root.absolutePath).trimStart('/')
                            .let { if (it.isEmpty()) "" else "$it/" }
                        videos += video(
                            uri = Uri.fromFile(f),
                            name = f.name,
                            folderPath = folderPath,
                            size = f.length(),
                            modifiedMs = f.lastModified(),
                        )
                    }
                }
            }
        }
        videos
    }

    /** Recorre una carpeta elegida con el selector del sistema (y sus subcarpetas). */
    suspend fun scanTree(tree: Uri): List<Video> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return@withContext emptyList()
        // El id suele ser "primary:Movies/Series": lo de después de ":" es la ruta real
        val rootPath = rootId.substringAfter(':', rootId).trim('/')
            .ifEmpty { folderName(tree) ?: "Carpeta" }

        val videos = mutableListOf<Video>()
        val stack = ArrayDeque<Triple<String, String, Int>>()
        stack.addLast(Triple(rootId, rootPath, 0))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (stack.isNotEmpty()) {
            val (docId, path, depth) = stack.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            runCatching {
                resolver.query(children, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        if (name.startsWith(".")) continue
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MAX_DEPTH) stack.addLast(Triple(id, "$path/$name", depth + 1))
                        } else if (mime.startsWith("video/") || isVideoName(name)) {
                            videos += video(
                                uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                                name = name,
                                folderPath = "$path/",
                                size = if (c.isNull(3)) 0 else c.getLong(3),
                                modifiedMs = if (c.isNull(4)) 0 else c.getLong(4),
                            )
                        }
                    }
                }
            }
        }
        videos
    }

    /** Nombre visible de una carpeta elegida, para mostrarla en la lista de carpetas. */
    fun folderName(tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(
            doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun video(uri: Uri, name: String, folderPath: String, size: Long, modifiedMs: Long) =
        Video(
            id = uri.toString().hashCode().toLong(),
            uri = uri,
            fileName = name,
            folderPath = folderPath,
            folder = folderPath.trimEnd('/').substringAfterLast('/').ifEmpty { "Almacenamiento" },
            durationMs = 0,
            sizeBytes = size,
            dateAddedSec = modifiedMs / 1000,
            kind = classifySafely(name, folderPath, 0),
        )

    companion object {
        private const val MAX_DEPTH = 12

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "asf", "flv", "f4v", "3gp", "3g2",
            "ts", "m2ts", "mts", "mpg", "mpeg", "vob", "ogv", "divx", "rmvb", "rm",
        )

        fun isVideoName(name: String) =
            name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    }
}
