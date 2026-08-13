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
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.Wearable
import it.peppedess.ted.bridge.ChatRepository
import it.peppedess.ted.bridge.WearBridge
import it.peppedess.ted.protocol.BridgeState
import it.peppedess.ted.protocol.WatchCommand
import it.peppedess.ted.tdlib.Td
import it.peppedess.ted.tdlib.TdClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tiene vivo il processo mentre TDLib e connesso, e fa da collante
 * fra il repository delle chat e il ponte verso l'orologio.
 */
class TdService : LifecycleService() {

    private lateinit var td: TdClient
    private lateinit var repository: ChatRepository
    private lateinit var bridge: WearBridge

    @Volatile
    private var lastActivity = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        // Prima di tutto: il servizio puo partire a processo freddo dal
        // WearableListenerService, e senza questo leggerebbe i default.
        Settings.load(this)
        _running.value = true
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_starting)))

        td = Td.get(this)
        bridge = WearBridge(this)
        repository = ChatRepository(td, lifecycleScope)
        repository.start()

        lifecycleScope.launch {
            td.stage.collectLatest { stage ->
                val text = when (stage) {
                    is TdClient.Stage.Ready -> getString(R.string.service_connected)
                    is TdClient.Stage.Starting -> getString(R.string.service_starting)
                    is TdClient.Stage.Failed -> getString(R.string.service_error, stage.message)
                    is TdClient.Stage.LoggedOut -> getString(R.string.service_disconnected)
                    else -> getString(R.string.service_waiting_login)
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
                runCatching { bridge.publishChats(list, repository.avatars()) }
            }
        }

        // Ogni cambio di preferenze scende sull'orologio.
        lifecycleScope.launch {
            Settings.prefs.collectLatest { runCatching { bridge.publishPrefs(it) } }
        }

        // Avvisi verso l'orologio, solo se l'utente li ha accesi.
        lifecycleScope.launch {
            repository.alerts.collect { alert ->
                if (!Settings.prefs.value.alerts) {
                    android.util.Log.d(TAG, "avviso scartato: notifiche spente")
                    return@collect
                }
                android.util.Log.d(TAG, "avviso inoltrato: ${alert.chatTitle}")
                runCatching { bridge.sendAlert(alert) }
                    .onFailure { android.util.Log.w(TAG, "invio avviso fallito", it) }
            }
        }

        // Spegnimento per inattivita: senza, START_STICKY terrebbe TDLib
        // connesso per giorni anche senza mai guardare l'orologio.
        lifecycleScope.launch {
            while (true) {
                delay(60_000)
                if (Settings.prefs.value.alerts) continue
                val idleMinutes = (System.currentTimeMillis() - lastActivity) / 60_000
                if (idleMinutes >= Settings.IDLE_MINUTES) {
                    android.util.Log.d(TAG, "inattivo da $idleMinutes min, mi spengo")
                    stopSelf()
                    break
                }
            }
        }

        // Vocali dall'orologio: l'Asset va risolto in byte prima di TDLib.
        lifecycleScope.launch {
            for (job in voiceJobs) {
                lastActivity = System.currentTimeMillis()
                runCatching {
                    val stream = Wearable.getDataClient(this@TdService)
                        .getFdForAsset(job.asset).await().inputStream
                    val bytes = stream?.use { it.readBytes() }
                    if (bytes != null) {
                        repository.sendVoice(job.chatId, bytes, job.seconds)
                    }
                }.onFailure {
                    android.util.Log.w(TAG, "vocale dall'orologio non recuperato", it)
                }
            }
        }

        lifecycleScope.launch {
            for (command in commands) {
                runCatching { handle(command) }
                    .onFailure { android.util.Log.w(TAG, "comando fallito", it) }
            }
        }
    }

    private suspend fun handle(command: WatchCommand) {
        lastActivity = System.currentTimeMillis()
        when (command) {
            is WatchCommand.Wake -> repository.requestRefresh()

            is WatchCommand.OpenChat -> {
                val payload = repository.loadThread(command.chatId, command.limit)
                bridge.publishThread(payload.thread, payload.assets)
            }

            is WatchCommand.SendText -> {
                repository.sendText(command.chatId, command.text, command.replyTo)
                // Ricarichiamo subito: cosi il messaggio appena inviato
                // compare sull'orologio senza aspettare l'update di TDLib.
                val payload = repository.loadThread(command.chatId, 30)
                bridge.publishThread(payload.thread, payload.assets)
                repository.requestRefresh()
            }

            is WatchCommand.MarkRead -> repository.markRead(command.chatId, command.upTo)

            is WatchCommand.RequestVoice -> {
                repository.voiceBytes(command.chatId, command.messageId)?.let { bytes ->
                    bridge.publishVoice(command.chatId, command.messageId, bytes)
                }
            }

            is WatchCommand.SearchChats -> {
                bridge.publishSearch(repository.search(command.query))
            }

            is WatchCommand.MuteChat ->
                repository.setMuted(command.chatId, command.muted)

            is WatchCommand.Sleep -> stopSelf()
        }
    }

    override fun onDestroy() {
        _running.value = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lastActivity = System.currentTimeMillis()
        return START_NOT_STICKY
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_channel_desc)
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_ted_send)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

    companion object {
        private const val TAG = "TdService"

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

        private data class VoiceJob(val chatId: Long, val asset: Asset, val seconds: Int)

        private val voiceJobs = Channel<VoiceJob>(capacity = 8)

        fun deliverVoice(chatId: Long, asset: Asset, seconds: Int) {
            voiceJobs.trySend(VoiceJob(chatId, asset, seconds))
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
