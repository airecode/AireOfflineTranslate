package com.example.myapplication.translate.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.translate.Language
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/** Speaks the translated text aloud in the listener's language. */
class Speaker(context: Context) {

    private val appContext = context.applicationContext

    private val ready = CompletableDeferred<Boolean>()
    private val utteranceIds = AtomicLong(0)

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready.complete(status == TextToSpeech.SUCCESS)
    }

    /**
     * Speaks [text], suspending until playback finishes so the caller can hold the conversation in
     * its "speaking" state for exactly as long as audio is actually playing.
     *
     * Returns an error string when the utterance could not be spoken, or null on success.
     */
    suspend fun speak(text: String, language: Language): String? {
        if (text.isBlank()) return null

        if (!ready.await()) return appContext.getString(R.string.err_tts_unavailable)

        when (tts.setLanguage(language.locale)) {
            TextToSpeech.LANG_MISSING_DATA ->
                return appContext.getString(R.string.err_tts_voice_missing, language.name)
            TextToSpeech.LANG_NOT_SUPPORTED ->
                return appContext.getString(R.string.err_tts_unsupported, language.name)
            else -> Unit
        }

        val id = "utt-${utteranceIds.incrementAndGet()}"

        return suspendCancellableCoroutine { continuation ->
            // Exactly one of the paths below may resume the coroutine.
            //
            // The callbacks arrive on a binder thread while tts.speak() returns on this one, so
            // `continuation.isActive` alone is a check-then-act race across threads. Worse,
            // UtteranceProgressListener.onError(String, int) calls the deprecated onError(String)
            // in its base implementation, so a single failure can fire two overrides. Resuming
            // twice throws IllegalStateException on a binder thread and takes the process down.
            val finished = AtomicBoolean(false)
            fun finish(error: String?) {
                if (finished.compareAndSet(false, true) && continuation.isActive) {
                    continuation.resume(error)
                }
            }

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) finish(null)
                }

                @Deprecated("Required by the base class", ReplaceWith(""))
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id) finish(appContext.getString(R.string.msg_playback_failed))
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id) finish(appContext.getString(R.string.msg_playback_failed))
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == id) finish(null)
                }
            })

            continuation.invokeOnCancellation { runCatching { tts.stop() } }

            val result = try {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            } catch (t: Throwable) {
                Log.e(TAG, "speak() threw", t)
                TextToSpeech.ERROR
            }
            if (result != TextToSpeech.SUCCESS) finish(appContext.getString(R.string.err_tts_start))
        }
    }

    fun stop() {
        runCatching { tts.stop() }.onFailure { Log.w(TAG, "stop failed", it) }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }.onFailure { Log.w(TAG, "shutdown failed", it) }
    }

    private companion object {
        const val TAG = "Speaker"
    }
}
