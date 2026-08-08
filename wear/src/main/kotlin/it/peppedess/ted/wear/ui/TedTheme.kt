package it.peppedess.ted.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Tema dell'app sull'orologio.
 *
 * Wear M3 puo derivare lo schema colori dal quadrante attivo: se il sistema
 * lo espone lo usiamo, altrimenti restano i colori di default.
 */
@Composable
fun TedTheme(content: @Composable () -> Unit) {
    val scheme = dynamicColorScheme(LocalContext.current)
    if (scheme != null) {
        MaterialTheme(colorScheme = scheme, content = content)
    } else {
        MaterialTheme(content = content)
    }
}
