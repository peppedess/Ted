package it.peppedess.ted.wear.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import it.peppedess.ted.protocol.WatchCommand
import it.peppedess.ted.wear.data.BridgeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Invia la risposta dettata dalla notifica, senza aprire l'app. */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L)
        if (chatId == 0L) return

        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY)
            ?.toString()
            ?.trim()
        if (text.isNullOrEmpty()) return

        // goAsync tiene vivo il receiver oltre onReceive: l'invio passa
        // dal Bluetooth e non e istantaneo.
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                BridgeClient(app).send(WatchCommand.SendText(chatId, text))
            }
            runCatching {
                NotificationManagerCompat.from(app).cancel(chatId.hashCode())
            }
            pending.finish()
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "ted_chat_id"
        const val KEY_REPLY = "ted_notif_reply"
    }
}
