package com.example.ui.navigation

import com.example.ui.screens.ModuleSubRoutes
import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose destinations for LifeWeave 2.0.
 */
sealed interface LifeWeaveDest {
    @Serializable data object Home : LifeWeaveDest
    @Serializable data object Water : LifeWeaveDest
    @Serializable data object Period : LifeWeaveDest
    @Serializable data object Modules : LifeWeaveDest
    @Serializable data object Settings : LifeWeaveDest
    @Serializable data object ProfileEdit : LifeWeaveDest

    @Serializable data object FitnessYoga : LifeWeaveDest
    @Serializable data object Diet : LifeWeaveDest
    @Serializable data object Medical : LifeWeaveDest
    @Serializable data object Beauty : LifeWeaveDest
    @Serializable data object Wellbeing : LifeWeaveDest
    @Serializable data object Reports : LifeWeaveDest
    @Serializable data object OnDeviceAi : LifeWeaveDest
    @Serializable data object ConnectedDevices : LifeWeaveDest
    @Serializable data object HealthVault : LifeWeaveDest
    @Serializable data object FamilyCircle : LifeWeaveDest
    @Serializable data object Subscription : LifeWeaveDest
    @Serializable data class RoutinesMood(val initialTab: Int = 0) : LifeWeaveDest

    companion object {
        fun fromSubRouteString(route: String): LifeWeaveDest = when (route) {
            ModuleSubRoutes.ROUTE_FITNESS_YOGA -> FitnessYoga
            ModuleSubRoutes.ROUTE_DIET -> Diet
            ModuleSubRoutes.ROUTE_MEDICAL -> Medical
            ModuleSubRoutes.ROUTE_BEAUTY -> Beauty
            ModuleSubRoutes.ROUTE_WELLBEING -> Wellbeing
            ModuleSubRoutes.ROUTE_REPORTS -> Reports
            ModuleSubRoutes.ROUTE_ON_DEVICE_AI -> OnDeviceAi
            ModuleSubRoutes.ROUTE_CONNECTED_DEVICES -> ConnectedDevices
            ModuleSubRoutes.ROUTE_HEALTH_VAULT -> HealthVault
            ModuleSubRoutes.ROUTE_FAMILY_CIRCLE -> FamilyCircle
            ModuleSubRoutes.ROUTE_SUBSCRIPTION -> Subscription
            ModuleSubRoutes.ROUTE_ROUTINES_MOOD -> RoutinesMood(0)
            ModuleSubRoutes.ROUTE_MOOD_JOURNAL -> RoutinesMood(1)
            ModuleSubRoutes.ROUTE_CHALLENGES -> RoutinesMood(2)
            "water" -> Water
            "period" -> Period
            "settings" -> Settings
            else -> Modules
        }
    }
}
