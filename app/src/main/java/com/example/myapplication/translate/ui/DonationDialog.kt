package com.example.myapplication.translate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.translate.billing.DonationBilling

/**
 * Donation tiers, priced by Play rather than by us.
 *
 * Each button shows [DonationBilling.Tier.formattedPrice] — the localised string Play returns for
 * the user's own country and currency. Hardcoding "$5" would show dollars to someone paying in
 * yen, at a number that is not what they would be charged.
 */
@Composable
fun DonationDialog(
    state: DonationBilling.State,
    onSelect: (DonationBilling.Tier) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.donate_title)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.donate_body),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (state) {
                    is DonationBilling.State.Connecting,
                    is DonationBilling.State.Purchasing -> {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    is DonationBilling.State.Ready -> {
                        state.tiers.forEach { tier ->
                            Button(
                                onClick = { onSelect(tier) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(tier.formattedPrice)
                            }
                        }
                    }

                    is DonationBilling.State.Thanks -> {
                        Text(
                            text = stringResource(R.string.donate_thanks),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                    }

                    is DonationBilling.State.Unavailable -> {
                        Text(
                            text = stringResource(R.string.donate_unavailable),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
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
