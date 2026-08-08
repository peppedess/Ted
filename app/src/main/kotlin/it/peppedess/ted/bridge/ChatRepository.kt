package it.peppedess.ted.bridge

import android.util.Log
import it.peppedess.ted.protocol.ChatList
import it.peppedess.ted.protocol.ChatMessage
import it.peppedess.ted.protocol.ChatThread
import it.peppedess.ted.protocol.MessageAlert
import it.peppedess.ted.protocol.TedPaths
import it.peppedess.ted.tdlib.TdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

    private val _alerts = MutableSharedFlow<MessageAlert>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val alerts: SharedFlow<MessageAlert> = _alerts

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

            is TdApi.UpdateNewMessage -> {
                val message = obj.message
                if (!message.isOutgoing) scope.launch { emitAlert(message) }
                requestRefresh()
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

        val messages = fetchHistory(chatId, limit)

        revision += 1
        return ChatThread(
            chatId = chatId,
            title = chat.title.ifBlank { "Senza nome" },
            // TDLib restituisce dal piu recente: sull'orologio li vogliamo cronologici.
            messages = messages.reversed(),
            revision = revision
        )
    }

    /**
     * TDLib con fromMessageId = 0 restituisce solo l'ultimo messaggio:
     * per risalire la cronologia va richiamato a catena, passando ogni volta
     * l'id del piu vecchio ricevuto. Da qui il ciclo.
     */
    private suspend fun fetchHistory(chatId: Long, target: Int): List<ChatMessage> {
        val collected = mutableListOf<TdApi.Message>()
        var fromId = 0L
        var attempts = 0

        while (collected.size < target && attempts < 12) {
            attempts++
            val batch = runCatching {
                td.send(
                    TdApi.GetChatHistory().apply {
                        this.chatId = chatId
                        fromMessageId = fromId
                        offset = 0
                        limit = (target - collected.size).coerceAtLeast(1)
                        onlyLocal = false
                    }
                ).messages.filterNotNull()
            }.getOrNull() ?: break

            if (batch.isEmpty()) {
                // Alla prima chiamata la cronologia puo non essere ancora
                // scesa dal server: diamo tempo e riproviamo una volta.
                if (collected.isEmpty() && attempts <= 2) {
                    delay(600)
                    continue
                }
                break
            }

            collected += batch
            fromId = collected.last().id
        }

        return collected.map { MessageMapper.toMessage(it, users) }
    }

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

    private suspend fun emitAlert(message: TdApi.Message) {
        val chat = runCatching {
            td.send(TdApi.GetChat().apply { chatId = message.chatId })
        }.getOrNull() ?: return

        // Le chat silenziate restano silenziate anche sul polso.
        if ((chat.notificationSettings?.muteFor ?: 0) > 0) return

        val sender = when (val s = message.senderId) {
            is TdApi.MessageSenderUser -> users[s.userId].orEmpty()
            else -> ""
        }

        _alerts.tryEmit(
            MessageAlert(
                chatId = message.chatId,
                chatTitle = chat.title.ifBlank { "Senza nome" },
                sender = sender,
                preview = ChatMapper.describe(message.content).take(160),
                messageId = message.id,
                date = message.date.toLong()
            )
        )
    }

    companion object {
        private const val TAG = "ChatRepository"
    }
}
