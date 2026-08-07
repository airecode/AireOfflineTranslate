package com.example.myapplication.translate.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.translate.CrashReporter
import com.example.myapplication.translate.ImageLoader
import com.example.myapplication.translate.Language
import com.example.myapplication.translate.Languages
import com.example.myapplication.translate.speech.Speaker
import com.example.myapplication.translate.speech.SpeechToText
import com.example.myapplication.translate.translator.EngineStatus
import com.example.myapplication.translate.translator.LiteRtTranslator
import com.example.myapplication.translate.translator.ModelDownloader
import com.example.myapplication.translate.translator.ModelVariant
import com.example.myapplication.translate.translator.StubTranslator
import com.example.myapplication.translate.translator.Translator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which half of the screen. [FAR] is the rotated panel facing the other person. */
enum class Side {
    NEAR, FAR;

    fun other(): Side = if (this == NEAR) FAR else NEAR
}

enum class Phase { IDLE, LISTENING, TRANSLATING, SPEAKING }

data class TranslateUiState(
    val nearLanguage: Language = Languages.ENGLISH,
    val farLanguage: Language = Languages.JAPANESE,
    val nearText: String = "",
    val farText: String = "",
    val activeSide: Side? = null,
    val phase: Phase = Phase.IDLE,
    val engineStatus: EngineStatus = EngineStatus.Idle,
    val activeVariant: ModelVariant = ModelVariant.DEFAULT,
    /** Download/installed state for every variant, so the models dialog can show them all. */
    val modelStates: Map<ModelVariant, ModelDownloader.State> = emptyMap(),
    val usingStubEngine: Boolean = false,
    val message: String? = null,
    val splitFraction: Float = 0.5f,
    /** Panel that just copied, so it can show confirmation inside its own rotation. */
    val copiedSide: Side? = null,
    /**
     * Whether the upper panel faces the person opposite. True by default — that mirroring is the
     * point of the layout — but it can be flipped upright when one person is holding the phone
     * and both are reading from the same side.
     */
    val farPanelRotated: Boolean = true,
) {
    val activeModelState: ModelDownloader.State
        get() = modelStates[activeVariant] ?: ModelDownloader.State.Absent

    fun languageFor(side: Side): Language = if (side == Side.NEAR) nearLanguage else farLanguage
    fun textFor(side: Side): String = if (side == Side.NEAR) nearText else farText

    val isBusy: Boolean get() = phase != Phase.IDLE
}

/**
 * Owns the push-to-talk conversation loop: hold to record, release to transcribe, translate, then
 * speak the result in the listener's language.
 *
 * Only one side can hold the microphone at a time. Both panels share a single audio input, so
 * letting both record concurrently would produce two recognisers fighting over the same stream.
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val speechToText = SpeechToText(application)
    private val speaker = Speaker(application)
    private val modelDownloader = ModelDownloader(application)
    private val prefs =
        application.getSharedPreferences("aire_prefs", android.content.Context.MODE_PRIVATE)

    /**
     * Swapped for the real engine the moment the model finishes downloading, so the first run does
     * not need an app restart to become useful.
     */
    private var translator: Translator = StubTranslator()

    private val _state = MutableStateFlow(TranslateUiState(usingStubEngine = true))
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    private var translationJob: Job? = null
    private var engineStatusJob: Job? = null
    private val downloadJobs = mutableMapOf<ModelVariant, Job>()
    private var copyFeedbackJob: Job? = null
    private var idleUnloadJob: Job? = null

    /** True while the camera or photo picker is in front of us. */
    private var awaitingExternalResult = false

    /** Resolves a string resource against the app context, so messages follow the phone locale. */
    private fun str(id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    init {
        // Surface the previous crash, if the process died rather than exited cleanly.
        CrashReporter.consume(application)?.let { report ->
            Log.e(TAG, "Previous run crashed:\n$report")
            _state.update { it.copy(message = str(R.string.msg_last_crash, CrashReporter.summarise(report))) }
        }

        // Restore the language pair. Each side falls back independently, so one unrecognised tag
        // does not discard the other side's choice.
        val savedNear = Languages.fromTag(prefs.getString(KEY_NEAR_LANGUAGE, null))
        val savedFar = Languages.fromTag(prefs.getString(KEY_FAR_LANGUAGE, null))
        if (savedNear != null || savedFar != null) {
            _state.update {
                it.copy(
                    nearLanguage = savedNear ?: it.nearLanguage,
                    farLanguage = savedFar ?: it.farLanguage,
                )
            }
        }

        val remembered = ModelVariant.fromId(prefs.getString(KEY_ACTIVE_VARIANT, null))
        // Prefer whichever variant is actually on disk, so a freshly downloaded one is not
        // ignored just because preferences still name the other.
        val startingVariant = when {
            modelDownloader.isInstalled(remembered) -> remembered
            else -> ModelVariant.entries.firstOrNull { modelDownloader.isInstalled(it) }
                ?: remembered
        }
        _state.update { it.copy(activeVariant = startingVariant) }

        refreshModelStates()
        if (modelDownloader.isInstalled(startingVariant)) {
            useRealEngine(startingVariant)
        } else {
            Log.i(TAG, "No weights for " + startingVariant.id + "; running on stub")
            observeEngineStatus()
        }
    }

    // ---- model lifecycle ---------------------------------------------------

    /**
     * Loading is explicit, never automatic. The engine holds ~3.7 GB resident once initialised,
     * which is a large share of an 8 GB phone, so the user decides when to pay for it.
     */
    fun onLoadModel() {
        val status = _state.value.engineStatus
        if (status is EngineStatus.Ready || status is EngineStatus.Loading) return
        // Clear any stale notice first: the status strip prefers `message` over live engine
        // state, so leaving "Model unloaded" there would mask the load about to happen.
        _state.update { it.copy(message = null) }
        viewModelScope.launch { translator.prepare() }
    }

    fun onUnloadModel() {
        if (_state.value.engineStatus !is EngineStatus.Ready) return
        translationJob?.cancel()
        speechToText.cancel()
        speaker.stop()
        // Releases the native engine and its KV cache; status drops back to Idle via the flow.
        translator.close()
        _state.update { it.copy(phase = Phase.IDLE, activeSide = null, message = str(R.string.msg_model_unloaded)) }
    }

    /**
     * Marks that the app is about to hand off to another app on the user's behalf — the camera or
     * the photo picker.
     *
     * Those launches background this app exactly like the user leaving does, so without this the
     * idle-unload countdown starts every time someone takes a photo, and the engine they are
     * about to need gets released while they are framing the shot.
     */
    fun onExternalActivityLaunched() {
        awaitingExternalResult = true
        idleUnloadJob?.cancel()
        idleUnloadJob = null
    }

    /**
     * Starts the idle-unload countdown when the app leaves the foreground.
     *
     * A backgrounded app holding ~3.7 GB is the first thing Android reclaims under pressure, and
     * being killed loses the conversation as well as the weights. Releasing them voluntarily after
     * a few minutes away keeps the process alive and cheap; reloading costs seconds, once.
     */
    fun onAppBackgrounded() {
        if (awaitingExternalResult) {
            Log.i(TAG, "Backgrounded for our own camera/picker; keeping the engine loaded")
            return
        }
        idleUnloadJob?.cancel()
        idleUnloadJob = viewModelScope.launch {
            delay(IDLE_UNLOAD_MS)
            val current = _state.value
            // Never yank the engine out from under a run still finishing in the background.
            if (current.engineStatus is EngineStatus.Ready && !current.isBusy) {
                Log.i(TAG, "Unloading model after ${IDLE_UNLOAD_MS / 60_000} minutes in background")
                translator.close()
                _state.update { it.copy(message = str(R.string.msg_model_unloaded_memory)) }
            }
        }
    }

    fun onAppForegrounded() {
        awaitingExternalResult = false
        idleUnloadJob?.cancel()
        idleUnloadJob = null
    }

    // ---- model acquisition -------------------------------------------------

    fun onDownloadModel(variant: ModelVariant) {
        modelDownloader.start(variant)
        observeDownload(variant)
    }

    fun onCancelDownload(variant: ModelVariant) {
        modelDownloader.cancel(variant)
        downloadJobs.remove(variant)?.cancel()
        refreshModelStates()
    }

    /**
     * Deletes a variant's weights.
     *
     * The engine is closed first when the variant being deleted is the loaded one: removing a file
     * still mapped by the native runtime is a crash, not an error message.
     */
    fun onDeleteModel(variant: ModelVariant) {
        val isActive = variant == _state.value.activeVariant
        if (isActive) {
            translationJob?.cancel()
            translator.close()
        }
        downloadJobs.remove(variant)?.cancel()
        modelDownloader.delete(variant)

        if (isActive) useStubEngine()
        refreshModelStates()
        _state.update { it.copy(message = str(R.string.msg_model_deleted, variant.displayName)) }
    }

    /** Switches which variant translations run on. */
    fun onSelectModel(variant: ModelVariant) {
        if (variant == _state.value.activeVariant) return
        prefs.edit().putString(KEY_ACTIVE_VARIANT, variant.id).apply()
        translationJob?.cancel()
        _state.update { it.copy(activeVariant = variant, message = null) }

        if (modelDownloader.isInstalled(variant)) {
            useRealEngine(variant, force = true)
        } else {
            useStubEngine()
        }
    }

    private fun refreshModelStates() {
        _state.update { current ->
            current.copy(
                modelStates = ModelVariant.entries.associateWith {
                    modelDownloader.currentState(it)
                }
            )
        }
        // Resume watching anything already in flight, e.g. after a process restart.
        ModelVariant.entries
            .filter { modelDownloader.isDownloading(it) }
            .forEach { observeDownload(it) }
    }

    private fun observeDownload(variant: ModelVariant) {
        downloadJobs.remove(variant)?.cancel()
        downloadJobs[variant] = viewModelScope.launch {
            modelDownloader.observe(variant).collect { downloadState ->
                _state.update {
                    it.copy(modelStates = it.modelStates + (variant to downloadState))
                }
                if (downloadState is ModelDownloader.State.Installed &&
                    variant == _state.value.activeVariant
                ) {
                    useRealEngine(variant, force = true)
                }
            }
        }
    }

    /** Swaps in the real engine. Does not load weights — that waits for use or [onLoadModel]. */
    private fun useRealEngine(variant: ModelVariant, force: Boolean = false) {
        if (!force && translator is LiteRtTranslator) return

        engineStatusJob?.cancel()
        translator.close()
        translator = LiteRtTranslator(getApplication(), variant)
        _state.update { it.copy(usingStubEngine = false) }
        observeEngineStatus()
    }

    private fun useStubEngine() {
        engineStatusJob?.cancel()
        translator.close()
        translator = StubTranslator()
        _state.update { it.copy(usingStubEngine = true) }
        observeEngineStatus()
    }

    private fun observeEngineStatus() {
        val active = translator
        engineStatusJob = viewModelScope.launch {
            active.status.collect { status ->
                _state.update { it.copy(engineStatus = status) }
            }
        }
    }

    // ---- push to talk ------------------------------------------------------

    /**
     * The microphone is a toggle, not a hold: first tap starts recording, second tap stops it and
     * kicks off transcribe → translate → speak. Holding a button down for the length of a spoken
     * sentence is awkward when the phone is lying flat on a table between two people.
     */
    fun onMicToggled(side: Side) {
        val current = _state.value
        when {
            // Stop speaking: finish the recording and let the pipeline run.
            current.phase == Phase.LISTENING && current.activeSide == side -> stopRecording(side)

            // Already translating or speaking: the same button abandons the run. A slow
            // translation should never leave the user stuck watching it — they can cut it and
            // start a fresh turn immediately.
            current.activeSide == side && current.phase != Phase.IDLE -> abortRun()

            current.phase == Phase.IDLE -> startRecording(side)

            // The other side's button while this one is mid-run: ignore.
            else -> Unit
        }
    }

    /** Tears down whatever is in flight and returns to idle, ready to record again. */
    private fun abortRun() {
        translationJob?.cancel()
        translationJob = null
        speechToText.cancel()
        speaker.stop()
        // Stops the native decode as well; cancelling the collector alone would leave the engine
        // burning through tokens on a result nobody is going to read.
        translator.cancelGeneration()

        _state.update { it.copy(phase = Phase.IDLE, activeSide = null, message = str(R.string.msg_cancelled)) }
    }

    private fun startRecording(side: Side) {
        if (_state.value.isBusy) return

        speaker.stop()
        translationJob?.cancel()

        _state.update {
            it.copy(
                activeSide = side,
                phase = Phase.LISTENING,
                nearText = "",
                farText = "",
                message = null,
            )
        }

        speechToText.start(
            language = _state.value.languageFor(side),
            listener = object : SpeechToText.Listener {
                override fun onPartial(text: String) = setText(side, text)

                override fun onFinal(text: String) {
                    setText(side, text)
                    translate(from = side, text = text)
                }

                override fun onError(message: String) = failWith(message)
            },
        )
    }

    private fun stopRecording(side: Side) {
        val current = _state.value
        if (current.activeSide != side || current.phase != Phase.LISTENING) return

        // The final transcript arrives asynchronously; show the transition immediately so the
        // tap is acknowledged rather than appearing to do nothing.
        _state.update { it.copy(phase = Phase.TRANSLATING) }
        speechToText.stop()
    }

    /**
     * Replays a panel's own text in that panel's own language — the near panel speaks the near
     * language, the far panel the far one. Useful when the other person missed it, and it needs no
     * model: the text is already translated.
     */
    fun onSpeakAgain(side: Side) {
        val current = _state.value
        if (current.isBusy) return
        val text = current.textFor(side)
        if (text.isBlank()) return

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _state.update { it.copy(phase = Phase.SPEAKING, activeSide = side, message = null) }
            val playbackError = try {
                speaker.speak(text, current.languageFor(side))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Log.e(TAG, "Replay failed", t)
                str(R.string.msg_playback_failed)
            }
            _state.update {
                it.copy(phase = Phase.IDLE, activeSide = null, message = playbackError ?: it.message)
            }
        }
    }

    /**
     * Copies a panel's text to the clipboard.
     *
     * A button rather than text selection, because the far panel is rotated 180° and the platform
     * selection UI — drag handles and the floating copy toolbar — renders in popups that ignore
     * that transform, leaving it unusable for the person opposite. One tap works either way up.
     */
    fun onCopyText(side: Side) {
        val text = _state.value.textFor(side)
        if (text.isBlank()) return

        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            _state.update { it.copy(message = str(R.string.msg_clipboard_unavailable)) }
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))

        // Confirmation is shown inside the panel, not in the top status strip, so the rotated
        // user can actually read it.
        copyFeedbackJob?.cancel()
        copyFeedbackJob = viewModelScope.launch {
            _state.update { it.copy(copiedSide = side) }
            delay(COPY_FEEDBACK_MS)
            _state.update { if (it.copiedSide == side) it.copy(copiedSide = null) else it }
        }
    }

    fun onSwapLanguages() {
        if (_state.value.isBusy) return
        _state.update {
            it.copy(
                nearLanguage = it.farLanguage,
                farLanguage = it.nearLanguage,
                nearText = "",
                farText = "",
            )
        }
        persistLanguages()
    }

    /**
     * Stores the chosen pair so the next launch opens on it.
     *
     * People translate between the same two languages over and over; re-picking both every time
     * the app is reopened is pure friction.
     */
    private fun persistLanguages() {
        val current = _state.value
        prefs.edit()
            .putString(KEY_NEAR_LANGUAGE, current.nearLanguage.tag)
            .putString(KEY_FAR_LANGUAGE, current.farLanguage.tag)
            .apply()
    }

    fun onLanguageSelected(side: Side, language: Language) {
        if (_state.value.isBusy) return
        _state.update {
            when (side) {
                Side.NEAR -> it.copy(nearLanguage = language, nearText = "", farText = "")
                Side.FAR -> it.copy(farLanguage = language, nearText = "", farText = "")
            }
        }
        persistLanguages()
    }

    /**
     * Reads a photo and translates whatever text it contains into the near language.
     *
     * Direction is fixed on purpose: photographing a sign or a menu means you cannot read it, so
     * the result belongs in the phone owner's language on their own panel.
     */
    fun onImagePicked(uri: Uri) {
        if (_state.value.isBusy) return
        val target = _state.value.nearLanguage

        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = Phase.TRANSLATING,
                    activeSide = Side.NEAR,
                    nearText = "",
                    farText = "",
                    message = null,
                )
            }

            val jpeg = withContext(Dispatchers.IO) {
                ImageLoader.readAsJpeg(getApplication(), uri)
            }
            if (jpeg == null) {
                failWith(str(R.string.msg_cannot_read_image))
                return@launch
            }

            if (translator.status.value !is EngineStatus.Ready) {
                translator.prepare()
            }
            if (translator.status.value !is EngineStatus.Ready) {
                val reason = (translator.status.value as? EngineStatus.Unavailable)?.reason
                    ?: str(R.string.msg_engine_unavailable)
                failWith(reason)
                return@launch
            }

            val builder = StringBuilder()
            try {
                translator.translateImage(jpeg, target).collect { delta ->
                    builder.append(delta)
                    setText(Side.NEAR, builder.toString().trim())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Log.e(TAG, "Image translation failed", t)
                val reason = (translator.status.value as? EngineStatus.Unavailable)?.reason
                    ?: t.message ?: str(R.string.msg_cannot_read_image)
                failWith(reason)
                return@launch
            }

            val translated = builder.toString().trim()
            if (translated.isEmpty()) {
                failWith(str(R.string.msg_no_text_in_image))
                return@launch
            }

            _state.update { it.copy(phase = Phase.SPEAKING) }
            val playbackError = try {
                speaker.speak(translated, target)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Log.e(TAG, "Playback failed", t)
                str(R.string.msg_playback_failed)
            }

            _state.update {
                it.copy(phase = Phase.IDLE, activeSide = null, message = playbackError ?: it.message)
            }
        }
    }

    fun onToggleFarRotation() {
        _state.update { it.copy(farPanelRotated = !it.farPanelRotated) }
    }

    /** Returns the layout to defaults: upper panel mirrored, panels split evenly. */
    fun onRestoreLayout() {
        _state.update { it.copy(farPanelRotated = true, splitFraction = 0.5f) }
    }

    /**
     * Translates text typed rather than spoken. Enters the same pipeline as a recording, so the
     * result renders on both panels and is read aloud identically.
     */
    fun onTranslateTypedText(text: String) {
        if (_state.value.isBusy) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        _state.update {
            it.copy(
                nearText = trimmed,
                farText = "",
                activeSide = Side.NEAR,
                message = null,
            )
        }
        translate(from = Side.NEAR, text = trimmed)
    }

    fun onSplitChanged(fraction: Float) {
        _state.update { it.copy(splitFraction = fraction.coerceIn(MIN_SPLIT, MAX_SPLIT)) }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun translate(from: Side, text: String) {
        val target = from.other()
        val snapshot = _state.value

        translationJob = viewModelScope.launch {
            _state.update { it.copy(phase = Phase.TRANSLATING) }

            // Load on demand if the user never pressed the load button. Startup still never loads
            // — the engine costs ~3.7 GB — but forgetting to load should cost a wait, not a
            // failed turn.
            if (translator.status.value !is EngineStatus.Ready) {
                translator.prepare()
            }
            val status = translator.status.value
            if (status !is EngineStatus.Ready) {
                val reason = (status as? EngineStatus.Unavailable)?.reason
                    ?: str(R.string.msg_engine_unavailable)
                failWith(reason)
                return@launch
            }

            val builder = StringBuilder()
            try {
                translator.translate(
                    text = text,
                    from = snapshot.languageFor(from),
                    to = snapshot.languageFor(target),
                ).collect { delta ->
                    builder.append(delta)
                    setText(target, builder.toString().trim())
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Log.e(TAG, str(R.string.msg_engine_unavailable), t)
                // Prefer the engine's own diagnosis: when a backend dies mid-inference it records
                // which one and why, which is more use than the bare native message.
                val reason = (translator.status.value as? EngineStatus.Unavailable)?.reason
                    ?: t.message
                    ?: str(R.string.msg_engine_unavailable)
                failWith(reason)
                return@launch
            }

            val translated = builder.toString().trim()
            if (translated.isEmpty()) {
                failWith(str(R.string.msg_no_translation))
                return@launch
            }

            _state.update { it.copy(phase = Phase.SPEAKING) }
            // Playback must never be able to take the process down: a failure to speak should
            // cost the user the audio, not the conversation.
            val playbackError = try {
                speaker.speak(translated, snapshot.languageFor(target))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Log.e(TAG, "Playback failed", t)
                str(R.string.msg_playback_failed)
            }

            _state.update {
                it.copy(phase = Phase.IDLE, activeSide = null, message = playbackError ?: it.message)
            }
        }
    }

    private fun setText(side: Side, text: String) {
        _state.update {
            when (side) {
                Side.NEAR -> it.copy(nearText = text)
                Side.FAR -> it.copy(farText = text)
            }
        }
    }

    private fun failWith(message: String) {
        _state.update { it.copy(phase = Phase.IDLE, activeSide = null, message = message) }
    }

    override fun onCleared() {
        translationJob?.cancel()
        speechToText.release()
        speaker.shutdown()
        translator.close()
        super.onCleared()
    }

    private companion object {
        const val TAG = "ConversationViewModel"
        const val MIN_SPLIT = 0.25f
        const val MAX_SPLIT = 0.75f
        const val CLIP_LABEL = "Aire Offline Translate"
        const val COPY_FEEDBACK_MS = 1_500L

        /** How long the app may sit in the background before the engine is released. */
        const val IDLE_UNLOAD_MS = 5 * 60 * 1000L
        const val KEY_ACTIVE_VARIANT = "active_variant"
        const val KEY_NEAR_LANGUAGE = "near_language"
        const val KEY_FAR_LANGUAGE = "far_language"
    }
}
