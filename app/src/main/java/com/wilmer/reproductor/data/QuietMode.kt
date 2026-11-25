package com.wilmer.reproductor.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Modo "No molestar" de Android mientras se ve un video: silencia notificaciones y llamadas
 * (como el botón No molestar del teléfono) y al terminar deja el modo que había antes.
 * Necesita que el usuario permita "Acceso a No molestar" para la app en los ajustes.
 */
class QuietMode(context: Context) {

    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val prefs = context.getSharedPreferences("no_molestar", Context.MODE_PRIVATE)

    /** El usuario quiere No molestar al reproducir (se recuerda para los próximos videos). */
    var wanted: Boolean
        get() = prefs.getBoolean(KEY_WANTED, false)
        set(value) = prefs.edit().putBoolean(KEY_WANTED, value).apply()

    val hasAccess: Boolean
        get() = notifications?.isNotificationPolicyAccessGranted == true

    /** Activa No molestar y guarda el modo anterior para restaurarlo. */
    fun start(): Boolean {
        val nm = notifications ?: return false
        if (!hasAccess) return false
        return runCatching {
            if (!prefs.contains(KEY_PREVIOUS)) {
                prefs.edit().putInt(KEY_PREVIOUS, nm.currentInterruptionFilter).commit()
            }
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }.isSuccess
    }

    /** Devuelve el teléfono al modo que tenía antes de activar No molestar desde la app. */
    fun stop() {
        val nm = notifications ?: return
        if (!prefs.contains(KEY_PREVIOUS)) return
        val previous = prefs.getInt(KEY_PREVIOUS, NotificationManager.INTERRUPTION_FILTER_ALL)
        prefs.edit().remove(KEY_PREVIOUS).apply()
        if (!hasAccess) return
        // Si el usuario cambió el modo a mano mientras tanto, no se toca
        if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) return
        val restore = if (previous == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            previous
        }
        runCatching { nm.setInterruptionFilter(restore) }
    }

    companion object {
        private const val KEY_WANTED = "activado"
        private const val KEY_PREVIOUS = "modo_anterior"

        /** Pantalla de Ajustes donde se da el permiso de "Acceso a No molestar". */
        fun accessSettingsIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }
}
