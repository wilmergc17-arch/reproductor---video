package com.wilmer.reproductor.ui

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wilmer.reproductor.classify.VideoClassifier
import com.wilmer.reproductor.classify.VideoKind
import com.wilmer.reproductor.data.CachedLibrary
import com.wilmer.reproductor.data.DeleteResult
import com.wilmer.reproductor.data.FileScanner
import com.wilmer.reproductor.data.VideoDeleter
import com.wilmer.reproductor.data.LibraryCache
import com.wilmer.reproductor.data.readDurations
import com.wilmer.reproductor.data.withDuration
import com.wilmer.reproductor.data.PlaylistStore
import com.wilmer.reproductor.data.ScanSettings
import com.wilmer.reproductor.data.ScanSettingsStore
import com.wilmer.reproductor.data.Video
import com.wilmer.reproductor.data.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Show(
    val key: String,
    val name: String,
    /** Episodios agrupados por temporada, ya ordenados. */
    val seasons: Map<Int, List<Video>>,
) {
    val episodes: List<Video> get() = seasons.values.flatten()
}

data class Folder(val path: String, val name: String, val videos: List<Video>)

data class LibraryState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val videos: List<Video> = emptyList(),
    val movies: List<Video> = emptyList(),
    val shows: List<Show> = emptyList(),
    val reels: List<Video> = emptyList(),
    val others: List<Video> = emptyList(),
    val folders: List<Folder> = emptyList(),
    /** Cuántos videos dio la biblioteca de Android y cuántos el escaneo de carpetas. */
    val fromAndroid: Int = 0,
    val fromScan: Int = 0,
    val error: String? = null,
) {
    val byUri: Map<String, Video> by lazy { videos.associateBy { it.uri.toString() } }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)
    private val scanner = FileScanner(app)
    private val scanStore = ScanSettingsStore(app)
    val playlists = PlaylistStore(app)
    val scanSettings: StateFlow<ScanSettings> = scanStore.settings

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val cache = LibraryCache(app)
    private val deleter = VideoDeleter(app)
    private var cached: CachedLibrary? = null
    private var observer: ContentObserver? = null
    private var reloadJob: Job? = null

    init {
        // Muestra enseguida lo guardado; si hay carpetas configuradas, lo renueva en segundo plano
        val settings = scanStore.settings.value
        if (settings.fullScan || settings.folders.isNotEmpty()) {
            refresh()
        } else {
            viewModelScope.launch {
                val previous = cached ?: cache.load().also { cached = it }
                if (!_state.value.loaded && previous.videos.isNotEmpty()) {
                    _state.value = buildState(previous.videos).copy(loading = true)
                }
            }
        }
    }

    fun setFullScan(enabled: Boolean) {
        scanStore.setFullScan(enabled)
        refresh()
    }

    fun addFolder(tree: Uri) {
        scanStore.addFolder(tree.toString())
        refresh()
    }

    fun removeFolder(tree: String) {
        scanStore.removeFolder(tree)
        refresh()
    }

    fun folderName(tree: String): String =
        scanner.folderName(Uri.parse(tree))
            ?: Uri.decode(tree).substringAfterLast(':').ifEmpty { tree }

    /** Borra los videos del teléfono y los quita de la biblioteca y de las listas. */
    suspend fun delete(uris: List<Uri>): DeleteResult {
        val result = deleter.delete(uris)
        forget(result.deleted)
        return result
    }

    /** Tras confirmar en el diálogo de Android: reintenta y quita lo que ya no existe. */
    suspend fun deleteConfirmed(uris: List<Uri>): DeleteResult {
        val result = deleter.delete(uris)
        // En Android 11+ el propio diálogo ya los borró: el reintento no encuentra nada que borrar
        forget(uris.map { it.toString() }.toSet())
        return result
    }

    private suspend fun forget(uris: Set<String>) {
        if (uris.isEmpty()) return
        playlists.removeEverywhere(uris)
        val current = _state.value
        val remaining = current.videos.filter { it.uri.toString() !in uris }
        _state.value = buildState(remaining).copy(
            loading = current.loading,
            fromAndroid = current.fromAndroid,
            fromScan = current.fromScan,
            error = current.error,
        )
        cached?.let { c ->
            val updated = c.copy(
                videos = c.videos.filter { it.uri.toString() !in uris },
                scannedUris = c.scannedUris - uris,
            )
            cached = updated
            cache.save(updated)
        }
    }

    /** Se llama cuando ya hay permiso para leer los videos del teléfono. */
    fun onPermissionGranted() {
        if (observer == null) {
            // Si se agregan o borran videos, la biblioteca se actualiza sola
            observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh(debounce = true)
            }.also {
                getApplication<Application>().contentResolver
                    .registerContentObserver(repository.collection, true, it)
            }
        }
        refresh()
    }

    /**
     * Actualiza la biblioteca. Muestra primero lo guardado del último escaneo y luego lo renueva:
     * la galería de Android se lee siempre (es rápida); las carpetas se vuelven a recorrer solo si
     * cambiaron los ajustes, si pasó más de una hora o si se pide con [forceScan].
     */
    fun refresh(debounce: Boolean = false, forceScan: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (debounce) delay(800)
            val previous = cached ?: cache.load().also { cached = it }
            if (!_state.value.loaded && previous.videos.isNotEmpty()) {
                _state.value = buildState(previous.videos).copy(loading = true)
            } else {
                _state.update { it.copy(loading = true) }
            }

            val errors = mutableListOf<String>()
            val fromAndroid = if (observer != null) {
                runCatching { repository.loadVideos() }
                    .onFailure { errors += "biblioteca de Android: ${it.describe()}" }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val settings = scanStore.settings.value
            val scanKey = settings.toString()
            val hasScanSources = settings.fullScan || settings.folders.isNotEmpty()
            val now = System.currentTimeMillis()
            val scanNow = hasScanSources &&
                (forceScan || scanKey != previous.scanKey || now - previous.lastScanMs > RESCAN_AFTER_MS)
            val scanned: List<Video> = when {
                !hasScanSources -> emptyList()
                !scanNow -> previous.videos.filter { it.uri.toString() in previous.scannedUris }
                else -> buildList {
                    if (settings.fullScan) {
                        runCatching { scanner.scanStorage() }
                            .onSuccess { addAll(it) }
                            .onFailure { errors += "escaneo del teléfono: ${it.describe()}" }
                    }
                    settings.folders.forEach { tree ->
                        runCatching { scanner.scanTree(Uri.parse(tree)) }
                            .onSuccess { addAll(it) }
                            .onFailure { errors += "carpeta: ${it.describe()}" }
                    }
                }
            }

            // Un mismo archivo puede salir por los dos caminos: se queda la versión de Android
            val seen = fromAndroid.map { it.fileName to it.sizeBytes }.toHashSet()
            // Las duraciones ya leídas antes se reutilizan (leerlas es lo más lento)
            val known = previous.videos.associateBy { it.uri.toString() }
            var extra = scanned.distinctBy { it.uri.toString() }
                .filter { seen.add(it.fileName to it.sizeBytes) }
                .map { v ->
                    val old = known[v.uri.toString()]
                    if (v.durationMs == 0L && old != null && old.sizeBytes == v.sizeBytes && old.durationMs != 0L) {
                        v.withDuration(old.durationMs)
                    } else {
                        v
                    }
                }
            val error = errors.firstOrNull()?.let { "Error al leer la $it" }
            fun publish(loading: Boolean) {
                _state.value = buildState(fromAndroid + extra).copy(
                    loading = loading,
                    fromAndroid = fromAndroid.size,
                    fromScan = scanned.size,
                    error = error,
                )
            }

            suspend fun save() {
                val library = CachedLibrary(
                    videos = fromAndroid + extra,
                    scannedUris = extra.map { it.uri.toString() }.toSet(),
                    lastScanMs = if (scanNow) now else previous.lastScanMs,
                    scanKey = if (scanNow || !hasScanSources) scanKey else previous.scanKey,
                )
                cached = library
                cache.save(library)
            }

            // Los archivos nuevos se clasifican por duración: se leen por tandas mostrando el avance
            val missing = extra.filter { it.durationMs == 0L }
            publish(loading = missing.isNotEmpty())
            save()
            if (missing.isNotEmpty()) {
                val resolved = HashMap<String, Video>()
                missing.chunked(DURATION_BATCH).forEach { batch ->
                    readDurations(getApplication<Application>(), batch).forEach { resolved[it.uri.toString()] = it }
                    extra = extra.map { resolved[it.uri.toString()] ?: it }
                    publish(loading = resolved.size < missing.size)
                    save()
                }
            }
        }
    }

    override fun onCleared() {
        observer?.let { getApplication<Application>().contentResolver.unregisterContentObserver(it) }
    }

    private fun buildState(videos: List<Video>): LibraryState {
        val movies = videos.filter { it.kind is VideoKind.Movie }
            .sortedBy { it.kind.title.lowercase() }
        val reels = videos.filter { it.kind is VideoKind.Reel }.sortedByDescending { it.dateAddedSec }
        val others = videos.filter { it.kind is VideoKind.Other }

        val shows = videos.filter { it.kind is VideoKind.Episode }
            .groupBy { VideoClassifier.showKey((it.kind as VideoKind.Episode).show) }
            .map { (key, episodes) ->
                val name = episodes.groupingBy { (it.kind as VideoKind.Episode).show }
                    .eachCount().maxBy { it.value }.key
                val seasons = episodes
                    .sortedWith(
                        compareBy(
                            { (it.kind as VideoKind.Episode).season },
                            { (it.kind as VideoKind.Episode).episode },
                            { it.fileName.lowercase() },
                        )
                    )
                    .groupBy { (it.kind as VideoKind.Episode).season }
                Show(key, name, seasons)
            }
            .sortedBy { it.name.lowercase() }

        val folders = videos.groupBy { it.folderPath }
            .map { (path, list) -> Folder(path, list.first().folder, list.sortedBy { it.fileName.lowercase() }) }
            .sortedBy { it.name.lowercase() }

        return LibraryState(
            loading = false,
            loaded = true,
            videos = videos,
            movies = movies,
            shows = shows,
            reels = reels,
            others = others,
            folders = folders,
        )
    }
}

private const val RESCAN_AFTER_MS = 60 * 60 * 1000L
private const val DURATION_BATCH = 40

/** Texto del error con su tipo, para poder diagnosticarlo desde una captura. */
private fun Throwable.describe(): String {
    val root = generateSequence(this) { it.cause }.last()
    return listOfNotNull(javaClass.simpleName, root.takeIf { it !== this }?.javaClass?.simpleName, root.message)
        .joinToString(": ")
}
