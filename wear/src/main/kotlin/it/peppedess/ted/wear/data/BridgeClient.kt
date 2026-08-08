package it.peppedess.ted.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import it.peppedess.ted.protocol.BridgeStatus
import it.peppedess.ted.protocol.ChatList
import it.peppedess.ted.protocol.TedCodec
import it.peppedess.ted.protocol.TedPaths
import it.peppedess.ted.protocol.WatchCommand
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Lato orologio del ponte.
 *
 * Legge gli snapshot dal DataClient e manda i comandi via MessageClient.
 * La divisione non e arbitraria: i DataItem sopravvivono alla disconnessione
 * e si risincronizzano da soli, i messaggi no ma sono immediati.
 */
class BridgeClient(context: Context) {

    private val app = context.applicationContext
    private val dataClient = Wearable.getDataClient(app)
    private val messageClient = Wearable.getMessageClient(app)
    private val capabilityClient = Wearable.getCapabilityClient(app)
    private val nodeClient = Wearable.getNodeClient(app)

    fun chatList(): Flow<ChatList> =
        itemFlow(TedPaths.CHATS) { TedCodec.decodeOrNull<ChatList>(it) }

    fun status(): Flow<BridgeStatus> =
        itemFlow(TedPaths.STATUS) { TedCodec.decodeOrNull<BridgeStatus>(it) }

    private fun <T> itemFlow(path: String, decode: (ByteArray) -> T?): Flow<T> = callbackFlow {
        val uri = Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .path(path)
            .build()

        val listener = DataClient.OnDataChangedListener { events ->
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != path) continue
                payloadOf(event.dataItem)?.let { bytes ->
                    decode(bytes)?.let { trySend(it) }
                }
            }
        }
        dataClient.addListener(listener)

        // Lettura iniziale: il DataItem puo essere gia sul dispositivo.
        runCatching {
            val buffer = dataClient.getDataItems(uri).await()
            try {
                buffer.firstOrNull()
                    ?.let { payloadOf(it) }
                    ?.let { decode(it) }
                    ?.let { trySend(it) }
            } finally {
                buffer.release()
            }
        }.onFailure { Log.w(TAG, "lettura iniziale di $path fallita", it) }

        awaitClose { dataClient.removeListener(listener) }
    }

    private fun payloadOf(item: DataItem): ByteArray? = runCatching {
        DataMapItem.fromDataItem(item).dataMap.getByteArray(TedPaths.KEY_PAYLOAD)
    }.getOrNull()

    suspend fun send(command: WatchCommand) {
        val payload = TedCodec.encode(command)
        val targets = phoneNodes()
        if (targets.isEmpty()) error("telefono non raggiungibile")
        targets.forEach { nodeId ->
            messageClient.sendMessage(nodeId, TedPaths.COMMAND, payload).await()
        }
        Log.d(TAG, "inviato ${command::class.java.simpleName} a ${targets.size} nodi")
    }

    /**
     * Prima la capability, che identifica il telefono con Ted installato.
     * Se non risponde ripieghiamo sui nodi connessi: capita che la capability
     * impieghi qualche secondo a propagarsi dopo un'installazione.
     */
    private suspend fun phoneNodes(): List<String> {
        val byCapability = runCatching {
            capabilityClient
                .getCapability(TedPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await()
                .nodes
                .map { it.id }
        }.getOrDefault(emptyList())

        if (byCapability.isNotEmpty()) return byCapability

        return runCatching {
            nodeClient.connectedNodes.await().map { it.id }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val TAG = "BridgeClient"
    }
}
