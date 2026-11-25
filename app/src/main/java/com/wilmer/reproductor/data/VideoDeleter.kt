package com.wilmer.reproductor.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resultado de borrar videos. Si Android pide que el usuario confirme en su propio diálogo,
 * [confirm] trae ese diálogo y [pending] los videos que esperan esa confirmación.
 */
data class DeleteResult(
    val deleted: Set<String>,
    val failed: Int,
    val pending: List<Uri> = emptyList(),
    val confirm: IntentSender? = null,
)

/** Borra archivos de video del almacenamiento del teléfono, según de dónde vino cada uno. */
class VideoDeleter(private val context: Context) {

    suspend fun delete(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val deleted = mutableSetOf<String>()
        val needConfirm = mutableListOf<Uri>()
        var recoverable: IntentSender? = null
        var failed = 0
        val scanned = mutableListOf<String>()

        for (uri in uris) {
            val ok = try {
                when {
                    uri.scheme == "file" -> {
                        val file = File(uri.path.orEmpty())
                        (file.delete() || !file.exists()).also { if (it) scanned += file.absolutePath }
                    }
                    uri.authority == MediaStore.AUTHORITY -> resolver.delete(uri, null, null) > 0 || !exists(uri)
                    DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(resolver, uri)
                    else -> resolver.delete(uri, null, null) > 0
                }
            } catch (e: SecurityException) {
                // Android 10+: los videos de otras apps necesitan que el usuario confirme
                if (uri.authority == MediaStore.AUTHORITY) {
                    needConfirm += uri
                    if (recoverable == null && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                        e is RecoverableSecurityException
                    ) {
                        recoverable = e.userAction.actionIntent.intentSender
                    }
                    continue
                }
                false
            } catch (e: Exception) {
                false
            }
            if (ok) deleted += uri.toString() else failed++
        }

        // Avisa a la galería de Android que esos archivos ya no están
        if (scanned.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, scanned.toTypedArray(), null, null)
        }

        val confirm = when {
            needConfirm.isEmpty() -> null
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                MediaStore.createDeleteRequest(resolver, needConfirm).intentSender
            else -> recoverable
        }
        if (needConfirm.isNotEmpty() && confirm == null) failed += needConfirm.size
        DeleteResult(
            deleted = deleted,
            failed = failed,
            pending = if (confirm != null) needConfirm else emptyList(),
            confirm = confirm,
        )
    }

    private fun exists(uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }.getOrDefault(false)
}
