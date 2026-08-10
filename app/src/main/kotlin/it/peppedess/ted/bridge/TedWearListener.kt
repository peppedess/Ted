package it.peppedess.ted.bridge

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
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

    /**
     * Vocale registrato dall'orologio. Arriva come DataItem perche un Asset
     * non ha il tetto dei cento kilobyte di un messaggio.
     */
    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            val chatId = TedPaths.chatIdFromOutVoice(path) ?: continue

            val map = runCatching { DataMapItem.fromDataItem(event.dataItem).dataMap }
                .getOrNull() ?: continue
            val asset = map.getAsset(TedPaths.KEY_VOICE) ?: continue
            val seconds = map.getInt(TedPaths.KEY_DURATION, 0)

            Log.d(TAG, "vocale in arrivo dall'orologio per $chatId, ${seconds}s")
            TdService.start(this)
            TdService.deliverVoice(chatId, asset, seconds)

            // Senza questo il vocale verrebbe riproposto a ogni
            // risincronizzazione del Data Layer.
            runCatching {
                Wearable.getDataClient(this).deleteDataItems(event.dataItem.uri)
            }
        }
    }

    companion object {
        private const val TAG = "TedWearListener"
    }
}
