package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.coroutines.resume

interface PlayBillingGateway {
    val purchaseUpdates: Flow<PlayPurchaseUpdate>
    suspend fun isPlayStoreAvailable(): Boolean
    suspend fun querySubscriptionProducts(
        productIds: List<String> = BillingCatalogIds.ALL_PLAY_SUBSCRIPTION_PRODUCTS
    ): Result<Map<String, PlayStoreProductPrice>>
    suspend fun queryActiveSubscriptions(): Result<List<PlayPurchaseRecord>>
    suspend fun launchSubscriptionPurchase(
        activity: Activity,
        productId: String,
        basePlanId: String,
        oldPurchaseToken: String? = null,
        obfuscatedAccountId: String? = null
    ): Result<Unit>
    suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit>
    fun endConnection()
}

class GooglePlayBillingGateway(
    context: Context
) : PlayBillingGateway {
    private val appContext = context.applicationContext
    private val connectMutex = Mutex()
    private val cachedProductDetails = mutableMapOf<String, ProductDetails>()

    private val _purchaseUpdates = MutableSharedFlow<PlayPurchaseUpdate>(extraBufferCapacity = 16)
    override val purchaseUpdates: Flow<PlayPurchaseUpdate> = _purchaseUpdates.asSharedFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val mapped = purchases.orEmpty().map { it.toPlayPurchaseRecord() }
                _purchaseUpdates.tryEmit(PlayPurchaseUpdate.Success(mapped))
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseUpdates.tryEmit(PlayPurchaseUpdate.Cancelled("Purchase cancelled in Google Play."))
            }
            else -> {
                _purchaseUpdates.tryEmit(
                    PlayPurchaseUpdate.Error(
                        responseCode = billingResult.responseCode,
                        message = billingResult.debugMessage.ifBlank { "Google Play Billing error (${billingResult.responseCode})." }
                    )
                )
            }
        }
    }

    @Volatile
    private var billingClientInstance: BillingClient? = null

    private fun getOrCreateBillingClient(): BillingClient {
        return billingClientInstance ?: synchronized(this) {
            billingClientInstance ?: BillingClient.newBuilder(appContext)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .enablePrepaidPlans()
                        .build()
                )
                .enableAutoServiceReconnection()
                .build()
                .also { billingClientInstance = it }
        }
    }

    private suspend fun ensureConnected(): Result<BillingClient> = connectMutex.withLock {
        val client = getOrCreateBillingClient()
        if (client.isReady) {
            return@withLock Result.success(client)
        }
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (!cont.isActive) return
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        cont.resume(Result.success(client))
                    } else {
                        cont.resume(
                            Result.failure(
                                IllegalStateException(
                                    billingResult.debugMessage.ifBlank {
                                        "Google Play Billing unavailable (code ${billingResult.responseCode})."
                                    }
                                )
                            )
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // enableAutoServiceReconnection handles transient disconnects automatically.
                }
            })
        }
    }

    override suspend fun isPlayStoreAvailable(): Boolean = ensureConnected().isSuccess

    override suspend fun querySubscriptionProducts(
        productIds: List<String>
    ): Result<Map<String, PlayStoreProductPrice>> {
        if (productIds.isEmpty()) return Result.success(emptyMap())
        val client = ensureConnected().getOrElse { return Result.failure(it) }

        val products = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return Result.failure(
                IllegalStateException(
                    result.billingResult.debugMessage.ifBlank {
                        "Failed to query Play Store products (${result.billingResult.responseCode})."
                    }
                )
            )
        }

        val priceMap = mutableMapOf<String, PlayStoreProductPrice>()
        result.productDetailsList.orEmpty().forEach { details ->
            cachedProductDetails[details.productId] = details
            details.subscriptionOfferDetails.orEmpty().forEach { offer ->
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull()
                if (phase != null) {
                    val key = "${details.productId}:${offer.basePlanId}"
                    val current = priceMap[key]
                    if (current == null || offer.offerId.isNullOrBlank()) {
                        priceMap[key] = PlayStoreProductPrice(
                            productId = details.productId,
                            basePlanId = offer.basePlanId,
                            offerId = offer.offerId.orEmpty(),
                            offerToken = offer.offerToken,
                            formattedPrice = phase.formattedPrice,
                            currencyCode = phase.priceCurrencyCode,
                            priceAmountMicros = phase.priceAmountMicros,
                            billingPeriodIso = phase.billingPeriod
                        )
                    }
                }
            }
        }
        return Result.success(priceMap)
    }

    override suspend fun queryActiveSubscriptions(): Result<List<PlayPurchaseRecord>> {
        val client = ensureConnected().getOrElse { return Result.failure(it) }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return Result.failure(
                IllegalStateException(
                    result.billingResult.debugMessage.ifBlank {
                        "Failed to query Play purchases (${result.billingResult.responseCode})."
                    }
                )
            )
        }
        return Result.success(result.purchasesList.map { it.toPlayPurchaseRecord() })
    }

    override suspend fun launchSubscriptionPurchase(
        activity: Activity,
        productId: String,
        basePlanId: String,
        oldPurchaseToken: String?,
        obfuscatedAccountId: String?
    ): Result<Unit> {
        val client = ensureConnected().getOrElse { return Result.failure(it) }
        if (!cachedProductDetails.containsKey(productId)) {
            querySubscriptionProducts(listOf(productId))
        }
        val details = cachedProductDetails[productId]
            ?: return Result.failure(
                IllegalStateException(
                    "Subscription '$productId' is not configured yet in Google Play Console for this app ID."
                )
            )

        val offer = details.subscriptionOfferDetails.orEmpty()
            .firstOrNull { it.basePlanId == basePlanId && it.offerId.isNullOrBlank() }
            ?: details.subscriptionOfferDetails.orEmpty().firstOrNull { it.basePlanId == basePlanId }
            ?: details.subscriptionOfferDetails.orEmpty().firstOrNull()
            ?: return Result.failure(
                IllegalStateException("No active Google Play offer found for $productId ($basePlanId).")
            )

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()

        val flowBuilder = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))

        if (!obfuscatedAccountId.isNullOrBlank()) {
            flowBuilder.setObfuscatedAccountId(sha256Hex(obfuscatedAccountId))
        }

        if (!oldPurchaseToken.isNullOrBlank()) {
            flowBuilder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(oldPurchaseToken)
                    .setSubscriptionReplacementMode(
                        BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                    )
                    .build()
            )
        }

        val launchResult = client.launchBillingFlow(activity, flowBuilder.build())
        return if (launchResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    launchResult.debugMessage.ifBlank {
                        "Unable to open Google Play checkout (code ${launchResult.responseCode})."
                    }
                )
            )
        }
    }

    override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> {
        if (purchaseToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Blank purchase token."))
        }
        val client = ensureConnected().getOrElse { return Result.failure(it) }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val result = client.acknowledgePurchase(params)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    result.debugMessage.ifBlank {
                        "Acknowledge failed (${result.responseCode})."
                    }
                )
            )
        }
    }

    override fun endConnection() {
        synchronized(this) {
            runCatching { billingClientInstance?.endConnection() }
            billingClientInstance = null
        }
    }

    private fun Purchase.toPlayPurchaseRecord(): PlayPurchaseRecord {
        val productId = products.firstOrNull().orEmpty()
        return PlayPurchaseRecord(
            productId = productId,
            basePlanId = "",
            orderId = orderId.orEmpty(),
            purchaseToken = purchaseToken,
            purchaseTimeEpochMs = purchaseTime,
            purchaseState = BillingPurchaseState.fromPlayState(purchaseState),
            isAcknowledged = isAcknowledged,
            isAutoRenewing = isAutoRenewing,
            obfuscatedAccountId = accountIdentifiers?.obfuscatedAccountId
        )
    }

    companion object {
        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
