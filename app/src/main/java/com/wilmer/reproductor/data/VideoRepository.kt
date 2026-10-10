package com.wilmer.reproductor.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.wilmer.reproductor.classify.VideoClassifier
import com.wilmer.reproductor.classify.VideoKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Video(
    val id: Long,
    val uri: Uri,
    val fileName: String,
    /** Ruta de la carpeta dentro del almacenamiento, p. ej. "Movies/Dark/Season 1/". */
    val folderPath: String,
    /** Nombre de la carpeta que lo contiene, p. ej. "Season 1". */
    val folder: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val kind: VideoKind,
)

/** Lee los videos del teléfono desde MediaStore y los clasifica por su nombre. */
class VideoRepository(private val context: Context) {

    val collection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    suspend fun loadVideos(): List<Video> = withContext(Dispatchers.IO) {
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            pathColumn,
        )
        val videos = mutableListOf<Video>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(pathColumn)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: continue
                val rawPath = c.getString(pathCol).orEmpty()
                val folderPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rawPath
                } else {
                    // DATA trae la ruta completa: nos quedamos con la carpeta relativa
                    File(rawPath).parent.orEmpty()
                        .removePrefix("/storage/emulated/0").trimStart('/') + "/"
                }
                val duration = c.getLong(durCol)
                videos += Video(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    fileName = name,
                    folderPath = folderPath,
                    folder = c.getString(bucketCol)
                        ?: folderPath.trimEnd('/').substringAfterLast('/').ifEmpty { "Almacenamiento" },
                    durationMs = duration,
                    sizeBytes = c.getLong(sizeCol),
                    dateAddedSec = c.getLong(dateCol),
                    kind = VideoClassifier.classify(name, folderPath, duration),
                )
            }
        }
        videos
    }
}
