package com.wilmer.reproductor.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wilmer.reproductor.classify.VideoClassifier
import com.wilmer.reproductor.classify.VideoKind
import com.wilmer.reproductor.data.PlaylistStore
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
) {
    val byUri: Map<String, Video> by lazy { videos.associateBy { it.uri.toString() } }
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)
    val playlists = PlaylistStore(app)

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var observer: ContentObserver? = null
    private var reloadJob: Job? = null

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
        if (!_state.value.loaded) refresh()
    }

    fun refresh(debounce: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (debounce) delay(800)
            _state.update { it.copy(loading = true) }
            val videos = runCatching { repository.loadVideos() }.getOrDefault(emptyList())
            _state.value = buildState(videos)
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
