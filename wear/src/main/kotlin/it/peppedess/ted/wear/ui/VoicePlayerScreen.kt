package it.peppedess.ted.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import it.peppedess.ted.wear.R
import it.peppedess.ted.wear.data.VoicePlayer

@Composable
fun VoicePlayerScreen(
    title: String,
    state: VoicePlayer.State,
    volume: Int,
    volumeMax: Int,
    onTogglePlay: () -> Unit,
    onSeekBy: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    ScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            when {
                state.error != null -> Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )

                state.loading -> {
                    Text(
                        text = "Scarico dal telefono...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
                }

                else -> {
                    ProgressBar(
                        position = state.positionMs,
                        duration = state.durationMs,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    Text(
                        text = "${clock(state.positionMs)} / ${clock(state.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(onClick = { onSeekBy(-5000) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ted_prev),
                                contentDescription = "Indietro di 5 secondi"
                            )
                        }
                        Button(onClick = onTogglePlay) {
                            Icon(
                                painter = painterResource(
                                    if (state.playing) R.drawable.ic_ted_pause
                                    else R.drawable.ic_ted_play
                                ),
                                contentDescription = if (state.playing) "Pausa" else "Riproduci"
                            )
                        }
                        CompactButton(onClick = { onSeekBy(5000) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ted_next),
                                contentDescription = "Avanti di 5 secondi"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactButton(onClick = { onVolumeChange(volume - 1) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ted_vol_down),
                                contentDescription = "Abbassa il volume"
                            )
                        }
                        Text(
                            text = if (volumeMax > 0) "${volume * 100 / volumeMax}%" else "--",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompactButton(onClick = { onVolumeChange(volume + 1) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ted_vol_up),
                                contentDescription = "Alza il volume"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(
    position: Int,
    duration: Int,
    modifier: Modifier = Modifier
) {
    val fraction = if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun clock(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
