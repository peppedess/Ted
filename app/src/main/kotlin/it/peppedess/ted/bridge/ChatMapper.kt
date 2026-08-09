package it.peppedess.ted.bridge

import it.peppedess.ted.protocol.ChatSummary
import org.drinkless.tdlib.TdApi

/** Traduce le strutture TDLib nei modelli compatti di :protocol. */
object ChatMapper {

    fun avatarKey(chatId: Long) = "a$chatId"

    fun toSummary(chat: TdApi.Chat, users: Map<Long, String>): ChatSummary {
        val last = chat.lastMessage
        val isGroup = chat.type is TdApi.ChatTypeBasicGroup ||
            chat.type is TdApi.ChatTypeSupergroup

        val preview = buildString {
            if (last == null) return@buildString
            when {
                last.isOutgoing -> append("Tu: ")
                isGroup -> senderName(last.senderId, users)?.let { append(it).append(": ") }
            }
            append(describe(last.content))
        }

        return ChatSummary(
            chatId = chat.id,
            title = chat.title.ifBlank { "Senza nome" },
            preview = preview.take(120),
            date = last?.date?.toLong() ?: 0L,
            unread = chat.unreadCount,
            outgoing = last?.isOutgoing ?: false,
            muted = (chat.notificationSettings?.muteFor ?: 0) > 0,
            avatar = if (chat.photo != null) avatarKey(chat.id) else null
        )
    }

    private fun senderName(sender: TdApi.MessageSender, users: Map<Long, String>): String? =
        when (sender) {
            is TdApi.MessageSenderUser -> users[sender.userId]
            else -> null
        }

    fun describe(content: TdApi.MessageContent): String = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessagePhoto -> content.caption.text.ifBlank { "Foto" }
        is TdApi.MessageVideo -> content.caption.text.ifBlank { "Video" }
        is TdApi.MessageVoiceNote -> "Messaggio vocale"
        is TdApi.MessageVideoNote -> "Videomessaggio"
        is TdApi.MessageSticker -> content.sticker.emoji.ifBlank { "Sticker" }
        is TdApi.MessageAnimation -> content.caption.text.ifBlank { "GIF" }
        is TdApi.MessageDocument -> content.document.fileName.ifBlank { "File" }
        is TdApi.MessageAudio -> "Audio"
        is TdApi.MessageLocation -> "Posizione"
        is TdApi.MessageContact -> "Contatto"
        is TdApi.MessagePoll -> "Sondaggio"
        is TdApi.MessageCall -> "Chiamata"
        else -> "Messaggio"
    }
}
