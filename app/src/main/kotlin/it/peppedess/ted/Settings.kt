package it.peppedess.ted

import android.content.Context
import it.peppedess.ted.protocol.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferenze dell'app, persistite sul telefono e propagate all'orologio.
 *
 * Vivono qui e non sul watch perche il telefono ha una tastiera vera:
 * l'orologio le riceve e basta.
 */
object Settings {

    private const val STORE = "ted_prefs"
    private const val KEY_ALERTS = "alerts"
    private const val KEY_FONT = "font_scale"
    private const val KEY_DENSITY = "density"
    private const val KEY_DYNAMIC = "dynamic_colors"
    private const val KEY_REVISION = "revision"

    /** Minuti di inattivita dopo i quali il ponte si spegne da solo. */
    const val IDLE_MINUTES = 10L

    private val _prefs = MutableStateFlow(Preferences())
    val prefs: StateFlow<Preferences> = _prefs.asStateFlow()

    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val store = store(context)
        _prefs.value = Preferences(
            alerts = store.getBoolean(KEY_ALERTS, false),
            fontScale = store.getFloat(KEY_FONT, 1f),
            density = store.getInt(KEY_DENSITY, 1),
            dynamicColors = store.getBoolean(KEY_DYNAMIC, false),
            revision = store.getLong(KEY_REVISION, 0L)
        )
        loaded = true
    }

    fun update(context: Context, transform: (Preferences) -> Preferences) {
        val updated = transform(_prefs.value).let { it.copy(revision = it.revision + 1) }
        store(context).edit()
            .putBoolean(KEY_ALERTS, updated.alerts)
            .putFloat(KEY_FONT, updated.fontScale)
            .putInt(KEY_DENSITY, updated.density)
            .putBoolean(KEY_DYNAMIC, updated.dynamicColors)
            .putLong(KEY_REVISION, updated.revision)
            .apply()
        _prefs.value = updated
    }

    private fun store(context: Context) =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
}
