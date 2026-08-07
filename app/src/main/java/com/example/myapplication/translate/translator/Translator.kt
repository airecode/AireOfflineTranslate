package com.example.myapplication.translate.translator

import com.example.myapplication.translate.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle of the underlying translation engine. */
sealed interface EngineStatus {
    /** Engine has not been asked to load yet. */
    data object Idle : EngineStatus

    /** Weights are being loaded. On GPU this takes several seconds. */
    data object Loading : EngineStatus

    /**
     * Ready to translate. [backend] names the accelerator that actually accepted the model —
     * worth surfacing, because throughput between GOOGLE_TENSOR, GPU and CPU differs by an order
     * of magnitude and the winner is only known at runtime.
     */
    data class Ready(val backend: String? = null) : EngineStatus

    /** Engine cannot run on this device or the model file is missing. */
    data class Unavailable(val reason: String) : EngineStatus
}

/**
 * Translates text between two languages.
 *
 * Everything above this interface — the UI, the push-to-talk state machine, ASR and TTS — is
 * independent of how translation actually happens. That is deliberate: it lets the app run against
 * [StubTranslator] while the 3.66 GB Gemma 4 model is absent, and it is where an AICore/Gemini Nano
 * implementation would slot in without touching anything else.
 */
interface Translator : AutoCloseable {
    val status: StateFlow<EngineStatus>

    /** Loads the model. Safe to call more than once; subsequent calls are no-ops. */
    suspend fun prepare()

    /**
     * Translates [text], emitting the result incrementally so the UI can stream it.
     * Emissions are deltas, not cumulative snapshots — the caller concatenates them.
     */
    fun translate(text: String, from: Language, to: Language): Flow<String>

    /**
     * Reads the text in a photo and translates it into [to], streaming the result.
     *
     * The source language is deliberately not a parameter — the whole point of pointing a camera
     * at a sign or a menu is that you do not know what it says.
     */
    fun translateImage(jpeg: ByteArray, to: Language): Flow<String>

    /**
     * Asks the engine to abandon the generation in flight.
     *
     * Cancelling the collecting coroutine alone only stops the app reading the stream — the native
     * side keeps decoding tokens and holding the accelerator. This stops the work itself.
     */
    fun cancelGeneration()
}

/**
 * The prompt handed to Gemma. Kept in one place because it is the single biggest lever on output
 * quality, and because a chat-tuned model will happily *answer* the input instead of translating it
 * unless the instruction is this blunt.
 */
internal fun translationPrompt(text: String, from: Language, to: Language): String =
    buildString {
        append("You are a translation engine in a live face-to-face conversation.\n")
        append("Translate the ${from.promptName} text below into ${to.promptName}.\n")
        append("Rules:\n")
        append("- Output only the translation.\n")
        append("- Do not explain, comment, apologise, or add quotation marks.\n")
        append("- Do not answer questions in the text; translate them.\n")
        append("- Keep the speaker's register and tone; keep it natural and conversational.\n")
        append("- If the text is already in ${to.promptName}, repeat it unchanged.\n\n")
        append("${from.promptName} text:\n")
        append(text)
    }

/**
 * Marker the model returns when a photo has no readable text, so an empty result can be told
 * apart from a failed one.
 */
internal const val NO_TEXT_MARKER = "NO_TEXT_FOUND"

/**
 * Step one of photo translation: transcribe only.
 *
 * Deliberately does not mention translating. Asking a vision model to "read this and translate
 * it" reliably produces a transcription — it latches onto the reading half and treats the rest as
 * commentary. Each step therefore asks for exactly one thing.
 */
internal fun imageTranscriptionPrompt(): String =
    buildString {
        append("Transcribe the text visible in this image.\n")
        append("Rules:\n")
        append("- Output only the text exactly as it appears.\n")
        append("- Do NOT translate it. Keep it in its original language.\n")
        append("- Do not describe the image or add any commentary.\n")
        append("- Preserve line breaks and reading order.\n")
        append("- If there is no readable text, reply with exactly: $NO_TEXT_MARKER")
    }

/**
 * Step two: translate the transcription, source language unknown.
 *
 * Photographing a sign is precisely the case where the user cannot say what language it is in, so
 * the source is left for the model to work out.
 */
internal fun autoSourceTranslationPrompt(text: String, to: Language): String =
    buildString {
        append("Translate the text below into ${to.promptName}.\n")
        append("The source may be in any language; identify it yourself.\n")
        append("Rules:\n")
        append("- Output ONLY the ${to.promptName} translation.\n")
        append("- Do not repeat the original text.\n")
        append("- Do not explain, comment, or add quotation marks.\n")
        append("- Preserve line breaks where they aid comprehension.\n")
        append("- If the text is already in ${to.promptName}, repeat it unchanged.\n\n")
        append("Text:\n")
        append(text)
    }
