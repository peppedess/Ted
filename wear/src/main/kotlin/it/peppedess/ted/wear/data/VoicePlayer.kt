package it.peppedess.ted.wear.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Riproduce i vocali sull'altoparlante dell'orologio.
 *
 * I vocali Telegram sono Opus dentro Ogg: MediaPlayer li gestisce
 * nativamente, ma pretende un file su disco, non un array di byte.
 */
object VoicePlayer {

    data class State(
        val loading: Boolean = false,
        val playing: Boolean = false,
        val ready: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val error: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun markLoading() {
        _state.value = State(loading = true)
    }

    fun load(context: Context, bytes: ByteArray) {
        stop()
        val file = File(context.cacheDir, "ted_voice.ogg")
        runCatching { file.writeBytes(bytes) }.onFailure {
            _state.value = State(error = "File non scrivibile")
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
                setOnCompletionListener { VoicePlayer.onCompleted() }
                setOnErrorListener { _, _, _ ->
                    VoicePlayer.onError()
                    true
                }
                prepare()
                start()
            }
            _state.value = State(
                playing = true,
                ready = true,
                durationMs = player?.duration ?: 0
            )
            startTicker()
        }.onFailure {
            _state.value = State(error = "Formato non riproducibile")
            stop()
        }
    }

    fun togglePlayPause() {
        val p = player ?: return
        runCatching {
            if (p.isPlaying) {
                p.pause()
                stopTicker()
                _state.value = _state.value.copy(playing = false)
            } else {
                p.start()
                _state.value = _state.value.copy(playing = true)
                startTicker()
            }
        }
    }

    fun seekBy(deltaMs: Int) {
        val p = player ?: return
        runCatching {
            val target = (p.currentPosition + deltaMs).coerceIn(0, p.duration)
            p.seekTo(target)
            _state.value = _state.value.copy(positionMs = target)
        }
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        runCatching {
            val target = (p.duration * fraction).toInt().coerceIn(0, p.duration)
            p.seekTo(target)
            _state.value = _state.value.copy(positionMs = target)
        }
    }

    fun stop() {
        stopTicker()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _state.value = State()
    }

    private fun onCompleted() {
        stopTicker()
        _state.value = _state.value.copy(
            playing = false,
            positionMs = _state.value.durationMs
        )
    }

    private fun onError() {
        stopTicker()
        _state.value = State(error = "Riproduzione interrotta")
        runCatching { player?.release() }
        player = null
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (true) {
                delay(200)
                val p = player ?: break
                val pos = runCatching { p.currentPosition }.getOrNull() ?: break
                _state.value = _state.value.copy(positionMs = pos)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    // --- Volume di sistema, flusso musica ---

    fun volumeMax(context: Context): Int =
        audio(context)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    fun volume(context: Context): Int =
        audio(context)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

    fun setVolume(context: Context, value: Int) {
        val manager = audio(context) ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, value.coerceIn(0, max), 0)
        }
    }

    private fun audio(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
}
