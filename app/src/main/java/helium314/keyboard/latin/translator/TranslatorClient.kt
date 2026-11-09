// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.translator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dev.davidv.translator.ITranslationCallback
import dev.davidv.translator.ITranslationService
import dev.davidv.translator.TranslationError
import helium314.keyboard.latin.utils.Log

/**
 * Conversa com o serviço do app Offline Translator (dev.davidv.translator).
 * A tradução acontece no outro app, offline; o teclado só manda o texto e recebe o resultado.
 * Os retornos chegam sempre na thread principal.
 */
class TranslatorClient(private val context: Context) {
    sealed interface Result {
        data class Success(val text: String) : Result
        data class Failure(val reason: Reason, val detail: String?) : Result
    }

    enum class Reason { NOT_CONNECTED, COULD_NOT_DETECT_LANGUAGE, LANGUAGE_NOT_DOWNLOADED, UNEXPECTED }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var service: ITranslationService? = null
    private var bound = false
    private val pending = mutableListOf<() -> Unit>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ITranslationService.Stub.asInterface(binder)
            val toRun = pending.toList()
            pending.clear()
            toRun.forEach { it() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun isInstalled(): Boolean = context.packageManager.resolveService(serviceIntent(), 0) != null

    fun connect(): Boolean {
        if (bound) return true
        bound = try {
            context.bindService(serviceIntent(), connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.w(TAG, "não foi possível conectar ao Offline Translator", e)
            false
        }
        return bound
    }

    fun disconnect() {
        pending.clear()
        if (!bound) return
        try {
            context.unbindService(connection)
        } catch (_: IllegalArgumentException) { }
        bound = false
        service = null
    }

    /** [from] vazio = detectar o idioma automaticamente. */
    fun translate(text: String, from: String, to: String, onResult: (Result) -> Unit) {
        val request = { send(text, from, to, onResult) }
        if (service != null) return request()
        if (!connect()) return onResult(Result.Failure(Reason.NOT_CONNECTED, null))
        pending.add(request)
    }

    private fun send(text: String, from: String, to: String, onResult: (Result) -> Unit) {
        val callback = object : ITranslationCallback.Stub() {
            override fun onTranslationResult(translatedText: String?) {
                mainHandler.post { onResult(Result.Success(translatedText.orEmpty())) }
            }

            override fun onTranslationError(error: TranslationError?) {
                val reason = when (error?.type) {
                    dev.davidv.translator.ErrorType.COULD_NOT_DETECT_LANGUAGE -> Reason.COULD_NOT_DETECT_LANGUAGE
                    dev.davidv.translator.ErrorType.DETECTED_BUT_UNAVAILABLE -> Reason.LANGUAGE_NOT_DOWNLOADED
                    else -> Reason.UNEXPECTED
                }
                mainHandler.post { onResult(Result.Failure(reason, error?.language ?: error?.message)) }
            }
        }
        try {
            service?.translate(text, from, to, callback) ?: onResult(Result.Failure(Reason.NOT_CONNECTED, null))
        } catch (e: Exception) {
            Log.w(TAG, "falha ao pedir tradução", e)
            service = null
            onResult(Result.Failure(Reason.NOT_CONNECTED, null))
        }
    }

    private fun serviceIntent() = Intent(SERVICE_ACTION).setPackage(PACKAGE)

    companion object {
        private const val TAG = "TranslatorClient"
        const val PACKAGE = "dev.davidv.translator"
        private const val SERVICE_ACTION = "dev.davidv.translator.ITranslationService"
        const val FDROID_URL = "https://f-droid.org/packages/dev.davidv.translator"
    }
}
