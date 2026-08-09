package it.peppedess.ted.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF229ED9)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBFF),
    onPrimaryContainer = Color(0xFF00344C),
    secondary = Color(0xFF4E6373),
    surface = Color(0xFFFAFCFE),
    onSurface = Color(0xFF191C1E),
    onSurfaceVariant = Color(0xFF41484D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3EAEE8),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF1D5B7A),
    onPrimaryContainer = Color(0xFFCDEBFF),
    secondary = Color(0xFFB6CAD9),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E3E5),
    onSurfaceVariant = Color(0xFFC0C8CD)
)

@Composable
fun TedPhoneTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
