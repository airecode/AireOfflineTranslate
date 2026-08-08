package com.example.myapplication.translate.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.translate.translator.ModelDownloader
import com.example.myapplication.translate.translator.ModelVariant

/**
 * Model manager: download, delete, and choose which variant translations run on.
 *
 * Variants are listed whether installed or not, so the download size is visible before committing
 * to it rather than after. Only E2B ships today, so the selection control hides itself rather than
 * offering a radio button with nothing to switch to.
 */
@Composable
fun ModelsDialog(
    activeVariant: ModelVariant,
    states: Map<ModelVariant, ModelDownloader.State>,
    onSelect: (ModelVariant) -> Unit,
    onDownload: (ModelVariant) -> Unit,
    onCancelDownload: (ModelVariant) -> Unit,
    onDelete: (ModelVariant) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.models_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.models_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ModelVariant.entries.forEach { variant ->
                    HorizontalDivider(
                        Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ModelRow(
                        variant = variant,
                        state = states[variant] ?: ModelDownloader.State.Absent,
                        isActive = variant == activeVariant,
                        onSelect = { onSelect(variant) },
                        onDownload = { onDownload(variant) },
                        onCancelDownload = { onCancelDownload(variant) },
                        onDelete = { onDelete(variant) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun ModelRow(
    variant: ModelVariant,
    state: ModelDownloader.State,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    // A radio button is only meaningful when there is something to switch to.
    val selectable = ModelVariant.entries.size > 1

    Row(
        // The whole row is the tap target, not just the radio circle. A ~20dp hit area next to a
        // full-width label is a trap: tapping the model name looks like selecting it and does
        // nothing. `selectable` also gives the row the right accessibility role.
        //
        // Selectable whether or not it is installed: choosing which model you want is what you do
        // *before* downloading it, and gating selection on installation left every row dead on a
        // fresh install.
        Modifier
            .fillMaxWidth()
            .then(
                if (selectable) {
                    Modifier.selectable(
                        selected = isActive,
                        role = Role.RadioButton,
                        onClick = onSelect,
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            RadioButton(
                selected = isActive,
                onClick = null, // handled by the row
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = variant.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (variant) {
                    ModelVariant.E2B -> stringResource(R.string.models_e2b_note, variant.sizeGb)
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    when (state) {
        is ModelDownloader.State.InProgress -> {
            LinearProgressIndicator(
                progress = { state.fraction },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCancelDownload) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        is ModelDownloader.State.Installed -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        is ModelDownloader.State.Failed -> {
            Text(
                text = state.reason,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onDownload) {
                Text(
                    text = stringResource(R.string.action_retry),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ModelDownloader.State.Absent -> {
            TextButton(onClick = onDownload) {
                Text(
                    text = stringResource(R.string.action_download),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
