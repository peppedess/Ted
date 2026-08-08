package it.peppedess.ted.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import it.peppedess.ted.protocol.BridgeState
import it.peppedess.ted.protocol.WatchCommand
import it.peppedess.ted.wear.data.BridgeClient
import it.peppedess.ted.wear.ui.ChatListScreen
import it.peppedess.ted.wear.ui.ChatScreen
import it.peppedess.ted.wear.ui.TedTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearRoot() }
    }
}

@Composable
private fun WearRoot() {
    val context = LocalContext.current
    val bridge = remember { BridgeClient(context) }
    val navController = rememberSwipeDismissableNavController()

    val now by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis() / 1000
        }
    }

    TedTheme {
        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = "chats"
            ) {
                composable("chats") {
                    ChatsRoute(
                        bridge = bridge,
                        now = now,
                        onChatClick = { chatId -> navController.navigate("chat/$chatId") }
                    )
                }
                composable("chat/{chatId}") { entry ->
                    val chatId = entry.arguments?.getString("chatId")?.toLongOrNull()
                    if (chatId == null) {
                        Placeholder("Chat non valida", spinning = false)
                    } else {
                        ThreadRoute(bridge = bridge, chatId = chatId, now = now)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatsRoute(
    bridge: BridgeClient,
    now: Long,
    onChatClick: (Long) -> Unit
) {
    val chatsFlow = remember(bridge) { bridge.chatList() }
    val statusFlow = remember(bridge) { bridge.status() }
    val chatList by chatsFlow.collectAsState(initial = null)
    val status by statusFlow.collectAsState(initial = null)
    var wakeError by remember { mutableStateOf<String?>(null) }

    // All'apertura svegliamo il telefono: TDLib non gira h24, parte su richiesta.
    LaunchedEffect(Unit) {
        runCatching { bridge.send(WatchCommand.Wake) }
            .onFailure { wakeError = it.message }
    }

    val chats = chatList?.chats
    if (chats != null && chats.isNotEmpty()) {
        ChatListScreen(chats = chats, now = now, onChatClick = onChatClick)
    } else {
        Placeholder(
            message = placeholderText(
                hasList = chatList != null,
                bridgeState = status?.state,
                wakeError = wakeError
            ),
            spinning = wakeError == null && chatList == null
        )
    }
}

@Composable
private fun ThreadRoute(
    bridge: BridgeClient,
    chatId: Long,
    now: Long
) {
    val scope = rememberCoroutineScope()
    val threadFlow = remember(bridge, chatId) { bridge.thread(chatId) }
    val thread by threadFlow.collectAsState(initial = null)

    LaunchedEffect(chatId) {
        runCatching { bridge.send(WatchCommand.OpenChat(chatId)) }
    }

    // Marchiamo come letto solo quando i messaggi sono davvero arrivati.
    LaunchedEffect(thread?.revision) {
        val last = thread?.messages?.lastOrNull() ?: return@LaunchedEffect
        runCatching { bridge.send(WatchCommand.MarkRead(chatId, last.messageId)) }
    }

    val current = thread
    if (current == null) {
        Placeholder("Caricamento...", spinning = true)
    } else {
        ChatScreen(
            title = current.title,
            messages = current.messages,
            now = now,
            onSend = { text ->
                scope.launch {
                    runCatching { bridge.send(WatchCommand.SendText(chatId, text)) }
                }
            }
        )
    }
}

private fun placeholderText(
    hasList: Boolean,
    bridgeState: BridgeState?,
    wakeError: String?
): String = when {
    wakeError != null -> "Telefono non raggiungibile"
    bridgeState == BridgeState.AUTH_REQUIRED -> "Accedi a Telegram dal telefono"
    bridgeState == BridgeState.OFFLINE -> "Ponte spento sul telefono"
    bridgeState == BridgeState.ERROR -> "Errore sul telefono"
    hasList -> "Nessuna chat"
    else -> "Sincronizzazione..."
}

@Composable
private fun Placeholder(message: String, spinning: Boolean) {
    ScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (spinning) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
