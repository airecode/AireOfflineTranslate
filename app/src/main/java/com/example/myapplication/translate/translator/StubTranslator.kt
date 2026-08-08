package com.example.myapplication.translate.translator

import com.example.myapplication.translate.Language
import kotlinx.coroutines.CancellationException
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
 * appearing once the real 2.59 GB model is in place.
 */
class StubTranslator : Translator {

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    override suspend fun prepare() {
        if (_status.value is EngineStatus.Ready) return
        _status.value = EngineStatus.Loading
        try {
            delay(LOAD_MS)
        } catch (e: CancellationException) {
            // Unlike the real engine this load *is* interruptible, so the status has to be put
            // back by hand — nothing else runs after the throw.
            _status.value = EngineStatus.Idle
            throw e
        }
        _status.value = EngineStatus.Ready("STUB")
    }

    /** The stub's load is a cancellable `delay`, so the coroutine cancellation does the work. */
    override fun cancelLoad() = Unit

    override fun translate(text: String, from: Language?, to: Language): Flow<String> = flow {
        delay(TIME_TO_FIRST_TOKEN_MS)
        val words = "[${to.promptName}] $text".split(" ")
        words.forEachIndexed { index, word ->
            emit(if (index == 0) word else " $word")
            delay(MS_PER_TOKEN)
        }
    }

    override suspend fun transcribeImage(jpeg: ByteArray): String {
        // Vision encoding is a real, one-off cost before any token appears; mimic it so the UI is
        // exercised against realistic timing rather than an instant response.
        delay(VISION_ENCODE_MS)
        return "[text from photo, ${jpeg.size / 1024} KB]"
    }

    /** Always reports the first candidate, so the stub exercises the near-source direction. */
    override suspend fun detectLanguage(text: String, candidates: List<Language>): Language? {
        delay(200)
        return candidates.firstOrNull()
    }

    /** Nothing native to stop; cancelling the collector is enough for the fake stream. */
    override fun cancelGeneration() = Unit

    override fun close() {
        _status.value = EngineStatus.Idle
    }

    private companion object {
        /**
         * Loading the real weights takes several seconds, not the fraction of one this used to
         * wait. The old value was short enough that the loading dialog barely appeared, which is
         * the opposite of what a stub built to mimic real timings is for.
         */
        const val LOAD_MS = 3_000L

        /** Matches the published GPU-backend TTFT for Gemma 4. */
        const val TIME_TO_FIRST_TOKEN_MS = 800L

        /** ~22 tokens/sec, the published GPU decode rate. */
        const val MS_PER_TOKEN = 45L

        /** Vision encoding runs before the first token and is not free. */
        const val VISION_ENCODE_MS = 2_000L
    }
}
