package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.billing.BillingCatalogIds
import com.example.billing.BillingCycleFilter
import com.example.billing.EntitlementLifecycleStatus
import com.example.data.local.entity.ConfigurablePricingPlanEntity
import com.example.security.SubscriptionTier
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.ErrorCoralLight
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.HeroForestDark
import com.example.ui.theme.HeroTealDeep
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.SubscriptionUiState
import com.example.ui.viewmodel.SubscriptionViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionScreen(
    uiState: SubscriptionUiState,
    viewModel: SubscriptionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val entitlement = uiState.effectiveEntitlement
    var showConfigPanel by rememberSaveable { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<ConfigurablePricingPlanEntity?>(null) }

    val displayedPlans = remember(uiState.pricingPlans, uiState.selectedBillingCycle) {
        val cycleLabel = if (uiState.selectedBillingCycle == BillingCycleFilter.ANNUAL) "Annual" else "Monthly"
        val freePlan = uiState.pricingPlans.firstOrNull { it.planKey == BillingCatalogIds.PLAN_KEY_FREE }
        val plusPlan = uiState.pricingPlans.firstOrNull {
            it.tierId == SubscriptionTier.PLUS.id && it.billingPeriodLabel.equals(cycleLabel, ignoreCase = true)
        }
        val familyPlan = uiState.pricingPlans.firstOrNull {
            it.tierId == SubscriptionTier.FAMILY.id && it.billingPeriodLabel.equals(cycleLabel, ignoreCase = true)
        }
        listOfNotNull(freePlan, plusPlan, familyPlan)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("subscription_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "💎 ${stringResource(id = R.string.subscription_screen_title)}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Google Play Billing • Server-Verified Entitlements",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = viewModel::restorePurchases,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.testTag("subscription_restore_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(id = R.string.subscription_restore_cta))
                }
            }
        }

        // Current Entitlement Hero Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag("subscription_status_hero"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = HeroForestDark)
            ) {
                Column(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(HeroForestDark, HeroTealDeep)))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entitlement.tier.displayName,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = entitlement.statusSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = when (entitlement.lifecycleStatus) {
                                EntitlementLifecycleStatus.ACTIVE -> FertileLeafGreen.copy(alpha = 0.30f)
                                EntitlementLifecycleStatus.GRACE_PERIOD -> AmberNotice.copy(alpha = 0.30f)
                                EntitlementLifecycleStatus.EXPIRED,
                                EntitlementLifecycleStatus.REVOKED -> ErrorCoralLight.copy(alpha = 0.35f)
                                else -> Color.White.copy(alpha = 0.16f)
                            }
                        ) {
                            Text(
                                text = entitlement.lifecycleStatus.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HeroMetaChip("Profiles: Up to ${entitlement.maxAllowedProfiles}")
                        HeroMetaChip("Verify: ${entitlement.verificationStatus.label}")
                        if (entitlement.expiresAtEpochMs > 0L) {
                            val expStr = DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .format(Date(entitlement.expiresAtEpochMs))
                            HeroMetaChip("Renewal/Expiry: $expStr")
                        }
                    }
                }
            }
        }

        // Non-negotiable Free Period Tracker Pledge Banner
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag("period_always_free_pledge_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(id = R.string.subscription_period_always_free),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Cycle & Period Tracking, predictions, symptoms, and local encrypted vault backup are permanently ₹0 with zero paywalls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (uiState.statusMessage != null || uiState.errorBanner != null) {
            item {
                val isError = uiState.errorBanner != null
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.errorBanner ?: uiState.statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearBanners) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss status")
                        }
                    }
                }
            }
        }

        // Billing Cycle Switch (responsive FlowRow for compact screens and large font scales)
        item {
            FlowRow(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Billing Cycle:",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                BillingCycleFilter.entries.forEach { cycle ->
                    FilterChip(
                        selected = uiState.selectedBillingCycle == cycle,
                        onClick = { viewModel.selectBillingCycle(cycle) },
                        label = {
                            Text(if (cycle == BillingCycleFilter.ANNUAL) "Annual (Best Value)" else "Monthly")
                        },
                        modifier = Modifier.testTag("billing_cycle_${cycle.name.lowercase()}")
                    )
                }
                if (uiState.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.CenterVertically),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // Tier Plan Cards
        displayedPlans.forEach { plan ->
            item(key = plan.planKey) {
                val isCurrentTier = entitlement.tier.id == plan.tierId &&
                    (plan.tierId == SubscriptionTier.FREE.id || entitlement.isEntitledToPaidFeatures)
                TierPlanCard(
                    plan = plan,
                    isCurrentPlan = isCurrentTier,
                    isBusy = uiState.isBusy,
                    onSelectPlan = { viewModel.launchSubscriptionPurchase(activity, plan) }
                )
            }
        }

        // Restore & Expiry Re-Check Row
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Server Validation & Subscription Lifecycle", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = "Purchases are verified by our backend via Google Play Developer API before unlocking features. Subscriptions automatically downgrade to Free on expiry or revocation (Period Tracker stays free).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = viewModel::restorePurchases,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("subscription_restore_cta_card")
                        ) {
                            Text("Restore Purchases")
                        }
                        OutlinedButton(
                            onClick = viewModel::verifyExpiryAndSyncPlayPrices,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("subscription_sync_status_button")
                        ) {
                            Text("Verify Expiry & Prices")
                        }
                        OutlinedButton(
                            onClick = viewModel::fetchRemotePricingCatalog,
                            enabled = !uiState.isBusy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("subscription_refresh_catalog_button")
                        ) {
                            Text("Sync Remote Pricing")
                        }
                    }
                }
            }
        }

        // Configurable Pricing & Endpoint Configuration Card (editable only in debug/staging builds)
        if (uiState.canEditRuntimeConfig) {
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Staging Pricing & Endpoints", style = MaterialTheme.typography.titleSmall)
                            }
                            TextButton(
                                onClick = { showConfigPanel = !showConfigPanel },
                                modifier = Modifier.testTag("toggle_pricing_config_button")
                            ) {
                                Text(if (showConfigPanel) "Hide" else "Configure")
                            }
                        }

                        if (showConfigPanel) {
                            var vUrlInput by remember(uiState.verificationUrl) { mutableStateOf(uiState.verificationUrl) }
                            var pUrlInput by remember(uiState.pricingConfigUrl) { mutableStateOf(uiState.pricingConfigUrl) }

                            OutlinedTextField(
                                value = vUrlInput,
                                onValueChange = { vUrlInput = it },
                                label = { Text("HTTPS Purchase Verification URL") },
                                placeholder = { Text("https://api.yourdomain.com/v1/billing/verify") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("billing_verification_url_input")
                            )
                            OutlinedTextField(
                                value = pUrlInput,
                                onValueChange = { pUrlInput = it },
                                label = { Text("HTTPS Pricing Catalog URL") },
                                placeholder = { Text("https://api.yourdomain.com/v1/billing/pricing") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("billing_pricing_url_input")
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.saveEndpoints(vUrlInput, pUrlInput) },
                                    enabled = !uiState.isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("save_billing_endpoints_button")
                                ) {
                                    Text("Save URLs")
                                }
                                OutlinedButton(
                                    onClick = viewModel::fetchRemotePricingCatalog,
                                    enabled = !uiState.isBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("fetch_remote_pricing_button")
                                ) {
                                    Text("Fetch Remote")
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Editable Pricing Plans (${uiState.pricingPlans.size})", style = MaterialTheme.typography.labelLarge)
                            uiState.pricingPlans.forEach { plan ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${plan.displayName} (${plan.billingPeriodLabel})", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${plan.priceText} • Source: ${plan.configSource} • ${if (plan.isEnabled) "Enabled" else "Paused"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = { editingPlan = plan }) {
                                        Text("Edit")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = viewModel::resetConfigurableDefaults,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text("Reset Pricing Catalog to Defaults")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    editingPlan?.let { target ->
        EditPricingPlanDialog(
            plan = target,
            onDismiss = { editingPlan = null },
            onSave = { priceText, badgeText, enabled ->
                viewModel.updateConfigurablePlan(target.planKey, priceText, badgeText, enabled)
                editingPlan = null
            }
        )
    }
}

@Composable
private fun HeroMetaChip(text: String) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TierPlanCard(
    plan: ConfigurablePricingPlanEntity,
    isCurrentPlan: Boolean,
    isBusy: Boolean,
    onSelectPlan: () -> Unit
) {
    val isPaid = plan.tierId != SubscriptionTier.FREE.id
    val features = remember(plan.featuresCsv) {
        plan.featuresCsv.split("|").filter { it.isNotBlank() }
    }
    Card(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("plan_card_${plan.planKey}"),
        shape = RoundedCornerShape(22.dp),
        border = if (isCurrentPlan) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (plan.tierId == SubscriptionTier.FAMILY.id) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isPaid) {
                            Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        Text(plan.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    if (plan.badgeText.isNotBlank()) {
                        Text(plan.badgeText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    text = plan.priceText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoMetricFont,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            features.forEach { feature ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(feature, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (isPaid) {
                Button(
                    onClick = onSelectPlan,
                    enabled = !isBusy && plan.isEnabled && !isCurrentPlan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("subscribe_btn_${plan.planKey}")
                ) {
                    Text(
                        when {
                            isCurrentPlan -> "Current Verified Plan"
                            !plan.isEnabled -> "Temporarily Unavailable"
                            else -> "Subscribe via Google Play (${plan.priceText})"
                        }
                    )
                }
            } else {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isCurrentPlan) "Active Free Forever Baseline" else "Included Forever (₹0)")
                }
            }
        }
    }
}

@Composable
private fun EditPricingPlanDialog(
    plan: ConfigurablePricingPlanEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var priceText by remember { mutableStateOf(plan.priceText) }
    var badgeText by remember { mutableStateOf(plan.badgeText) }
    var enabled by remember { mutableStateOf(plan.isEnabled) }
    val isFree = plan.tierId == SubscriptionTier.FREE.id

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${plan.displayName} (${plan.billingPeriodLabel})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    enabled = !isFree,
                    label = { Text(if (isFree) "Price (Locked ₹0 Forever)" else "Display Price Label") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = badgeText,
                    onValueChange = { badgeText = it },
                    label = { Text("Badge Text") },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isFree) "Free Tier Always Enabled" else "Plan Offered in Catalog", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = if (isFree) true else enabled,
                        onCheckedChange = { if (!isFree) enabled = it },
                        enabled = !isFree
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(priceText, badgeText, enabled) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
