package com.example.myapplication.translate.translator

import com.example.myapplication.translate.Language
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Fake translator used when the Gemma model file is not on the device, and for UI work.
 *
 * It deliberately mimics the real engine's *timing* — a load delay, a time-to-first-token pause,
 * then token-rate streaming — so that latency problems in the UI show up here rather than only
 * appearing once the real 3.66 GB model is in place.
 */
class StubTranslator : Translator {

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    override suspend fun prepare() {
        if (_status.value is EngineStatus.Ready) return
        _status.value = EngineStatus.Loading
        delay(600)
        _status.value = EngineStatus.Ready("STUB")
    }

    override fun translate(text: String, from: Language, to: Language): Flow<String> = flow {
        delay(TIME_TO_FIRST_TOKEN_MS)
        val words = "[${to.promptName}] $text".split(" ")
        words.forEachIndexed { index, word ->
            emit(if (index == 0) word else " $word")
            delay(MS_PER_TOKEN)
        }
    }

    override fun translateImage(jpeg: ByteArray, to: Language): Flow<String> = flow {
        // Vision encoding is a real, one-off cost before any token appears; mimic it so the UI is
        // exercised against realistic timing rather than an instant response.
        delay(VISION_ENCODE_MS)
        val words = "[${to.promptName} from photo, ${jpeg.size / 1024} KB]".split(" ")
        words.forEachIndexed { index, word ->
            emit(if (index == 0) word else " $word")
            delay(MS_PER_TOKEN)
        }
    }

    /** Nothing native to stop; cancelling the collector is enough for the fake stream. */
    override fun cancelGeneration() = Unit

    override fun close() {
        _status.value = EngineStatus.Idle
    }

    private companion object {
        /** Matches the published GPU-backend TTFT for Gemma 4 E4B. */
        const val TIME_TO_FIRST_TOKEN_MS = 800L

        /** ~22 tokens/sec, the published GPU decode rate. */
        const val MS_PER_TOKEN = 45L

        /** Vision encoding runs before the first token and is not free. */
        const val VISION_ENCODE_MS = 2_000L
    }
}
