package com.example.billing

import com.example.data.local.entity.ConfigurablePricingPlanEntity
import com.example.data.local.entity.SubscriptionEntitlementEntity
import com.example.security.SubscriptionTier
import org.json.JSONArray
import org.json.JSONObject

object SubscriptionEntitlementEngine {

    fun tierFromId(rawId: String?): SubscriptionTier = when (rawId?.trim()?.lowercase()) {
        SubscriptionTier.PLUS.id -> SubscriptionTier.PLUS
        SubscriptionTier.FAMILY.id -> SubscriptionTier.FAMILY
        SubscriptionTier.LIFETIME.id -> SubscriptionTier.LIFETIME
        else -> SubscriptionTier.FREE
    }

    fun tierForProductId(productId: String): SubscriptionTier = when (productId.trim()) {
        BillingCatalogIds.PRODUCT_PLUS -> SubscriptionTier.PLUS
        BillingCatalogIds.PRODUCT_FAMILY -> SubscriptionTier.FAMILY
        else -> SubscriptionTier.FREE
    }

    fun defaultConfigurableCatalog(nowEpochMs: Long = System.currentTimeMillis()): List<ConfigurablePricingPlanEntity> = listOf(
        ConfigurablePricingPlanEntity(
            planKey = BillingCatalogIds.PLAN_KEY_FREE,
            tierId = SubscriptionTier.FREE.id,
            playProductId = "",
            basePlanId = "",
            displayName = "LifeWeave Free",
            billingPeriodLabel = "Free Forever",
            priceText = "₹0 / Forever",
            currencyCode = "INR",
            priceMicros = 0L,
            badgeText = "Period Tracker 100% Free Forever",
            maxProfiles = 1,
            featuresCsv = listOf(
                "Cycle & Period Tracker 100% Free Forever",
                "Hydration, Exercise, Yoga, Sleep & Reminders",
                "On-device Encrypted Vault & Local JSON Backup",
                "Single Wellness Profile"
            ).joinToString("|"),
            isEnabled = true,
            sortOrder = 0,
            configSource = "DEFAULT",
            updatedAtEpochMs = nowEpochMs
        ),
        ConfigurablePricingPlanEntity(
            planKey = BillingCatalogIds.PLAN_KEY_PLUS_MONTHLY,
            tierId = SubscriptionTier.PLUS.id,
            playProductId = BillingCatalogIds.PRODUCT_PLUS,
            basePlanId = BillingCatalogIds.BASE_PLAN_PLUS_MONTHLY,
            displayName = "LifeWeave Plus",
            billingPeriodLabel = "Monthly",
            priceText = "₹199 / mo",
            currencyCode = "INR",
            priceMicros = 199_000_000L,
            badgeText = "Personal AI & Nutrition",
            maxProfiles = 1,
            featuresCsv = listOf(
                "AI Personal Health Intelligence & Briefings",
                "Indian Regional AI Meal Planner & Health Connect",
                "Health Records Vault Analytics & Doctor PDF",
                "All Free Essentials + Period Tracker Free Forever"
            ).joinToString("|"),
            isEnabled = true,
            sortOrder = 1,
            configSource = "DEFAULT",
            updatedAtEpochMs = nowEpochMs
        ),
        ConfigurablePricingPlanEntity(
            planKey = BillingCatalogIds.PLAN_KEY_PLUS_ANNUAL,
            tierId = SubscriptionTier.PLUS.id,
            playProductId = BillingCatalogIds.PRODUCT_PLUS,
            basePlanId = BillingCatalogIds.BASE_PLAN_PLUS_ANNUAL,
            displayName = "LifeWeave Plus",
            billingPeriodLabel = "Annual",
            priceText = "₹1,499 / yr",
            currencyCode = "INR",
            priceMicros = 1_499_000_000L,
            badgeText = "Save 37%",
            maxProfiles = 1,
            featuresCsv = listOf(
                "AI Personal Health Intelligence & Briefings",
                "Indian Regional AI Meal Planner & Health Connect",
                "Health Records Vault Analytics & Doctor PDF",
                "Priority Restore Across Devices via Google Play"
            ).joinToString("|"),
            isEnabled = true,
            sortOrder = 2,
            configSource = "DEFAULT",
            updatedAtEpochMs = nowEpochMs
        ),
        ConfigurablePricingPlanEntity(
            planKey = BillingCatalogIds.PLAN_KEY_FAMILY_MONTHLY,
            tierId = SubscriptionTier.FAMILY.id,
            playProductId = BillingCatalogIds.PRODUCT_FAMILY,
            basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_MONTHLY,
            displayName = "LifeWeave Family Circle",
            billingPeriodLabel = "Monthly",
            priceText = "₹299 / mo",
            currencyCode = "INR",
            priceMicros = 299_000_000L,
            badgeText = "Up to 5 Profiles",
            maxProfiles = 5,
            featuresCsv = listOf(
                "Everything in LifeWeave Plus Included",
                "Up to 5 Isolated Wellness Profiles",
                "Caregiver Reminders & Shared Doctor Visits",
                "Role-Based & Explicit Consent Family Circle"
            ).joinToString("|"),
            isEnabled = true,
            sortOrder = 3,
            configSource = "DEFAULT",
            updatedAtEpochMs = nowEpochMs
        ),
        ConfigurablePricingPlanEntity(
            planKey = BillingCatalogIds.PLAN_KEY_FAMILY_ANNUAL,
            tierId = SubscriptionTier.FAMILY.id,
            playProductId = BillingCatalogIds.PRODUCT_FAMILY,
            basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_ANNUAL,
            displayName = "LifeWeave Family Circle",
            billingPeriodLabel = "Annual",
            priceText = "₹2,499 / yr",
            currencyCode = "INR",
            priceMicros = 2_499_000_000L,
            badgeText = "Best Value • 5 Profiles",
            maxProfiles = 5,
            featuresCsv = listOf(
                "Everything in LifeWeave Plus Included",
                "Up to 5 Isolated Wellness Profiles",
                "Caregiver Reminders & Shared Doctor Visits",
                "Consent-Controlled Family Sharing on Cloud"
            ).joinToString("|"),
            isEnabled = true,
            sortOrder = 4,
            configSource = "DEFAULT",
            updatedAtEpochMs = nowEpochMs
        )
    )

    fun evaluateEffectiveEntitlement(
        entity: SubscriptionEntitlementEntity?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): EffectiveEntitlement {
        if (entity == null) {
            return EffectiveEntitlement(
                tier = SubscriptionTier.FREE,
                lifecycleStatus = EntitlementLifecycleStatus.FREE,
                verificationStatus = ServerVerificationStatus.NONE,
                maxAllowedProfiles = SubscriptionTier.FREE.maxProfiles,
                isEntitledToPaidFeatures = false,
                isPeriodTrackerAlwaysFree = true,
                statusSummary = "LifeWeave Free • Period Tracker 100% Free Forever"
            )
        }

        val rawTier = tierFromId(entity.tierId)
        val purchaseState = BillingPurchaseState.fromId(entity.purchaseState)
        val verificationStatus = ServerVerificationStatus.fromId(entity.verificationStatus)
        val storedLifecycle = EntitlementLifecycleStatus.fromId(entity.lifecycleStatus)

        if (storedLifecycle == EntitlementLifecycleStatus.REVOKED ||
            verificationStatus == ServerVerificationStatus.REJECTED
        ) {
            return EffectiveEntitlement(
                tier = SubscriptionTier.FREE,
                lifecycleStatus = EntitlementLifecycleStatus.REVOKED,
                verificationStatus = ServerVerificationStatus.REJECTED,
                maxAllowedProfiles = 1,
                isEntitledToPaidFeatures = false,
                isPeriodTrackerAlwaysFree = true,
                productId = entity.productId,
                basePlanId = entity.basePlanId,
                lastValidatedEpochMs = entity.lastValidatedEpochMs,
                statusSummary = entity.verificationMessage.ifBlank {
                    "Subscription revoked or refunded. Reverted to LifeWeave Free (Period Tracker stays free)."
                }
            )
        }

        if (storedLifecycle == EntitlementLifecycleStatus.EXPIRED) {
            return EffectiveEntitlement(
                tier = SubscriptionTier.FREE,
                lifecycleStatus = EntitlementLifecycleStatus.EXPIRED,
                verificationStatus = verificationStatus,
                maxAllowedProfiles = 1,
                isEntitledToPaidFeatures = false,
                isPeriodTrackerAlwaysFree = true,
                productId = entity.productId,
                basePlanId = entity.basePlanId,
                expiresAtEpochMs = entity.expiresAtEpochMs,
                lastValidatedEpochMs = entity.lastValidatedEpochMs,
                verificationReceiptId = entity.verificationReceiptId,
                statusSummary = entity.verificationMessage.ifBlank {
                    "Previous subscription expired. Reverted to LifeWeave Free (Period Tracker remains free forever)."
                }
            )
        }

        // Pending Google Play purchases must NEVER grant entitlements before settlement + validation.
        if (purchaseState == BillingPurchaseState.PENDING) {
            return EffectiveEntitlement(
                tier = SubscriptionTier.FREE,
                lifecycleStatus = EntitlementLifecycleStatus.PENDING_VERIFICATION,
                verificationStatus = verificationStatus,
                maxAllowedProfiles = 1,
                isEntitledToPaidFeatures = false,
                isPeriodTrackerAlwaysFree = true,
                productId = entity.productId,
                basePlanId = entity.basePlanId,
                purchaseToken = entity.purchaseToken,
                lastValidatedEpochMs = entity.lastValidatedEpochMs,
                statusSummary = "Payment pending confirmation in Google Play. Paid features unlock after settlement & server check."
            )
        }

        if (rawTier == SubscriptionTier.FREE || storedLifecycle == EntitlementLifecycleStatus.FREE) {
            return EffectiveEntitlement(
                tier = SubscriptionTier.FREE,
                lifecycleStatus = EntitlementLifecycleStatus.FREE,
                verificationStatus = verificationStatus,
                maxAllowedProfiles = 1,
                isEntitledToPaidFeatures = false,
                isPeriodTrackerAlwaysFree = true,
                lastValidatedEpochMs = entity.lastValidatedEpochMs,
                statusSummary = "LifeWeave Free • Cycle & Period Tracker Always Free Forever"
            )
        }

        // Client NEVER grants paid tiers without authoritative server verification.
        if (verificationStatus != ServerVerificationStatus.SERVER_VERIFIED) {
            return EffectiveEntitlement(
                tier = SubscriptionTier.FREE,
                lifecycleStatus = EntitlementLifecycleStatus.PENDING_VERIFICATION,
                verificationStatus = verificationStatus,
                maxAllowedProfiles = 1,
                isEntitledToPaidFeatures = false,
                isPeriodTrackerAlwaysFree = true,
                productId = entity.productId,
                basePlanId = entity.basePlanId,
                purchaseToken = entity.purchaseToken,
                lastValidatedEpochMs = entity.lastValidatedEpochMs,
                statusSummary = entity.verificationMessage.ifBlank {
                    "Awaiting server-side Google Play receipt validation before unlocking ${rawTier.displayName}."
                }
            )
        }

        if (entity.expiresAtEpochMs in 1L..nowEpochMs) {
            if (entity.gracePeriodEndsAtEpochMs > nowEpochMs) {
                return EffectiveEntitlement(
                    tier = rawTier,
                    lifecycleStatus = EntitlementLifecycleStatus.GRACE_PERIOD,
                    verificationStatus = verificationStatus,
                    maxAllowedProfiles = rawTier.maxProfiles,
                    isEntitledToPaidFeatures = true,
                    isPeriodTrackerAlwaysFree = true,
                    inGracePeriod = true,
                    autoRenewing = entity.autoRenewing,
                    productId = entity.productId,
                    basePlanId = entity.basePlanId,
                    purchaseToken = entity.purchaseToken,
                    expiresAtEpochMs = entity.expiresAtEpochMs,
                    gracePeriodEndsAtEpochMs = entity.gracePeriodEndsAtEpochMs,
                    lastValidatedEpochMs = entity.lastValidatedEpochMs,
                    verificationReceiptId = entity.verificationReceiptId,
                    statusSummary = "${rawTier.displayName} is in billing grace period — update payment method in Google Play to avoid interruption."
                )
            }

            return EffectiveEntitlement(
                tier = SubscriptionTier.FREE,
                lifecycleStatus = EntitlementLifecycleStatus.EXPIRED,
                verificationStatus = verificationStatus,
                maxAllowedProfiles = 1,
                isEntitledToPaidFeatures = false,
                isPeriodTrackerAlwaysFree = true,
                productId = entity.productId,
                basePlanId = entity.basePlanId,
                expiresAtEpochMs = entity.expiresAtEpochMs,
                lastValidatedEpochMs = entity.lastValidatedEpochMs,
                verificationReceiptId = entity.verificationReceiptId,
                statusSummary = "Previous ${rawTier.displayName} expired. Reverted to LifeWeave Free (Period Tracker remains free forever)."
            )
        }

        if (storedLifecycle == EntitlementLifecycleStatus.GRACE_PERIOD) {
            return EffectiveEntitlement(
                tier = rawTier,
                lifecycleStatus = EntitlementLifecycleStatus.GRACE_PERIOD,
                verificationStatus = verificationStatus,
                maxAllowedProfiles = rawTier.maxProfiles,
                isEntitledToPaidFeatures = true,
                isPeriodTrackerAlwaysFree = true,
                inGracePeriod = true,
                autoRenewing = entity.autoRenewing,
                productId = entity.productId,
                basePlanId = entity.basePlanId,
                purchaseToken = entity.purchaseToken,
                expiresAtEpochMs = entity.expiresAtEpochMs,
                gracePeriodEndsAtEpochMs = entity.gracePeriodEndsAtEpochMs,
                lastValidatedEpochMs = entity.lastValidatedEpochMs,
                verificationReceiptId = entity.verificationReceiptId,
                statusSummary = "${rawTier.displayName} grace period active."
            )
        }

        return EffectiveEntitlement(
            tier = rawTier,
            lifecycleStatus = EntitlementLifecycleStatus.ACTIVE,
            verificationStatus = ServerVerificationStatus.SERVER_VERIFIED,
            maxAllowedProfiles = rawTier.maxProfiles,
            isEntitledToPaidFeatures = true,
            isPeriodTrackerAlwaysFree = true,
            inGracePeriod = false,
            autoRenewing = entity.autoRenewing,
            productId = entity.productId,
            basePlanId = entity.basePlanId,
            purchaseToken = entity.purchaseToken,
            expiresAtEpochMs = entity.expiresAtEpochMs,
            gracePeriodEndsAtEpochMs = entity.gracePeriodEndsAtEpochMs,
            lastValidatedEpochMs = entity.lastValidatedEpochMs,
            verificationReceiptId = entity.verificationReceiptId,
            statusSummary = "${rawTier.displayName} active & server-verified (${rawTier.maxProfiles} profile${if (rawTier.maxProfiles > 1) "s" else ""})."
        )
    }

    fun mergeConfigurableCatalog(
        existing: List<ConfigurablePricingPlanEntity>,
        remotePlans: List<RemoteConfigurablePlanDto> = emptyList(),
        playStorePrices: Map<String, PlayStoreProductPrice> = emptyMap(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<ConfigurablePricingPlanEntity> {
        val baseList = existing.ifEmpty { defaultConfigurableCatalog(nowEpochMs) }
        val byKey = baseList.associateBy { it.planKey }.toMutableMap()

        remotePlans.forEach { remote ->
            val current = byKey[remote.planKey]
            byKey[remote.planKey] = ConfigurablePricingPlanEntity(
                planKey = remote.planKey,
                tierId = remote.tierId,
                playProductId = remote.playProductId,
                basePlanId = remote.basePlanId,
                displayName = remote.displayName.ifBlank { current?.displayName ?: remote.tierId },
                billingPeriodLabel = remote.billingPeriodLabel.ifBlank { current?.billingPeriodLabel ?: "Monthly" },
                priceText = remote.priceText.ifBlank { current?.priceText ?: "" },
                currencyCode = remote.currencyCode.ifBlank { current?.currencyCode ?: "INR" },
                priceMicros = remote.priceMicros,
                badgeText = remote.badgeText,
                maxProfiles = remote.maxProfiles.coerceIn(1, 5),
                featuresCsv = remote.featuresCsv.ifBlank { current?.featuresCsv.orEmpty() },
                isEnabled = remote.isEnabled,
                sortOrder = remote.sortOrder,
                configSource = "REMOTE_CONFIG",
                updatedAtEpochMs = nowEpochMs
            )
        }

        // Apply live localized Play Store checkout price if fetched from BillingClient.
        byKey.values.toList().forEach { plan ->
            if (plan.playProductId.isNotBlank() && plan.basePlanId.isNotBlank()) {
                val playKey = "${plan.playProductId}:${plan.basePlanId}"
                val livePrice = playStorePrices[playKey]
                if (livePrice != null) {
                    val suffix = if (plan.billingPeriodLabel.equals("Annual", ignoreCase = true)) " / yr" else " / mo"
                    byKey[plan.planKey] = plan.copy(
                        priceText = "${livePrice.formattedPrice}$suffix",
                        currencyCode = livePrice.currencyCode,
                        priceMicros = livePrice.priceAmountMicros,
                        configSource = "PLAY_STORE_LIVE",
                        updatedAtEpochMs = nowEpochMs
                    )
                }
            }
        }

        return byKey.values.sortedBy { it.sortOrder }
    }

    fun buildServerValidationRequestJson(request: ServerPurchaseValidationRequest): String {
        return JSONObject().apply {
            put("packageName", request.packageName)
            put("productId", request.productId)
            put("basePlanId", request.basePlanId)
            put("purchaseToken", request.purchaseToken)
            put("orderId", request.orderId)
            put("purchaseTimeEpochMs", request.purchaseTimeEpochMs)
            if (!request.firebaseUid.isNullOrBlank()) {
                put("firebaseUid", request.firebaseUid)
            }
            put("platform", "android_play_billing_v8")
        }.toString()
    }

    fun parseServerValidationResponseJson(
        responseBody: String,
        fallbackProductId: String,
        fallbackBasePlanId: String
    ): ServerPurchaseValidationResult {
        val json = JSONObject(responseBody)
        val valid = json.optBoolean("valid", false)
        val rawStatus = json.optString("status", if (valid) "ACTIVE" else "REVOKED")
        val lifecycle = EntitlementLifecycleStatus.fromId(rawStatus)
        val message = json.optString("message").trim()

        if (!valid || lifecycle == EntitlementLifecycleStatus.REVOKED) {
            return ServerPurchaseValidationResult.Rejected(
                reason = message.ifBlank { "Purchase failed server-side Google Play validation." },
                lifecycleStatus = if (lifecycle == EntitlementLifecycleStatus.EXPIRED) {
                    EntitlementLifecycleStatus.EXPIRED
                } else {
                    EntitlementLifecycleStatus.REVOKED
                }
            )
        }

        val resolvedProductId = json.optString("productId").ifBlank { fallbackProductId }
        val resolvedBasePlanId = json.optString("basePlanId").ifBlank { fallbackBasePlanId }
        val resolvedTier = when (val rawTier = json.optString("tierId").trim().lowercase()) {
            SubscriptionTier.PLUS.id -> SubscriptionTier.PLUS
            SubscriptionTier.FAMILY.id -> SubscriptionTier.FAMILY
            SubscriptionTier.LIFETIME.id -> SubscriptionTier.LIFETIME
            SubscriptionTier.FREE.id -> SubscriptionTier.FREE
            else -> tierForProductId(resolvedProductId)
        }

        return ServerPurchaseValidationResult.Verified(
            tier = resolvedTier,
            productId = resolvedProductId,
            basePlanId = resolvedBasePlanId,
            lifecycleStatus = if (lifecycle == EntitlementLifecycleStatus.FREE) {
                EntitlementLifecycleStatus.ACTIVE
            } else {
                lifecycle
            },
            autoRenewing = json.optBoolean("autoRenewing", true),
            acknowledgedByServer = json.optBoolean("acknowledgedByServer", false),
            purchasedAtEpochMs = json.optLong("purchasedAtEpochMs", 0L),
            expiresAtEpochMs = json.optLong("expiresAtEpochMs", 0L),
            gracePeriodEndsAtEpochMs = json.optLong("gracePeriodEndsAtEpochMs", 0L),
            receiptId = json.optString("receiptId").ifBlank { "srv_${System.currentTimeMillis()}" },
            message = message.ifBlank { "Verified by LifeWeave billing backend." }
        )
    }

    fun parseRemotePricingConfigJson(responseBody: String): RemotePricingFetchResult {
        return runCatching {
            val root = JSONObject(responseBody)
            val version = root.optString("version", "1")
            val array = root.optJSONArray("plans") ?: JSONArray()
            val plans = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val planKey = obj.optString("planKey").trim()
                    if (planKey.isEmpty()) continue
                    val tierId = obj.optString("tierId", "free").trim().lowercase()
                    add(
                        RemoteConfigurablePlanDto(
                            planKey = planKey,
                            tierId = tierId,
                            playProductId = obj.optString("playProductId").trim(),
                            basePlanId = obj.optString("basePlanId").trim(),
                            displayName = obj.optString("displayName").trim(),
                            billingPeriodLabel = obj.optString("billingPeriodLabel", "Monthly").trim(),
                            priceText = obj.optString("priceText").trim(),
                            currencyCode = obj.optString("currencyCode", "INR").trim(),
                            priceMicros = obj.optLong("priceMicros", 0L),
                            badgeText = obj.optString("badgeText").trim(),
                            maxProfiles = obj.optInt("maxProfiles", if (tierId == "family") 5 else 1).coerceIn(1, 5),
                            featuresCsv = obj.optString("featuresCsv").trim(),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            sortOrder = obj.optInt("sortOrder", i)
                        )
                    )
                }
            }
            if (plans.isEmpty()) {
                RemotePricingFetchResult.Failure("Remote pricing payload contained no valid plan entries.")
            } else {
                RemotePricingFetchResult.Success(version = version, plans = plans)
            }
        }.getOrElse { err ->
            RemotePricingFetchResult.Failure("Invalid pricing JSON: ${err.message ?: "parse error"}")
        }
    }
}
