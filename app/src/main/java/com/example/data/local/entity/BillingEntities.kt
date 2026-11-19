package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_entitlements")
data class SubscriptionEntitlementEntity(
    @PrimaryKey val entitlementId: String = "primary",
    val tierId: String = "free",
    val productId: String = "",
    val basePlanId: String = "",
    val offerId: String = "",
    val purchaseToken: String = "",
    val orderId: String = "",
    val purchaseState: String = "UNSPECIFIED",
    val lifecycleStatus: String = "FREE",
    val verificationStatus: String = "NONE",
    val verificationReceiptId: String = "",
    val verificationMessage: String = "",
    val autoRenewing: Boolean = false,
    val acknowledged: Boolean = false,
    val purchasedAtEpochMs: Long = 0L,
    val expiresAtEpochMs: Long = 0L,
    val gracePeriodEndsAtEpochMs: Long = 0L,
    val lastValidatedEpochMs: Long = 0L,
    val linkedFirebaseUid: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "configurable_pricing_plans")
data class ConfigurablePricingPlanEntity(
    @PrimaryKey val planKey: String,
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
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
    val configSource: String = "DEFAULT",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
