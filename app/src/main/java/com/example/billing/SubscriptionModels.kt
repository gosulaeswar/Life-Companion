package com.example.billing

import com.example.security.SubscriptionTier

object BillingCatalogIds {
    const val PRODUCT_PLUS = "lifeweave_plus_sub"
    const val PRODUCT_FAMILY = "lifeweave_family_sub"

    const val BASE_PLAN_PLUS_MONTHLY = "plus-monthly"
    const val BASE_PLAN_PLUS_ANNUAL = "plus-annual"
    const val BASE_PLAN_FAMILY_MONTHLY = "family-monthly"
    const val BASE_PLAN_FAMILY_ANNUAL = "family-annual"

    const val PLAN_KEY_FREE = "free_forever"
    const val PLAN_KEY_PLUS_MONTHLY = "plus_monthly"
    const val PLAN_KEY_PLUS_ANNUAL = "plus_annual"
    const val PLAN_KEY_FAMILY_MONTHLY = "family_monthly"
    const val PLAN_KEY_FAMILY_ANNUAL = "family_annual"

    val ALL_PLAY_SUBSCRIPTION_PRODUCTS = listOf(PRODUCT_PLUS, PRODUCT_FAMILY)
}

enum class BillingCycleFilter(val label: String) {
    MONTHLY("Monthly"),
    ANNUAL("Annual")
}

enum class EntitlementLifecycleStatus(val id: String, val label: String) {
    FREE("FREE", "Free Forever Plan"),
    ACTIVE("ACTIVE", "Active"),
    GRACE_PERIOD("GRACE_PERIOD", "Grace Period"),
    EXPIRED("EXPIRED", "Expired"),
    REVOKED("REVOKED", "Revoked / Refunded"),
    PENDING_VERIFICATION("PENDING_VERIFICATION", "Pending Verification");

    companion object {
        fun fromId(raw: String?): EntitlementLifecycleStatus =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: FREE
    }
}

enum class ServerVerificationStatus(val id: String, val label: String) {
    NONE("NONE", "Not Subscribed"),
    SERVER_VERIFIED("SERVER_VERIFIED", "Verified by Server"),
    ENDPOINT_UNCONFIGURED("ENDPOINT_UNCONFIGURED", "Server Endpoint Needed"),
    REJECTED("REJECTED", "Rejected by Server");

    companion object {
        fun fromId(raw: String?): ServerVerificationStatus =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class BillingPurchaseState(val id: String) {
    PURCHASED("PURCHASED"),
    PENDING("PENDING"),
    UNSPECIFIED("UNSPECIFIED");

    companion object {
        fun fromPlayState(playState: Int): BillingPurchaseState = when (playState) {
            1 -> PURCHASED
            2 -> PENDING
            else -> UNSPECIFIED
        }

        fun fromId(raw: String?): BillingPurchaseState =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: UNSPECIFIED
    }
}

data class EffectiveEntitlement(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val lifecycleStatus: EntitlementLifecycleStatus = EntitlementLifecycleStatus.FREE,
    val verificationStatus: ServerVerificationStatus = ServerVerificationStatus.NONE,
    val maxAllowedProfiles: Int = 1,
    val isEntitledToPaidFeatures: Boolean = false,
    val isPeriodTrackerAlwaysFree: Boolean = true,
    val inGracePeriod: Boolean = false,
    val autoRenewing: Boolean = false,
    val productId: String = "",
    val basePlanId: String = "",
    val purchaseToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val gracePeriodEndsAtEpochMs: Long = 0L,
    val lastValidatedEpochMs: Long = 0L,
    val verificationReceiptId: String = "",
    val statusSummary: String = "LifeWeave Free • Cycle & Period Tracker 100% Free Forever"
)

data class PlayStoreProductPrice(
    val productId: String,
    val basePlanId: String,
    val offerId: String = "",
    val offerToken: String = "",
    val formattedPrice: String,
    val currencyCode: String,
    val priceAmountMicros: Long,
    val billingPeriodIso: String = ""
)

data class PlayPurchaseRecord(
    val productId: String,
    val basePlanId: String,
    val orderId: String,
    val purchaseToken: String,
    val purchaseTimeEpochMs: Long,
    val purchaseState: BillingPurchaseState,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val obfuscatedAccountId: String? = null
)

sealed interface PlayPurchaseUpdate {
    data class Success(val purchases: List<PlayPurchaseRecord>) : PlayPurchaseUpdate
    data class Cancelled(val message: String) : PlayPurchaseUpdate
    data class Error(val responseCode: Int, val message: String) : PlayPurchaseUpdate
}

data class ServerPurchaseValidationRequest(
    val packageName: String,
    val productId: String,
    val basePlanId: String,
    val purchaseToken: String,
    val orderId: String,
    val purchaseTimeEpochMs: Long,
    val firebaseUid: String?,
    val firebaseIdToken: String?
)

sealed interface ServerPurchaseValidationResult {
    data class Verified(
        val tier: SubscriptionTier,
        val productId: String,
        val basePlanId: String,
        val lifecycleStatus: EntitlementLifecycleStatus,
        val autoRenewing: Boolean,
        val acknowledgedByServer: Boolean,
        val purchasedAtEpochMs: Long,
        val expiresAtEpochMs: Long,
        val gracePeriodEndsAtEpochMs: Long,
        val receiptId: String,
        val message: String
    ) : ServerPurchaseValidationResult

    data class Rejected(
        val reason: String,
        val lifecycleStatus: EntitlementLifecycleStatus = EntitlementLifecycleStatus.REVOKED
    ) : ServerPurchaseValidationResult

    data class EndpointUnconfigured(
        val reason: String
    ) : ServerPurchaseValidationResult

    data class TransientFailure(
        val reason: String
    ) : ServerPurchaseValidationResult
}

data class RemoteConfigurablePlanDto(
    val planKey: String,
    val tierId: String,
    val playProductId: String,
    val basePlanId: String,
    val displayName: String,
    val billingPeriodLabel: String,
    val priceText: String,
    val currencyCode: String,
    val priceMicros: Long,
    val badgeText: String,
    val maxProfiles: Int,
    val featuresCsv: String,
    val isEnabled: Boolean,
    val sortOrder: Int
)

sealed interface RemotePricingFetchResult {
    data class Success(
        val version: String,
        val plans: List<RemoteConfigurablePlanDto>
    ) : RemotePricingFetchResult

    data class Unconfigured(val reason: String) : RemotePricingFetchResult
    data class Failure(val reason: String) : RemotePricingFetchResult
}
