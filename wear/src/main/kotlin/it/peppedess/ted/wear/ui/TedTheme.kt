package it.peppedess.ted.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/**
 * Palette ispirata al tema scuro di Telegram.
 *
 * Fissa e non dinamica: prima prendeva i colori dal quadrante, con risultati
 * imprevedibili. Su un'app di messaggistica l'identita conta piu della coerenza
 * col watch face.
 */
private val TedColors = ColorScheme(
    primary = Color(0xFF3EAEE8),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF1D5B7A),
    onPrimaryContainer = Color(0xFFCDEBFF),

    secondary = Color(0xFF7FC7E8),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF17475F),
    onSecondaryContainer = Color(0xFFCDE9FA),

    background = Color(0xFF000000),
    onBackground = Color(0xFFE6EDF3),

    surfaceContainerLow = Color(0xFF13171B),
    surfaceContainer = Color(0xFF1A1F25),
    surfaceContainerHigh = Color(0xFF262C34),
    onSurface = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFF9FB0BD),

    outline = Color(0xFF5C6B77),
    outlineVariant = Color(0xFF323C45),

    error = Color(0xFFE8736B),
    onError = Color(0xFF3F0906)
)

@Composable
fun TedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TedColors, content = content)
}
