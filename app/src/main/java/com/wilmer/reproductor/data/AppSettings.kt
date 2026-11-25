package com.wilmer.reproductor.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Paletas de colores de la app. */
enum class ThemeChoice(val label: String) {
    POWER("Power"),
    OCEANO("Océano"),
    ESMERALDA("Esmeralda"),
    ATARDECER("Atardecer"),
    AMOLED("Negro AMOLED"),
    CLARO("Claro"),
    FONDO("Colores de mi fondo"),
}

/** Qué hace el doble toque en los lados izquierdo y derecho del reproductor. */
enum class TapMode(val label: String) {
    SALTAR("Adelantar o atrasar 10 s"),
    CAMBIAR_VIDEO("Video siguiente o anterior"),
}

data class Settings(
    val theme: ThemeChoice = ThemeChoice.POWER,
    val gestureVolume: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureSeek: Boolean = true,
    val tapMode: TapMode = TapMode.SALTAR,
    val backgroundAudio: Boolean = false,
)

/** Ajustes de la app guardados en el teléfono; una sola instancia para toda la app. */
class AppSettings private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    val current: Settings get() = _state.value

    fun update(change: (Settings) -> Settings) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit()
            .putString("tema", next.theme.name)
            .putBoolean("gesto_volumen", next.gestureVolume)
            .putBoolean("gesto_brillo", next.gestureBrightness)
            .putBoolean("gesto_avance", next.gestureSeek)
            .putString("doble_toque", next.tapMode.name)
            .putBoolean("audio_fondo", next.backgroundAudio)
            .apply()
    }

    private fun read() = Settings(
        theme = ThemeChoice.entries.firstOrNull { it.name == prefs.getString("tema", null) } ?: ThemeChoice.POWER,
        gestureVolume = prefs.getBoolean("gesto_volumen", true),
        gestureBrightness = prefs.getBoolean("gesto_brillo", true),
        gestureSeek = prefs.getBoolean("gesto_avance", true),
        tapMode = TapMode.entries.firstOrNull { it.name == prefs.getString("doble_toque", null) } ?: TapMode.SALTAR,
        backgroundAudio = prefs.getBoolean("audio_fondo", false),
    )

    companion object {
        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
