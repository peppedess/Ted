package it.peppedess.ted

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferenze del ponte.
 *
 * Una sola scelta, ma pesante: le notifiche richiedono TDLib connesso,
 * quindi accenderle significa rinunciare allo spegnimento per inattivita.
 */
object Settings {

    private const val PREFS = "ted_prefs"
    private const val KEY_ALERTS = "alerts_enabled"

    /** Minuti di inattivita dopo i quali il ponte si spegne da solo. */
    const val IDLE_MINUTES = 10L

    private val _alertsEnabled = MutableStateFlow(false)
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        _alertsEnabled.value = prefs(context).getBoolean(KEY_ALERTS, false)
        loaded = true
    }

    fun setAlertsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALERTS, enabled).apply()
        _alertsEnabled.value = enabled
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
