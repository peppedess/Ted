package it.peppedess.ted.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
            // Orologio ricalcolato ogni 30 s: le etichette "3 min" devono invecchiare.
            val now by produceState(initialValue = System.currentTimeMillis() / 1000) {
                while (true) {
                    value = System.currentTimeMillis() / 1000
                    delay(30_000)
                }
            }
            val chats = FakeData.chatList(now).chats
            ChatListScreen(
                chats = chats,
                now = now,
                onChatClick = { /* apertura thread: prossimo passo */ }
            )
        }
    }
}
