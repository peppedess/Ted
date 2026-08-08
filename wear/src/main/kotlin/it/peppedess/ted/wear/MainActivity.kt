package it.peppedess.ted.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.wear.compose.material3.AppScaffold
import it.peppedess.ted.wear.data.FakeData
import it.peppedess.ted.wear.ui.ChatListScreen
import it.peppedess.ted.wear.ui.TedTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearRoot() }
    }
}

@Composable
private fun WearRoot() {
    TedTheme {
        AppScaffold {
            // La lista e stabile: ricrearla a ogni ricomposizione buttava via
            // il diffing e faceva ricomporre tutte le righe.
            val chats = remember { FakeData.chatList().chats }

            // Orologio a granularita di minuto: basta per le etichette "3m".
            val now by produceState(initialValue = System.currentTimeMillis() / 1000) {
                while (true) {
                    delay(60_000)
                    value = System.currentTimeMillis() / 1000
                }
            }

            ChatListScreen(
                chats = chats,
                now = now,
                onChatClick = { /* apertura thread: prossimo passo */ }
            )
        }
    }
}
