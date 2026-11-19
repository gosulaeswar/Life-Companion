package com.example.billing

import android.app.Activity
import android.content.Context
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConfigurablePricingPlanEntity
import com.example.data.local.entity.SubscriptionEntitlementEntity
import com.example.family.FamilyCircleRepository
import com.example.security.LocalPreferencesManager
import com.example.security.SubscriptionTier
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class BillingOperationFeedback(
    val success: Boolean,
    val message: String,
    val effectiveEntitlement: EffectiveEntitlement
)

class SubscriptionRepository(
    private val appContext: Context,
    private val database: AppDatabase,
    private val preferencesManager: LocalPreferencesManager,
    private val playBillingGateway: PlayBillingGateway,
    private val verificationClient: ServerBillingVerificationClient,
    private val firebaseAuthProvider: () -> FirebaseAuth? = { runCatching { FirebaseAuth.getInstance() }.getOrNull() },
    private val familyCircleRepositoryProvider: () -> FamilyCircleRepository? = { null },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val allowLocalDebugLoopbackUrls: Boolean = BuildConfig.DEBUG,
    val allowRuntimeEndpointOverrides: Boolean = BuildConfig.DEBUG
) {
    private val billingDao = database.subscriptionBillingDao()
    private var latestPlayPrices: Map<String, PlayStoreProductPrice> = emptyMap()

    @Volatile
    private var latestEvaluatedEntitlement: EffectiveEntitlement = EffectiveEntitlement()

    val purchaseUpdates: Flow<PlayPurchaseUpdate> = playBillingGateway.purchaseUpdates

    fun currentCachedTier(): SubscriptionTier {
        val current = latestEvaluatedEntitlement
        val now = clockMs()
        val expired = current.expiresAtEpochMs in 1L..now && current.gracePeriodEndsAtEpochMs <= now
        return when {
            !current.isEntitledToPaidFeatures -> SubscriptionTier.FREE
            expired -> SubscriptionTier.FREE
            else -> current.tier
        }
    }

    fun observeEffectiveEntitlement(): Flow<EffectiveEntitlement> =
        billingDao.observeEntitlement().map { entity ->
            SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(entity, clockMs()).also {
                latestEvaluatedEntitlement = it
            }
        }

    fun observeRawEntitlement(): Flow<SubscriptionEntitlementEntity?> =
        billingDao.observeEntitlement()

    fun observeConfigurablePricingPlans(): Flow<List<ConfigurablePricingPlanEntity>> =
        billingDao.observePricingPlans().map { plans ->
            plans.ifEmpty { SubscriptionEntitlementEngine.defaultConfigurableCatalog(clockMs()) }
        }

    suspend fun getEffectiveEntitlementSnapshot(): EffectiveEntitlement = withContext(Dispatchers.IO) {
        val current = billingDao.getEntitlementSnapshot()
        SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(current, clockMs()).also {
            latestEvaluatedEntitlement = it
        }
    }

    private suspend fun resolveVerificationUrl(): String {
        if (!allowRuntimeEndpointOverrides) {
            return BuildConfig.BILLING_VERIFICATION_URL.trim()
        }
        return preferencesManager.preferencesFlow.first().billingVerificationUrl.trim()
    }

    private suspend fun resolvePricingConfigUrl(): String {
        if (!allowRuntimeEndpointOverrides) {
            return BuildConfig.BILLING_PRICING_CONFIG_URL.trim()
        }
        return preferencesManager.preferencesFlow.first().billingPricingConfigUrl.trim()
    }

    suspend fun ensureDefaultPricingSeeded(): List<ConfigurablePricingPlanEntity> = withContext(Dispatchers.IO) {
        val existing = billingDao.getPricingPlansSnapshot()
        if (existing.isEmpty()) {
            val defaults = SubscriptionEntitlementEngine.defaultConfigurableCatalog(clockMs())
            billingDao.upsertPricingPlans(defaults)
            defaults
        } else {
            existing
        }
    }

    suspend fun initializeAndRefresh(): EffectiveEntitlement = withContext(Dispatchers.IO) {
        ensureDefaultPricingSeeded()
        val afterExpiry = checkAndEnforceExpiry()
        // Best-effort live Play Store price sync without failing when offline or outside Play Store.
        syncLivePlayStorePrices()
        afterExpiry
    }

    suspend fun checkAndEnforceExpiry(): EffectiveEntitlement = withContext(Dispatchers.IO) {
        val now = clockMs()
        val stored = billingDao.getEntitlementSnapshot()
        val evaluated = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(stored, now)

        if (stored != null) {
            val statusChanged = !stored.lifecycleStatus.equals(evaluated.lifecycleStatus.id, ignoreCase = true) ||
                !stored.tierId.equals(evaluated.tier.id, ignoreCase = true)
            if (statusChanged && (evaluated.lifecycleStatus == EntitlementLifecycleStatus.EXPIRED ||
                    evaluated.lifecycleStatus == EntitlementLifecycleStatus.GRACE_PERIOD)
            ) {
                billingDao.upsertEntitlement(
                    stored.copy(
                        tierId = if (evaluated.lifecycleStatus == EntitlementLifecycleStatus.EXPIRED) {
                            SubscriptionTier.FREE.id
                        } else {
                            stored.tierId
                        },
                        lifecycleStatus = evaluated.lifecycleStatus.id,
                        verificationMessage = evaluated.statusSummary,
                        updatedAtEpochMs = now
                    )
                )
            }
        }

        preferencesManager.setVerifiedSubscriptionCache(
            tier = evaluated.tier,
            expiresAtEpochMs = evaluated.expiresAtEpochMs,
            gracePeriodEndsAtEpochMs = evaluated.gracePeriodEndsAtEpochMs,
            receiptId = evaluated.verificationReceiptId
        )

        if (evaluated.lifecycleStatus == EntitlementLifecycleStatus.EXPIRED) {
            syncVerifiedTierToFamilyBackend(evaluated.tier)
        }
        latestEvaluatedEntitlement = evaluated
        evaluated
    }

    suspend fun syncLivePlayStorePrices(): Result<Map<String, PlayStoreProductPrice>> = withContext(Dispatchers.IO) {
        ensureDefaultPricingSeeded()
        val result = playBillingGateway.querySubscriptionProducts()
        result.onSuccess { priceMap ->
            if (priceMap.isNotEmpty()) {
                latestPlayPrices = priceMap
                val existing = billingDao.getPricingPlansSnapshot()
                val merged = SubscriptionEntitlementEngine.mergeConfigurableCatalog(
                    existing = existing,
                    remotePlans = emptyList(),
                    playStorePrices = priceMap,
                    nowEpochMs = clockMs()
                )
                billingDao.upsertPricingPlans(merged)
            }
        }
        result
    }

    suspend fun refreshRemotePricingConfig(): BillingOperationFeedback = withContext(Dispatchers.IO) {
        ensureDefaultPricingSeeded()
        val configUrl = resolvePricingConfigUrl()
        when (val fetch = verificationClient.fetchRemotePricingCatalog(configUrl, allowLocalDebugLoopbackUrls)) {
            is RemotePricingFetchResult.Success -> {
                val existing = billingDao.getPricingPlansSnapshot()
                val merged = SubscriptionEntitlementEngine.mergeConfigurableCatalog(
                    existing = existing,
                    remotePlans = fetch.plans,
                    playStorePrices = latestPlayPrices,
                    nowEpochMs = clockMs()
                )
                billingDao.upsertPricingPlans(merged)
                BillingOperationFeedback(
                    success = true,
                    message = "Updated configurable pricing catalog (v${fetch.version}, ${fetch.plans.size} plans).",
                    effectiveEntitlement = getEffectiveEntitlementSnapshot()
                )
            }
            is RemotePricingFetchResult.Unconfigured -> {
                BillingOperationFeedback(
                    success = false,
                    message = fetch.reason,
                    effectiveEntitlement = getEffectiveEntitlementSnapshot()
                )
            }
            is RemotePricingFetchResult.Failure -> {
                BillingOperationFeedback(
                    success = false,
                    message = fetch.reason,
                    effectiveEntitlement = getEffectiveEntitlementSnapshot()
                )
            }
        }
    }

    suspend fun updateConfigurablePlan(
        planKey: String,
        priceText: String,
        badgeText: String,
        isEnabled: Boolean
    ): BillingOperationFeedback = withContext(Dispatchers.IO) {
        if (!allowRuntimeEndpointOverrides) {
            return@withContext BillingOperationFeedback(
                success = false,
                message = "Local pricing overrides are disabled in production builds. Use remote HTTPS pricing config or Google Play Console.",
                effectiveEntitlement = getEffectiveEntitlementSnapshot()
            )
        }
        val plans = ensureDefaultPricingSeeded()
        val target = plans.firstOrNull { it.planKey == planKey }
            ?: return@withContext BillingOperationFeedback(
                success = false,
                message = "Unknown pricing plan '$planKey'.",
                effectiveEntitlement = getEffectiveEntitlementSnapshot()
            )
        // Guardrail: Period Tracker / Free plan must ALWAYS stay enabled and ₹0.
        val isFreePlan = target.tierId == SubscriptionTier.FREE.id
        val sanitized = target.copy(
            priceText = if (isFreePlan) "₹0 / Forever" else priceText.trim().ifBlank { target.priceText },
            badgeText = badgeText.trim().ifBlank { target.badgeText },
            isEnabled = if (isFreePlan) true else isEnabled,
            configSource = "ADMIN_OVERRIDE",
            updatedAtEpochMs = clockMs()
        )
        billingDao.upsertPricingPlan(sanitized)
        BillingOperationFeedback(
            success = true,
            message = "Updated ${sanitized.displayName} (${sanitized.billingPeriodLabel}) pricing configuration.",
            effectiveEntitlement = getEffectiveEntitlementSnapshot()
        )
    }

    suspend fun resetConfigurablePricingDefaults(): BillingOperationFeedback = withContext(Dispatchers.IO) {
        val defaults = SubscriptionEntitlementEngine.defaultConfigurableCatalog(clockMs())
        val merged = SubscriptionEntitlementEngine.mergeConfigurableCatalog(
            existing = defaults,
            playStorePrices = latestPlayPrices,
            nowEpochMs = clockMs()
        )
        billingDao.upsertPricingPlans(merged)
        BillingOperationFeedback(
            success = true,
            message = "Restored default configurable pricing catalog.",
            effectiveEntitlement = getEffectiveEntitlementSnapshot()
        )
    }

    suspend fun saveBillingEndpoints(
        verificationUrl: String,
        pricingConfigUrl: String
    ): BillingOperationFeedback = withContext(Dispatchers.IO) {
        if (!allowRuntimeEndpointOverrides) {
            return@withContext BillingOperationFeedback(
                success = false,
                message = "Runtime billing endpoint overrides are locked in production builds to protect purchase verification integrity.",
                effectiveEntitlement = getEffectiveEntitlementSnapshot()
            )
        }
        val vTrim = verificationUrl.trim()
        val pTrim = pricingConfigUrl.trim()
        if (vTrim.isNotEmpty() && !verificationClient.isValidEndpointUrl(vTrim, allowLocalDebugLoopbackUrls)) {
            return@withContext BillingOperationFeedback(
                success = false,
                message = "Verification URL must use HTTPS.",
                effectiveEntitlement = getEffectiveEntitlementSnapshot()
            )
        }
        if (pTrim.isNotEmpty() && !verificationClient.isValidEndpointUrl(pTrim, allowLocalDebugLoopbackUrls)) {
            return@withContext BillingOperationFeedback(
                success = false,
                message = "Pricing config URL must use HTTPS.",
                effectiveEntitlement = getEffectiveEntitlementSnapshot()
            )
        }
        preferencesManager.setBillingEndpoints(vTrim, pTrim)
        BillingOperationFeedback(
            success = true,
            message = "Saved HTTPS billing server configuration.",
            effectiveEntitlement = getEffectiveEntitlementSnapshot()
        )
    }

    suspend fun startPurchase(
        activity: Activity,
        plan: ConfigurablePricingPlanEntity
    ): Result<Unit> {
        if (plan.playProductId.isBlank() || plan.basePlanId.isBlank()) {
            return Result.failure(IllegalArgumentException("Free Forever plan requires no purchase."))
        }
        if (!plan.isEnabled) {
            return Result.failure(IllegalStateException("${plan.displayName} (${plan.billingPeriodLabel}) is currently paused by pricing config."))
        }
        val current = withContext(Dispatchers.IO) { billingDao.getEntitlementSnapshot() }
        val oldToken = current?.purchaseToken?.takeIf {
            it.isNotBlank() &&
                current.lifecycleStatus in setOf(
                    EntitlementLifecycleStatus.ACTIVE.id,
                    EntitlementLifecycleStatus.GRACE_PERIOD.id
                ) &&
                current.productId != plan.playProductId
        }
        val uid = runCatching { firebaseAuthProvider()?.currentUser?.uid }.getOrNull()
        return playBillingGateway.launchSubscriptionPurchase(
            activity = activity,
            productId = plan.playProductId,
            basePlanId = plan.basePlanId,
            oldPurchaseToken = oldToken,
            obfuscatedAccountId = uid
        )
    }

    suspend fun handlePurchaseUpdate(update: PlayPurchaseUpdate): BillingOperationFeedback =
        withContext(Dispatchers.IO) {
            when (update) {
                is PlayPurchaseUpdate.Cancelled -> {
                    BillingOperationFeedback(
                        success = false,
                        message = update.message,
                        effectiveEntitlement = getEffectiveEntitlementSnapshot()
                    )
                }
                is PlayPurchaseUpdate.Error -> {
                    BillingOperationFeedback(
                        success = false,
                        message = update.message,
                        effectiveEntitlement = getEffectiveEntitlementSnapshot()
                    )
                }
                is PlayPurchaseUpdate.Success -> {
                    val primaryRecord = selectHighestPriorityRecord(update.purchases)
                    if (primaryRecord == null) {
                        BillingOperationFeedback(
                            success = false,
                            message = "No subscription purchase record returned by Google Play.",
                            effectiveEntitlement = getEffectiveEntitlementSnapshot()
                        )
                    } else {
                        verifyAndApplyPurchaseRecord(primaryRecord)
                    }
                }
            }
        }

    suspend fun restorePurchases(): BillingOperationFeedback = withContext(Dispatchers.IO) {
        val queryResult = playBillingGateway.queryActiveSubscriptions()
        val purchases = queryResult.getOrElse { err ->
            return@withContext BillingOperationFeedback(
                success = false,
                message = "Unable to query Google Play purchases: ${err.message ?: "offline"}",
                effectiveEntitlement = getEffectiveEntitlementSnapshot()
            )
        }

        val bestRecord = selectHighestPriorityRecord(purchases)
        if (bestRecord == null) {
            val now = clockMs()
            val prev = billingDao.getEntitlementSnapshot()
            val cleared = SubscriptionEntitlementEntity(
                entitlementId = "primary",
                tierId = SubscriptionTier.FREE.id,
                lifecycleStatus = if (prev != null && prev.tierId != SubscriptionTier.FREE.id) {
                    EntitlementLifecycleStatus.EXPIRED.id
                } else {
                    EntitlementLifecycleStatus.FREE.id
                },
                verificationStatus = ServerVerificationStatus.NONE.id,
                verificationMessage = "No active Google Play subscriptions found on this Google account.",
                updatedAtEpochMs = now
            )
            billingDao.upsertEntitlement(cleared)
            preferencesManager.setVerifiedSubscriptionCache(SubscriptionTier.FREE, 0L, 0L, "")
            syncVerifiedTierToFamilyBackend(SubscriptionTier.FREE)
            return@withContext BillingOperationFeedback(
                success = true,
                message = "No active Google Play subscriptions found. Period & Cycle Tracker remains free forever.",
                effectiveEntitlement = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(cleared, now)
            )
        }

        verifyAndApplyPurchaseRecord(bestRecord, isRestore = true)
    }

    suspend fun verifyAndApplyPurchaseRecord(
        record: PlayPurchaseRecord,
        isRestore: Boolean = false
    ): BillingOperationFeedback = withContext(Dispatchers.IO) {
        val now = clockMs()
        val expectedTier = SubscriptionEntitlementEngine.tierForProductId(record.productId)

        // Pending Play transactions must stay locked until settled & verified on server.
        if (record.purchaseState == BillingPurchaseState.PENDING) {
            val pendingEntity = SubscriptionEntitlementEntity(
                entitlementId = "primary",
                tierId = expectedTier.id,
                productId = record.productId,
                basePlanId = record.basePlanId,
                purchaseToken = record.purchaseToken,
                orderId = record.orderId,
                purchaseState = BillingPurchaseState.PENDING.id,
                lifecycleStatus = EntitlementLifecycleStatus.PENDING_VERIFICATION.id,
                verificationStatus = ServerVerificationStatus.NONE.id,
                verificationMessage = "Payment pending in Google Play. Access unlocks after settlement.",
                autoRenewing = record.isAutoRenewing,
                acknowledged = record.isAcknowledged,
                purchasedAtEpochMs = record.purchaseTimeEpochMs,
                updatedAtEpochMs = now
            )
            billingDao.upsertEntitlement(pendingEntity)
            preferencesManager.setVerifiedSubscriptionCache(SubscriptionTier.FREE, 0L, 0L, "")
            return@withContext BillingOperationFeedback(
                success = false,
                message = "Google Play payment is pending confirmation. Paid features unlock after settlement.",
                effectiveEntitlement = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(pendingEntity, now)
            )
        }

        val firebaseAuth = firebaseAuthProvider()
        val uid = runCatching { firebaseAuth?.currentUser?.uid }.getOrNull()
        val idToken = runCatching { firebaseAuth?.currentUser?.getIdToken(false)?.await()?.token }.getOrNull()
        val endpointUrl = resolveVerificationUrl()

        val request = ServerPurchaseValidationRequest(
            packageName = appContext.packageName,
            productId = record.productId,
            basePlanId = record.basePlanId,
            purchaseToken = record.purchaseToken,
            orderId = record.orderId,
            purchaseTimeEpochMs = record.purchaseTimeEpochMs,
            firebaseUid = uid,
            firebaseIdToken = idToken
        )

        val validationResult = verificationClient.verifyPurchase(
            request = request,
            endpointUrl = endpointUrl,
            allowLocalDebugLoopback = allowLocalDebugLoopbackUrls
        )

        when (validationResult) {
            is ServerPurchaseValidationResult.Verified -> {
                val preEvalEntity = SubscriptionEntitlementEntity(
                    entitlementId = "primary",
                    tierId = validationResult.tier.id,
                    productId = validationResult.productId,
                    basePlanId = validationResult.basePlanId,
                    purchaseToken = record.purchaseToken,
                    orderId = record.orderId,
                    purchaseState = record.purchaseState.id,
                    lifecycleStatus = validationResult.lifecycleStatus.id,
                    verificationStatus = ServerVerificationStatus.SERVER_VERIFIED.id,
                    verificationReceiptId = validationResult.receiptId,
                    verificationMessage = validationResult.message,
                    autoRenewing = validationResult.autoRenewing,
                    acknowledged = record.isAcknowledged || validationResult.acknowledgedByServer,
                    purchasedAtEpochMs = if (validationResult.purchasedAtEpochMs > 0L) {
                        validationResult.purchasedAtEpochMs
                    } else {
                        record.purchaseTimeEpochMs
                    },
                    expiresAtEpochMs = validationResult.expiresAtEpochMs,
                    gracePeriodEndsAtEpochMs = validationResult.gracePeriodEndsAtEpochMs,
                    lastValidatedEpochMs = now,
                    linkedFirebaseUid = uid.orEmpty(),
                    updatedAtEpochMs = now
                )
                val evaluated = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(preEvalEntity, now)
                val finalEntity = if (evaluated.lifecycleStatus == EntitlementLifecycleStatus.EXPIRED) {
                    preEvalEntity.copy(
                        tierId = SubscriptionTier.FREE.id,
                        lifecycleStatus = EntitlementLifecycleStatus.EXPIRED.id
                    )
                } else {
                    preEvalEntity
                }

                // Only acknowledge after authoritative server validation passes and entitlement is not expired/revoked.
                val acknowledgedNow = if (evaluated.isEntitledToPaidFeatures &&
                    !finalEntity.acknowledged
                ) {
                    playBillingGateway.acknowledgePurchase(record.purchaseToken).isSuccess
                } else {
                    finalEntity.acknowledged
                }

                val savedEntity = finalEntity.copy(acknowledged = acknowledgedNow)
                billingDao.upsertEntitlement(savedEntity)
                latestEvaluatedEntitlement = evaluated
                preferencesManager.setVerifiedSubscriptionCache(
                    tier = evaluated.tier,
                    expiresAtEpochMs = evaluated.expiresAtEpochMs,
                    gracePeriodEndsAtEpochMs = evaluated.gracePeriodEndsAtEpochMs,
                    receiptId = validationResult.receiptId
                )
                syncVerifiedTierToFamilyBackend(evaluated.tier)

                val actionWord = if (isRestore) "Restored" else "Verified"
                BillingOperationFeedback(
                    success = evaluated.isEntitledToPaidFeatures,
                    message = if (evaluated.isEntitledToPaidFeatures) {
                        "$actionWord ${evaluated.tier.displayName} (max ${evaluated.maxAllowedProfiles} profiles)."
                    } else {
                        evaluated.statusSummary
                    },
                    effectiveEntitlement = evaluated
                )
            }

            is ServerPurchaseValidationResult.Rejected -> {
                val rejectedEntity = SubscriptionEntitlementEntity(
                    entitlementId = "primary",
                    tierId = SubscriptionTier.FREE.id,
                    productId = record.productId,
                    basePlanId = record.basePlanId,
                    purchaseToken = record.purchaseToken,
                    orderId = record.orderId,
                    purchaseState = record.purchaseState.id,
                    lifecycleStatus = validationResult.lifecycleStatus.id,
                    verificationStatus = ServerVerificationStatus.REJECTED.id,
                    verificationMessage = validationResult.reason,
                    acknowledged = record.isAcknowledged,
                    lastValidatedEpochMs = now,
                    linkedFirebaseUid = uid.orEmpty(),
                    updatedAtEpochMs = now
                )
                billingDao.upsertEntitlement(rejectedEntity)
                val evaluatedRejected = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(rejectedEntity, now)
                latestEvaluatedEntitlement = evaluatedRejected
                preferencesManager.setVerifiedSubscriptionCache(SubscriptionTier.FREE, 0L, 0L, "")
                syncVerifiedTierToFamilyBackend(SubscriptionTier.FREE)
                BillingOperationFeedback(
                    success = false,
                    message = validationResult.reason,
                    effectiveEntitlement = evaluatedRejected
                )
            }

            is ServerPurchaseValidationResult.EndpointUnconfigured -> {
                val unconfiguredEntity = SubscriptionEntitlementEntity(
                    entitlementId = "primary",
                    tierId = expectedTier.id,
                    productId = record.productId,
                    basePlanId = record.basePlanId,
                    purchaseToken = record.purchaseToken,
                    orderId = record.orderId,
                    purchaseState = record.purchaseState.id,
                    lifecycleStatus = EntitlementLifecycleStatus.PENDING_VERIFICATION.id,
                    verificationStatus = ServerVerificationStatus.ENDPOINT_UNCONFIGURED.id,
                    verificationMessage = validationResult.reason,
                    autoRenewing = record.isAutoRenewing,
                    acknowledged = record.isAcknowledged,
                    purchasedAtEpochMs = record.purchaseTimeEpochMs,
                    linkedFirebaseUid = uid.orEmpty(),
                    updatedAtEpochMs = now
                )
                billingDao.upsertEntitlement(unconfiguredEntity)
                val evaluatedUnconfigured = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(unconfiguredEntity, now)
                latestEvaluatedEntitlement = evaluatedUnconfigured
                preferencesManager.setVerifiedSubscriptionCache(SubscriptionTier.FREE, 0L, 0L, "")
                BillingOperationFeedback(
                    success = false,
                    message = validationResult.reason,
                    effectiveEntitlement = evaluatedUnconfigured
                )
            }

            is ServerPurchaseValidationResult.TransientFailure -> {
                val pendingEntity = SubscriptionEntitlementEntity(
                    entitlementId = "primary",
                    tierId = expectedTier.id,
                    productId = record.productId,
                    basePlanId = record.basePlanId,
                    purchaseToken = record.purchaseToken,
                    orderId = record.orderId,
                    purchaseState = record.purchaseState.id,
                    lifecycleStatus = EntitlementLifecycleStatus.PENDING_VERIFICATION.id,
                    verificationStatus = ServerVerificationStatus.NONE.id,
                    verificationMessage = validationResult.reason,
                    autoRenewing = record.isAutoRenewing,
                    acknowledged = record.isAcknowledged,
                    purchasedAtEpochMs = record.purchaseTimeEpochMs,
                    linkedFirebaseUid = uid.orEmpty(),
                    updatedAtEpochMs = now
                )
                billingDao.upsertEntitlement(pendingEntity)
                val evaluatedPending = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(pendingEntity, now)
                latestEvaluatedEntitlement = evaluatedPending
                preferencesManager.setVerifiedSubscriptionCache(SubscriptionTier.FREE, 0L, 0L, "")
                BillingOperationFeedback(
                    success = false,
                    message = validationResult.reason,
                    effectiveEntitlement = evaluatedPending
                )
            }
        }
    }

    private suspend fun syncVerifiedTierToFamilyBackend(tier: SubscriptionTier) {
        val repo = familyCircleRepositoryProvider() ?: return
        val isFamily = tier == SubscriptionTier.FAMILY
        runCatching {
            repo.setFamilySubscriptionEligibility(isFamily, null)
        }
    }

    private fun selectHighestPriorityRecord(purchases: List<PlayPurchaseRecord>): PlayPurchaseRecord? {
        if (purchases.isEmpty()) return null
        return purchases.maxByOrNull { record ->
            val tierScore = when (SubscriptionEntitlementEngine.tierForProductId(record.productId)) {
                SubscriptionTier.FAMILY -> 30
                SubscriptionTier.PLUS -> 20
                SubscriptionTier.LIFETIME -> 25
                SubscriptionTier.FREE -> 0
            }
            val stateScore = when (record.purchaseState) {
                BillingPurchaseState.PURCHASED -> 5
                BillingPurchaseState.PENDING -> 2
                BillingPurchaseState.UNSPECIFIED -> 0
            }
            tierScore + stateScore
        }
    }
}
