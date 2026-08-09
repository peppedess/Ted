package it.peppedess.ted.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.peppedess.ted.tdlib.TdClient
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    client: TdClient,
    stage: TdClient.Stage,
    onReady: () -> Unit,
    onStop: () -> Unit,
    bridgeRunning: Boolean,
    alertsEnabled: Boolean,
    onAlertsChange: (Boolean) -> Unit,
    onTestAlert: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            runCatching { block() }
                .onFailure { error = it.message }
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Ted", style = MaterialTheme.typography.headlineLarge)

        when (stage) {
            is TdClient.Stage.Starting -> {
                Text(
                    "Avvio di TDLib...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }

            is TdClient.Stage.WaitPhone -> PhoneStep(
                busy = busy,
                onSubmit = { phone -> submit { client.submitPhone(phone) } }
            )

            is TdClient.Stage.WaitCode -> CodeStep(
                phone = stage.phone,
                busy = busy,
                onSubmit = { code -> submit { client.submitCode(code) } }
            )

            is TdClient.Stage.WaitPassword -> PasswordStep(
                hint = stage.hint,
                busy = busy,
                onSubmit = { pw -> submit { client.submitPassword(pw) } }
            )

            is TdClient.Stage.Ready -> ReadyStep(
                bridgeRunning = bridgeRunning,
                alertsEnabled = alertsEnabled,
                onAlertsChange = onAlertsChange,
                onTestAlert = onTestAlert,
                onOpenSettings = onOpenSettings,
                onContinue = onReady,
                onStop = onStop,
                onLogout = { submit { client.logOut() } }
            )

            is TdClient.Stage.LoggedOut -> Text(
                "Sessione chiusa. Riavvia l'app per accedere di nuovo.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )

            is TdClient.Stage.Failed -> Text(
                stage.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun PhoneStep(busy: Boolean, onSubmit: (String) -> Unit) {
    var phone by remember { mutableStateOf("+39") }
    Text(
        "Inserisci il numero in formato internazionale",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Numero") },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onSubmit(phone) },
        enabled = !busy && phone.length > 5,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text("Continua")
    }
}

@Composable
private fun CodeStep(phone: String, busy: Boolean, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text(
        if (phone.isBlank()) "Codice inviato su Telegram" else "Codice inviato a $phone",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter { ch -> ch.isDigit() } },
        label = { Text("Codice") },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onSubmit(code) },
        enabled = !busy && code.length >= 4,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text("Verifica")
    }
}

@Composable
private fun PasswordStep(hint: String, busy: Boolean, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Text(
        if (hint.isBlank()) "Verifica in due passaggi attiva" else "Suggerimento: $hint",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { onSubmit(password) },
        enabled = !busy && password.isNotEmpty(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text("Accedi")
    }
}

@Composable
private fun ReadyStep(
    bridgeRunning: Boolean,
    alertsEnabled: Boolean,
    onAlertsChange: (Boolean) -> Unit,
    onTestAlert: () -> Unit,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    onLogout: () -> Unit
) {
    Text(
        "Connesso a Telegram",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
    Text(
        if (bridgeRunning) "Ponte attivo. Puoi chiudere l'app." else "Il ponte non e ancora avviato.",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp)
    )
    Button(
        onClick = if (bridgeRunning) onStop else onContinue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Text(if (bridgeRunning) "Ferma il ponte" else "Avvia il ponte")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Notifiche sull'orologio", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tiene il ponte sempre acceso: consuma piu batteria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = alertsEnabled, onCheckedChange = onAlertsChange)
    }

    TextButton(
        onClick = onOpenSettings,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text("Impostazioni")
    }

    TextButton(
        onClick = onTestAlert,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text("Prova notifica")
    }

    TextButton(
        onClick = onLogout,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text("Disconnetti")
    }
}
