package it.peppedess.ted.bridge

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.peppedess.ted.TdService
import it.peppedess.ted.protocol.TedCodec
import it.peppedess.ted.protocol.TedPaths
import it.peppedess.ted.protocol.WatchCommand

/**
 * Riceve i comandi dall'orologio.
 *
 * Il sistema istanzia questo servizio anche a processo spento: e il modo
 * in cui l'orologio "sveglia" il telefono senza tenere TDLib acceso h24.
 */
class TedWearListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != TedPaths.COMMAND) return

        val command = TedCodec.decodeOrNull<WatchCommand>(event.data)
        if (command == null) {
            Log.w(TAG, "comando non decodificabile da ${event.sourceNodeId}")
            return
        }

        Log.d(TAG, "comando ricevuto: ${command::class.java.simpleName}")
        when (command) {
            is WatchCommand.Wake -> TdService.start(this)
            is WatchCommand.Sleep -> TdService.stop(this)
            else -> {
                TdService.start(this)
                TdService.deliver(command)
            }
        }
    }

    companion object {
        private const val TAG = "TedWearListener"
    }
}
