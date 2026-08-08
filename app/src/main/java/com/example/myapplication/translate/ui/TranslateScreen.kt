package com.example.myapplication.translate.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.BuildConfig
import com.example.myapplication.R
import com.example.myapplication.translate.Language
import com.example.myapplication.translate.Languages
import com.example.myapplication.translate.translator.EngineStatus
import com.example.myapplication.translate.translator.ModelDownloader
import java.util.Locale

/**
 * Face-to-face conversation screen.
 *
 * Both halves are the same [TranslatorPanel]; the far one is simply rotated 180°, which is what
 * makes the layout readable from across a table. Because the panel lays out as
 * transcript → microphone → language chips, rotating it puts the chips under the app bar and the
 * microphone within thumb reach of the person opposite — matching the reference design exactly.
 */
@Composable
fun TranslateScreen(
    state: TranslateUiState,
    micPermissionGranted: Boolean,
    onMicToggled: (Side) -> Unit,
    onSpeakAgain: (Side) -> Unit,
    onCopyText: (Side) -> Unit,
    onSwapLanguages: () -> Unit,
    onLanguageSelected: (Side, Language) -> Unit,
    onSplitChanged: (Float) -> Unit,
    onDonate: () -> Unit,
    onToggleFarRotation: () -> Unit,
    onRestoreLayout: () -> Unit,
    onTranslateTypedText: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onScanCamera: () -> Unit,
    onRestartSession: () -> Unit,
    onManageModels: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var splitAreaHeight by remember { mutableIntStateOf(1) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        TopBar(
            onDonate = onDonate,
            onManageModels = onManageModels,
            engineStatus = state.engineStatus,
            farRotated = state.farPanelRotated,
            onToggleFarRotation = onToggleFarRotation,
            onRestoreLayout = onRestoreLayout,
            onLoadModel = onLoadModel,
            onUnloadModel = onUnloadModel,
        )
        StatusStrip(state, micPermissionGranted)
        ModelBanner(
            variantName = state.activeVariant.displayName,
            sizeGb = state.activeVariant.sizeGb,
            modelState = state.activeModelState,
            onManage = onManageModels,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { splitAreaHeight = it.height.coerceAtLeast(1) }
        ) {
            TranslatorPanel(
                modifier = Modifier.weight(state.splitFraction),
                rotated = state.farPanelRotated,
                side = Side.FAR,
                state = state,
                micEnabled = micPermissionGranted,
                onMicToggled = { onMicToggled(Side.FAR) },
                onSpeakAgain = { onSpeakAgain(Side.FAR) },
                onCopyText = { onCopyText(Side.FAR) },
                onTranslateTypedText = onTranslateTypedText,
                onPickPhoto = onPickPhoto,
                onScanCamera = onScanCamera,
                onRestartSession = onRestartSession,
                onSwapLanguages = onSwapLanguages,
                onLanguageSelected = onLanguageSelected,
            )

            SplitHandle(
                onDragDelta = { delta ->
                    onSplitChanged(state.splitFraction + delta / splitAreaHeight)
                }
            )

            TranslatorPanel(
                modifier = Modifier.weight(1f - state.splitFraction),
                rotated = false,
                side = Side.NEAR,
                state = state,
                micEnabled = micPermissionGranted,
                onMicToggled = { onMicToggled(Side.NEAR) },
                onSpeakAgain = { onSpeakAgain(Side.NEAR) },
                onCopyText = { onCopyText(Side.NEAR) },
                onTranslateTypedText = onTranslateTypedText,
                onPickPhoto = onPickPhoto,
                onScanCamera = onScanCamera,
                onRestartSession = onRestartSession,
                onSwapLanguages = onSwapLanguages,
                onLanguageSelected = onLanguageSelected,
            )
        }
    }
}

@Composable
private fun TopBar(
    engineStatus: EngineStatus,
    farRotated: Boolean,
    onDonate: () -> Unit,
    onManageModels: () -> Unit,
    onToggleFarRotation: () -> Unit,
    onRestoreLayout: () -> Unit,
    onLoadModel: () -> Unit,
    onUnloadModel: () -> Unit,
) {
    val loaded = engineStatus is EngineStatus.Ready
    val loading = engineStatus is EngineStatus.Loading
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDonate) {
            Icon(
                painter = painterResource(R.drawable.ic_donate),
                contentDescription = stringResource(R.string.cd_donate),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        // Flips the upper panel between facing the other person and facing you.
        IconButton(onClick = onToggleFarRotation) {
            Icon(
                painter = painterResource(R.drawable.ic_flip),
                contentDescription = stringResource(R.string.cd_flip_upper),
                tint = if (farRotated) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        IconButton(onClick = onRestoreLayout) {
            Icon(
                painter = painterResource(R.drawable.ic_restore),
                contentDescription = stringResource(R.string.cd_restore_layout),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        // Build stamp. Cheap, and it removes any doubt about which APK is actually running when a
        // bug report arrives as a screenshot.
        Text(
            text = BuildConfig.VERSION_NAME,
            fontSize = 10.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        // Permanent entry to model management. The banner below only appears when nothing is
        // installed, so without this there is no route to switching or deleting a model once one
        // is on disk.
        IconButton(onClick = onManageModels) {
            Icon(
                painter = painterResource(R.drawable.ic_models),
                contentDescription = stringResource(R.string.cd_models),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Loading is manual: the engine costs several GB of RAM, so the user decides when to pay it.
        IconButton(onClick = onLoadModel, enabled = !loaded && !loading) {
            Icon(
                painter = painterResource(R.drawable.ic_model_load),
                contentDescription = stringResource(R.string.cd_load_model),
                tint = if (loaded || loading) dimmed else MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onUnloadModel, enabled = loaded) {
            Icon(
                painter = painterResource(R.drawable.ic_model_unload),
                contentDescription = stringResource(R.string.cd_unload_model),
                tint = if (loaded) MaterialTheme.colorScheme.onSurfaceVariant else dimmed,
            )
        }
    }
}

/** One line of feedback: engine loading, the current phase, or the most recent error. */
@Composable
private fun StatusStrip(state: TranslateUiState, micPermissionGranted: Boolean) {
    val engineStatus = state.engineStatus
    val text: String? = when {
        !micPermissionGranted -> stringResource(R.string.status_mic_permission)
        state.message != null -> state.message
        // Phase first: during a run the user cares what the app is doing, not what is loaded.
        // TRANSLATING is absent deliberately — RunProgressDialog owns that phase, and repeating the
        // message in the strip underneath it was noise.
        state.phase == Phase.LISTENING -> stringResource(R.string.status_recording)
        state.phase == Phase.SPEAKING -> stringResource(R.string.status_speaking)
        // Names the variant being loaded. A hardcoded model name here made switching to E2B
        // look like it had not taken effect.
        engineStatus is EngineStatus.Loading ->
            stringResource(R.string.status_loading_model, state.activeVariant.displayName)
        engineStatus is EngineStatus.Unavailable -> engineStatus.reason
        engineStatus is EngineStatus.Ready ->
            if (state.usingStubEngine) {
                stringResource(R.string.status_demo_engine)
            } else {
                // Names the variant, not just the backend: with two models installed, "loaded"
                // alone does not tell you which one you are actually translating with.
                stringResource(
                    R.string.status_model_loaded,
                    state.activeVariant.displayName,
                    engineStatus.backend ?: stringResource(R.string.status_unknown_backend),
                )
            }
        // Loading is manual now, so an unloaded engine has to be visible — otherwise the first
        // recording fails with no hint as to why.
        else -> stringResource(R.string.status_model_not_loaded)
    }

    if (text == null) {
        Spacer(Modifier.height(1.dp))
        return
    }

    Text(
        text = text,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp),
    )
}

/**
 * First-run model acquisition. The 2.59 GB weights cannot ship in the APK — Play caps a base
 * module at 200 MB — so the app fetches them once, here.
 */
@Composable
private fun ModelBanner(
    variantName: String,
    sizeGb: String,
    modelState: ModelDownloader.State,
    onManage: () -> Unit,
) {
    // A ready model needs no banner; the status strip already says which one is loaded.
    if (modelState is ModelDownloader.State.Installed) return

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when (modelState) {
            is ModelDownloader.State.InProgress -> {
                Text(
                    text = stringResource(
                        R.string.banner_downloading,
                        variantName,
                        formatGb(modelState.bytesDone),
                        formatGb(modelState.bytesTotal),
                    ),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { modelState.fraction },
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is ModelDownloader.State.Failed -> {
                Text(
                    text = modelState.reason,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            else -> {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.banner_model_missing),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$variantName · $sizeGb · " +
                                stringResource(R.string.banner_wifi_only),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onManage,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Text(stringResource(R.string.action_models))
                    }
                }
            }
        }
    }
}

private fun formatGb(bytes: Long): String =
    String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000.0)

@Composable
private fun TranslatorPanel(
    modifier: Modifier,
    rotated: Boolean,
    side: Side,
    state: TranslateUiState,
    micEnabled: Boolean,
    onMicToggled: () -> Unit,
    onSpeakAgain: () -> Unit,
    onCopyText: () -> Unit,
    onTranslateTypedText: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onScanCamera: () -> Unit,
    onRestartSession: () -> Unit,
    onSwapLanguages: () -> Unit,
    onLanguageSelected: (Side, Language) -> Unit,
) {
    val language = state.languageFor(side)
    val text = state.textFor(side)
    val isActive = state.activeSide == side

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (rotated) Modifier.rotate(180f) else Modifier)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                // Each panel gets its own SelectionContainer so the two speakers can select and
                // copy independently without one clearing the other's selection.
                SelectionContainer {
                    Text(
                        text = text.ifEmpty { language.readyPrompt },
                        fontSize = if (text.isEmpty()) 19.sp else 22.sp,
                        lineHeight = if (text.isEmpty()) 25.sp else 29.sp,
                        textAlign = TextAlign.Center,
                        color = if (text.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth()) {
                MicButton(
                    mode = when {
                        !isActive || state.phase == Phase.IDLE -> MicMode.IDLE
                        state.phase == Phase.LISTENING -> MicMode.RECORDING
                        else -> MicMode.CANCEL
                    },
                    // Both panels share one audio input, so only one side may drive a run. The
                    // active side keeps its button live throughout so it can stop or cancel.
                    enabled = micEnabled && (state.phase == Phase.IDLE || isActive),
                    onToggle = onMicToggled,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Copy left, speak right, both flanking the centred microphone and all three the
                // same circular control — see CircleButton.
                if (text.isNotBlank()) {
                    val copied = state.copiedSide == side

                    CircleButton(
                        painter = painterResource(
                            if (copied) R.drawable.ic_check else R.drawable.ic_copy
                        ),
                        contentDescription = stringResource(
                            if (copied) R.string.cd_copied else R.string.cd_copy_text
                        ),
                        enabled = true,
                        onClick = onCopyText,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp),
                    )

                    CircleButton(
                        painter = painterResource(R.drawable.ic_volume_up),
                        contentDescription = stringResource(R.string.cd_speak_again),
                        enabled = !state.isBusy,
                        onClick = onSpeakAgain,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp),
                    )
                }
            }

            // Typed input belongs to the lower panel only — it is the one whose owner is holding
            // the phone and can reach a keyboard.
            if (side == Side.NEAR) {
                TypedInputRow(
                    enabled = !state.isBusy,
                    onSubmit = onTranslateTypedText,
                    onPickPhoto = onPickPhoto,
                    onScanCamera = onScanCamera,
                    canRestart = !state.isBusy && state.hasTranscript,
                    onRestartSession = onRestartSession,
                )
            }

            Spacer(Modifier.height(10.dp))

            LanguagePairRow(
                ownSide = side,
                own = language,
                other = state.languageFor(side.other()),
                enabled = !state.isBusy,
                onSwap = onSwapLanguages,
                onSelect = onLanguageSelected,
            )

            Spacer(Modifier.height(10.dp))
        }
    }
}

/**
 * Keyboard entry for the lower panel. Collapsed to a single icon until wanted, so it costs no
 * vertical space in the common case where the conversation is spoken.
 */
@Composable
private fun TypedInputRow(
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onScanCamera: () -> Unit,
    canRestart: Boolean,
    onRestartSession: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Clearing focus is what actually dismisses the soft keyboard; without it the IME stays up
    // over a collapsed row.
    fun collapse() {
        focusManager.clearFocus()
        draft = ""
        expanded = false
    }

    fun submit() {
        if (draft.isBlank()) return
        onSubmit(draft)
        collapse()
    }

    Spacer(Modifier.height(10.dp))

    if (!expanded) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { expanded = true }, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard),
                    contentDescription = stringResource(R.string.cd_type_text),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onScanCamera, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = stringResource(R.string.cd_camera),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onPickPhoto, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_gallery),
                    contentDescription = stringResource(R.string.cd_pick_photo),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // Only once there is a finished turn to clear. Shown permanently it would be a button
            // that does nothing for most of the app's life.
            if (canRestart) {
                IconButton(onClick = onRestartSession) {
                    Icon(
                        painter = painterResource(R.drawable.ic_restart),
                        contentDescription = stringResource(R.string.cd_restart),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Without this the row is a one-way door: expanding hides the camera and gallery buttons,
        // and only a non-empty submit could get them back.
        IconButton(onClick = { collapse() }) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.cd_close_input),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(stringResource(R.string.hint_type_text)) },
            enabled = enabled,
            // Single-line on purpose: a multi-line field ignores imeAction entirely and gives the
            // keyboard a newline key instead of Send, so the declared action never fires.
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { submit() }, enabled = enabled && draft.isNotBlank()) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = stringResource(R.string.cd_send_text),
                tint = if (draft.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/**
 * The one circular control the panel uses for record, copy and replay.
 *
 * Shared rather than three lookalikes so the size, shape and disabled treatment cannot drift
 * apart — they sit side by side and any difference reads as a mistake.
 */
@Composable
private fun CircleButton(
    painter: Painter,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    scale: Float = 1f,
    iconSize: Dp = 26.dp,
) {
    Box(
        modifier
            .size(BUTTON_DIAMETER)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

private val BUTTON_DIAMETER = 56.dp

/** What the primary button does right now. */
private enum class MicMode { IDLE, RECORDING, CANCEL }

/**
 * The panel's primary button, in one of three states.
 *
 * Recording and cancelling are deliberately different glyphs. Both are destructive-looking red,
 * but a square means "stop capturing, keep what I said" while a cross means "throw this run away"
 * — sharing one icon for both made a stop and an abort look identical.
 */
@Composable
private fun MicButton(
    mode: MicMode,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (mode == MicMode.RECORDING) 1.15f else 1f,
        label = "micScale",
    )

    val icon = when (mode) {
        MicMode.IDLE -> R.drawable.ic_mic
        MicMode.RECORDING -> R.drawable.ic_stop
        MicMode.CANCEL -> R.drawable.ic_close
    }
    val description = when (mode) {
        MicMode.IDLE -> R.string.cd_start_recording
        MicMode.RECORDING -> R.string.cd_stop_recording
        MicMode.CANCEL -> R.string.cd_cancel_run
    }

    CircleButton(
        painter = painterResource(icon),
        contentDescription = stringResource(description),
        enabled = enabled,
        onClick = onToggle,
        modifier = modifier,
        containerColor = if (mode == MicMode.IDLE) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        scale = scale,
        iconSize = if (mode == MicMode.IDLE) 26.dp else 22.dp,
    )
}

/**
 * Own language on the left, the other person's on the right.
 *
 * Stated relative to the panel rather than to the screen on purpose: the far panel is rotated, so
 * hard-coding near-then-far would put the *other* person's language on the far user's left. Each
 * speaker should see their own language in the source position.
 */
@Composable
private fun LanguagePairRow(
    ownSide: Side,
    own: Language,
    other: Language,
    enabled: Boolean,
    onSwap: () -> Unit,
    onSelect: (Side, Language) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageChip(
            language = own,
            enabled = enabled,
            onSelect = { onSelect(ownSide, it) },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSwap, enabled = enabled) {
            Icon(
                painter = painterResource(R.drawable.ic_swap_horiz),
                contentDescription = stringResource(R.string.cd_swap_languages),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        LanguageChip(
            language = other,
            enabled = enabled,
            onSelect = { onSelect(ownSide.other(), it) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LanguageChip(
    language: Language,
    enabled: Boolean,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        // Single line: the region was dropped to save vertical space, so `shortName` carries any
        // disambiguation the region used to provide.
        Text(
            text = language.shortName,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled) { expanded = true }
                .padding(vertical = 8.dp, horizontal = 10.dp),
        )

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Languages.ALL.forEach { option ->
                DropdownMenuItem(
                    // English name plus the endonym: recognisable whichever of the two the reader
                    // is looking for, and it separates the Chinese scripts without a country.
                    text = { Text("${option.name} · ${option.nativeName}") },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Drag to give one speaker more of the screen than the other. */
@Composable
private fun SplitHandle(onDragDelta: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> onDragDelta(delta) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            Modifier
                .width(46.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(2) {
                    Box(
                        Modifier
                            .width(30.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }
    }
}
