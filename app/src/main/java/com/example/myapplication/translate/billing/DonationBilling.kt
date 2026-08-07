package com.example.myapplication.translate.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-off donations through Google Play Billing.
 *
 * Products are **consumable**, not one-time entitlements. A donation is not something the user
 * owns — it is an act they may want to repeat — and an unconsumed purchase would block every
 * later donation at the same tier with "you already own this item".
 *
 * Prices are never hardcoded for display. Play returns a formatted, localised, currency-correct
 * string per tier, and that is what the UI shows; the dollar figures below only name the products.
 */
class DonationBilling(context: Context) {

    data class Tier(val productId: String, val formattedPrice: String, val details: ProductDetails)

    sealed interface State {
        data object Connecting : State
        data class Ready(val tiers: List<Tier>) : State
        data class Unavailable(val reason: String) : State
        data object Purchasing : State
        data object Thanks : State
    }

    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state.asStateFlow()

    private val purchasesUpdated = com.android.billingclient.api.PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                purchases.forEach(::consume)

            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                refreshProducts()

            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                refreshProducts()
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdated)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        // Play can drop the service binding; without this every later query fails silently.
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) {
            refreshProducts()
            return
        }
        _state.value = State.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshProducts()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                    _state.value = State.Unavailable(result.debugMessage.ifBlank { "Billing unavailable" })
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    private fun refreshProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PRODUCT_IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Product query failed: ${result.responseCode} ${result.debugMessage}")
                _state.value = State.Unavailable(result.debugMessage.ifBlank { "Could not load donation options" })
                return@queryProductDetailsAsync
            }

            val tiers = queryResult.productDetailsList
                .mapNotNull { details ->
                    val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: return@mapNotNull null
                    Tier(details.productId, price, details)
                }
                // Play returns products in arbitrary order; show them cheapest first.
                .sortedBy { PRODUCT_IDS.indexOf(it.productId) }

            _state.value = if (tiers.isEmpty()) {
                // Expected until the products exist in Play Console and the app is installed from
                // a Play track — a sideloaded build can connect but will never see products.
                Log.w(TAG, "No donation products returned; unfetched=${queryResult.unfetchedProductList.size}")
                State.Unavailable("No donation options available")
            } else {
                State.Ready(tiers)
            }
        }
    }

    fun launch(activity: Activity, tier: Tier) {
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(tier.details)
                        .build()
                )
            )
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.responseCode} ${result.debugMessage}")
        } else {
            _state.value = State.Purchasing
        }
    }

    /** Consuming makes the tier purchasable again, which is the whole point for a donation. */
    private fun consume(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.consumeAsync(params) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.value = State.Thanks
            } else {
                Log.w(TAG, "Consume failed: ${result.responseCode} ${result.debugMessage}")
                refreshProducts()
            }
        }
    }

    fun release() {
        runCatching { client.endConnection() }
    }

    companion object {
        private const val TAG = "DonationBilling"

        /**
         * Must exist as one-time (consumable) products in Play Console under exactly these IDs.
         * Order here is the order shown to the user.
         */
        val PRODUCT_IDS = listOf(
            "donate_1",
            "donate_5",
            "donate_10",
            "donate_25",
            "donate_50",
            "donate_100",
        )
    }
}
