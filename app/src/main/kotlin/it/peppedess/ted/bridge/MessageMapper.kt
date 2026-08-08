package it.peppedess.ted.bridge

import it.peppedess.ted.protocol.ChatMessage
import it.peppedess.ted.protocol.MessageContent
import org.drinkless.tdlib.TdApi

/** Traduce i messaggi TDLib nel formato compatto che viaggia verso l'orologio. */
object MessageMapper {

    /** Chiavi degli Asset nel DataMap: deterministiche, cosi il watch le ritrova. */
    fun photoKey(messageId: Long) = "p$messageId"

    fun voiceKey(messageId: Long) = "v$messageId"

    fun toMessage(msg: TdApi.Message, users: Map<Long, String>): ChatMessage = ChatMessage(
        messageId = msg.id,
        sender = senderLabel(msg, users),
        outgoing = msg.isOutgoing,
        date = msg.date.toLong(),
        content = mapContent(msg.content, msg.id)
    )

    private fun senderLabel(msg: TdApi.Message, users: Map<Long, String>): String = when {
        msg.isOutgoing -> "Tu"
        else -> when (val s = msg.senderId) {
            is TdApi.MessageSenderUser -> users[s.userId] ?: "?"
            else -> ""
        }
    }

    private fun mapContent(content: TdApi.MessageContent, messageId: Long): MessageContent = when (content) {
        is TdApi.MessageText -> MessageContent.Text(content.text.text)

        is TdApi.MessageVoiceNote -> MessageContent.Voice(
            seconds = content.voiceNote.duration,
            waveform = emptyList()
        )

        is TdApi.MessageSticker -> MessageContent.Sticker(
            content.sticker.emoji.ifBlank { "Sticker" }
        )

        is TdApi.MessagePhoto -> {
            val largest = content.photo.sizes.filterNotNull().maxByOrNull { it.width }
            MessageContent.Photo(
                caption = content.caption.text,
                asset = photoKey(messageId),
                width = largest?.width ?: 0,
                height = largest?.height ?: 0
            )
        }

        is TdApi.MessageVideo -> MessageContent.Unsupported(
            content.caption.text.ifBlank { "Video" }
        )

        is TdApi.MessageAnimation -> MessageContent.Unsupported(
            content.caption.text.ifBlank { "GIF" }
        )

        is TdApi.MessageDocument -> MessageContent.Unsupported(
            content.document.fileName.ifBlank { "File" }
        )

        is TdApi.MessageVideoNote -> MessageContent.Unsupported("Videomessaggio")
        is TdApi.MessageAudio -> MessageContent.Unsupported("Audio")
        is TdApi.MessageLocation -> MessageContent.Unsupported("Posizione")
        is TdApi.MessageContact -> MessageContent.Unsupported("Contatto")
        is TdApi.MessagePoll -> MessageContent.Unsupported("Sondaggio")
        is TdApi.MessageCall -> MessageContent.Unsupported("Chiamata")
        else -> MessageContent.Unsupported("Messaggio")
    }
}
