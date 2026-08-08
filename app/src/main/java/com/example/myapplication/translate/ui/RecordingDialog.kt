package com.example.myapplication.translate.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.R

/**
 * Modal shown while a side is recording.
 *
 * The ring turns continuously for as long as recording lasts, and its arc length is a live
 * microphone level. Neither is elapsed time: recording has no known length, so a progress indicator
 * can only honestly report that it is running and that the microphone is hearing something — which
 * between them are the whole of what a user wants to know while speaking into a phone lying on a
 * table.
 *
 * [partialText] is the recogniser's running hypothesis. It is already going to the panel behind
 * this dialog, but the dialog covers it, and watching the words appear is how the user knows they
 * are being understood rather than merely heard.
 *
 * Not dismissible by back press or an outside tap: the two ways out are the two buttons, which mean
 * different things. Done keeps the turn, Cancel throws it away.
 */
@Composable
fun RecordingDialog(
    level: Float,
    partialText: String,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    // The recogniser reports roughly ten times a second; animating between readings turns that
    // into a ring that moves with the voice rather than one that steps.
    val animatedLevel by animateFloatAsState(targetValue = level, label = "micLevel")

    // Floored so there is always an arc to see turning. Without it silence leaves the ring empty,
    // the rotation becomes invisible, and the dialog looks frozen at exactly the moment the user is
    // wondering whether the microphone is on. Presence says "recording", length still says "level".
    val arc = MIN_ARC + animatedLevel * (1f - MIN_ARC)

    val spin = rememberInfiniteTransition(label = "recordSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            // Linear, and Restart rather than Reverse: anything else makes the ring slow at the
            // wrap or swing back on itself instead of turning steadily.
            animation = tween(durationMillis = SPIN_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "recordSpinAngle",
    )

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        // The icon slot centres the meter above the title, which is where LoadingDialog puts its
        // spinner — the two read as siblings rather than two unrelated designs.
        icon = {
            CircularProgressIndicator(
                progress = { arc },
                // Rotating the whole indicator rather than animating its start angle: the arc keeps
                // its determinate length, so the level reading survives the spin.
                modifier = Modifier
                    .size(40.dp)
                    .rotate(angle),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary,
                // Tinted rather than surfaceVariant, which is close enough to the dialog's own
                // surface that at silence the ring looked absent instead of empty.
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            )
        },
        title = { Text(stringResource(R.string.recording_dialog_title)) },
        text = {
            Text(
                text = partialText.ifBlank { stringResource(R.string.recording_dialog_hint) },
                fontSize = 13.sp,
                color = if (partialText.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDone) {
                Text(
                    text = stringResource(R.string.action_done),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** One full revolution. Fast, because it has to read as "live" at a glance. */
private const val SPIN_MS = 500

/** Shortest arc drawn, as a fraction of the ring. Enough to see turning in silence. */
private const val MIN_ARC = 0.10f
