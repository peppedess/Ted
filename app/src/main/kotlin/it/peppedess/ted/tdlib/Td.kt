package it.peppedess.ted.tdlib

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Unica istanza di TDLib nel processo. TDLib mantiene un database locale
 * e una sessione: due client concorrenti si pesterebbero i piedi.
 */
object Td {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var instance: TdClient? = null

    fun get(context: Context): TdClient =
        instance ?: synchronized(this) {
            instance ?: TdClient(context.applicationContext, scope)
                .also { it.start(); instance = it }
        }
}
