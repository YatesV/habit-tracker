package com.zerorizz.habits

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/** What Habitify Pro unlocks. Edit this list to change the free/Pro split. */
object Pro {
    const val FREE_HABIT_LIMIT = 5
    const val PRODUCT_ID = "habitify_pro"

    val perks = listOf(
        "♾️" to "Unlimited habits (free: $FREE_HABIT_LIMIT)",
        "😊" to "Mood Tracker add-on with mood-boosting insights",
        "📦" to "Import community habit packs and themes",
        "🎨" to "Premium theme presets and custom accent colors",
        "💛" to "Support an indie developer",
    )

    fun canAddHabits(data: AppData, count: Int = 1): Boolean =
        data.isPro || data.activeHabits.size + count <= FREE_HABIT_LIMIT

    fun slotsLeft(data: AppData): Int =
        if (data.isPro) Int.MAX_VALUE else (FREE_HABIT_LIMIT - data.activeHabits.size).coerceAtLeast(0)
}

data class ProOffer(val label: String, val price: String, val details: ProductDetails, val offerToken: String)

/**
 * Google Play Billing for the Pro subscription.
 * Works only when the app is installed from Google Play and the "habitify_pro"
 * subscription exists in Play Console. Otherwise it reports "unavailable".
 */
class BillingManager(context: Context, private val onPro: (Boolean) -> Unit) {
    enum class Status { CONNECTING, READY, UNAVAILABLE }

    var status by mutableStateOf(Status.CONNECTING)
        private set
    var offers by mutableStateOf<List<ProOffer>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private val client: BillingClient? = runCatching {
        BillingClient.newBuilder(context)
            .setListener { result, purchases -> handlePurchases(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
    }.getOrNull()

    fun start() {
        val c = client ?: run { status = Status.UNAVAILABLE; return }
        runCatching {
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        loadOffers()
                        restore()
                    } else {
                        status = Status.UNAVAILABLE
                    }
                }

                override fun onBillingServiceDisconnected() {
                    status = Status.UNAVAILABLE
                }
            })
        }.onFailure { status = Status.UNAVAILABLE }
    }

    private fun loadOffers() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(Pro.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        ).build()
        c.queryProductDetailsAsync(params) { result, list ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                list.flatMap { pd ->
                    pd.subscriptionOfferDetails.orEmpty().map { offer ->
                        val phase = offer.pricingPhases.pricingPhaseList.lastOrNull()
                        val period = when (phase?.billingPeriod) {
                            "P1M" -> "Monthly"
                            "P1Y" -> "Yearly"
                            "P1W" -> "Weekly"
                            else -> "Pro"
                        }
                        ProOffer(period, phase?.formattedPrice ?: "", pd, offer.offerToken)
                    }
                }
            } else emptyList()
            offers = found
            status = if (found.isEmpty()) Status.UNAVAILABLE else Status.READY
        }
    }

    fun restore() {
        val c = client ?: return
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val active = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && Pro.PRODUCT_ID in it.products }
                purchases.forEach { acknowledge(it) }
                onPro(active)
            }
        }
    }

    fun buy(activity: Activity, offer: ProOffer) {
        val c = client ?: return
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(offer.details)
                    .setOfferToken(offer.offerToken)
                    .build()
            )
        ).build()
        c.launchBillingFlow(activity, params)
    }

    private fun handlePurchases(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { p ->
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledge(p)
                        onPro(true)
                        message = "Welcome to Pro 👑"
                    } else if (p.purchaseState == Purchase.PurchaseState.PENDING) {
                        message = "Payment pending. Pro unlocks when it goes through."
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> message = null
            else -> message = "Purchase didn't go through (${result.debugMessage.ifBlank { "code ${result.responseCode}" }})"
        }
    }

    private fun acknowledge(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED || p.isAcknowledged) return
        client?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
        ) { }
    }
}
