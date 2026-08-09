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
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Mantiene lo snapshot della lista chat leggendo da TDLib, serve su richiesta
 * la cronologia delle conversazioni e scarica i media.
 */
class ChatRepository(
    private val td: TdClient,
    private val scope: CoroutineScope
) {

    /** Thread piu gli Asset binari che lo accompagnano. */
    data class ThreadPayload(
        val thread: ChatThread,
        val assets: Map<String, ByteArray>
    )

    private val users = ConcurrentHashMap<Long, String>()

    // Gli avatar cambiano di rado: scaricarli a ogni refresh sarebbe uno spreco.
    private val avatarCache = ConcurrentHashMap<String, ByteArray>()
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
                // Gli update arrivano a raffica: la lista serve una volta sola.
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

    private suspend fun emitAlert(message: TdApi.Message) {
        val chat = runCatching {
            td.send(TdApi.GetChat().apply { chatId = message.chatId })
        }.getOrNull() ?: return

        // Le chat silenziate restano silenziate anche sul polso.
        if ((chat.notificationSettings?.muteFor ?: 0) > 0) {
            Log.d(TAG, "avviso soppresso: ${chat.title} e silenziata")
            return
        }

        val sender = when (val s = message.senderId) {
            is TdApi.MessageSenderUser -> users[s.userId].orEmpty()
            else -> ""
        }

        Log.d(TAG, "nuovo messaggio in ${chat.title}")
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

        // Solo per le chat in cima: piu giu non si guardano comunque.
        summaries.take(MAX_AVATARS).forEach { summary ->
            val key = summary.avatar ?: return@forEach
            if (avatarCache.containsKey(key)) return@forEach
            avatarBytes(summary.chatId)?.let { avatarCache[key] = it }
        }

        revision += 1
        _chats.value = ChatList(summaries, revision)
        Log.d(TAG, "lista chat aggiornata: ${summaries.size} voci, rev $revision")
    }

    suspend fun loadThread(chatId: Long, limit: Int): ThreadPayload {
        val chat = td.send(TdApi.GetChat().apply { this.chatId = chatId })

        val rawMessages = fetchRaw(chatId, limit)
        val messages = rawMessages.map { MessageMapper.toMessage(it, users) }

        // Solo le foto piu recenti: ogni miniatura e un trasferimento Bluetooth.
        val assets = mutableMapOf<String, ByteArray>()
        rawMessages
            .filter { it.content is TdApi.MessagePhoto }
            .take(MAX_PHOTOS)
            .forEach { message ->
                photoThumb(message)?.let { assets[MessageMapper.photoKey(message.id)] = it }
            }

        revision += 1
        return ThreadPayload(
            thread = ChatThread(
                chatId = chatId,
                title = chat.title.ifBlank { "Senza nome" },
                // TDLib restituisce dal piu recente: sul polso li vogliamo cronologici.
                messages = messages.reversed(),
                revision = revision
            ),
            assets = assets
        )
    }

    /**
     * TDLib con fromMessageId = 0 restituisce solo l'ultimo messaggio:
     * per risalire la cronologia va richiamato a catena, passando ogni volta
     * l'id del piu vecchio ricevuto. Da qui il ciclo.
     */
    private suspend fun fetchRaw(chatId: Long, target: Int): List<TdApi.Message> {
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

        return collected
    }

    /**
     * Cerca fra le chat conosciute e fra i contatti in rubrica.
     *
     * I contatti senza conversazione aperta non hanno un chatId: glielo
     * creiamo al volo, che e esattamente quello che fa Telegram quando
     * scrivi a qualcuno per la prima volta.
     */
    suspend fun search(query: String): ChatList {
        val ids = mutableListOf<Long>()

        runCatching {
            td.send(
                TdApi.SearchChats().apply {
                    this.query = query
                    limit = 15
                }
            ).chatIds.toList()
        }.getOrNull()?.let { ids += it }

        runCatching {
            td.send(
                TdApi.SearchContacts().apply {
                    this.query = query
                    limit = 10
                }
            ).userIds.toList()
        }.getOrNull()?.forEach { userId ->
            runCatching {
                td.send(
                    TdApi.CreatePrivateChat().apply {
                        this.userId = userId
                        force = false
                    }
                ).id
            }.getOrNull()?.let { if (it !in ids) ids += it }
        }

        val summaries = ids.take(20).mapNotNull { id ->
            runCatching {
                ChatMapper.toSummary(td.send(TdApi.GetChat().apply { chatId = id }), users)
            }.getOrNull()
        }

        revision += 1
        Log.d(TAG, "ricerca '$query': ${summaries.size} risultati")
        return ChatList(summaries, revision)
    }

    /** Avatar gia scaricati, pronti per il Data Layer. */
    fun avatars(): Map<String, ByteArray> = avatarCache.toMap()

    private suspend fun avatarBytes(chatId: Long): ByteArray? {
        val chat = runCatching {
            td.send(TdApi.GetChat().apply { this.chatId = chatId })
        }.getOrNull() ?: return null
        val photo = chat.photo ?: return null
        val path = downloadFile(photo.small.id) ?: return null
        return MediaScaler.avatar(path)
    }

    /** Scarica un file TDLib e restituisce il percorso locale, o null. */
    private suspend fun downloadFile(fileId: Int): String? = runCatching {
        td.send(
            TdApi.DownloadFile().apply {
                this.fileId = fileId
                priority = 16
                offset = 0
                limit = 0
                synchronous = true
            }
        ).local?.takeIf { it.isDownloadingCompleted }?.path?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private suspend fun photoThumb(message: TdApi.Message): ByteArray? {
        val content = message.content as? TdApi.MessagePhoto ?: return null
        val sizes = content.photo.sizes.filterNotNull()
        if (sizes.isEmpty()) return null
        // La piu piccola che superi i 200 px, altrimenti la piu grande disponibile.
        val chosen = sizes.firstOrNull { it.width >= 200 || it.height >= 200 } ?: sizes.last()
        val path = downloadFile(chosen.photo.id) ?: return null
        return MediaScaler.thumbnail(path)?.bytes
    }

    /** Vocale, scaricato solo su richiesta esplicita dall'orologio. */
    suspend fun voiceBytes(targetChatId: Long, targetMessageId: Long): ByteArray? {
        val message = runCatching {
            td.send(
                TdApi.GetMessage().apply {
                    chatId = targetChatId
                    messageId = targetMessageId
                }
            )
        }.getOrNull() ?: return null

        val voice = (message.content as? TdApi.MessageVoiceNote)?.voiceNote ?: return null
        val path = downloadFile(voice.voice.id) ?: return null
        val file = File(path)
        // Oltre il mezzo mega il trasferimento su Bluetooth diventa penoso.
        if (!file.exists() || file.length() > 512 * 1024) return null
        return runCatching { file.readBytes() }.getOrNull()
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

    suspend fun setMuted(targetChatId: Long, muted: Boolean) {
        runCatching {
            td.send(
                TdApi.SetChatNotificationSettings().apply {
                    chatId = targetChatId
                    notificationSettings = TdApi.ChatNotificationSettings().apply {
                        useDefaultMuteFor = false
                        // TDLib vuole secondi: un anno equivale a "per sempre".
                        muteFor = if (muted) 365 * 24 * 3600 else 0
                    }
                }
            )
        }
        requestRefresh()
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
        private const val MAX_PHOTOS = 12
        private const val MAX_AVATARS = 20
    }
}
