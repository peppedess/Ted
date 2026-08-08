package it.peppedess.ted

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import it.peppedess.ted.bridge.WearBridge
import it.peppedess.ted.protocol.MessageAlert
import it.peppedess.ted.tdlib.Td
import it.peppedess.ted.ui.LoginScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PhoneRoot() } }
    }
}

@Composable
private fun PhoneRoot() {
    val context = LocalContext.current
    val client = remember { Td.get(context) }
    val scope = rememberCoroutineScope()
    val bridge = remember { WearBridge(context) }
    val stage by client.stage.collectAsState()
    val bridgeRunning by TdService.running.collectAsState()
    val alertsEnabled by Settings.alertsEnabled.collectAsState()

    LaunchedEffect(Unit) { Settings.load(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* l'esito non blocca nulla: senza notifica il servizio parte comunque */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold { inner ->
        LoginScreen(
            client = client,
            stage = stage,
            bridgeRunning = bridgeRunning,
            alertsEnabled = alertsEnabled,
            onAlertsChange = { Settings.setAlertsEnabled(context, it) },
            onTestAlert = {
                // Salta TDLib e l'interruttore: prova solo trasporto e notifica.
                scope.launch {
                    runCatching {
                        bridge.sendAlert(
                            MessageAlert(
                                chatId = 1L,
                                chatTitle = "Prova",
                                sender = "Ted",
                                preview = "Se leggi questo, il ponte funziona",
                                messageId = System.currentTimeMillis(),
                                date = System.currentTimeMillis() / 1000
                            )
                        )
                    }
                }
            },
            onReady = { TdService.start(context) },
            onStop = { TdService.stop(context) },
            modifier = Modifier.padding(inner)
        )
    }
}
