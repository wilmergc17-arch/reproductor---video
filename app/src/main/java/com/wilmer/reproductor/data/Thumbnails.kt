package com.wilmer.reproductor.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniaturas de los videos, guardadas en memoria para no recalcularlas al desplazarse. */
object Thumbnails {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun cached(uri: Uri): Bitmap? = cache.get(uri.toString())

    suspend fun load(context: Context, uri: Uri): Bitmap? {
        cached(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bitmap = if (uri.authority == MediaStore.AUTHORITY) {
                fromMediaStore(context, uri) ?: fromFrame(context, uri)
            } else {
                // Archivos encontrados por el escaneo de carpetas: se toma un cuadro del video
                fromFrame(context, uri)
            }
            bitmap?.also { cache.put(uri.toString(), it) }
        }
    }

    private fun fromMediaStore(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
        } else {
            val id = uri.lastPathSegment?.toLongOrNull() ?: return@runCatching null
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver, id, MediaStore.Video.Thumbnails.MINI_KIND, null
            )
        }
    }.getOrNull()

    private fun fromFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(2_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            val width = 320
            val height = (frame.height * width / frame.width.coerceAtLeast(1)).coerceAtLeast(1)
            Bitmap.createScaledBitmap(frame, width, height, true).also {
                if (it !== frame) frame.recycle()
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
