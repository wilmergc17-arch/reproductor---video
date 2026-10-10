package com.wilmer.reproductor.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Un elemento de una lista: se guarda la dirección del video y su nombre por si se borra. */
data class PlaylistEntry(val uri: String, val title: String)

data class Playlist(val id: String, val name: String, val entries: List<PlaylistEntry>)

/** Listas de reproducción guardadas en el teléfono (SharedPreferences en JSON). */
class PlaylistStore(context: Context) {

    private val prefs = context.getSharedPreferences("listas", Context.MODE_PRIVATE)
    private val _playlists = MutableStateFlow(read())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun create(name: String, entries: List<PlaylistEntry> = emptyList()): Playlist {
        val playlist = Playlist(UUID.randomUUID().toString(), name.trim(), entries.distinctBy { it.uri })
        save(_playlists.value + playlist)
        return playlist
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name.trim()) }

    fun delete(id: String) = save(_playlists.value.filterNot { it.id == id })

    fun add(id: String, entries: List<PlaylistEntry>) = edit(id) { p ->
        val existing = p.entries.map { it.uri }.toSet()
        p.copy(entries = p.entries + entries.filter { it.uri !in existing }.distinctBy { it.uri })
    }

    fun remove(id: String, uri: String) = edit(id) { p ->
        p.copy(entries = p.entries.filterNot { it.uri == uri })
    }

    fun move(id: String, from: Int, to: Int) = edit(id) { p ->
        if (from !in p.entries.indices || to !in p.entries.indices) return@edit p
        val list = p.entries.toMutableList()
        list.add(to, list.removeAt(from))
        p.copy(entries = list)
    }

    private fun edit(id: String, change: (Playlist) -> Playlist) =
        save(_playlists.value.map { if (it.id == id) change(it) else it })

    private fun save(list: List<Playlist>) {
        _playlists.value = list
        val json = JSONArray()
        list.forEach { p ->
            json.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("entries", JSONArray().apply {
                    p.entries.forEach { e -> put(JSONObject().put("uri", e.uri).put("title", e.title)) }
                })
            })
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private fun read(): List<Playlist> = runCatching {
        val json = JSONArray(prefs.getString(KEY, "[]"))
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            val entries = o.getJSONArray("entries")
            Playlist(
                id = o.getString("id"),
                name = o.getString("name"),
                entries = List(entries.length()) { j ->
                    val e = entries.getJSONObject(j)
                    PlaylistEntry(e.getString("uri"), e.optString("title"))
                },
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val KEY = "listas"
    }
}

/** Recuerda dónde se quedó cada video para continuar desde ahí, como VLC. */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("posiciones", Context.MODE_PRIVATE)

    fun get(uri: String): Long = prefs.getLong(uri, 0L)

    fun save(uri: String, positionMs: Long, durationMs: Long) {
        // Muy al principio o casi al final no vale la pena recordarlo
        if (positionMs < 5_000 || (durationMs > 0 && positionMs > durationMs - 10_000)) {
            clear(uri)
        } else {
            prefs.edit().putLong(uri, positionMs).apply()
        }
    }

    fun clear(uri: String) = prefs.edit().remove(uri).apply()
}
