package it.peppedess.ted.wear.data

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * Riproduce i vocali sull'altoparlante dell'orologio.
 *
 * I vocali Telegram sono Opus dentro Ogg: MediaPlayer li gestisce
 * nativamente, ma pretende un file su disco, non un array di byte.
 */
object VoicePlayer {

    private var player: MediaPlayer? = null

    fun play(context: Context, bytes: ByteArray, onFinished: () -> Unit) {
        stopPlayback()
        val file = File(context.cacheDir, "ted_voice.ogg")
        runCatching { file.writeBytes(bytes) }.onFailure {
            onFinished()
            return
        }

        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    onFinished()
                    VoicePlayer.stopPlayback()
                }
                setOnErrorListener { _, _, _ ->
                    onFinished()
                    VoicePlayer.stopPlayback()
                    true
                }
                prepare()
                start()
            }
        }.onFailure {
            onFinished()
            stopPlayback()
        }
    }

    fun stopPlayback() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }
}
