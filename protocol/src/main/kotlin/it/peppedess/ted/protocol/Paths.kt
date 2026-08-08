package it.peppedess.ted.protocol

/**
 * Contratto dei path del Wearable Data Layer.
 *
 * Regola: DataClient per lo stato che deve sopravvivere alla disconnessione,
 * MessageClient per le azioni istantanee, ChannelClient per i byte pesanti.
 */
object TedPaths {

    // --- DataClient: stato persistente, telefono -> orologio ---
    const val CHATS = "/ted/chats"
    const val STATUS = "/ted/status"
    private const val THREAD_PREFIX = "/ted/thread/"

    fun thread(chatId: Long): String = "$THREAD_PREFIX$chatId"

    fun chatIdFromThread(path: String): Long? =
        path.removePrefix(THREAD_PREFIX).takeIf { it != path }?.toLongOrNull()

    // --- MessageClient: comandi, orologio -> telefono ---
    const val COMMAND = "/ted/cmd"

    // --- MessageClient: avvisi, telefono -> orologio ---
    const val ALERT = "/ted/alert"

    // --- ChannelClient: vocali on demand ---
    const val CHANNEL_VOICE = "/ted/voice"

    fun voiceChannel(chatId: Long, messageId: Long): String =
        "$CHANNEL_VOICE/$chatId/$messageId"

    // --- Chiavi dentro il DataMap ---
    const val KEY_PAYLOAD = "p"
    const val KEY_REVISION = "r"

    // --- Capability dichiarate in wear.xml ---
    const val CAPABILITY_PHONE = "ted_phone"
    const val CAPABILITY_WATCH = "ted_watch"

    /** Tetto pratico del payload di un DataItem. Oltre, Android rifiuta il put. */
    const val MAX_PAYLOAD_BYTES = 100 * 1024

    /** Numero di chat sincronizzate nello snapshot. */
    const val CHAT_PAGE_SIZE = 25
}
