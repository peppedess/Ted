package it.peppedess.ted.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * Palette ispirata al tema scuro di Telegram.
 *
 * E il default perche su un'app di messaggistica l'identita conta piu della
 * coerenza col quadrante. Chi preferisce il contrario lo accende dalle
 * impostazioni sul telefono.
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

/** Spaziature governate dalla preferenza "densita". */
data class TedSpacing(
    val listGap: Dp,
    val bubbleGap: Dp,
    val bubblePadding: Dp
)

val LocalTedSpacing = staticCompositionLocalOf {
    TedSpacing(listGap = 4.dp, bubbleGap = 6.dp, bubblePadding = 6.dp)
}

private fun spacingFor(density: Int): TedSpacing = when (density) {
    0 -> TedSpacing(listGap = 2.dp, bubbleGap = 3.dp, bubblePadding = 4.dp)
    2 -> TedSpacing(listGap = 8.dp, bubbleGap = 10.dp, bubblePadding = 10.dp)
    else -> TedSpacing(listGap = 4.dp, bubbleGap = 6.dp, bubblePadding = 6.dp)
}

@Composable
fun TedTheme(
    dynamicColors: Boolean = false,
    fontScale: Float = 1f,
    density: Int = 1,
    content: @Composable () -> Unit
) {
    val scheme = if (dynamicColors) {
        dynamicColorScheme(LocalContext.current) ?: TedColors
    } else {
        TedColors
    }

    val current = LocalDensity.current

    CompositionLocalProvider(
        // Scalare il fontScale della Density e l'unico modo per ingrandire
        // tutto il testo senza toccare ogni singolo stile.
        LocalDensity provides Density(
            density = current.density,
            fontScale = current.fontScale * fontScale.coerceIn(0.7f, 1.5f)
        ),
        LocalTedSpacing provides spacingFor(density)
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
