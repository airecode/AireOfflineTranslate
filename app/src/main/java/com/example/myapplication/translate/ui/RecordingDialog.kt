package com.example.myapplication.translate.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.R

/**
 * Modal shown while a side is recording.
 *
 * The bar is a live microphone level, not elapsed time. Recording has no known length, so a
 * progress bar can only honestly report one thing — whether the microphone is hearing anything —
 * and that happens to be the single question a user has while speaking into a phone lying on a
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
    // into a bar that moves with the voice rather than one that steps.
    val animatedLevel by animateFloatAsState(targetValue = level, label = "micLevel")

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text(stringResource(R.string.recording_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = partialText.ifBlank { stringResource(R.string.recording_dialog_hint) },
                    fontSize = 13.sp,
                    color = if (partialText.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                LinearProgressIndicator(
                    progress = { animatedLevel },
                    // Padding before height: the other way round the padding eats into the 6dp
                    // rather than sitting outside it, and the bar collapses.
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    // Tinted rather than surfaceVariant, which is close enough to the dialog's own
                    // surface that at silence the bar looked absent instead of empty.
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                )
            }
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
