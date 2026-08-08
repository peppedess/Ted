package it.peppedess.ted.tdlib

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Verifica che libtdjni.so sia caricabile e che il ponte JNI risponda.
 * Serve solo in questa fase: sparira quando il servizio TDLib sara pronto.
 */
object TdLibProbe {

    data class Result(val ok: Boolean, val detail: String)

    @Volatile
    private var loaded = false

    fun run(): Result = try {
        if (!loaded) {
            System.loadLibrary("tdjni")
            loaded = true
        }
        // Richiesta sincrona: se questa passa, il JNI e vivo.
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        val version = runCatching {
            (Client.execute(TdApi.GetOption("version")) as? TdApi.OptionValueString)?.value
        }.getOrNull()
        Result(true, version?.let { "TDLib $it" } ?: "JNI attivo")
    } catch (t: Throwable) {
        Result(false, "${t::class.java.simpleName}: ${t.message ?: "?"}")
    }
}
