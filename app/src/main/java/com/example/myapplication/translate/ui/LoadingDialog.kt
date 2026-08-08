package com.example.myapplication.translate.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.R

/**
 * Modal shown while the engine loads, blocking the conversation UI behind it.
 *
 * Loading is not a background nicety here: nothing the app offers works until it finishes, and a
 * user who taps the microphone during it gets a request queued behind several seconds of native
 * initialisation with no sign anything is happening. Taking the screen away is the honest signal.
 *
 * Deliberately not dismissible by back press or an outside tap. The only way out is [onCancel],
 * because dismissing the window would hide the load without stopping it.
 */
@Composable
fun LoadingDialog(
    modelName: String,
    cancelling: Boolean,
    onCancel: () -> Unit,
) {
    AlertDialog(
        // Nothing to do: dismissal is only reachable through the cancel button.
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        // The icon slot centres the spinner above the title. Putting it inline beside the title
        // instead leaves it stranded against the left edge as soon as the model name wraps.
        icon = {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.status_loading_model, modelName)) },
        text = {
            Text(
                text = stringResource(
                    if (cancelling) R.string.loading_dialog_cancelling else R.string.loading_dialog_body
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            // Disabled once tapped: the native load cannot be interrupted twice, and a button that
            // still looks live would invite the user to conclude cancelling had failed.
            TextButton(onClick = onCancel, enabled = !cancelling) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = if (cancelling) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
    )
}
