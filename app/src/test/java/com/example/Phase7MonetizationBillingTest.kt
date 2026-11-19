package com.example

import android.app.Activity
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.billing.BillingCatalogIds
import com.example.billing.BillingCycleFilter
import com.example.billing.BillingHttpTransport
import com.example.billing.BillingPurchaseState
import com.example.billing.EntitlementLifecycleStatus
import com.example.billing.HttpTransportRequest
import com.example.billing.HttpTransportResponse
import com.example.billing.PlayBillingGateway
import com.example.billing.PlayPurchaseRecord
import com.example.billing.PlayPurchaseUpdate
import com.example.billing.PlayStoreProductPrice
import com.example.billing.ServerBillingVerificationClient
import com.example.billing.ServerVerificationStatus
import com.example.billing.SubscriptionEntitlementEngine
import com.example.billing.SubscriptionRepository
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SubscriptionEntitlementEntity
import com.example.security.FeatureGate
import com.example.security.LocalDefaultEntitlementPolicy
import com.example.security.LocalPreferencesManager
import com.example.security.SubscriptionTier
import com.example.ui.viewmodel.SubscriptionViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.billing.EffectiveEntitlement
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.theme.LifeCompanionTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SubscriptionUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase7MonetizationBillingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakePlayBillingGateway : PlayBillingGateway {
        private val updates = MutableSharedFlow<PlayPurchaseUpdate>(extraBufferCapacity = 8)
        override val purchaseUpdates: Flow<PlayPurchaseUpdate> = updates.asSharedFlow()

        var playStoreConnected = true
        var productPrices: Map<String, PlayStoreProductPrice> = emptyMap()
        var activePurchases: List<PlayPurchaseRecord> = emptyList()
        val acknowledgedTokens = mutableListOf<String>()
        var lastLaunchedProductId: String? = null
        var lastOldPurchaseToken: String? = null

        override suspend fun isPlayStoreAvailable(): Boolean = playStoreConnected

        override suspend fun querySubscriptionProducts(productIds: List<String>): Result<Map<String, PlayStoreProductPrice>> =
            if (playStoreConnected) Result.success(productPrices) else Result.failure(IllegalStateException("Offline"))

        override suspend fun queryActiveSubscriptions(): Result<List<PlayPurchaseRecord>> =
            if (playStoreConnected) Result.success(activePurchases) else Result.failure(IllegalStateException("Offline"))

        override suspend fun launchSubscriptionPurchase(
            activity: Activity,
            productId: String,
            basePlanId: String,
            oldPurchaseToken: String?,
            obfuscatedAccountId: String?
        ): Result<Unit> {
            lastLaunchedProductId = productId
            lastOldPurchaseToken = oldPurchaseToken
            return Result.success(Unit)
        }

        override suspend fun acknowledgePurchase(purchaseToken: String): Result<Unit> {
            acknowledgedTokens += purchaseToken
            return Result.success(Unit)
        }

        suspend fun emitPurchaseUpdate(update: PlayPurchaseUpdate) {
            updates.emit(update)
        }

        override fun endConnection() = Unit
    }

    private fun buildMemoryDb(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Test
    fun migration11To12PreservesExistingUserRecordsAndCreatesBillingTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val schemaFile = listOf(
            File("schemas/com.example.data.local.AppDatabase/13.json"),
            File("app/schemas/com.example.data.local.AppDatabase/13.json"),
            File("schemas/com.example.data.local.AppDatabase/12.json"),
            File("app/schemas/com.example.data.local.AppDatabase/12.json")
        ).firstOrNull { it.exists() }
        assertNotNull("Expected Room schema 12.json or 13.json to be exported", schemaFile)
        val schemaJson = JSONObject(schemaFile!!.readText()).getJSONObject("database")
        assertTrue(schemaJson.getInt("version") >= 12)
        val entitiesArray = schemaJson.getJSONArray("entities")
        assertTrue(entitiesArray.length() >= 31)
        val schemaTableNames = (0 until entitiesArray.length())
            .map { entitiesArray.getJSONObject(it).getString("tableName") }
            .toSet()
        assertTrue(schemaTableNames.contains("subscription_entitlements"))
        assertTrue(schemaTableNames.contains("configurable_pricing_plans"))

        val dbName = "migration_11_12_test.db"
        context.deleteDatabase(dbName)

        val v11Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE user_profiles (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                fullName TEXT NOT NULL,
                                age INTEGER NOT NULL,
                                genderIdentity TEXT NOT NULL,
                                customGenderLabel TEXT NOT NULL,
                                heightCm INTEGER,
                                weightKg REAL,
                                activityLevel TEXT NOT NULL,
                                dailyWaterGoalMl INTEGER NOT NULL,
                                cycleTrackingEnabled INTEGER NOT NULL,
                                averageCycleLengthDays INTEGER NOT NULL,
                                averagePeriodDurationDays INTEGER NOT NULL,
                                preferredLanguageCode TEXT NOT NULL,
                                relationLabel TEXT NOT NULL,
                                linkedFamilyId TEXT,
                                linkedSlotId TEXT,
                                createdAtEpochMs INTEGER NOT NULL,
                                updatedAtEpochMs INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE period_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                startDateEpochDay INTEGER NOT NULL,
                                endDateEpochDay INTEGER,
                                flowIntensity TEXT NOT NULL,
                                symptomsCsv TEXT NOT NULL,
                                mood TEXT NOT NULL,
                                notes TEXT NOT NULL,
                                updatedAtEpochMs INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO user_profiles (
                                id, fullName, age, genderIdentity, customGenderLabel, activityLevel,
                                dailyWaterGoalMl, cycleTrackingEnabled, averageCycleLengthDays,
                                averagePeriodDurationDays, preferredLanguageCode, relationLabel,
                                createdAtEpochMs, updatedAtEpochMs
                            ) VALUES (1, 'Asha Nair', 29, 'Female', '', 'Moderate', 2500, 1, 28, 5, 'en', 'Self', 1000, 1000)
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO period_records (profileId, startDateEpochDay, flowIntensity, symptomsCsv, mood, notes, updatedAtEpochMs) VALUES (1, 20000, 'Medium', '', 'Calm', 'Kept across migration', 1000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val rawDb = v11Helper.writableDatabase
        AppDatabase.MIGRATION_11_12.migrate(rawDb)

        rawDb.query("SELECT fullName FROM user_profiles WHERE id = 1").use { profileCursor ->
            assertTrue(profileCursor.moveToFirst())
            assertEquals("Asha Nair", profileCursor.getString(0))
        }

        rawDb.query("SELECT notes FROM period_records WHERE profileId = 1").use { periodCursor ->
            assertTrue(periodCursor.moveToFirst())
            assertEquals("Kept across migration", periodCursor.getString(0))
        }

        val found = buildList {
            rawDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('subscription_entitlements', 'configurable_pricing_plans')"
            ).use { tablesCursor ->
                while (tablesCursor.moveToNext()) add(tablesCursor.getString(0))
            }
        }
        assertEquals(2, found.size)

        // Verify inserts into both migrated billing tables succeed with expected columns
        rawDb.execSQL(
            """
            INSERT INTO subscription_entitlements (
                entitlementId, tierId, productId, basePlanId, offerId, purchaseToken, orderId, purchaseState,
                lifecycleStatus, verificationStatus, verificationReceiptId, verificationMessage,
                autoRenewing, acknowledged, purchasedAtEpochMs, expiresAtEpochMs,
                gracePeriodEndsAtEpochMs, lastValidatedEpochMs, linkedFirebaseUid, updatedAtEpochMs
            ) VALUES (
                'primary', 'free', '', '', '', '', '', 'UNSPECIFIED',
                'FREE', 'NONE', '', 'Free forever',
                0, 0, 0, 0, 0, 0, '', 2000
            )
            """.trimIndent()
        )
        rawDb.execSQL(
            """
            INSERT INTO configurable_pricing_plans (
                planKey, tierId, playProductId, basePlanId, displayName, billingPeriodLabel,
                priceText, currencyCode, priceMicros, badgeText, maxProfiles, featuresCsv,
                isEnabled, sortOrder, configSource, updatedAtEpochMs
            ) VALUES (
                'free_forever', 'free', '', '', 'LifeWeave Free', 'Free Forever',
                '₹0 / Forever', 'INR', 0, 'Period Tracker Free', 1, 'Period Tracker',
                1, 0, 'DEFAULT', 2000
            )
            """.trimIndent()
        )
        rawDb.query("SELECT COUNT(*) FROM subscription_entitlements").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        rawDb.query("SELECT COUNT(*) FROM configurable_pricing_plans").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        v11Helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun periodTrackerIsAlwaysFreeAcrossAllEntitlementAndRevokedStates() {
        val revokedEntity = SubscriptionEntitlementEntity(
            tierId = SubscriptionTier.FAMILY.id,
            lifecycleStatus = EntitlementLifecycleStatus.REVOKED.id,
            verificationStatus = ServerVerificationStatus.REJECTED.id
        )
        val revokedEval = SubscriptionEntitlementEngine.evaluateEffectiveEntitlement(revokedEntity, 50_000L)
        assertEquals(SubscriptionTier.FREE, revokedEval.tier)
        assertEquals(1, revokedEval.maxAllowedProfiles)
        assertTrue(revokedEval.isPeriodTrackerAlwaysFree)
        assertFalse(revokedEval.isEntitledToPaidFeatures)

        val policy = LocalDefaultEntitlementPolicy { revokedEval.tier }
        assertTrue(policy.hasAccess(FeatureGate.PERIOD_TRACKER))
        assertTrue(policy.hasAccess(FeatureGate.WATER_TRACKER))
        assertFalse(policy.hasAccess(FeatureGate.AI_HEALTH_INTELLIGENCE))
        assertFalse(policy.hasAccess(FeatureGate.FAMILY_CIRCLE))

        val familyPolicy = LocalDefaultEntitlementPolicy { SubscriptionTier.FAMILY }
        assertTrue(familyPolicy.hasAccess(FeatureGate.PERIOD_TRACKER))
        assertTrue(familyPolicy.hasAccess(FeatureGate.FAMILY_CIRCLE))
        assertEquals(5, familyPolicy.maxAllowedProfiles())
    }

    @Test
    fun serverValidationRequiredBeforeGrantingEntitlementOrAcknowledging() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildMemoryDb(context)
        val prefs = LocalPreferencesManager(context)
        prefs.resetAll()
        val fakePlay = FakePlayBillingGateway()

        val capturedRequests = mutableListOf<HttpTransportRequest>()
        var serverShouldApprove = true
        var nowMs = 1_750_000_000_000L

        val client = ServerBillingVerificationClient(
            transport = BillingHttpTransport { req ->
                capturedRequests += req
                if (serverShouldApprove) {
                    HttpTransportResponse(
                        statusCode = 200,
                        body = """
                        {
                          "valid": true,
                          "tierId": "family",
                          "productId": "lifeweave_family_sub",
                          "basePlanId": "family-annual",
                          "status": "ACTIVE",
                          "autoRenewing": true,
                          "acknowledgedByServer": false,
                          "purchasedAtEpochMs": ${nowMs - 1_000L},
                          "expiresAtEpochMs": ${nowMs + 86_400_000L},
                          "gracePeriodEndsAtEpochMs": ${nowMs + 259_200_000L},
                          "receiptId": "srv_rcpt_901",
                          "message": "Verified via Google Play Developer API"
                        }
                        """.trimIndent()
                    )
                } else {
                    HttpTransportResponse(
                        statusCode = 403,
                        body = """{"valid":false,"message":"Purchase token revoked in Play Console"}"""
                    )
                }
            }
        )

        val repo = SubscriptionRepository(
            appContext = context,
            database = db,
            preferencesManager = prefs,
            playBillingGateway = fakePlay,
            verificationClient = client,
            firebaseAuthProvider = { null },
            clockMs = { nowMs }
        )

        // Step 1: Without HTTPS endpoint configured, unverified token CANNOT grant Plus/Family.
        val unconfiguredFeedback = repo.verifyAndApplyPurchaseRecord(
            PlayPurchaseRecord(
                productId = BillingCatalogIds.PRODUCT_FAMILY,
                basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_ANNUAL,
                orderId = "GPA.1234",
                purchaseToken = "tok_unverified",
                purchaseTimeEpochMs = nowMs,
                purchaseState = BillingPurchaseState.PURCHASED,
                isAcknowledged = false,
                isAutoRenewing = true
            )
        )
        assertFalse(unconfiguredFeedback.success)
        assertEquals(SubscriptionTier.FREE, unconfiguredFeedback.effectiveEntitlement.tier)
        assertEquals(ServerVerificationStatus.ENDPOINT_UNCONFIGURED, unconfiguredFeedback.effectiveEntitlement.verificationStatus)
        assertTrue(fakePlay.acknowledgedTokens.isEmpty())

        // Step 2: Reject non-HTTPS endpoint
        val httpReject = repo.saveBillingEndpoints("http://insecure.example.com/verify", "")
        assertFalse(httpReject.success)

        // Step 3: Save valid HTTPS endpoint & verify pending payment never grants access
        assertTrue(repo.saveBillingEndpoints("https://billing.example.com/v1/verify", "https://billing.example.com/v1/pricing").success)
        val pendingFeedback = repo.verifyAndApplyPurchaseRecord(
            PlayPurchaseRecord(
                productId = BillingCatalogIds.PRODUCT_FAMILY,
                basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_ANNUAL,
                orderId = "GPA.PENDING",
                purchaseToken = "tok_pending",
                purchaseTimeEpochMs = nowMs,
                purchaseState = BillingPurchaseState.PENDING,
                isAcknowledged = false,
                isAutoRenewing = true
            )
        )
        assertFalse(pendingFeedback.success)
        assertEquals(SubscriptionTier.FREE, pendingFeedback.effectiveEntitlement.tier)
        assertTrue(fakePlay.acknowledgedTokens.isEmpty())

        // Step 4: Server approves settled purchase -> grants Family (5 profiles) and acknowledges token
        val verifiedFeedback = repo.verifyAndApplyPurchaseRecord(
            PlayPurchaseRecord(
                productId = BillingCatalogIds.PRODUCT_FAMILY,
                basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_ANNUAL,
                orderId = "GPA.5678",
                purchaseToken = "tok_valid_family",
                purchaseTimeEpochMs = nowMs,
                purchaseState = BillingPurchaseState.PURCHASED,
                isAcknowledged = false,
                isAutoRenewing = true
            )
        )
        assertTrue(verifiedFeedback.success)
        assertEquals(SubscriptionTier.FAMILY, verifiedFeedback.effectiveEntitlement.tier)
        assertEquals(5, verifiedFeedback.effectiveEntitlement.maxAllowedProfiles)
        assertEquals(EntitlementLifecycleStatus.ACTIVE, verifiedFeedback.effectiveEntitlement.lifecycleStatus)
        assertEquals(listOf("tok_valid_family"), fakePlay.acknowledgedTokens)
        assertTrue(prefs.preferencesFlow.first().familyPlanActivated)
        assertTrue(capturedRequests.single().body!!.contains("tok_valid_family"))

        // Step 5: Server rejects forged/refunded token -> downgrades immediately
        serverShouldApprove = false
        val rejectedFeedback = repo.verifyAndApplyPurchaseRecord(
            PlayPurchaseRecord(
                productId = BillingCatalogIds.PRODUCT_FAMILY,
                basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_ANNUAL,
                orderId = "GPA.5678",
                purchaseToken = "tok_revoked",
                purchaseTimeEpochMs = nowMs,
                purchaseState = BillingPurchaseState.PURCHASED,
                isAcknowledged = true,
                isAutoRenewing = false
            )
        )
        assertFalse(rejectedFeedback.success)
        assertEquals(SubscriptionTier.FREE, rejectedFeedback.effectiveEntitlement.tier)
        assertEquals(EntitlementLifecycleStatus.REVOKED, rejectedFeedback.effectiveEntitlement.lifecycleStatus)
        assertFalse(prefs.preferencesFlow.first().familyPlanActivated)

        db.close()
    }

    @Test
    fun expiryAndGracePeriodDowngradeAutomaticallyToFree() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildMemoryDb(context)
        val prefs = LocalPreferencesManager(context)
        prefs.resetAll()
        val fakePlay = FakePlayBillingGateway()

        var nowMs = 1_000_000L
        val client = ServerBillingVerificationClient {
            HttpTransportResponse(
                statusCode = 200,
                body = """
                {
                  "valid": true,
                  "tierId": "plus",
                  "productId": "lifeweave_plus_sub",
                  "basePlanId": "plus-monthly",
                  "status": "ACTIVE",
                  "autoRenewing": false,
                  "acknowledgedByServer": true,
                  "purchasedAtEpochMs": 900000,
                  "expiresAtEpochMs": 2000000,
                  "gracePeriodEndsAtEpochMs": 3000000,
                  "receiptId": "rcpt_exp_1"
                }
                """.trimIndent()
            )
        }
        val repo = SubscriptionRepository(
            appContext = context,
            database = db,
            preferencesManager = prefs,
            playBillingGateway = fakePlay,
            verificationClient = client,
            firebaseAuthProvider = { null },
            clockMs = { nowMs }
        )
        repo.saveBillingEndpoints("https://billing.example.com/verify", "")
        val initial = repo.verifyAndApplyPurchaseRecord(
            PlayPurchaseRecord(
                productId = BillingCatalogIds.PRODUCT_PLUS,
                basePlanId = BillingCatalogIds.BASE_PLAN_PLUS_MONTHLY,
                orderId = "GPA.PLUS",
                purchaseToken = "tok_plus_1",
                purchaseTimeEpochMs = nowMs,
                purchaseState = BillingPurchaseState.PURCHASED,
                isAcknowledged = false,
                isAutoRenewing = false
            )
        )
        assertEquals(SubscriptionTier.PLUS, initial.effectiveEntitlement.tier)
        assertEquals(EntitlementLifecycleStatus.ACTIVE, initial.effectiveEntitlement.lifecycleStatus)

        // Advance past expiresAtEpochMs but inside grace period
        nowMs = 2_500_000L
        val inGrace = repo.checkAndEnforceExpiry()
        assertEquals(SubscriptionTier.PLUS, inGrace.tier)
        assertEquals(EntitlementLifecycleStatus.GRACE_PERIOD, inGrace.lifecycleStatus)
        assertTrue(inGrace.inGracePeriod)
        assertTrue(inGrace.isEntitledToPaidFeatures)

        // Advance past gracePeriodEndsAtEpochMs -> expires & reverts to Free
        nowMs = 3_500_000L
        val expired = repo.checkAndEnforceExpiry()
        assertEquals(SubscriptionTier.FREE, expired.tier)
        assertEquals(EntitlementLifecycleStatus.EXPIRED, expired.lifecycleStatus)
        assertFalse(expired.isEntitledToPaidFeatures)
        assertTrue(expired.isPeriodTrackerAlwaysFree)
        assertEquals(1, expired.maxAllowedProfiles)
        assertEquals("free", prefs.preferencesFlow.first().cachedSubscriptionTierId)

        db.close()
    }

    @Test
    fun restorePurchasesAndConfigurablePricingWorkEndToEnd() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildMemoryDb(context)
        val prefs = LocalPreferencesManager(context)
        prefs.resetAll()
        val fakePlay = FakePlayBillingGateway()
        val nowMs = 5_000_000L

        fakePlay.productPrices = mapOf(
            "${BillingCatalogIds.PRODUCT_PLUS}:${BillingCatalogIds.BASE_PLAN_PLUS_ANNUAL}" to PlayStoreProductPrice(
                productId = BillingCatalogIds.PRODUCT_PLUS,
                basePlanId = BillingCatalogIds.BASE_PLAN_PLUS_ANNUAL,
                formattedPrice = "$19.99",
                currencyCode = "USD",
                priceAmountMicros = 19_990_000L
            )
        )
        fakePlay.activePurchases = listOf(
            PlayPurchaseRecord(
                productId = BillingCatalogIds.PRODUCT_FAMILY,
                basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_MONTHLY,
                orderId = "GPA.RESTORE",
                purchaseToken = "tok_restored_family",
                purchaseTimeEpochMs = nowMs,
                purchaseState = BillingPurchaseState.PURCHASED,
                isAcknowledged = true,
                isAutoRenewing = true
            )
        )

        val client = ServerBillingVerificationClient { req ->
            if (req.method == "GET") {
                HttpTransportResponse(
                    statusCode = 200,
                    body = """
                    {
                      "version": "2026.11",
                      "plans": [
                        {
                          "planKey": "family_monthly",
                          "tierId": "family",
                          "playProductId": "lifeweave_family_sub",
                          "basePlanId": "family-monthly",
                          "displayName": "LifeWeave Family Circle",
                          "billingPeriodLabel": "Monthly",
                          "priceText": "₹249 / mo (Promo)",
                          "currencyCode": "INR",
                          "priceMicros": 249000000,
                          "badgeText": "Festival Offer • 5 Profiles",
                          "maxProfiles": 5,
                          "featuresCsv": "All Plus Features|Up to 5 Profiles",
                          "isEnabled": true,
                          "sortOrder": 3
                        }
                      ]
                    }
                    """.trimIndent()
                )
            } else {
                HttpTransportResponse(
                    statusCode = 200,
                    body = """
                    {
                      "valid": true,
                      "tierId": "family",
                      "productId": "lifeweave_family_sub",
                      "basePlanId": "family-monthly",
                      "status": "ACTIVE",
                      "autoRenewing": true,
                      "acknowledgedByServer": true,
                      "expiresAtEpochMs": ${nowMs + 30L * 86_400_000L},
                      "receiptId": "rcpt_restored_99"
                    }
                    """.trimIndent()
                )
            }
        }

        val repo = SubscriptionRepository(
            appContext = context,
            database = db,
            preferencesManager = prefs,
            playBillingGateway = fakePlay,
            verificationClient = client,
            firebaseAuthProvider = { null },
            clockMs = { nowMs }
        )
        repo.saveBillingEndpoints(
            verificationUrl = "https://api.example.com/verify",
            pricingConfigUrl = "https://api.example.com/pricing"
        )

        // Sync remote pricing & Play Store live prices
        assertTrue(repo.syncLivePlayStorePrices().isSuccess)
        val remoteRefresh = repo.refreshRemotePricingConfig()
        assertTrue(remoteRefresh.success)

        val plans = repo.observeConfigurablePricingPlans().first()
        val plusAnnual = plans.first { it.planKey == BillingCatalogIds.PLAN_KEY_PLUS_ANNUAL }
        assertEquals("$19.99 / yr", plusAnnual.priceText)
        assertEquals("PLAY_STORE_LIVE", plusAnnual.configSource)

        val famMonthly = plans.first { it.planKey == BillingCatalogIds.PLAN_KEY_FAMILY_MONTHLY }
        assertEquals("₹249 / mo (Promo)", famMonthly.priceText)
        assertEquals("Festival Offer • 5 Profiles", famMonthly.badgeText)

        // Guardrail: Period Tracker / Free plan cannot be priced or disabled
        repo.updateConfigurablePlan(BillingCatalogIds.PLAN_KEY_FREE, "₹999", "Locked", false)
        val freeAfterEdit = repo.observeConfigurablePricingPlans().first()
            .first { it.planKey == BillingCatalogIds.PLAN_KEY_FREE }
        assertEquals("₹0 / Forever", freeAfterEdit.priceText)
        assertTrue(freeAfterEdit.isEnabled)

        // Restore purchases restores Family plan via Play + Server validation
        val viewModel = SubscriptionViewModel(repo, prefs, autoInitialize = false)
        viewModel.selectBillingCycle(BillingCycleFilter.MONTHLY)
        val restored = repo.restorePurchases()
        assertTrue(restored.success)
        assertEquals(SubscriptionTier.FAMILY, restored.effectiveEntitlement.tier)
        assertEquals(5, restored.effectiveEntitlement.maxAllowedProfiles)

        // When Play reports zero active subscriptions on a subsequent restore, stale local state reverts to Free
        fakePlay.activePurchases = emptyList()
        val cleared = repo.restorePurchases()
        assertTrue(cleared.success)
        assertEquals(SubscriptionTier.FREE, cleared.effectiveEntitlement.tier)
        assertEquals(1, cleared.effectiveEntitlement.maxAllowedProfiles)

        db.close()
    }

    @Test
    fun releaseModeBlocksRuntimeEndpointSpoofingAndLocalPricingOverrides() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildMemoryDb(context)
        val prefs = LocalPreferencesManager(context)
        prefs.resetAll()
        val fakePlay = FakePlayBillingGateway()

        var contactedExternalHost = false
        val client = ServerBillingVerificationClient {
            contactedExternalHost = true
            HttpTransportResponse(
                statusCode = 200,
                body = """{"valid":true,"tierId":"family","status":"ACTIVE"}"""
            )
        }

        // Simulate release build configuration: allowRuntimeEndpointOverrides = false
        val releaseRepo = SubscriptionRepository(
            appContext = context,
            database = db,
            preferencesManager = prefs,
            playBillingGateway = fakePlay,
            verificationClient = client,
            firebaseAuthProvider = { null },
            allowLocalDebugLoopbackUrls = false,
            allowRuntimeEndpointOverrides = false
        )

        // Attack 1: Attempting to inject a spoofed HTTPS verification URL must be blocked
        val endpointAttempt = releaseRepo.saveBillingEndpoints(
            "https://attacker.example.com/fake-verify",
            "https://attacker.example.com/fake-pricing"
        )
        assertFalse(endpointAttempt.success)

        // Attack 2: Even if preferences were tampered with previously, releaseRepo ignores runtime prefs
        prefs.setBillingEndpoints("https://attacker.example.com/fake-verify", "https://attacker.example.com/fake-pricing")
        val verifyAttempt = releaseRepo.verifyAndApplyPurchaseRecord(
            PlayPurchaseRecord(
                productId = BillingCatalogIds.PRODUCT_FAMILY,
                basePlanId = BillingCatalogIds.BASE_PLAN_FAMILY_ANNUAL,
                orderId = "GPA.SPOOF",
                purchaseToken = "tok_spoof_attempt",
                purchaseTimeEpochMs = 10_000L,
                purchaseState = BillingPurchaseState.PURCHASED,
                isAcknowledged = false,
                isAutoRenewing = true
            )
        )
        assertFalse(verifyAttempt.success)
        assertFalse("Release mode must NEVER send purchase/Bearer tokens to runtime-overridden URLs", contactedExternalHost)
        assertEquals(SubscriptionTier.FREE, releaseRepo.currentCachedTier())

        // Attack 3: Local price/toggle tampering must be blocked in release builds
        val priceTamper = releaseRepo.updateConfigurablePlan(
            BillingCatalogIds.PLAN_KEY_PLUS_MONTHLY,
            "₹1 / mo",
            "Hacked",
            false
        )
        assertFalse(priceTamper.success)

        // Non-blocking policy reflects authoritative repository cache (not legacy familyPlanActivated pref)
        prefs.setFamilyPlanActivated(true, "legacy_unverified")
        val policy = LocalDefaultEntitlementPolicy { releaseRepo.currentCachedTier() }
        assertEquals(SubscriptionTier.FREE, policy.currentTier())
        assertTrue(policy.hasAccess(FeatureGate.PERIOD_TRACKER))
        assertFalse(policy.hasAccess(FeatureGate.FAMILY_CIRCLE))

        db.close()
    }

    @Test
    fun subscriptionScreenRendersInLightAndDarkModesAndHidesStagingConfigInRelease() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildMemoryDb(context)
        val prefs = LocalPreferencesManager(context)
        val fakePlay = FakePlayBillingGateway()
        val repo = SubscriptionRepository(
            appContext = context,
            database = db,
            preferencesManager = prefs,
            playBillingGateway = fakePlay,
            verificationClient = ServerBillingVerificationClient(),
            firebaseAuthProvider = { null },
            allowRuntimeEndpointOverrides = false
        )
        val viewModel = SubscriptionViewModel(repo, prefs, autoInitialize = false)

        var activeTheme by mutableStateOf(ThemeMode.LIGHT)
        var capturedLightBgLum = 0f
        var capturedDarkBgLum = 1f
        val catalog = SubscriptionEntitlementEngine.defaultConfigurableCatalog(1_000L)
        var cycle by mutableStateOf(BillingCycleFilter.ANNUAL)

        composeRule.setContent {
            LifeCompanionTheme(themeMode = activeTheme) {
                val bgLum = MaterialTheme.colorScheme.background.luminance()
                if (activeTheme == ThemeMode.LIGHT) capturedLightBgLum = bgLum else capturedDarkBgLum = bgLum

                SubscriptionScreen(
                    uiState = SubscriptionUiState(
                        effectiveEntitlement = EffectiveEntitlement(),
                        pricingPlans = catalog,
                        selectedBillingCycle = cycle,
                        canEditRuntimeConfig = false
                    ),
                    viewModel = viewModel,
                    onBack = {}
                )
            }
        }

        // Light mode assertions
        composeRule.onNodeWithTag("subscription_status_hero").assertIsDisplayed()
        composeRule.onNodeWithTag("period_always_free_pledge_card").assertIsDisplayed()
        composeRule.onNodeWithTag("billing_cycle_annual").assertIsDisplayed()
        composeRule.onNodeWithTag("billing_cycle_monthly").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_pricing_config_button").assertDoesNotExist()
        assertTrue("Expected bright background luminance in Light mode", capturedLightBgLum > 0.75f)

        // Switch dynamically to Dark mode and Monthly cycle
        activeTheme = ThemeMode.DARK
        cycle = BillingCycleFilter.MONTHLY
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("subscription_status_hero").assertIsDisplayed()
        composeRule.onNodeWithTag("period_always_free_pledge_card").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_pricing_config_button").assertDoesNotExist()
        assertTrue("Expected deep dark background luminance in Dark mode", capturedDarkBgLum < 0.10f)

        db.close()
    }

    @Test
    fun preHomeAuthScreenRendersAppGuideAndProminentLogins() {
        var continuedOffline = false
        var activeTheme by mutableStateOf(ThemeMode.LIGHT)

        composeRule.setContent {
            LifeCompanionTheme(themeMode = activeTheme) {
                com.example.ui.screens.PreHomeAuthScreen(
                    signedInUser = null,
                    canGoBackToWelcome = true,
                    onBackToWelcome = {},
                    onSignInWithGoogle = { Result.failure(IllegalStateException("test")) },
                    onSignInWithGitHub = { Result.failure(IllegalStateException("test")) },
                    onSignInWithMicrosoft = { Result.failure(IllegalStateException("test")) },
                    onContinueToApp = { continuedOffline = true }
                )
            }
        }

        composeRule.onNodeWithTag("prehome_app_overview_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("prehome_google_signin_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("prehome_github_signin_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("prehome_microsoft_signin_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("prehome_offline_continue_button").performScrollTo().assertIsDisplayed()

        // Switch to Dark mode and verify offline continuation button works
        activeTheme = ThemeMode.DARK
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("prehome_google_signin_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("prehome_offline_continue_button").performScrollTo().performClick()
        assertTrue(continuedOffline)
    }

    @Test
    fun permissionsPopupDialogRendersAndDismissesCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundManager = com.example.notifications.AlertSoundManager(context)
        val wellbeingManager = com.example.wellbeing.DeviceWellbeingManager(context)
        var dismissed = false

        composeRule.setContent {
            LifeCompanionTheme(themeMode = ThemeMode.LIGHT) {
                com.example.ui.screens.PermissionsPopupDialog(
                    alertSoundManager = soundManager,
                    deviceWellbeingManager = wellbeingManager,
                    onDismissComplete = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("permissions_popup_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("permissions_popup_more_toggle").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("permissions_popup_later_button").assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }
}
