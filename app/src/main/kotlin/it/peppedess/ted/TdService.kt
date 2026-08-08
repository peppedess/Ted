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
import it.peppedess.ted.tdlib.Td
import it.peppedess.ted.tdlib.TdClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Tiene vivo il processo mentre TDLib e connesso.
 *
 * Non e un servizio permanente: lo avviamo quando serve e lo fermiamo
 * quando l'orologio non ha piu bisogno del ponte.
 */
class TdService : LifecycleService() {

    override fun onCreate() {
        super.onCreate()
        _running.value = true
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Avvio..."))

        val td = Td.get(this)
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
            }
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
