package it.peppedess.ted.tdlib

import android.content.Context
import android.os.Build
import android.util.Log
import it.peppedess.ted.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TdException(val code: Int, override val message: String) :
    Exception("TDLib $code: $message")

/**
 * Involucro Kotlin su org.drinkless.tdlib.Client.
 *
 * Espone due cose: [stage], la macchina a stati dell'autenticazione,
 * e [updates], il flusso grezzo degli aggiornamenti da mappare piu avanti.
 */
class TdClient(
    context: Context,
    private val scope: CoroutineScope
) {

    sealed interface Stage {
        data object Starting : Stage
        data object WaitPhone : Stage
        data class WaitCode(val phone: String) : Stage
        data class WaitPassword(val hint: String) : Stage
        data object Ready : Stage
        data object LoggedOut : Stage
        data class Failed(val message: String) : Stage
    }

    private val appContext = context.applicationContext

    private val _stage = MutableStateFlow<Stage>(Stage.Starting)
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<TdApi.Object> = _updates

    @Volatile
    private var client: Client? = null

    fun start() {
        if (client != null) return
        TdNative.load()
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        client = Client.create(
            { obj -> onUpdate(obj) },
            { e -> Log.e(TAG, "eccezione su update", e) },
            { e -> Log.e(TAG, "eccezione generica", e) }
        )
    }

    private fun onUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            onAuthState(obj.authorizationState)
        }
        _updates.tryEmit(obj)
    }

    private fun onAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters ->
                scope.launch { sendParameters() }

            is TdApi.AuthorizationStateWaitPhoneNumber ->
                _stage.value = Stage.WaitPhone

            is TdApi.AuthorizationStateWaitCode ->
                _stage.value = Stage.WaitCode(state.codeInfo?.phoneNumber.orEmpty())

            is TdApi.AuthorizationStateWaitPassword ->
                _stage.value = Stage.WaitPassword(state.passwordHint.orEmpty())

            is TdApi.AuthorizationStateReady ->
                _stage.value = Stage.Ready

            is TdApi.AuthorizationStateClosed -> {
                client = null
                _stage.value = Stage.LoggedOut
            }

            is TdApi.AuthorizationStateLoggingOut ->
                _stage.value = Stage.LoggedOut

            else -> Log.d(TAG, "stato auth non gestito: ${state::class.java.simpleName}")
        }
    }

    private suspend fun sendParameters() {
        // Assegnazione per campo invece che via costruttore: la firma di
        // SetTdlibParameters e cambiata fra le versioni di TDLib, i nomi dei campi no.
        val params = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = File(appContext.filesDir, "td").absolutePath
            filesDirectory = File(appContext.filesDir, "td-files").absolutePath
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = BuildConfig.TG_API_ID
            apiHash = BuildConfig.TG_API_HASH
            systemLanguageCode = Locale.getDefault().language.ifBlank { "en" }
            deviceModel = Build.MODEL ?: "Android"
            systemVersion = Build.VERSION.RELEASE ?: "?"
            applicationVersion = BuildConfig.VERSION_NAME
        }
        runCatching { send(params) }
            .onFailure { _stage.value = Stage.Failed(it.message ?: "parametri rifiutati") }
    }

    private suspend fun awaitClient(): Client {
        var c = client
        var waited = 0
        while (c == null && waited < 5_000) {
            delay(20)
            waited += 20
            c = client
        }
        return c ?: error("client TDLib non inizializzato")
    }

    suspend fun <R : TdApi.Object> send(query: TdApi.Function<R>): R {
        val c = awaitClient()
        return suspendCancellableCoroutine { cont ->
            c.send(
                query,
                { result ->
                    if (result is TdApi.Error) {
                        cont.resumeWithException(TdException(result.code, result.message.orEmpty()))
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        cont.resume(result as R)
                    }
                },
                { e -> cont.resumeWithException(e) }
            )
        }
    }

    suspend fun submitPhone(phone: String) {
        send(TdApi.SetAuthenticationPhoneNumber().apply { phoneNumber = phone.trim() })
    }

    suspend fun submitCode(value: String) {
        send(TdApi.CheckAuthenticationCode().apply { code = value.trim() })
    }

    suspend fun submitPassword(value: String) {
        send(TdApi.CheckAuthenticationPassword().apply { password = value })
    }

    suspend fun logOut() {
        runCatching { send(TdApi.LogOut()) }
    }

    fun reportFailure(t: Throwable) {
        _stage.value = Stage.Failed(t.message ?: t::class.java.simpleName)
    }

    companion object {
        private const val TAG = "TdClient"
    }
}
