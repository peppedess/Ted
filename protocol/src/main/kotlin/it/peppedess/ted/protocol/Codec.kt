package it.peppedess.ted.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Serializzazione condivisa fra i due lati del ponte.
 *
 * classDiscriminator accorciato a "t" perche ogni byte conta:
 * il Data Layer si muove su Bluetooth e il DataItem ha un tetto duro.
 */
object TedCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        classDiscriminator = "t"
    }

    inline fun <reified T> encode(value: T): ByteArray =
        json.encodeToString(value).encodeToByteArray()

    inline fun <reified T> decode(bytes: ByteArray): T =
        json.decodeFromString(bytes.decodeToString())

    /** Decodifica tollerante: null invece di eccezione se il payload e di versione diversa. */
    inline fun <reified T> decodeOrNull(bytes: ByteArray?): T? = runCatching {
        bytes?.let { decode<T>(it) }
    }.getOrNull()
}
