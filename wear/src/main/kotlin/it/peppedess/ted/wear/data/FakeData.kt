package it.peppedess.ted.wear.data

import it.peppedess.ted.protocol.ChatList
import it.peppedess.ted.protocol.ChatSummary

/**
 * Dati finti per sviluppare la UI prima che il ponte TDLib sia pronto.
 * Rispetta il contratto di :protocol, quindi sostituirlo con i dati veri
 * non richiedera modifiche alla UI.
 */
object FakeData {

    private const val MINUTE = 60L

    fun chatList(now: Long = System.currentTimeMillis() / 1000): ChatList = ChatList(
        revision = 1,
        chats = listOf(
            ChatSummary(
                chatId = 1,
                title = "Marta",
                preview = "Ci vediamo alle sette davanti al cinema",
                date = now - 3 * MINUTE,
                unread = 2
            ),
            ChatSummary(
                chatId = 2,
                title = "Casa",
                preview = "Papa: ho preso il pane",
                date = now - 18 * MINUTE,
                unread = 5
            ),
            ChatSummary(
                chatId = 3,
                title = "Luca",
                preview = "Tu: te lo mando domani mattina",
                date = now - 47 * MINUTE,
                outgoing = true
            ),
            ChatSummary(
                chatId = 4,
                title = "ESPHome Italia",
                preview = "Andrea: il C6 va in brownout se alimenti dal 3V3",
                date = now - 90 * MINUTE,
                unread = 31,
                muted = true
            ),
            ChatSummary(
                chatId = 5,
                title = "Officina 3D",
                preview = "Foto",
                date = now - 5 * 60 * MINUTE
            ),
            ChatSummary(
                chatId = 6,
                title = "Giulia",
                preview = "Messaggio vocale",
                date = now - 26 * 60 * MINUTE
            ),
            ChatSummary(
                chatId = 7,
                title = "Salvato",
                preview = "Tu: promemoria filamento PETG",
                date = now - 50 * 60 * MINUTE,
                outgoing = true
            )
        )
    )
}
