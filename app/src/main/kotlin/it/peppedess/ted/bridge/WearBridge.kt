package it.peppedess.ted.bridge

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import it.peppedess.ted.protocol.BridgeState
import it.peppedess.ted.protocol.BridgeStatus
import it.peppedess.ted.protocol.ChatList
import it.peppedess.ted.protocol.TedCodec
import it.peppedess.ted.protocol.TedPaths
import kotlinx.coroutines.tasks.await

/** Lato telefono del ponte: pubblica gli snapshot sul Data Layer. */
class WearBridge(context: Context) {

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun publishChats(list: ChatList) {
        val trimmed = fitPayload(list)
        val payload = TedCodec.encode(trimmed)
        putItem(TedPaths.CHATS, payload, trimmed.revision)
        Log.d(TAG, "pubblicate ${trimmed.chats.size} chat, ${payload.size} byte")
    }

    suspend fun publishStatus(state: BridgeState, detail: String?, revision: Long) {
        val payload = TedCodec.encode(BridgeStatus(state, detail, revision))
        putItem(TedPaths.STATUS, payload, revision)
    }

    private suspend fun putItem(path: String, payload: ByteArray, revision: Long) {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putByteArray(TedPaths.KEY_PAYLOAD, payload)
            dataMap.putLong(TedPaths.KEY_REVISION, revision)
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
    }

    /**
     * Il DataItem ha un tetto duro. Se lo sforiamo Android rifiuta il put
     * in silenzio, quindi tagliamo le chat piu vecchie finche non ci sta.
     */
    private fun fitPayload(list: ChatList): ChatList {
        var current = list
        while (current.chats.size > 5 &&
            TedCodec.encode(current).size > TedPaths.MAX_PAYLOAD_BYTES
        ) {
            current = current.copy(chats = current.chats.dropLast(3))
        }
        return current
    }

    companion object {
        private const val TAG = "WearBridge"
    }
}
