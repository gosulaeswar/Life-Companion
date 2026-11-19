package com.example.security

enum class SubscriptionTier(
    val id: String,
    val displayName: String,
    val maxProfiles: Int
) {
    FREE(id = "free", displayName = "LifeWeave Free", maxProfiles = 1),
    PLUS(id = "plus", displayName = "LifeWeave Plus", maxProfiles = 1),
    FAMILY(id = "family", displayName = "LifeWeave Family Circle", maxProfiles = 5),
    LIFETIME(id = "lifetime", displayName = "LifeWeave Lifetime", maxProfiles = 1)
}

enum class FeatureGate(
    val key: String,
    val requiredTier: SubscriptionTier,
    val isAlwaysFreeForever: Boolean = false
) {
    WATER_TRACKER("water_tracker", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    PERIOD_TRACKER("period_tracker", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    MEDICAL_CARE("medical_care", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    EXERCISE_TRACKER("exercise_tracker", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    YOGA_TRACKER("yoga_tracker", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    DIET_TRACKER("diet_tracker", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    DAILY_REMINDERS("daily_reminders", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    BEAUTY_CARE("beauty_care", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    DIGITAL_WELLBEING("digital_wellbeing", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    REPORTS_ANALYTICS("reports_analytics", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    ON_DEVICE_AI("on_device_ai", SubscriptionTier.FREE, isAlwaysFreeForever = true),
    CONNECTED_DEVICES("connected_devices", SubscriptionTier.FREE, isAlwaysFreeForever = true),

    // LifeWeave 2.0 upcoming capabilities (Phases 2-7)
    AI_HEALTH_INTELLIGENCE("ai_health_intelligence", SubscriptionTier.PLUS),
    HEALTH_RECORDS_VAULT("health_records_vault", SubscriptionTier.PLUS),
    INDIAN_NUTRITION_PLANNER("indian_nutrition_planner", SubscriptionTier.PLUS),
    FAMILY_CIRCLE("family_circle", SubscriptionTier.FAMILY)
}

/**
 * Cleanly decouples billing/subscription entitlement evaluation from Android Keystore
 * data encryption ([KeystoreVaultManager]) and ECDSA integrity checks ([OfflineLicenseVerifier]).
 * All Phase 1 modules and Cycle & Period Care remain permanently Free.
 */
interface EntitlementPolicy {
    fun currentTier(): SubscriptionTier
    fun hasAccess(feature: FeatureGate): Boolean
    fun maxAllowedProfiles(): Int
}

class LocalDefaultEntitlementPolicy(
    private val activeTierProvider: () -> SubscriptionTier = { SubscriptionTier.FREE }
) : EntitlementPolicy {
    override fun currentTier(): SubscriptionTier = activeTierProvider()

    override fun hasAccess(feature: FeatureGate): Boolean {
        if (feature.isAlwaysFreeForever || feature.requiredTier == SubscriptionTier.FREE) {
            return true
        }
        val tier = currentTier()
        return when (feature.requiredTier) {
            SubscriptionTier.FREE -> true
            SubscriptionTier.PLUS -> tier in setOf(SubscriptionTier.PLUS, SubscriptionTier.FAMILY, SubscriptionTier.LIFETIME)
            SubscriptionTier.LIFETIME -> tier == SubscriptionTier.LIFETIME
            SubscriptionTier.FAMILY -> tier == SubscriptionTier.FAMILY
        }
    }

    override fun maxAllowedProfiles(): Int = currentTier().maxProfiles

    companion object {
        fun maxProfilesForTier(tier: SubscriptionTier): Int = tier.maxProfiles
    }
}
