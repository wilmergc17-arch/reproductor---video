package com.wilmer.reproductor.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Abre el menú "Compartir" de Android con uno o varios videos (WhatsApp, Telegram, Drive…).
 * Las direcciones de red se comparten como texto.
 */
fun shareVideos(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val (links, files) = uris.partition { it.scheme in setOf("http", "https", "rtsp", "rtmp") }
    val intent = if (files.isEmpty()) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, links.joinToString("\n"))
        }
    } else {
        val shareable = files.mapNotNull { shareableUri(context, it) }
        if (shareable.isEmpty()) {
            Toast.makeText(context, "No se pudo compartir este video", Toast.LENGTH_SHORT).show()
            return
        }
        val base = if (shareable.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, shareable.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareable))
        }
        base.apply {
            type = "video/*"
            // ClipData hace que el permiso de lectura llegue a todos los videos, no solo al primero
            clipData = ClipData.newRawUri("videos", shareable.first()).also { clip ->
                shareable.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val title = if (uris.size == 1) "Compartir video" else "Compartir ${uris.size} videos"
    runCatching { context.startActivity(Intent.createChooser(intent, title)) }
        .onFailure { Toast.makeText(context, "No hay apps para compartir", Toast.LENGTH_SHORT).show() }
}

/** Los archivos (file://) no se pueden pasar a otras apps directamente: se envuelven en un FileProvider. */
private fun shareableUri(context: Context, uri: Uri): Uri? =
    if (uri.scheme == "file") {
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.archivos", File(uri.path.orEmpty()))
        }.getOrNull()
    } else {
        uri
    }
