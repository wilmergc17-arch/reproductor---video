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
import com.wilmer.reproductor.data.FileScanner
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

    private var observer: ContentObserver? = null
    private var reloadJob: Job? = null

    init {
        val settings = scanStore.settings.value
        if (settings.fullScan || settings.folders.isNotEmpty()) refresh()
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

    fun refresh(debounce: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (debounce) delay(800)
            _state.update { it.copy(loading = true) }
            val errors = mutableListOf<String>()
            val fromAndroid = if (observer != null) {
                runCatching { repository.loadVideos() }
                    .onFailure { errors += "biblioteca de Android: ${it.describe()}" }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val settings = scanStore.settings.value
            val scanned = buildList {
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
            // Un mismo archivo puede salir por los dos caminos: se queda la versión de Android
            val seen = fromAndroid.map { it.fileName to it.sizeBytes }.toHashSet()
            val extra = scanned.distinctBy { it.uri.toString() }
                .filter { seen.add(it.fileName to it.sizeBytes) }
            _state.value = buildState(fromAndroid + extra).copy(
                fromAndroid = fromAndroid.size,
                fromScan = scanned.size,
                error = errors.firstOrNull()?.let { "Error al leer la $it" },
            )
        }
    }

    override fun onCleared() {
        observer?.let { getApplication<Application>().contentResolver.unregisterContentObserver(it) }
    }

    private fun buildState(videos: List<Video>): LibraryState {
        val movies = videos.filter { it.kind is VideoKind.Movie }
            .sortedBy { it.kind.title.lowercase() }
        val others = videos.filter { it.kind is VideoKind.Other }

        val shows = videos.filter { it.kind is VideoKind.Episode }
            .groupBy { VideoClassifier.showKey((it.kind as VideoKind.Episode).show) }
            .map { (key, episodes) ->
                val name = episodes.groupingBy { (it.kind as VideoKind.Episode).show }
                    .eachCount().maxBy { it.value }.key
                val seasons = episodes
                    .sortedWith(compareBy({ (it.kind as VideoKind.Episode).season }, { (it.kind as VideoKind.Episode).episode }))
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
            others = others,
            folders = folders,
        )
    }
}

/** Texto del error con su tipo, para poder diagnosticarlo desde una captura. */
private fun Throwable.describe(): String {
    val root = generateSequence(this) { it.cause }.last()
    return listOfNotNull(javaClass.simpleName, root.takeIf { it !== this }?.javaClass?.simpleName, root.message)
        .joinToString(": ")
}
