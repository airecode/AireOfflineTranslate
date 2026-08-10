package com.example.myapplication.translate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.translate.billing.DonationBilling

/**
 * Tip tiers, priced by Play rather than by us.
 *
 * Each button shows [DonationBilling.Tier.formattedPrice] — the localised string Play returns for
 * the user's own country and currency. Hardcoding "$5" would show dollars to someone paying in
 * yen, at a number that is not what they would be charged, which is also why the treat beside the
 * price never names an amount: "buy me a coffee" travels, "$5 coffee" does not.
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
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    val label = treatLabel(tier.productId)
                                    if (label != null) Text(stringResource(label))
                                    Text(tier.formattedPrice)
                                }
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

/**
 * The treat each tier stands for.
 *
 * Deliberately kept here rather than on the billing tier: these are copy, they change with the
 * wording rather than with the catalogue, and the billing layer has no business holding string
 * resources. An unrecognised id falls back to showing the price alone, so adding a product in Play
 * Console before adding its label here degrades to something sane rather than crashing.
 */
@StringRes
private fun treatLabel(productId: String): Int? = when (productId) {
    "donate_1" -> R.string.treat_1
    "donate_5" -> R.string.treat_5
    "donate_10" -> R.string.treat_10
    "donate_25" -> R.string.treat_25
    "donate_50" -> R.string.treat_50
    "donate_100" -> R.string.treat_100
    else -> null
}
