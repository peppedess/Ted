package it.peppedess.ted.tdlib

/** Caricamento idempotente di libtdjni.so. */
object TdNative {

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        System.loadLibrary("tdjni")
        loaded = true
    }
}
