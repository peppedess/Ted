package it.peppedess.ted.wear.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.peppedess.ted.protocol.MessageAlert
import it.peppedess.ted.protocol.TedCodec
import it.peppedess.ted.protocol.TedPaths
import it.peppedess.ted.wear.MainActivity
import it.peppedess.ted.wear.R

/**
 * Riceve gli avvisi dal telefono e li trasforma in notifiche locali.
 *
 * Sono locali di proposito: se fossero bridged dal telefono arriverebbero
 * doppie, visto che anche Telegram ufficiale notifica sullo stesso account.
 */
class AlertListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != TedPaths.ALERT) return
        val alert = TedCodec.decodeOrNull<MessageAlert>(event.data) ?: return
        runCatching { show(alert) }
    }

    private fun show(alert: MessageAlert) {
        createChannel()

        val body = if (alert.sender.isBlank()) {
            alert.preview
        } else {
            "${alert.sender}: ${alert.preview}"
        }
        val id = alert.chatId.hashCode()

        val open = PendingIntent.getActivity(
            this,
            id,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(MainActivity.EXTRA_CHAT_ID, alert.chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Rispondere senza aprire l'app e il motivo per cui esiste un orologio.
        val replyIntent = Intent(this, ReplyReceiver::class.java).apply {
            putExtra(ReplyReceiver.EXTRA_CHAT_ID, alert.chatId)
        }
        val replyPending = PendingIntent.getBroadcast(
            this,
            id,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            it.peppedess.ted.wear.R.drawable.ic_ted_send,
            getString(R.string.reply),
            replyPending
        ).addRemoteInput(
            RemoteInput.Builder(ReplyReceiver.KEY_REPLY)
                .setLabel(getString(R.string.reply))
                .build()
        ).build()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(it.peppedess.ted.wear.R.drawable.ic_ted_notification)
            .setContentTitle(alert.chatTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(id, notification)
        }
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_channel_messages_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ted_messages"
    }
}
