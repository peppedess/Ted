package it.peppedess.ted.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
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
import it.peppedess.ted.protocol.Preferences
import it.peppedess.ted.protocol.WatchCommand
import it.peppedess.ted.wear.data.BridgeClient
import it.peppedess.ted.wear.data.VoicePlayer
import it.peppedess.ted.wear.data.VoiceRecorder
import it.peppedess.ted.wear.ui.ChatListScreen
import it.peppedess.ted.wear.ui.ChatListSkeleton
import it.peppedess.ted.wear.ui.ChatScreen
import it.peppedess.ted.wear.ui.RecordScreen
import it.peppedess.ted.wear.ui.SearchScreen
import it.peppedess.ted.wear.ui.VoicePlayerScreen
import it.peppedess.ted.wear.ui.TedTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = intent?.getLongExtra(EXTRA_CHAT_ID, 0L)?.takeIf { it != 0L }
        setContent { WearRoot(initialChatId = initial) }
    }

    companion object {
        const val EXTRA_CHAT_ID = "ted_chat_id"
    }
}

@Composable
private fun WearRoot(initialChatId: Long? = null) {
    val context = LocalContext.current
    val bridge = remember { BridgeClient(context) }
    val navController = rememberSwipeDismissableNavController()

    val prefsFlow = remember(bridge) { bridge.prefs() }
    val prefs by prefsFlow.collectAsState(initial = Preferences())

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Aperta da una notifica: saltiamo dritti nella conversazione.
    LaunchedEffect(initialChatId) {
        if (initialChatId != null) navController.navigate("chat/$initialChatId")
    }

    val now by produceState(initialValue = System.currentTimeMillis() / 1000) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis() / 1000
        }
    }

    TedTheme(
        dynamicColors = prefs.dynamicColors,
        fontScale = prefs.fontScale,
        density = prefs.density
    ) {
        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = "chats"
            ) {
                composable("chats") {
                    ChatsRoute(
                        bridge = bridge,
                        now = now,
                        onChatClick = { chatId -> navController.navigate("chat/$chatId") },
                        onNewChat = { navController.navigate("search") }
                    )
                }
                composable("record/{chatId}") { entry ->
                    val cid = entry.arguments?.getString("chatId")?.toLongOrNull()
                    if (cid == null) {
                        Placeholder("Chat non valida", spinning = false)
                    } else {
                        RecordRoute(
                            bridge = bridge,
                            chatId = cid,
                            onDone = { navController.popBackStack() }
                        )
                    }
                }
                composable("search") {
                    SearchRoute(
                        bridge = bridge,
                        onSelect = { chatId ->
                            navController.navigate("chat/$chatId") {
                                popUpTo("chats")
                            }
                        }
                    )
                }
                composable("voice/{chatId}/{messageId}") { entry ->
                    val cid = entry.arguments?.getString("chatId")?.toLongOrNull()
                    val mid = entry.arguments?.getString("messageId")?.toLongOrNull()
                    if (cid == null || mid == null) {
                        Placeholder("Vocale non valido", spinning = false)
                    } else {
                        VoiceRoute(bridge = bridge, chatId = cid, messageId = mid)
                    }
                }
                composable("chat/{chatId}") { entry ->
                    val chatId = entry.arguments?.getString("chatId")?.toLongOrNull()
                    if (chatId == null) {
                        Placeholder("Chat non valida", spinning = false)
                    } else {
                        ThreadRoute(
                            bridge = bridge,
                            chatId = chatId,
                            now = now,
                            onOpenVoice = { messageId ->
                                navController.navigate("voice/$chatId/$messageId")
                            },
                            onRecord = { navController.navigate("record/$chatId") }
                        )
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
    onChatClick: (Long) -> Unit,
    onNewChat: () -> Unit
) {
    val scope = rememberCoroutineScope()
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

    val chats = chatList?.list?.chats
    if (chats != null && chats.isNotEmpty()) {
        val assets = chatList?.assets.orEmpty()
        ChatListScreen(
            chats = chats,
            now = now,
            loadAvatar = { key ->
                assets[key]
                    ?.let { bridge.loadAsset(it) }
                    ?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
            },
            onChatClick = onChatClick,
            onNewChat = onNewChat
        )
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
    now: Long,
    onOpenVoice: (Long) -> Unit,
    onRecord: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val threadFlow = remember(bridge, chatId) { bridge.thread(chatId) }
    val thread by threadFlow.collectAsState(initial = null)

    // Cresce a scatti quando l'utente chiede altri messaggi. Il tetto e dettato
    // dai 100 KB del DataItem, non da una scelta di comodo.
    var limit by remember(chatId) { mutableIntStateOf(30) }

    // Una volta risaliti nella cronologia smettiamo di riportare l'utente in fondo.
    var browsingBack by remember(chatId) { mutableStateOf(false) }

    LaunchedEffect(chatId, limit) {
        runCatching { bridge.send(WatchCommand.OpenChat(chatId, limit)) }
    }

    // Marchiamo come letto solo quando i messaggi sono davvero arrivati.
    LaunchedEffect(thread?.thread?.revision) {
        val last = thread?.thread?.messages?.lastOrNull() ?: return@LaunchedEffect
        runCatching { bridge.send(WatchCommand.MarkRead(chatId, last.messageId)) }
    }

    val current = thread
    if (current == null) {
        Placeholder("Caricamento...", spinning = true)
    } else {
        val assets = current.assets
        ChatScreen(
            title = current.thread.title,
            messages = current.thread.messages,
            now = now,
            loadImage = { key ->
                assets[key]
                    ?.let { bridge.loadAsset(it) }
                    ?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
            },
            onPlayVoice = { messageId -> onOpenVoice(messageId) },
            playingId = null,
            canLoadMore = current.thread.messages.size >= limit && limit < MAX_MESSAGES,
            onLoadMore = {
                limit = (limit + 40).coerceAtMost(MAX_MESSAGES)
                browsingBack = true
            },
            anchorKey = if (browsingBack) 0L else current.thread.revision,
            onRecord = onRecord,
            onSend = { text ->
                scope.launch {
                    runCatching { bridge.send(WatchCommand.SendText(chatId, text)) }
                }
            }
        )
    }
}

/** Oltre questa soglia il thread non entra piu in un singolo DataItem. */
private const val MAX_MESSAGES = 200

@Composable
private fun SearchRoute(
    bridge: BridgeClient,
    onSelect: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val resultsFlow = remember(bridge) { bridge.search() }
    val results by resultsFlow.collectAsState(initial = null)

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    // La ricerca e finita quando arriva una revisione piu recente di quella
    // che avevamo prima di chiedere.
    val baseline = remember { results?.revision ?: 0L }
    LaunchedEffect(results?.revision) {
        if ((results?.revision ?: 0L) > baseline) searching = false
    }

    SearchScreen(
        query = query,
        results = results?.chats.orEmpty(),
        searching = searching,
        onQuery = { text ->
            query = text
            searching = true
            scope.launch {
                runCatching { bridge.send(WatchCommand.SearchChats(text)) }
                    .onFailure { searching = false }
            }
        },
        onSelect = onSelect
    )
}

@Composable
private fun RecordRoute(
    bridge: BridgeClient,
    chatId: Long,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recording by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recording = VoiceRecorder.start(context)
            if (!recording) error = "Microfono non disponibile"
        } else {
            error = "Permesso microfono negato"
        }
    }

    // Contatore vivo mentre si registra.
    val seconds by produceState(initialValue = 0, key1 = recording) {
        while (recording) {
            value = VoiceRecorder.elapsedSeconds()
            delay(250)
        }
    }

    // Il registratore si ferma da solo al tetto: seguiamolo.
    LaunchedEffect(seconds) {
        if (recording && seconds >= VoiceRecorder.MAX_SECONDS) {
            recording = false
            VoiceRecorder.finish()?.let { result ->
                runCatching { bridge.sendVoice(chatId, result.bytes, result.seconds) }
            }
            onDone()
        }
    }

    DisposableEffect(Unit) {
        onDispose { VoiceRecorder.stop() }
    }

    RecordScreen(
        recording = recording,
        seconds = seconds,
        maxSeconds = VoiceRecorder.MAX_SECONDS,
        error = error,
        onToggle = {
            if (recording) {
                recording = false
                val result = VoiceRecorder.finish()
                if (result == null) {
                    error = "Registrazione troppo breve"
                } else {
                    scope.launch {
                        runCatching { bridge.sendVoice(chatId, result.bytes, result.seconds) }
                            .onFailure { error = it.message }
                        onDone()
                    }
                }
            } else {
                error = null
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    recording = VoiceRecorder.start(context)
                    if (!recording) error = "Microfono non disponibile"
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    )
}

@Composable
private fun VoiceRoute(
    bridge: BridgeClient,
    chatId: Long,
    messageId: Long
) {
    val context = LocalContext.current
    val state by VoicePlayer.state.collectAsState()

    // Il volume di sistema puo cambiare da fuori: lo rileggiamo di continuo.
    // Legato al ciclo di vita: a schermo spento non ha senso interrogare
    // AudioManager due volte al secondo.
    val lifecycleOwner = LocalLifecycleOwner.current
    val volume by produceState(initialValue = VoicePlayer.volume(context), key1 = Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = VoicePlayer.volume(context)
                delay(1_000)
            }
        }
    }
    val volumeMax = remember { VoicePlayer.volumeMax(context) }

    LaunchedEffect(chatId, messageId) {
        VoicePlayer.markLoading()
        runCatching { bridge.send(WatchCommand.RequestVoice(chatId, messageId)) }
        val asset = withTimeoutOrNull(25_000) { bridge.voice(chatId, messageId).first() }
        val bytes = asset?.let { bridge.loadAsset(it) }
        if (bytes != null) VoicePlayer.load(context, bytes) else VoicePlayer.stop()
    }

    DisposableEffect(Unit) {
        onDispose { VoicePlayer.stop() }
    }

    VoicePlayerScreen(
        title = "Messaggio vocale",
        state = state,
        volume = volume,
        volumeMax = volumeMax,
        onTogglePlay = { VoicePlayer.togglePlayPause() },
        onSeekBy = { VoicePlayer.seekBy(it) },
        onVolumeChange = { VoicePlayer.setVolume(context, it) }
    )
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
                // Uno scheletro che luccica comunica "sto arrivando" meglio
                // di una rotellina, perche anticipa la forma del contenuto.
                ChatListSkeleton(modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
