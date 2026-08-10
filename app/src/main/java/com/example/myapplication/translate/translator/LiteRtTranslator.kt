package com.example.myapplication.translate.translator

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.translate.Language
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs Gemma 4 E2B on-device through LiteRT-LM.
 *
 * Backend is GPU, not CPU, and that is not a tuning preference. Published Gemma 4 figures put
 * CPU time-to-first-token at 5.3 s against 0.8 s on GPU; at 5 s per utterance the conversation UI
 * this app is built around stops working. CPU is therefore only used as an explicit fallback.
 */
class LiteRtTranslator(context: Context, private val variant: ModelVariant) : Translator {

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var activeBackend: String = "unknown"

    /**
     * Set by [cancelLoad] and read at every point in [prepare] where the loop is between blocking
     * native calls. A flag rather than coroutine cancellation because `initialize()` is a blocking
     * JNI call: cancelling the coroutine would leave it running with nothing left to close it.
     */
    @Volatile
    private var loadCancelled = false

    /**
     * Conversations awaiting close, retired lazily before the next run rather than at the end of
     * their own. Releasing native resources on the completion path of an async generation has
     * been a source of process death. A list rather than a single slot because photo translation
     * uses two conversations per run.
     */
    private val pending = java.util.Collections.synchronizedList(mutableListOf<Conversation>())

    private fun retirePendingConversations() {
        synchronized(pending) {
            pending.forEach { conversation ->
                runCatching { conversation.close() }
                    .onFailure { Log.w(TAG, "Deferred conversation close failed", it) }
            }
            pending.clear()
        }
    }

    /** See [ModelLocation]; weights are downloaded on demand, never bundled. */
    val modelFile: File
        get() = ModelLocation.modelFile(appContext, variant)

    override suspend fun prepare() {
        when (_status.value) {
            is EngineStatus.Ready, is EngineStatus.Loading -> return
            else -> Unit
        }

        val file = modelFile
        // Size-checked rather than exists-checked: a partial file would otherwise reach Engine()
        // and fail deep inside the native loader with a far less useful message.
        if (!ModelLocation.isInstalled(appContext, variant)) {
            // The path belongs in the log, not on screen: a user shown
            // /storage/emulated/0/Android/data/... learns nothing they can act on.
            Log.w(TAG, "No weights for ${variant.id} at ${file.absolutePath}")
            _status.value =
                EngineStatus.Unavailable(appContext.getString(R.string.msg_model_not_installed))
            return
        }

        loadCancelled = false
        _status.value = EngineStatus.Loading
        withContext(Dispatchers.IO) {
            val candidates = candidateBackends()
            Log.i(
                TAG,
                "${variant.id}: SoC=${Build.SOC_MANUFACTURER}/${Build.SOC_MODEL} " +
                    "hardware=${Build.HARDWARE} model=${Build.MODEL} → " +
                    "trying ${candidates.joinToString { it.name }}"
            )

            // Every failure is kept, not just the last one. With a fallback chain the interesting
            // question is why *each* backend was rejected, and that has to be visible on screen —
            // the device under test is not always attached to adb.
            val failures = mutableListOf<String>()

            for (backend in candidates) {
                if (loadCancelled) {
                    Log.i(TAG, "Load cancelled before trying ${backend.name}")
                    _status.value = EngineStatus.Idle
                    return@withContext
                }

                var created: Engine? = null
                try {
                    created = Engine(
                        EngineConfig(
                            modelPath = file.absolutePath,
                            backend = backend,
                            // Vision stays on CPU deliberately. The container pins the vision
                            // adapter and end-of-vision stages to CPU anyway, and encoding runs
                            // once per photo rather than per token — so the cost is bounded, and
                            // it keeps image support from putting the working text path at risk
                            // of another accelerator-specific failure.
                            visionBackend = Backend.CPU(),
                            maxNumTokens = MAX_NUM_TOKENS,
                            maxNumImages = 1,
                            cacheDir = appContext.cacheDir.absolutePath,
                        )
                    )
                    // Documented to take up to ~10 s, hence the IO dispatcher.
                    created.initialize()

                    // initialize() succeeding proves nothing. LiteRT-LM defers kernel compilation,
                    // so a device with no OpenCL driver initialises cleanly and only fails when the
                    // first kernel runs. Without this probe the loop always stops at the first
                    // backend and the real failure surfaces later, mid-translation.
                    probe(created)

                    // The cancel could have arrived while initialize() or probe() was blocked. A
                    // working engine is not worth keeping if the user has already said no: it
                    // would hold ~2.6 GB that nothing is going to use.
                    if (loadCancelled) {
                        Log.i(TAG, "Load cancelled during ${backend.name}; discarding the engine")
                        runCatching { created.close() }
                        _status.value = EngineStatus.Idle
                        return@withContext
                    }

                    engine = created
                    activeBackend = backend.name
                    Log.i(TAG, "${variant.displayName} running on ${backend.name}")
                    _status.value = EngineStatus.Ready(backend.name)
                    return@withContext
                } catch (t: Throwable) {
                    Log.w(TAG, "Backend ${backend.name} rejected the model", t)
                    failures += "${backend.name}: ${t.message?.take(70) ?: t::class.java.simpleName}"
                    runCatching { created?.close() }
                }
            }

            if (loadCancelled) {
                Log.i(TAG, "Load cancelled; not reporting the backend failures as an error")
                _status.value = EngineStatus.Idle
                return@withContext
            }

            Log.e(TAG, "No usable backend for Gemma 4. Attempts: $failures")
            _status.value = EngineStatus.Unavailable("No backend accepted the model — $failures")
        }
    }

    /**
     * Stops the load at the next point [prepare] is between native calls. See [Translator.cancelLoad]
     * for why this cannot be immediate.
     */
    override fun cancelLoad() {
        loadCancelled = true
    }

    /**
     * Forces the backend to compile and run a kernel, so an accelerator that cannot actually
     * execute fails here — during an explicit load the user is waiting on — rather than halfway
     * through their first translation.
     *
     * Doubles as a warm-up: the cost of first-kernel compilation is paid now instead of being
     * charged to the first utterance.
     */
    private fun probe(engine: Engine) {
        engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = GREEDY_TOP_K,
                    topP = TRANSLATION_TOP_P,
                    temperature = TRANSLATION_TEMPERATURE,
                ),
                maxOutputToken = 1,
            )
        ).use { conversation ->
            conversation.sendMessage("Hi")
        }
    }

    /**
     * Accelerators to try, best first.
     *
     * Ordering is not cosmetic. [Backend.GPU] talks to OpenCL, and Google's Tensor SoCs ship no
     * OpenCL driver — on a Pixel it fails with "Can not find OpenCL library on this device". Those
     * devices have their own path via [Backend.GOOGLE_TENSOR], so it is tried first there. CPU is
     * always last: it works everywhere but its time-to-first-token is measured in seconds.
     */
    private fun candidateBackends(): List<Backend> = buildList {
        if (isGoogleTensorDevice()) add(Backend.GOOGLE_TENSOR())
        add(Backend.GPU())
        add(Backend.CPU())
    }

    private fun isGoogleTensorDevice(): Boolean =
        Build.SOC_MANUFACTURER.equals("Google", ignoreCase = true) ||
            // gs101 / gs201 / zuma / zumapro are the Tensor board names.
            Build.HARDWARE.startsWith("gs", ignoreCase = true) ||
            Build.HARDWARE.startsWith("zuma", ignoreCase = true)

    override fun translate(text: String, from: Language?, to: Language): Flow<String> = flow {
        val active = engine ?: throw IllegalStateException("Engine not initialised; call prepare() first")

        // Retire previous conversations now, while the engine is provably idle.
        retirePendingConversations()

        // A fresh conversation per utterance. Reusing one would let earlier turns leak into the
        // prompt, and a chat-tuned model given conversational context starts *replying* to the
        // speaker instead of translating them.
        val conversation = newConversation(active, MAX_OUTPUT_TOKENS)

        // Handed to the *next* run to close, never closed here. See retirePendingConversations.
        pending += conversation

        try {
            // The stream contract is not pinned down by the API docs, so this tolerates both
            // shapes: chunks that are deltas, and chunks that are cumulative snapshots.
            val seen = StringBuilder()
                    // A null source means the text came from a photo, where the language is unknown.
        val prompt = if (from == null) {
            autoSourceTranslationPrompt(text, to)
        } else {
            translationPrompt(text, from, to)
        }

        conversation.sendMessageAsync(prompt).collect { message ->
                val chunk = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                if (chunk.isEmpty()) return@collect

                val delta = if (chunk.length > seen.length && chunk.startsWith(seen)) {
                    chunk.substring(seen.length).also { seen.setLength(0); seen.append(chunk) }
                } else {
                    chunk.also { seen.append(it) }
                }
                if (delta.isNotEmpty()) emit(delta)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            // On some devices a backend accepts the model at init and only fails once it actually
            // runs a kernel. Tear the engine down so the state reflects reality and a reload can
            // pick a different backend, instead of leaving it looking Ready but broken.
            Log.e(TAG, "Inference failed on $activeBackend", t)
            runCatching { engine?.close() }
            engine = null
            _status.value = EngineStatus.Unavailable(
                "$activeBackend failed during inference: ${t.message?.take(70)}"
            )
            throw t
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Vision pass: transcription only, never translation.
     *
     * A single "read this and translate it" instruction does not work — the model performs the
     * reading and skips the translating, returning the source verbatim. The caller runs the
     * result back through [translate], so each pass asks for one unambiguous thing.
     */
    override suspend fun transcribeImage(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val active = engine
            ?: throw IllegalStateException("Engine not initialised; call prepare() first")

        retirePendingConversations()

        try {
            val conversation = newConversation(active, MAX_IMAGE_OUTPUT_TOKENS)
            pending += conversation

            val text = conversation.sendMessage(
                Contents.of(
                    Content.ImageBytes(jpeg),
                    Content.Text(imageTranscriptionPrompt()),
                )
            ).textContent().trim()

            Log.i(TAG, "Transcribed ${text.length} chars from image")
            if (text.contains(NO_TEXT_MARKER)) "" else text
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            Log.e(TAG, "Image transcription failed on $activeBackend", t)
            _status.value = EngineStatus.Unavailable(
                "$activeBackend failed reading the image: ${t.message?.take(70)}"
            )
            throw t
        }
    }

    private fun newConversation(engine: Engine, maxTokens: Int) = engine.createConversation(
        ConversationConfig(
            samplerConfig = SamplerConfig(
                topK = GREEDY_TOP_K,
                topP = TRANSLATION_TOP_P,
                temperature = TRANSLATION_TEMPERATURE,
            ),
            maxOutputToken = maxTokens,
        )
    )

    private fun com.google.ai.edge.litertlm.Message.textContent(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString(separator = "") { it.text }

    override suspend fun detectLanguage(
        text: String,
        candidates: List<Language>,
    ): Language? = withContext(Dispatchers.IO) {
        if (text.isBlank() || candidates.isEmpty()) return@withContext null
        val active = engine ?: return@withContext null

        try {
            // Its own short-lived conversation: a few tokens out, and no shared context that
            // could bias the answer toward whatever was translated last.
            val conversation = newConversation(active, MAX_DETECTION_TOKENS)
            pending += conversation

            val answer = conversation
                .sendMessage(languageDetectionPrompt(text, candidates))
                .textContent()
                .trim()

            // Matched loosely: the model sometimes answers "Japanese." or "It is Japanese".
            val detected = candidates.firstOrNull {
                answer.contains(it.promptName, ignoreCase = true)
            }
            Log.i(TAG, "Language detection answered '$answer' → ${detected?.tag ?: "unknown"}")
            detected
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (t: Throwable) {
            // Detection is an optimisation, never a hard requirement — the caller falls back to a
            // sensible default direction rather than failing the translation.
            Log.w(TAG, "Language detection failed", t)
            null
        }
    }

    override fun cancelGeneration() {
        synchronized(pending) {
            pending.forEach { conversation ->
                runCatching { conversation.cancelProcess() }
                    .onFailure { Log.w(TAG, "cancelProcess failed", it) }
            }
        }
    }

    override fun close() {
        retirePendingConversations()
        runCatching { engine?.close() }
            .onFailure { Log.w(TAG, "Error closing engine", it) }
        engine = null
        _status.value = EngineStatus.Idle
    }

    companion object {
        private const val TAG = "LiteRtTranslator"

        /** Utterances are short; a large KV cache would only cost memory. */
        private const val MAX_NUM_TOKENS = 1024

        /** Caps runaway generation — a translation should never be much longer than its source. */
        private const val MAX_OUTPUT_TOKENS = 256

        /** Signs and menus carry far more text than one spoken utterance. */
        private const val MAX_IMAGE_OUTPUT_TOKENS = 512

        /** A language name is a word or two; anything more is the model padding. */
        private const val MAX_DETECTION_TOKENS = 12

        /** Greedy decoding. Translation wants reproducibility, not creativity. */
        private const val GREEDY_TOP_K = 1

        /** Inert at topK = 1, but the parameter has no default. */
        private const val TRANSLATION_TOP_P = 1.0
        private const val TRANSLATION_TEMPERATURE = 0.1
    }
}
