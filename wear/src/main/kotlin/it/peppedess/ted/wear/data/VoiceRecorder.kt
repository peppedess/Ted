package it.peppedess.ted.wear.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Registra un vocale dal microfono dell'orologio.
 *
 * Formato OGG/Opus: e quello che Telegram si aspetta per i messaggi vocali,
 * quindi il telefono puo inoltrarlo senza riconvertire nulla.
 */
object VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private var target: File? = null

    /** Durata massima: oltre, il trasferimento su Bluetooth diventa penoso. */
    const val MAX_SECONDS = 60

    fun start(context: Context): Boolean {
        stop()
        val file = File(context.cacheDir, "ted_rec.ogg")
        runCatching { if (file.exists()) file.delete() }

        return runCatching {
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioSamplingRate(48_000)
                setAudioEncodingBitRate(24_000)
                setMaxDuration(MAX_SECONDS * 1000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            target = file
            startedAt = SystemClock.elapsedRealtime()
            true
        }.getOrElse {
            stop()
            false
        }
    }

    fun elapsedSeconds(): Int =
        if (startedAt == 0L) 0 else ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()

    data class Result(val bytes: ByteArray, val seconds: Int)

    /** Chiude la registrazione e restituisce i byte, o null se troppo corta. */
    fun finish(): Result? {
        val seconds = elapsedSeconds()
        val file = target
        stop()
        if (file == null || !file.exists()) return null
        // Sotto il secondo e quasi sempre un tocco involontario.
        if (seconds < 1) {
            runCatching { file.delete() }
            return null
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return Result(bytes, seconds)
    }

    fun stop() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        startedAt = 0L
    }
}
