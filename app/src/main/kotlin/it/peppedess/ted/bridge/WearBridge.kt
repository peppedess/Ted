package it.peppedess.ted.bridge

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import it.peppedess.ted.protocol.BridgeState
import it.peppedess.ted.protocol.BridgeStatus
import it.peppedess.ted.protocol.ChatList
import it.peppedess.ted.protocol.ChatThread
import it.peppedess.ted.protocol.MessageAlert
import it.peppedess.ted.protocol.TedCodec
import it.peppedess.ted.protocol.TedPaths
import kotlinx.coroutines.tasks.await

/** Lato telefono del ponte: pubblica gli snapshot sul Data Layer. */
class WearBridge(context: Context) {

    private val app = context.applicationContext
    private val dataClient = Wearable.getDataClient(app)
    private val messageClient = Wearable.getMessageClient(app)
    private val capabilityClient = Wearable.getCapabilityClient(app)
    private val nodeClient = Wearable.getNodeClient(app)

    suspend fun publishChats(list: ChatList) {
        val trimmed = fitPayload(list)
        val payload = TedCodec.encode(trimmed)
        putItem(TedPaths.CHATS, payload, trimmed.revision)
        Log.d(TAG, "pubblicate ${trimmed.chats.size} chat, ${payload.size} byte")
    }

    suspend fun publishThread(thread: ChatThread, assets: Map<String, ByteArray> = emptyMap()) {
        var current = thread
        // Stesso tetto della lista chat: meglio meno messaggi che nessun invio.
        while (current.messages.size > 5 &&
            TedCodec.encode(current).size > TedPaths.MAX_PAYLOAD_BYTES
        ) {
            current = current.copy(messages = current.messages.drop(5))
        }
        val payload = TedCodec.encode(current)
        // Gli Asset viaggiano su un canale separato: non contano nei 100 KB.
        val request = PutDataMapRequest.create(TedPaths.thread(current.chatId)).apply {
            dataMap.putByteArray(TedPaths.KEY_PAYLOAD, payload)
            dataMap.putLong(TedPaths.KEY_REVISION, current.revision)
            assets.forEach { (key, bytes) ->
                dataMap.putAsset(key, Asset.createFromBytes(bytes))
            }
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
        Log.d(TAG, "pubblicati ${current.messages.size} messaggi, ${payload.size} byte, ${assets.size} asset")
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

    /**
     * Gli avvisi passano da MessageClient e non da DataClient: un messaggio
     * nuovo e un evento, non uno stato, e non deve sopravvivere allo spegnimento.
     */
    suspend fun sendAlert(alert: MessageAlert) {
        val payload = TedCodec.encode(alert)
        watchNodes().forEach { nodeId ->
            runCatching { messageClient.sendMessage(nodeId, TedPaths.ALERT, payload).await() }
        }
    }

    private suspend fun watchNodes(): List<String> {
        val byCapability = runCatching {
            capabilityClient
                .getCapability(TedPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                .await().nodes.map { it.id }
        }.getOrDefault(emptyList())
        if (byCapability.isNotEmpty()) return byCapability
        return runCatching { nodeClient.connectedNodes.await().map { it.id } }
            .getOrDefault(emptyList())
    }

    suspend fun publishVoice(chatId: Long, messageId: Long, bytes: ByteArray) {
        val request = PutDataMapRequest.create(TedPaths.voiceChannel(chatId, messageId)).apply {
            dataMap.putAsset(TedPaths.KEY_VOICE, Asset.createFromBytes(bytes))
            dataMap.putLong(TedPaths.KEY_REVISION, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
        Log.d(TAG, "vocale pubblicato: ${bytes.size} byte")
    }

    suspend fun publishVoice(chatId: Long, messageId: Long, bytes: ByteArray) {
        val request = PutDataMapRequest.create(TedPaths.voiceChannel(chatId, messageId)).apply {
            dataMap.putAsset(TedPaths.KEY_VOICE, Asset.createFromBytes(bytes))
            dataMap.putLong(TedPaths.KEY_REVISION, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request).await()
        Log.d(TAG, "vocale pubblicato: ${bytes.size} byte")
    }

    companion object {
        private const val TAG = "WearBridge"
    }
}
