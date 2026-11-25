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
    SALTAR("Adelantar o atrasar"),
    CAMBIAR_VIDEO("Video siguiente o anterior"),
}

data class Settings(
    val theme: ThemeChoice = ThemeChoice.POWER,
    val gestureVolume: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureSeek: Boolean = true,
    val tapMode: TapMode = TapMode.SALTAR,
    val backgroundAudio: Boolean = false,
    /** Al terminar un video pasa solo al siguiente de la lista. */
    val autoNext: Boolean = true,
    /** Continuar desde donde se dejó cada video. */
    val rememberPosition: Boolean = true,
    val defaultSpeed: Float = 1f,
    /** Segundos que salta el doble toque y los botones de adelantar o atrasar. */
    val seekStepSec: Int = 10,
    /** Nombre de AspectMode con el que abre el reproductor. */
    val defaultAspect: String = "AJUSTAR",
    /** Ventana flotante automática al salir de la app con un video sonando. */
    val autoPip: Boolean = true,
    /** Mantener presionado el video para verlo a 2x. */
    val holdForSpeed: Boolean = true,
    /** En Reels cada video se repite hasta que deslizas. */
    val loopReels: Boolean = true,
    /** Tamaño de los subtítulos (1 = normal). */
    val subtitleScale: Float = 1f,
)

val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
val SEEK_STEP_OPTIONS = listOf(5, 10, 15, 30)
val SUBTITLE_SCALES = listOf(0.8f to "Pequeño", 1f to "Normal", 1.3f to "Grande", 1.6f to "Muy grande")

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
            .putBoolean("siguiente_auto", next.autoNext)
            .putBoolean("recordar_posicion", next.rememberPosition)
            .putFloat("velocidad", next.defaultSpeed)
            .putInt("salto_seg", next.seekStepSec)
            .putString("aspecto", next.defaultAspect)
            .putBoolean("pip_auto", next.autoPip)
            .putBoolean("mantener_2x", next.holdForSpeed)
            .putBoolean("repetir_reels", next.loopReels)
            .putFloat("subtitulos_tamano", next.subtitleScale)
            .apply()
    }

    private fun read() = Settings(
        theme = ThemeChoice.entries.firstOrNull { it.name == prefs.getString("tema", null) } ?: ThemeChoice.POWER,
        gestureVolume = prefs.getBoolean("gesto_volumen", true),
        gestureBrightness = prefs.getBoolean("gesto_brillo", true),
        gestureSeek = prefs.getBoolean("gesto_avance", true),
        tapMode = TapMode.entries.firstOrNull { it.name == prefs.getString("doble_toque", null) } ?: TapMode.SALTAR,
        backgroundAudio = prefs.getBoolean("audio_fondo", false),
        autoNext = prefs.getBoolean("siguiente_auto", true),
        rememberPosition = prefs.getBoolean("recordar_posicion", true),
        defaultSpeed = prefs.getFloat("velocidad", 1f),
        seekStepSec = prefs.getInt("salto_seg", 10),
        defaultAspect = prefs.getString("aspecto", null) ?: "AJUSTAR",
        autoPip = prefs.getBoolean("pip_auto", true),
        holdForSpeed = prefs.getBoolean("mantener_2x", true),
        loopReels = prefs.getBoolean("repetir_reels", true),
        subtitleScale = prefs.getFloat("subtitulos_tamano", 1f),
    )

    companion object {
        @Volatile private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
