package it.peppedess.ted.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Stato del ponte telefono <-> orologio. */
@Serializable
enum class BridgeState {
    @SerialName("offline") OFFLINE,
    @SerialName("connecting") CONNECTING,
    @SerialName("auth") AUTH_REQUIRED,
    @SerialName("ready") READY,
    @SerialName("error") ERROR
}

@Serializable
data class BridgeStatus(
    val state: BridgeState,
    val detail: String? = null,
    val revision: Long
)

/** Riga della lista chat. Deve restare compatta: il DataItem ha un tetto di 100 KB. */
@Serializable
data class ChatSummary(
    val chatId: Long,
    val title: String,
    val preview: String,
    val date: Long,
    val unread: Int = 0,
    val outgoing: Boolean = false,
    val muted: Boolean = false,
    /** Chiave dell'Asset avatar nel DataMap, null se assente. */
    val avatar: String? = null
)

@Serializable
data class ChatList(
    val chats: List<ChatSummary>,
    val revision: Long
)

@Serializable
sealed interface MessageContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : MessageContent

    @Serializable
    @SerialName("photo")
    data class Photo(
        val caption: String = "",
        val asset: String,
        val width: Int,
        val height: Int
    ) : MessageContent

    @Serializable
    @SerialName("voice")
    data class Voice(
        val seconds: Int,
        /** Ampiezze 0..31 gia decimate dal telefono, max 64 campioni. */
        val waveform: List<Int> = emptyList()
    ) : MessageContent

    @Serializable
    @SerialName("sticker")
    data class Sticker(val emoji: String) : MessageContent

    @Serializable
    @SerialName("other")
    data class Unsupported(val label: String) : MessageContent
}

@Serializable
data class ChatMessage(
    val messageId: Long,
    val sender: String,
    val outgoing: Boolean,
    val date: Long,
    val content: MessageContent
)

@Serializable
data class ChatThread(
    val chatId: Long,
    val title: String,
    val messages: List<ChatMessage>,
    val revision: Long
)

/** Comandi orologio -> telefono, via MessageClient (fire and forget). */
@Serializable
sealed interface WatchCommand {
    @Serializable
    @SerialName("wake")
    data object Wake : WatchCommand

    @Serializable
    @SerialName("open")
    data class OpenChat(val chatId: Long, val limit: Int = 30) : WatchCommand

    @Serializable
    @SerialName("send")
    data class SendText(
        val chatId: Long,
        val text: String,
        val replyTo: Long? = null
    ) : WatchCommand

    @Serializable
    @SerialName("read")
    data class MarkRead(val chatId: Long, val upTo: Long) : WatchCommand

    @Serializable
    @SerialName("voice")
    data class RequestVoice(val chatId: Long, val messageId: Long) : WatchCommand

    @Serializable
    @SerialName("mute")
    data class MuteChat(val chatId: Long, val muted: Boolean) : WatchCommand

    @Serializable
    @SerialName("search")
    data class SearchChats(val query: String) : WatchCommand

    @Serializable
    @SerialName("sleep")
    data object Sleep : WatchCommand
}

/** Avviso di messaggio nuovo, telefono -> orologio via MessageClient. */
@Serializable
data class MessageAlert(
    val chatId: Long,
    val chatTitle: String,
    val sender: String,
    val preview: String,
    val messageId: Long,
    val date: Long
)

/** Preferenze condivise: decise sul telefono, applicate sull'orologio. */
@Serializable
data class Preferences(
    val alerts: Boolean = false,
    /** Fattore di scala del testo, 0.8 - 1.3. */
    val fontScale: Float = 1f,
    /** 0 compatta, 1 normale, 2 ariosa. */
    val density: Int = 1,
    val dynamicColors: Boolean = false,
    val revision: Long = 0
)
