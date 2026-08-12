package it.peppedess.ted.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import it.peppedess.ted.wear.R

@Composable
fun RecordScreen(
    recording: Boolean,
    seconds: Int,
    maxSeconds: Int,
    error: String?,
    onToggle: () -> Unit
) {
    ScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Il punto rosso pulsa col passare dei secondi: un segnale di vita
            // piu leggibile di un contatore da solo.
            val pulse = if (recording && seconds % 2 == 0) 1f else 0.82f
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(if (recording) pulse else 1f)
                    .clip(CircleShape)
                    .background(
                        if (recording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
            )

            Text(
                text = if (recording) clock(seconds) else "Pronto",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 10.dp)
            )

            if (recording) {
                Text(
                    text = "max ${clock(maxSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onToggle,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    painter = painterResource(
                        if (recording) R.drawable.ic_ted_send else R.drawable.ic_ted_mic
                    ),
                    contentDescription = if (recording) "Invia" else "Registra"
                )
            }
        }
    }
}

private fun clock(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
