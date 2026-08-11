package it.peppedess.ted.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.peppedess.ted.protocol.Preferences
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    prefs: Preferences,
    onChange: ((Preferences) -> Preferences) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineSmall)

        SettingRow(
            title = "Notifiche sull'orologio",
            subtitle = "Tiene il ponte sempre acceso: consuma piu batteria."
        ) {
            Switch(
                checked = prefs.alerts,
                onCheckedChange = { value -> onChange { it.copy(alerts = value) } }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text("Aspetto sull'orologio", style = MaterialTheme.typography.titleMedium)

        SettingRow(
            title = "Colori dal quadrante",
            subtitle = "Spegnilo per usare la palette fissa di Ted."
        ) {
            Switch(
                checked = prefs.dynamicColors,
                onCheckedChange = { value -> onChange { it.copy(dynamicColors = value) } }
            )
        }

        Text(
            "Dimensione testo: ${(prefs.fontScale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Slider(
            value = prefs.fontScale,
            onValueChange = { value ->
                // Scatti del 5%: sul quadrante fra 97 e 98 per cento non cambia nulla.
                val snapped = (value * 20).roundToInt() / 20f
                onChange { it.copy(fontScale = snapped) }
            },
            valueRange = 0.8f..1.3f,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            "Spaziatura delle bolle",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Compatta", "Normale", "Ariosa").forEachIndexed { index, label ->
                FilterChip(
                    selected = prefs.density == index,
                    onClick = { onChange { it.copy(density = index) } },
                    label = { Text(label) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Fatto")
        }

        TextButton(
            onClick = { onChange { Preferences(revision = it.revision) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text("Ripristina i valori predefiniti")
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        control()
    }
}
