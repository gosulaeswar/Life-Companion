package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.local.entity.UserProfileEntity
import com.example.di.AppContainer
import com.example.di.SimpleViewModelFactory
import com.example.family.AuthenticatedUserSnapshot
import com.example.family.FamilyProfileSlotDoc
import com.example.family.FamilyRole
import com.example.family.InviteStatus
import com.example.family.ReminderCategory
import com.example.family.SharedWellnessCategory
import com.example.ui.viewmodel.FamilyCircleUiState
import com.example.ui.viewmodel.FamilyCircleViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class FamilySectionTab(val label: String) {
    PROFILES("Profiles & Roles"),
    SHARED_FEED("Shared Wellness"),
    REMINDERS("Caregiver Alerts"),
    APPOINTMENTS("Appointments"),
    INVITES("Invites")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyCircleRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSubscriptions: () -> Unit = {}
) {
    val authUser by container.googleAuthManager.observeAuthUser()
        .collectAsStateWithLifecycle(initialValue = container.googleAuthManager.currentUserSnapshot())
    val localProfiles by container.userProfileRepository.observeAllProfiles()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val activeLocalProfile by container.userProfileRepository.observeActiveProfile()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var authBusy by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.family_circle_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("family_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (authUser != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    container.googleAuthManager.signOut()
                                }
                            },
                            modifier = Modifier.testTag("family_sign_out_button")
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val user = authUser
        if (user == null) {
            FamilyCircleSignInGate(
                modifier = Modifier.padding(innerPadding),
                localProfiles = localProfiles,
                activeLocalProfile = activeLocalProfile,
                isBusy = authBusy,
                errorMessage = authError,
                onSwitchLocalProfile = { profile ->
                    scope.launch { container.userProfileRepository.switchActiveProfile(profile.id) }
                },
                onSignInWithGoogle = {
                    scope.launch {
                        authBusy = true
                        authError = null
                        val res = container.googleAuthManager.signInWithGoogleInteractive(context)
                        res.onFailure { authError = it.message }
                        authBusy = false
                    }
                }
            )
        } else {
            key(user.uid) {
                val vm: FamilyCircleViewModel = viewModel(
                    key = user.uid,
                    factory = SimpleViewModelFactory {
                        FamilyCircleViewModel(
                            currentUser = user,
                            familyRepo = container.familyCircleRepository,
                            userProfileRepository = container.userProfileRepository,
                            waterRepository = container.waterRepository,
                            periodRepository = container.periodRepository,
                            allModulesRepository = container.allModulesRepository,
                            customAlarmRepository = container.customAlarmRepository,
                            alertSoundManager = container.alertSoundManager,
                            preferencesManager = container.preferencesManager,
                            firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance(),
                            subscriptionRepository = container.subscriptionRepository
                        )
                    }
                )
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                FamilyCircleDashboard(
                    modifier = Modifier.padding(innerPadding),
                    uiState = uiState,
                    viewModel = vm,
                    onOpenSubscriptions = onOpenSubscriptions
                )
            }
        }
    }
}

@Composable
private fun FamilyCircleSignInGate(
    modifier: Modifier = Modifier,
    localProfiles: List<UserProfileEntity>,
    activeLocalProfile: UserProfileEntity?,
    isBusy: Boolean,
    errorMessage: String?,
    onSwitchLocalProfile: (UserProfileEntity) -> Unit,
    onSignInWithGoogle: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("LifeWeave Family Circle", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Text(
                        text = stringResource(id = R.string.family_circle_consent_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Button(
                        onClick = onSignInWithGoogle,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("family_google_signin_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Continue with Google Account")
                        }
                    }
                }
            }
        }

        if (localProfiles.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Local On-Device Profiles (${localProfiles.size})", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Individual wellness vaults stay 100% isolated on-device even while signed out of Family Circle cloud sync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        localProfiles.forEach { profile ->
                            val selected = profile.id == activeLocalProfile?.id
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = { onSwitchLocalProfile(profile) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${profile.fullName} (${profile.relationLabel})", style = MaterialTheme.typography.titleSmall)
                                        Text("${profile.dailyWaterGoalMl} ml water goal", style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (selected) {
                                        Text("Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyCircleDashboard(
    modifier: Modifier = Modifier,
    uiState: FamilyCircleUiState,
    viewModel: FamilyCircleViewModel,
    onOpenSubscriptions: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreateCircleDialog by remember { mutableStateOf(false) }
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showAppointmentDialog by remember { mutableStateOf(false) }
    var editingConsentSlot by remember { mutableStateOf<FamilyProfileSlotDoc?>(null) }

    val activeFamily = uiState.activeFamily
    val tabs = FamilySectionTab.entries

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeFamily?.familyName ?: "Set Up Your Family Circle",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${uiState.currentUser.email} • ${uiState.myRole.displayLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (uiState.familyPlanEligible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (uiState.familyPlanEligible) "5-Profile Family Plan" else "Free (1 Profile)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (uiState.familyPlanEligible) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!uiState.familyPlanEligible) {
                            Button(
                                onClick = onOpenSubscriptions,
                                enabled = !uiState.isBusy,
                                modifier = Modifier.weight(1f).testTag("activate_family_plan_button")
                            ) {
                                Text("Upgrade to Family Plan (5 Profiles)")
                            }
                            OutlinedButton(
                                onClick = viewModel::restoreFamilyPurchase,
                                enabled = !uiState.isBusy,
                                modifier = Modifier.testTag("restore_family_purchase_button")
                            ) {
                                Text("Restore")
                            }
                        } else if (activeFamily == null) {
                            Button(
                                onClick = { showCreateCircleDialog = true },
                                enabled = !uiState.isBusy,
                                modifier = Modifier.weight(1f).testTag("create_family_circle_button")
                            ) {
                                Text("Create Family Circle")
                            }
                        }
                        if (!uiState.familyPlanEligible && activeFamily == null) {
                            OutlinedButton(
                                onClick = { showCreateCircleDialog = true },
                                enabled = !uiState.isBusy
                            ) {
                                Text("Start 1-Profile Circle")
                            }
                        }
                    }
                }
            }
        }

        if (uiState.errorBanner != null || uiState.statusMessage != null) {
            item {
                val isError = uiState.errorBanner != null
                Surface(
                    modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
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
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearBanners) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }
        }

        if (uiState.incomingInvitations.any { it.status == InviteStatus.PENDING.id }) {
            item {
                val pending = uiState.incomingInvitations.filter { it.status == InviteStatus.PENDING.id }
                Card(
                    modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Incoming Family Invitations (${pending.size})", style = MaterialTheme.typography.titleSmall)
                        pending.forEach { invite ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${invite.familyName} • ${FamilyRole.fromId(invite.assignedRole).displayLabel}", style = MaterialTheme.typography.labelLarge)
                                    Text("From ${invite.inviterName}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { viewModel.declineInvitation(invite) }) { Text("Decline") }
                                    Button(onClick = { viewModel.acceptInvitation(invite, "Family") }) { Text("Accept") }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEachIndexed { idx, tab ->
                    FilterChip(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("family_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        when (tabs[selectedTab]) {
            FamilySectionTab.PROFILES -> {
                item {
                    Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val cap = if (uiState.familyPlanEligible) 5 else 1
                                    Text("Individual Wellness Profiles (${uiState.localProfiles.size}/$cap)", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Switching active profile isolates hydration, labs, meds & diet on this device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { showAddProfileDialog = true },
                                    modifier = Modifier.testTag("add_family_profile_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Text("Add")
                                }
                            }

                            uiState.localProfiles.forEach { local ->
                                val isActive = local.id == uiState.activeLocalProfile?.id
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    onClick = { viewModel.switchActiveLocalProfile(local) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${local.fullName} (${local.relationLabel})", style = MaterialTheme.typography.titleSmall)
                                            Text("Age ${local.age} • Goal ${local.dailyWaterGoalMl} ml", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            text = if (isActive) "Active Vault" else "Switch",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (activeFamily != null) {
                    item {
                        Text(
                            text = "Circle Profile Slots & Consent (${uiState.profileSlots.size}/${activeFamily.maxProfiles})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
                        )
                    }
                    items(uiState.profileSlots, key = { it.id }) { slot ->
                        val isSelfOrOwner = slot.ownerUid == uiState.currentUser.uid ||
                            slot.linkedMemberUid == uiState.currentUser.uid ||
                            (uiState.myRole.canManageMembersAndReminders && slot.managedByCaregiver)
                        Card(
                            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${slot.displayName} • ${slot.relationLabel} (${slot.id})", style = MaterialTheme.typography.titleSmall)
                                    val cycleLabel = if (slot.shareCycle) "Cycle shared" else "Cycle private"
                                    Text(
                                        text = "Reminders: ${if (slot.allowCaregiverReminders) "Allowed" else "Blocked"} • $cycleLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelfOrOwner) {
                                    OutlinedButton(onClick = { editingConsentSlot = slot }) {
                                        Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Consent")
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.members.isNotEmpty()) {
                        item {
                            Text(
                                text = "Circle Members & Roles",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
                            )
                        }
                        items(uiState.members, key = { it.uid }) { member ->
                            val role = FamilyRole.fromId(member.role)
                            var roleMenuOpen by remember { mutableStateOf(false) }
                            Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${member.displayName} (${member.relationLabel})", style = MaterialTheme.typography.titleSmall)
                                        Text(role.displayLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (uiState.myRole == FamilyRole.OWNER && member.uid != activeFamily.ownerUid) {
                                        Box {
                                            OutlinedButton(onClick = { roleMenuOpen = true }) {
                                                Text("Change Role")
                                            }
                                            DropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                                                listOf(FamilyRole.CAREGIVER, FamilyRole.MEMBER, FamilyRole.DEPENDENT_VIEW).forEach { targetRole ->
                                                    DropdownMenuItem(
                                                        text = { Text(targetRole.displayLabel) },
                                                        onClick = {
                                                            roleMenuOpen = false
                                                            viewModel.changeMemberRole(member.uid, targetRole)
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FamilySectionTab.SHARED_FEED -> {
                item {
                    Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Consent-Based Wellness Feed", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Only metrics explicitly authorized by each profile appear here. Cycle Care remains private unless explicitly opted in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = viewModel::publishActiveProfileConsentedSnapshots,
                                enabled = activeFamily != null && !uiState.isBusy,
                                modifier = Modifier.fillMaxWidth().testTag("sync_consented_snapshots_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Share Active Profile's Consented Summary")
                            }
                        }
                    }
                }
                if (uiState.sharedSnapshots.isEmpty()) {
                    item {
                        Text(
                            text = "No consented shared snapshots yet. Tap above to publish allowed categories for your active profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 620.dp).padding(8.dp)
                        )
                    }
                } else {
                    items(uiState.sharedSnapshots, key = { it.id }) { snap ->
                        val cat = SharedWellnessCategory.fromId(snap.category)
                        Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${cat.emoji} ${snap.profileName} • ${cat.label}", style = MaterialTheme.typography.titleSmall)
                                    Text(snap.summaryLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(snap.primaryMetricLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            FamilySectionTab.REMINDERS -> {
                item {
                    Row(
                        modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Caregiver Reminders", style = MaterialTheme.typography.titleMedium)
                        if (activeFamily != null && uiState.myRole.canManageMembersAndReminders) {
                            Button(
                                onClick = { showReminderDialog = true },
                                modifier = Modifier.testTag("add_caregiver_reminder_button")
                            ) {
                                Icon(Icons.Rounded.Alarm, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Schedule")
                            }
                        }
                    }
                }
                if (uiState.reminders.isEmpty()) {
                    item {
                        Text(
                            text = "No caregiver reminders scheduled yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 620.dp).padding(8.dp)
                        )
                    }
                } else {
                    items(uiState.reminders, key = { it.id }) { rem ->
                        val cat = ReminderCategory.fromId(rem.category)
                        Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${cat.emoji} ${rem.title} • ${rem.scheduleTime24h}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(rem.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("For ${rem.targetDisplayName} • From ${rem.createdByName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (rem.message.isNotBlank()) {
                                    Text(rem.message, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.syncReminderToLocalDeviceAlarm(rem) }) {
                                        Text("Sync to Local Alarm")
                                    }
                                    if (rem.status != "COMPLETED" && (rem.targetMemberUid == uiState.currentUser.uid || uiState.myRole.canManageMembersAndReminders)) {
                                        Button(onClick = { viewModel.markReminderCompleted(rem) }) {
                                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.size(4.dp))
                                            Text("Done")
                                        }
                                    }
                                    if (uiState.myRole == FamilyRole.OWNER || rem.createdByUid == uiState.currentUser.uid) {
                                        IconButton(onClick = { viewModel.deleteCaregiverReminder(rem) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete reminder")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FamilySectionTab.APPOINTMENTS -> {
                item {
                    Row(
                        modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shared Doctor Appointments", style = MaterialTheme.typography.titleMedium)
                        if (activeFamily != null && uiState.profileSlots.isNotEmpty()) {
                            Button(
                                onClick = { showAppointmentDialog = true },
                                modifier = Modifier.testTag("add_shared_appt_button")
                            ) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Add Visit")
                            }
                        }
                    }
                }
                if (uiState.appointments.isEmpty()) {
                    item {
                        Text(
                            text = "No shared doctor appointments scheduled yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(max = 620.dp).padding(8.dp)
                        )
                    }
                } else {
                    items(uiState.appointments, key = { it.id }) { appt ->
                        val dateText = remember(appt.scheduledEpochMs) {
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(appt.scheduledEpochMs))
                        }
                        Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🩺 ${appt.doctorName} (${appt.specialty})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text(appt.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Text("${appt.patientName} • $dateText • ${appt.clinicOrHospital.ifBlank { "Clinic" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (appt.notes.isNotBlank()) {
                                    Text(appt.notes, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (appt.status == "SCHEDULED") {
                                        OutlinedButton(onClick = { viewModel.completeAppointment(appt) }) {
                                            Text("Mark Completed")
                                        }
                                    }
                                    if (uiState.myRole == FamilyRole.OWNER || appt.createdByUid == uiState.currentUser.uid || appt.patientMemberUid == uiState.currentUser.uid) {
                                        IconButton(onClick = { viewModel.deleteAppointment(appt) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete appointment")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FamilySectionTab.INVITES -> {
                item {
                    Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Family Circle Invitations", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (uiState.familyPlanEligible) {
                                            "Invite up to 5 Google accounts with verified email and role-based access."
                                        } else {
                                            "Upgrade to the 5-Profile Family Plan to invite family members."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (activeFamily != null && uiState.myRole.canManageMembersAndReminders && uiState.familyPlanEligible) {
                                    Button(
                                        onClick = { showInviteDialog = true },
                                        modifier = Modifier.testTag("open_send_invite_button")
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                                        Spacer(Modifier.size(4.dp))
                                        Text("Invite")
                                    }
                                }
                            }
                        }
                    }
                }
                items(uiState.outgoingInvitations, key = { it.id }) { invite ->
                    Card(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(invite.inviteeEmailLower, style = MaterialTheme.typography.titleSmall)
                                Text("${FamilyRole.fromId(invite.assignedRole).displayLabel} • ${invite.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (invite.status == InviteStatus.PENDING.id) {
                                TextButton(
                                    onClick = { viewModel.revokeInvitation(invite.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Revoke")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showCreateCircleDialog) {
        CreateFamilyCircleDialog(
            eligibleForFiveProfiles = uiState.familyPlanEligible,
            onDismiss = { showCreateCircleDialog = false },
            onConfirm = { name ->
                showCreateCircleDialog = false
                viewModel.createFamilyCircle(name)
            }
        )
    }

    if (showAddProfileDialog) {
        AddFamilyProfileDialog(
            onDismiss = { showAddProfileDialog = false },
            onConfirm = { name, rel, age, gender, waterMl, managed ->
                showAddProfileDialog = false
                viewModel.createLocalAndCloudProfile(name, rel, age, gender, waterMl, managed)
            }
        )
    }

    editingConsentSlot?.let { slot ->
        ConsentEditorDialog(
            slot = slot,
            onDismiss = { editingConsentSlot = null },
            onSave = { updated ->
                editingConsentSlot = null
                viewModel.saveConsentPreferences(slot, updated)
            }
        )
    }

    if (showInviteDialog) {
        SendFamilyInviteDialog(
            onDismiss = { showInviteDialog = false },
            onSend = { email, role, note ->
                showInviteDialog = false
                viewModel.sendInvitation(email, role, note)
            }
        )
    }

    if (showReminderDialog) {
        AddCaregiverReminderDialog(
            slots = uiState.profileSlots,
            onDismiss = { showReminderDialog = false },
            onCreate = { targetSlot, title, cat, time24h, msg ->
                showReminderDialog = false
                viewModel.createCaregiverReminder(targetSlot, title, cat, time24h, msg)
            }
        )
    }

    if (showAppointmentDialog) {
        AddSharedAppointmentDialog(
            slots = uiState.profileSlots,
            onDismiss = { showAppointmentDialog = false },
            onCreate = { patientSlot, doc, spec, clinic, days, notes, share ->
                showAppointmentDialog = false
                viewModel.createSharedAppointment(patientSlot, doc, spec, clinic, days, notes, share)
            }
        )
    }
}
