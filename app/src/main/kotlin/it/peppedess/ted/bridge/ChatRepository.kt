package it.peppedess.ted.bridge

import android.util.Log
import it.peppedess.ted.protocol.ChatList
import it.peppedess.ted.protocol.ChatMessage
import it.peppedess.ted.protocol.ChatThread
import it.peppedess.ted.protocol.TedPaths
import it.peppedess.ted.tdlib.TdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Mantiene lo snapshot della lista chat leggendo da TDLib.
 *
 * TDLib non offre una "lista pronta": va caricata e poi tenuta aggiornata
 * a colpi di update. Qui li accorpiamo con un debounce, perche all'avvio
 * ne arrivano centinaia in pochi secondi.
 */
class ChatRepository(
    private val td: TdClient,
    private val scope: CoroutineScope
) {

    private val users = ConcurrentHashMap<Long, String>()
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private val _chats = MutableStateFlow(ChatList(emptyList(), 0))
    val chats: StateFlow<ChatList> = _chats.asStateFlow()

    private var revision = 0L

    fun start() {
        scope.launch {
            td.updates.collect { obj -> onUpdate(obj) }
        }
        scope.launch {
            for (ignored in refreshRequests) {
                // Finestra di calma: gli update arrivano a raffica, la lista serve una volta sola.
                delay(1_200)
                while (refreshRequests.tryReceive().isSuccess) Unit
                runCatching { refresh() }
                    .onFailure { Log.w(TAG, "refresh fallito", it) }
            }
        }
    }

    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateUser -> {
                val u = obj.user
                users[u.id] = u.firstName.ifBlank { u.lastName }.ifBlank { "?" }
            }

            is TdApi.UpdateNewChat,
            is TdApi.UpdateChatLastMessage,
            is TdApi.UpdateChatReadInbox,
            is TdApi.UpdateChatTitle,
            is TdApi.UpdateChatPosition,
            is TdApi.UpdateChatNotificationSettings -> requestRefresh()

            else -> Unit
        }
    }

    suspend fun refresh() {
        // LoadChats risponde 404 quando non c'e altro da caricare: non e un errore.
        runCatching {
            td.send(
                TdApi.LoadChats().apply {
                    chatList = TdApi.ChatListMain()
                    limit = 40
                }
            )
        }

        val ids = td.send(
            TdApi.GetChats().apply {
                chatList = TdApi.ChatListMain()
                limit = TedPaths.CHAT_PAGE_SIZE
            }
        ).chatIds.toList()

        val summaries = ids.mapNotNull { id ->
            runCatching {
                val chat = td.send(TdApi.GetChat().apply { chatId = id })
                ChatMapper.toSummary(chat, users)
            }.getOrNull()
        }

        revision += 1
        _chats.value = ChatList(summaries, revision)
        Log.d(TAG, "lista chat aggiornata: ${summaries.size} voci, rev $revision")
    }

    /**
     * Carica la cronologia di una chat.
     *
     * Alla prima chiamata TDLib risponde spesso con pochi o zero messaggi,
     * perche deve ancora tirarli giu dal server: per questo riproviamo.
     */
    suspend fun loadThread(chatId: Long, limit: Int): ChatThread {
        val chat = td.send(TdApi.GetChat().apply { this.chatId = chatId })

        var messages = fetchHistory(chatId, limit)
        if (messages.isEmpty()) {
            delay(700)
            messages = fetchHistory(chatId, limit)
        }

        revision += 1
        return ChatThread(
            chatId = chatId,
            title = chat.title.ifBlank { "Senza nome" },
            // TDLib restituisce dal piu recente: sull'orologio li vogliamo cronologici.
            messages = messages.reversed(),
            revision = revision
        )
    }

    private suspend fun fetchHistory(chatId: Long, limit: Int): List<ChatMessage> =
        runCatching {
            td.send(
                TdApi.GetChatHistory().apply {
                    this.chatId = chatId
                    fromMessageId = 0
                    offset = 0
                    this.limit = limit
                    onlyLocal = false
                }
            ).messages.mapNotNull { msg ->
                msg?.let { MessageMapper.toMessage(it, users) }
            }
        }.getOrDefault(emptyList())

    suspend fun sendText(targetChatId: Long, body: String, replyToId: Long?) {
        val request = TdApi.SendMessage().apply {
            chatId = targetChatId
            inputMessageContent = TdApi.InputMessageText().apply {
                text = TdApi.FormattedText().apply {
                    this.text = body
                    entities = emptyArray()
                }
            }
            if (replyToId != null) {
                replyTo = TdApi.InputMessageReplyToMessage().apply { messageId = replyToId }
            }
        }
        td.send(request)
        Log.d(TAG, "messaggio inviato a $targetChatId")
    }

    suspend fun markRead(targetChatId: Long, upTo: Long) {
        runCatching {
            td.send(
                TdApi.ViewMessages().apply {
                    chatId = targetChatId
                    messageIds = longArrayOf(upTo)
                    forceRead = true
                }
            )
        }
    }

    companion object {
        private const val TAG = "ChatRepository"
    }
}
