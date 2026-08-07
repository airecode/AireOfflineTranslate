package com.example.myapplication.translate.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.translate.Language

/**
 * Push-to-talk speech recognition.
 *
 * Prefers the on-device recogniser (API 33+, which is this app's `minSdk`) so a conversation can
 * run with no network — the whole point of translating locally with Gemma. Falls back to the
 * default recogniser when no on-device model is installed for the chosen language.
 *
 * All methods must be called from the main thread; [SpeechRecognizer] enforces this.
 */
class SpeechToText(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    /**
     * Most recent partial hypothesis. Retained so a recogniser that discards its result when cut
     * off by the stop button does not cost the user their whole turn.
     */
    private var lastPartial: String = ""

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    /** True once [start] has been called and no final result or error has arrived yet. */
    var isListening: Boolean = false
        private set

    fun start(language: Language, listener: Listener) {
        release()

        lastPartial = ""

        // The default recogniser, not createOnDeviceSpeechRecognizer(). The on-device-only API
        // needs a downloaded pack for this exact language and reports a missing one as a generic
        // ERROR_NO_MATCH; the default one still prefers offline (see EXTRA_PREFER_OFFLINE below)
        // but can fall back instead of simply failing.
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot create speech recogniser", t)
            listener.onError(appContext.getString(R.string.err_speech_unavailable))
            return
        }

        recognizer = created
        isListening = true

        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstResult()?.let { text ->
                    lastPartial = text
                    listener.onPartial(text)
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                // Fall back to the last partial: some recognisers return an empty final result
                // after stopListening() even though they streamed a usable hypothesis.
                val text = results.firstResult() ?: lastPartial.takeIf { it.isNotBlank() }
                if (text.isNullOrBlank()) {
                    listener.onError(appContext.getString(R.string.err_no_match))
                } else {
                    listener.onFinal(text)
                }
            }

            override fun onError(error: Int) {
                isListening = false
                val salvaged = lastPartial
                if (salvaged.isNotBlank() && error.isRecoverableAfterSpeech()) {
                    // We were cut off mid-utterance by the stop button. The recogniser throws its
                    // hypothesis away, but we already have it — use it rather than losing the turn.
                    Log.i(TAG, "Recovered transcript from partial results after error $error")
                    listener.onFinal(salvaged)
                } else {
                    Log.w(TAG, "Recognition error $error with no usable partial")
                    listener.onError(describeError(error))
                }
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Push-to-talk defines its own endpoint: the user releasing the button. Silence-based
            // endpointing would cut the speaker off mid-pause, so push it out of the way.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            // Keeps recognition local where a model exists, matching the app's offline goal,
            // without hard-failing when it does not.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            created.startListening(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "startListening failed", t)
            isListening = false
            listener.onError(appContext.getString(R.string.err_speech_start))
        }
    }

    /** Called when the user lifts their finger; the final result arrives via the listener. */
    fun stop() {
        if (!isListening) return
        runCatching { recognizer?.stopListening() }
            .onFailure { Log.w(TAG, "stopListening failed", it) }
    }

    /** Abandons the current utterance without waiting for a result. */
    fun cancel() {
        isListening = false
        runCatching { recognizer?.cancel() }
    }

    fun release() {
        isListening = false
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    /**
     * Errors that mean "I gave up on this audio", as opposed to a hard failure. If a partial
     * transcript exists these are recoverable; permission, audio and language errors are not.
     */
    private fun Int.isRecoverableAfterSpeech(): Boolean = when (this) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER -> true
        else -> false
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> appContext.getString(R.string.err_mic)
        SpeechRecognizer.ERROR_CLIENT -> appContext.getString(R.string.err_recognition_cancelled)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> appContext.getString(R.string.err_mic_permission)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            appContext.getString(R.string.err_language_pack)
        SpeechRecognizer.ERROR_NO_MATCH -> appContext.getString(R.string.err_no_match)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> appContext.getString(R.string.err_recognizer_busy)
        SpeechRecognizer.ERROR_SERVER -> appContext.getString(R.string.err_recognition_service)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> appContext.getString(R.string.err_speech_timeout)
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> appContext.getString(R.string.err_language_pack)
        else -> appContext.getString(R.string.err_recognition_generic, error)
    }

    private companion object {
        const val TAG = "SpeechToText"
        const val SILENCE_MS = 10_000
    }
}
