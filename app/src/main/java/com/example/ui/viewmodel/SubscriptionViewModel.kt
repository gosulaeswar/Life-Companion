package com.example.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billing.BillingCycleFilter
import com.example.billing.EffectiveEntitlement
import com.example.billing.SubscriptionRepository
import com.example.data.local.entity.ConfigurablePricingPlanEntity
import com.example.security.LocalPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val effectiveEntitlement: EffectiveEntitlement = EffectiveEntitlement(),
    val pricingPlans: List<ConfigurablePricingPlanEntity> = emptyList(),
    val selectedBillingCycle: BillingCycleFilter = BillingCycleFilter.ANNUAL,
    val verificationUrl: String = "",
    val pricingConfigUrl: String = "",
    val canEditRuntimeConfig: Boolean = false,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val errorBanner: String? = null
)

class SubscriptionViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val preferencesManager: LocalPreferencesManager
) : ViewModel() {

    private val selectedCycle = MutableStateFlow(BillingCycleFilter.ANNUAL)
    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorBanner = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            subscriptionRepository.initializeAndRefresh()
        }
        viewModelScope.launch {
            subscriptionRepository.purchaseUpdates.collectLatest { update ->
                isBusy.value = true
                try {
                    val feedback = subscriptionRepository.handlePurchaseUpdate(update)
                    if (feedback.success) {
                        statusMessage.value = feedback.message
                        errorBanner.value = null
                    } else {
                        errorBanner.value = feedback.message
                    }
                } finally {
                    isBusy.value = false
                }
            }
        }
    }

    val uiState: StateFlow<SubscriptionUiState> = combine(
        subscriptionRepository.observeEffectiveEntitlement(),
        subscriptionRepository.observeConfigurablePricingPlans(),
        preferencesManager.preferencesFlow,
        selectedCycle,
        combine(isBusy, statusMessage, errorBanner) { busy, msg, err -> Triple(busy, msg, err) }
    ) { entitlement, plans, prefs, cycle, meta ->
        SubscriptionUiState(
            effectiveEntitlement = entitlement,
            pricingPlans = plans,
            selectedBillingCycle = cycle,
            verificationUrl = prefs.billingVerificationUrl,
            pricingConfigUrl = prefs.billingPricingConfigUrl,
            canEditRuntimeConfig = subscriptionRepository.allowRuntimeEndpointOverrides,
            isBusy = meta.first,
            statusMessage = meta.second,
            errorBanner = meta.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SubscriptionUiState(canEditRuntimeConfig = subscriptionRepository.allowRuntimeEndpointOverrides)
    )

    fun selectBillingCycle(cycle: BillingCycleFilter) {
        selectedCycle.value = cycle
    }

    fun clearBanners() {
        statusMessage.value = null
        errorBanner.value = null
    }

    fun launchSubscriptionPurchase(activity: Activity?, plan: ConfigurablePricingPlanEntity) {
        if (activity == null) {
            errorBanner.value = "Unable to open Google Play Billing without an active screen context."
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            clearBanners()
            try {
                subscriptionRepository.startPurchase(activity, plan)
                    .onFailure { err -> errorBanner.value = err.message ?: "Unable to launch Google Play checkout." }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            isBusy.value = true
            clearBanners()
            try {
                val feedback = subscriptionRepository.restorePurchases()
                if (feedback.success) {
                    statusMessage.value = feedback.message
                } else {
                    errorBanner.value = feedback.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun verifyExpiryAndSyncPlayPrices() {
        viewModelScope.launch {
            isBusy.value = true
            clearBanners()
            try {
                val ent = subscriptionRepository.checkAndEnforceExpiry()
                val priceSync = subscriptionRepository.syncLivePlayStorePrices()
                statusMessage.value = if (priceSync.isSuccess) {
                    "Entitlement refreshed (${ent.tier.displayName} • ${ent.lifecycleStatus.label}) and live Play Store prices synced."
                } else {
                    "Entitlement verified (${ent.tier.displayName} • ${ent.lifecycleStatus.label})."
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun fetchRemotePricingCatalog() {
        viewModelScope.launch {
            isBusy.value = true
            clearBanners()
            try {
                val res = subscriptionRepository.refreshRemotePricingConfig()
                if (res.success) {
                    statusMessage.value = res.message
                } else {
                    errorBanner.value = res.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun updateConfigurablePlan(
        planKey: String,
        priceText: String,
        badgeText: String,
        isEnabled: Boolean
    ) {
        viewModelScope.launch {
            isBusy.value = true
            clearBanners()
            try {
                val res = subscriptionRepository.updateConfigurablePlan(planKey, priceText, badgeText, isEnabled)
                if (res.success) statusMessage.value = res.message else errorBanner.value = res.message
            } finally {
                isBusy.value = false
            }
        }
    }

    fun resetConfigurableDefaults() {
        viewModelScope.launch {
            isBusy.value = true
            try {
                statusMessage.value = subscriptionRepository.resetConfigurablePricingDefaults().message
            } finally {
                isBusy.value = false
            }
        }
    }

    fun saveEndpoints(verificationUrl: String, pricingConfigUrl: String) {
        viewModelScope.launch {
            isBusy.value = true
            clearBanners()
            try {
                val res = subscriptionRepository.saveBillingEndpoints(verificationUrl, pricingConfigUrl)
                if (res.success) statusMessage.value = res.message else errorBanner.value = res.message
            } finally {
                isBusy.value = false
            }
        }
    }
}
