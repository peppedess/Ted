package it.peppedess.ted

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import it.peppedess.ted.bridge.WearBridge
import it.peppedess.ted.protocol.MessageAlert
import it.peppedess.ted.tdlib.Td
import it.peppedess.ted.ui.LoginScreen
import it.peppedess.ted.ui.SettingsScreen
import it.peppedess.ted.ui.TedPhoneTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TedPhoneTheme(dark = isSystemInDarkTheme()) { PhoneRoot() }
        }
    }
}

@Composable
private fun PhoneRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { Td.get(context) }
    val bridge = remember { WearBridge(context) }

    val stage by client.stage.collectAsState()
    val bridgeRunning by TdService.running.collectAsState()
    val prefs by Settings.prefs.collectAsState()

    var showSettings by remember { mutableStateOf(false) }

    // Risolte qui: dentro la lambda del click non c'e piu il contesto composable.
    val appName = stringResource(R.string.app_name)
    val testTitle = stringResource(R.string.test_title)
    val testBody = stringResource(R.string.test_body)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* l'esito non blocca nulla: senza notifica il servizio parte comunque */ }

    LaunchedEffect(Unit) {
        Settings.load(context)
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
        if (showSettings) {
            SettingsScreen(
                prefs = prefs,
                onChange = { transform ->
                    Settings.update(context, transform)
                    scope.launch {
                        runCatching { bridge.publishPrefs(Settings.prefs.value) }
                    }
                },
                onBack = { showSettings = false },
                modifier = Modifier.padding(inner)
            )
        } else {
            LoginScreen(
                client = client,
                stage = stage,
                bridgeRunning = bridgeRunning,
                onReady = { TdService.start(context) },
                onStop = { TdService.stop(context) },
                onOpenSettings = { showSettings = true },
                onTestAlert = {
                    // Salta TDLib e l'interruttore: prova solo trasporto e notifica.
                    scope.launch {
                        runCatching {
                            bridge.sendAlert(
                                MessageAlert(
                                    chatId = 1L,
                                    chatTitle = testTitle,
                                    sender = appName,
                                    preview = testBody,
                                    messageId = System.currentTimeMillis(),
                                    date = System.currentTimeMillis() / 1000
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.padding(inner)
            )
        }
    }
}
