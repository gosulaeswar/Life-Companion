package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ConfigurablePricingPlanEntity
import com.example.data.local.entity.SubscriptionEntitlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionBillingDao {
    @Query("SELECT * FROM subscription_entitlements WHERE entitlementId = :id LIMIT 1")
    fun observeEntitlement(id: String = "primary"): Flow<SubscriptionEntitlementEntity?>

    @Query("SELECT * FROM subscription_entitlements WHERE entitlementId = :id LIMIT 1")
    suspend fun getEntitlementSnapshot(id: String = "primary"): SubscriptionEntitlementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntitlement(entity: SubscriptionEntitlementEntity)

    @Query("DELETE FROM subscription_entitlements")
    suspend fun clearAllEntitlements()

    @Query("SELECT * FROM configurable_pricing_plans ORDER BY sortOrder ASC")
    fun observePricingPlans(): Flow<List<ConfigurablePricingPlanEntity>>

    @Query("SELECT * FROM configurable_pricing_plans ORDER BY sortOrder ASC")
    suspend fun getPricingPlansSnapshot(): List<ConfigurablePricingPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPricingPlans(plans: List<ConfigurablePricingPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPricingPlan(plan: ConfigurablePricingPlanEntity)
}
