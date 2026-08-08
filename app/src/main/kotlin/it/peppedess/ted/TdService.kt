package it.peppedess.ted

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import it.peppedess.ted.bridge.ChatRepository
import it.peppedess.ted.bridge.WearBridge
import it.peppedess.ted.protocol.BridgeState
import it.peppedess.ted.protocol.WatchCommand
import it.peppedess.ted.tdlib.Td
import it.peppedess.ted.tdlib.TdClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tiene vivo il processo mentre TDLib e connesso, e fa da collante
 * fra il repository delle chat e il ponte verso l'orologio.
 */
class TdService : LifecycleService() {

    private lateinit var td: TdClient
    private lateinit var repository: ChatRepository
    private lateinit var bridge: WearBridge

    override fun onCreate() {
        super.onCreate()
        _running.value = true
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Avvio..."))

        td = Td.get(this)
        bridge = WearBridge(this)
        repository = ChatRepository(td, lifecycleScope)
        repository.start()

        lifecycleScope.launch {
            td.stage.collectLatest { stage ->
                val text = when (stage) {
                    is TdClient.Stage.Ready -> "Connesso"
                    is TdClient.Stage.Starting -> "Avvio..."
                    is TdClient.Stage.Failed -> "Errore: ${stage.message}"
                    is TdClient.Stage.LoggedOut -> "Disconnesso"
                    else -> "In attesa di accesso"
                }
                notificationManager().notify(NOTIFICATION_ID, buildNotification(text))

                val bridgeState = when (stage) {
                    is TdClient.Stage.Ready -> BridgeState.READY
                    is TdClient.Stage.Starting -> BridgeState.CONNECTING
                    is TdClient.Stage.Failed -> BridgeState.ERROR
                    is TdClient.Stage.LoggedOut -> BridgeState.OFFLINE
                    else -> BridgeState.AUTH_REQUIRED
                }
                runCatching {
                    bridge.publishStatus(bridgeState, text, System.currentTimeMillis())
                }

                if (stage is TdClient.Stage.Ready) repository.requestRefresh()
            }
        }

        lifecycleScope.launch {
            repository.chats.collectLatest { list ->
                if (list.chats.isEmpty()) return@collectLatest
                runCatching { bridge.publishChats(list) }
            }
        }

        lifecycleScope.launch {
            for (command in commands) {
                runCatching { handle(command) }
                    .onFailure { android.util.Log.w("TdService", "comando fallito", it) }
            }
        }
    }

    private suspend fun handle(command: WatchCommand) {
        when (command) {
            is WatchCommand.Wake -> repository.requestRefresh()

            is WatchCommand.OpenChat -> {
                val thread = repository.loadThread(command.chatId, command.limit)
                bridge.publishThread(thread)
            }

            is WatchCommand.SendText -> {
                repository.sendText(command.chatId, command.text, command.replyTo)
                // Ricarichiamo subito: cosi il messaggio appena inviato
                // compare sull'orologio senza aspettare l'update di TDLib.
                bridge.publishThread(repository.loadThread(command.chatId, 30))
                repository.requestRefresh()
            }

            is WatchCommand.MarkRead -> repository.markRead(command.chatId, command.upTo)

            is WatchCommand.RequestVoice -> Unit // riproduzione vocali: fase successiva

            is WatchCommand.Sleep -> stopSelf()
        }
    }

    override fun onDestroy() {
        _running.value = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ponte Telegram",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene attiva la connessione verso l'orologio"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ted")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

    companion object {
        private val _running = MutableStateFlow(false)

        /** Vero mentre il servizio e vivo. Serve alla UI per non mentire all'utente. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /**
         * Buca delle lettere fra il WearableListenerService e il servizio:
         * il primo puo essere istanziato prima che il secondo sia pronto.
         */
        private val commands = Channel<WatchCommand>(capacity = 32)

        fun deliver(command: WatchCommand) {
            commands.trySend(command)
        }

        // Canale nuovo: l'importanza di uno gia creato non si puo alzare.
        private const val CHANNEL_ID = "ted_bridge_v2"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, TdService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TdService::class.java))
        }
    }
}
