package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billing.SubscriptionRepository
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.PeriodRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.domain.CycleCalculator
import com.example.domain.HydrationCalculator
import com.example.family.AuthenticatedUserSnapshot
import com.example.family.CaregiverReminderDoc
import com.example.family.FamilyCircleDoc
import com.example.family.FamilyCircleRepository
import com.example.family.FamilyInvitationDoc
import com.example.family.FamilyMemberDoc
import com.example.family.FamilyProfileSlotDoc
import com.example.family.FamilyRole
import com.example.family.FirestoreErrorHandler
import com.example.family.OperationType
import com.example.family.ReminderCategory
import com.example.family.SharedAppointmentDoc
import com.example.family.SharedWellnessCategory
import com.example.family.SharedWellnessSnapshotDoc
import com.example.family.SharingConsentSettings
import com.example.family.UserAccountDoc
import com.example.notifications.AlertSoundManager
import com.example.security.LocalPreferencesManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FamilyCircleUiState(
    val isLoading: Boolean = true,
    val currentUser: AuthenticatedUserSnapshot,
    val userAccount: UserAccountDoc? = null,
    val localProfiles: List<UserProfileEntity> = emptyList(),
    val activeLocalProfile: UserProfileEntity? = null,
    val familyPlanEligible: Boolean = false,
    val families: List<FamilyCircleDoc> = emptyList(),
    val activeFamily: FamilyCircleDoc? = null,
    val myRole: FamilyRole = FamilyRole.MEMBER,
    val members: List<FamilyMemberDoc> = emptyList(),
    val profileSlots: List<FamilyProfileSlotDoc> = emptyList(),
    val reminders: List<CaregiverReminderDoc> = emptyList(),
    val appointments: List<SharedAppointmentDoc> = emptyList(),
    val sharedSnapshots: List<SharedWellnessSnapshotDoc> = emptyList(),
    val incomingInvitations: List<FamilyInvitationDoc> = emptyList(),
    val outgoingInvitations: List<FamilyInvitationDoc> = emptyList(),
    val statusMessage: String? = null,
    val errorBanner: String? = null,
    val isBusy: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyCircleViewModel(
    private val currentUser: AuthenticatedUserSnapshot,
    private val familyRepo: FamilyCircleRepository,
    private val userProfileRepository: UserProfileRepository,
    private val waterRepository: WaterRepository,
    private val periodRepository: PeriodRepository,
    private val allModulesRepository: AllModulesRepository,
    private val customAlarmRepository: CustomAlarmRepository,
    private val alertSoundManager: AlertSoundManager,
    private val preferencesManager: LocalPreferencesManager,
    private val firebaseAuth: FirebaseAuth,
    private val subscriptionRepository: SubscriptionRepository? = null
) : ViewModel() {

    private val selectedFamilyId = MutableStateFlow<String?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorBanner = MutableStateFlow<String?>(null)
    private val isBusy = MutableStateFlow(false)

    private fun <T> Flow<T>.safeCatch(path: String, fallback: T): Flow<T> = catch { err ->
        if (err is CancellationException) throw err
        val code = (err as? FirebaseFirestoreException)?.code
        if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            errorBanner.value = FirestoreErrorHandler.handle(err, OperationType.LIST, path, firebaseAuth)
        } else {
            FirestoreErrorHandler.handle(err, OperationType.LIST, path, firebaseAuth)
        }
        emit(fallback)
    }

    init {
        viewModelScope.launch {
            val verifiedFamily = subscriptionRepository?.checkAndEnforceExpiry()?.let {
                it.isEntitledToPaidFeatures && it.tier == com.example.security.SubscriptionTier.FAMILY
            } ?: preferencesManager.preferencesFlow.first().familyPlanActivated
            familyRepo.syncUserAccount(currentUser, verifiedFamily).onFailure { err ->
                errorBanner.value = err.message
            }
        }
    }

    private val userAccountFlow = familyRepo.observeUserAccount(currentUser.uid)
        .safeCatch("users/${currentUser.uid}", null)

    private val familiesFlow = familyRepo.observeMyFamilies(currentUser.uid)
        .safeCatch("families", emptyList())

    private val incomingInvitesFlow = if (currentUser.emailVerified && currentUser.email.isNotBlank()) {
        familyRepo.observeIncomingInvitations(currentUser.email)
            .safeCatch("familyInvitations", emptyList())
    } else {
        flowOf(emptyList())
    }

    private val outgoingInvitesFlow = familyRepo.observeOutgoingInvitations(currentUser.uid)
        .safeCatch("familyInvitations", emptyList())

    private val activeFamilyFlow = combine(
        familiesFlow,
        userAccountFlow,
        selectedFamilyId
    ) { families, account, explicitId ->
        families.firstOrNull { it.id == explicitId }
            ?: families.firstOrNull { it.id == account?.activeFamilyId }
            ?: families.firstOrNull()
    }

    private data class SubcollectionsBundle(
        val members: List<FamilyMemberDoc> = emptyList(),
        val slots: List<FamilyProfileSlotDoc> = emptyList(),
        val reminders: List<CaregiverReminderDoc> = emptyList(),
        val appointments: List<SharedAppointmentDoc> = emptyList(),
        val snapshots: List<SharedWellnessSnapshotDoc> = emptyList()
    )

    private val subcollectionsFlow = activeFamilyFlow.flatMapLatest { family ->
        if (family == null) {
            flowOf(SubcollectionsBundle())
        } else {
            val famId = family.id
            combine(
                familyRepo.observeFamilyMembers(famId, currentUser.uid)
                    .safeCatch("families/$famId/members", emptyList()),
                familyRepo.observeProfileSlots(famId, currentUser.uid)
                    .safeCatch("families/$famId/profiles", emptyList()),
                familyRepo.observeCaregiverReminders(famId, currentUser.uid)
                    .safeCatch("families/$famId/caregiverReminders", emptyList()),
                familyRepo.observeSharedAppointments(famId, currentUser.uid)
                    .safeCatch("families/$famId/appointments", emptyList()),
                familyRepo.observeSharedWellnessSnapshots(famId, currentUser.uid)
                    .safeCatch("families/$famId/sharedWellnessSnapshots", emptyList())
            ) { members, slots, reminders, appointments, snapshots ->
                SubcollectionsBundle(
                    members = members,
                    slots = slots,
                    reminders = reminders,
                    appointments = appointments,
                    snapshots = snapshots
                )
            }
        }
    }

    val uiState: StateFlow<FamilyCircleUiState> = combine(
        combine(
            userAccountFlow,
            familiesFlow,
            activeFamilyFlow,
            subcollectionsFlow
        ) { acct, families, activeFam, subs ->
            listOf(acct, families, activeFam, subs)
        },
        combine(
            incomingInvitesFlow,
            outgoingInvitesFlow,
            userProfileRepository.observeAllProfiles(),
            userProfileRepository.observeActiveProfile(),
            preferencesManager.preferencesFlow.map { it.familyPlanActivated }
        ) { incoming, outgoing, allProfiles, activeLocal, localPlan ->
            listOf(incoming, outgoing, allProfiles, activeLocal, localPlan)
        },
        combine(
            statusMessage,
            errorBanner,
            isBusy
        ) { status, error, busy ->
            Triple(status, error, busy)
        }
    ) { cloudList, localList, uiMeta ->
        @Suppress("UNCHECKED_CAST")
        val account = cloudList[0] as? UserAccountDoc
        @Suppress("UNCHECKED_CAST")
        val families = cloudList[1] as List<FamilyCircleDoc>
        val activeFamily = cloudList[2] as? FamilyCircleDoc
        val subs = cloudList[3] as SubcollectionsBundle

        @Suppress("UNCHECKED_CAST")
        val incoming = localList[0] as List<FamilyInvitationDoc>
        @Suppress("UNCHECKED_CAST")
        val outgoing = localList[1] as List<FamilyInvitationDoc>
        @Suppress("UNCHECKED_CAST")
        val localProfiles = localList[2] as List<UserProfileEntity>
        val activeLocalProfile = localList[3] as? UserProfileEntity
        val localPlanEligible = localList[4] as Boolean

        val effectiveEligible = localPlanEligible ||
            (account?.eligibleForFiveProfiles == true) ||
            (activeFamily?.eligibleForFiveProfiles == true)

        FamilyCircleUiState(
            isLoading = false,
            currentUser = currentUser,
            userAccount = account,
            localProfiles = localProfiles,
            activeLocalProfile = activeLocalProfile,
            familyPlanEligible = effectiveEligible,
            families = families,
            activeFamily = activeFamily,
            myRole = activeFamily?.roleOf(currentUser.uid) ?: FamilyRole.MEMBER,
            members = subs.members,
            profileSlots = subs.slots,
            reminders = subs.reminders,
            appointments = subs.appointments,
            sharedSnapshots = subs.snapshots,
            incomingInvitations = incoming,
            outgoingInvitations = outgoing,
            statusMessage = uiMeta.first,
            errorBanner = uiMeta.second,
            isBusy = uiMeta.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FamilyCircleUiState(currentUser = currentUser)
    )

    fun selectFamily(familyId: String) {
        selectedFamilyId.value = familyId
    }

    fun clearBanners() {
        statusMessage.value = null
        errorBanner.value = null
    }

    fun restoreFamilyPurchase() {
        val repo = subscriptionRepository
        if (repo == null) {
            errorBanner.value = "Open LifeWeave Plans to verify and restore Google Play purchases."
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            try {
                val feedback = repo.restorePurchases()
                val verifiedFamily = feedback.effectiveEntitlement.isEntitledToPaidFeatures &&
                    feedback.effectiveEntitlement.tier == com.example.security.SubscriptionTier.FAMILY
                val ownedFamily = uiState.value.activeFamily?.takeIf { it.ownerUid == currentUser.uid }
                familyRepo.setFamilySubscriptionEligibility(verifiedFamily, ownedFamily)
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

    fun createFamilyCircle(familyName: String) {
        viewModelScope.launch {
            isBusy.value = true
            try {
                val eligible = uiState.value.familyPlanEligible
                val ownerLocal = uiState.value.activeLocalProfile
                familyRepo.createFamilyCircle(
                    familyName = familyName,
                    ownerLocalProfile = ownerLocal,
                    eligibleForFiveProfiles = eligible
                ).onSuccess { created ->
                    if (ownerLocal != null) {
                        userProfileRepository.linkProfileToFamilySlot(ownerLocal.id, created.id, "slot_1")
                    }
                    selectedFamilyId.value = created.id
                    statusMessage.value = "Created Family Circle '${created.familyName}'."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun createLocalAndCloudProfile(
        displayName: String,
        relationLabel: String,
        age: Int,
        genderIdentity: String,
        dailyWaterGoalMl: Int,
        managedByCaregiver: Boolean
    ) {
        viewModelScope.launch {
            isBusy.value = true
            try {
                val state = uiState.value
                val maxProfiles = if (state.familyPlanEligible) 5 else 1
                val localResult = userProfileRepository.createIndividualProfile(
                    profile = UserProfileEntity(
                        fullName = displayName.trim(),
                        age = age.coerceIn(1, 120),
                        genderIdentity = genderIdentity,
                        dailyWaterGoalMl = dailyWaterGoalMl.coerceIn(500, 6000),
                        cycleTrackingEnabled = genderIdentity == "Female" || genderIdentity == "Non-Binary",
                        relationLabel = relationLabel.trim().ifEmpty { "Family Member" }
                    ),
                    maxAllowedProfiles = maxProfiles,
                    switchToCreated = true
                )
                localResult.onSuccess { createdLocal ->
                    val family = state.activeFamily
                    if (family != null) {
                        familyRepo.createFamilyProfileSlot(
                            family = family,
                            existingSlots = state.profileSlots,
                            displayName = createdLocal.fullName,
                            relationLabel = createdLocal.relationLabel,
                            age = createdLocal.age,
                            genderIdentity = createdLocal.genderIdentity,
                            dailyWaterGoalMl = createdLocal.dailyWaterGoalMl,
                            managedByCaregiver = managedByCaregiver
                        ).onSuccess { slotId ->
                            userProfileRepository.linkProfileToFamilySlot(createdLocal.id, family.id, slotId)
                            statusMessage.value = "Created individual wellness profile '${createdLocal.fullName}' ($slotId)."
                        }.onFailure { cloudErr ->
                            errorBanner.value = cloudErr.message
                        }
                    } else {
                        statusMessage.value = "Created local wellness profile '${createdLocal.fullName}'."
                    }
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun switchActiveLocalProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            userProfileRepository.switchActiveProfile(profile.id)
            statusMessage.value = "Switched active wellness vault to ${profile.fullName} (${profile.relationLabel})."
        }
    }

    fun sendInvitation(inviteeEmail: String, assignedRole: FamilyRole, note: String) {
        val family = uiState.value.activeFamily ?: return
        val inviterName = uiState.value.activeLocalProfile?.fullName ?: currentUser.displayName
        viewModelScope.launch {
            isBusy.value = true
            try {
                familyRepo.sendInvitation(
                    family = family,
                    inviterName = inviterName,
                    inviteeEmail = inviteeEmail,
                    assignedRole = assignedRole,
                    inviteNote = note
                ).onSuccess {
                    statusMessage.value = "Invitation sent to ${inviteeEmail.trim().lowercase()} as ${assignedRole.displayLabel}."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun acceptInvitation(invitation: FamilyInvitationDoc, relationLabel: String) {
        val joinerName = uiState.value.activeLocalProfile?.fullName ?: currentUser.displayName
        viewModelScope.launch {
            isBusy.value = true
            try {
                familyRepo.acceptInvitation(
                    invitation = invitation,
                    joinerDisplayName = joinerName,
                    relationLabel = relationLabel
                ).onSuccess {
                    selectedFamilyId.value = invitation.familyId
                    statusMessage.value = "Joined '${invitation.familyName}' as ${FamilyRole.fromId(invitation.assignedRole).displayLabel}."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun declineInvitation(invitation: FamilyInvitationDoc) {
        viewModelScope.launch {
            familyRepo.declineInvitation(invitation).onSuccess {
                statusMessage.value = "Declined invitation to '${invitation.familyName}'."
            }.onFailure { err ->
                errorBanner.value = err.message
            }
        }
    }

    fun revokeInvitation(invitationId: String) {
        viewModelScope.launch {
            familyRepo.revokeInvitation(invitationId).onSuccess {
                statusMessage.value = "Invitation revoked."
            }.onFailure { err ->
                errorBanner.value = err.message
            }
        }
    }

    fun changeMemberRole(memberUid: String, newRole: FamilyRole) {
        val family = uiState.value.activeFamily ?: return
        viewModelScope.launch {
            isBusy.value = true
            try {
                familyRepo.updateMemberRole(family, memberUid, newRole).onSuccess {
                    statusMessage.value = "Updated role to ${newRole.displayLabel}."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun saveConsentPreferences(slot: FamilyProfileSlotDoc?, consent: SharingConsentSettings) {
        val family = uiState.value.activeFamily ?: return
        val updateOwnMember = slot?.linkedMemberUid == null || slot.linkedMemberUid == currentUser.uid
        viewModelScope.launch {
            isBusy.value = true
            try {
                familyRepo.updateConsentSettings(
                    family = family,
                    targetProfileSlot = slot,
                    consent = consent,
                    updateOwnMemberRecord = updateOwnMember
                ).onSuccess {
                    statusMessage.value = "Updated explicit consent settings; any revoked category snapshots were removed."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun publishActiveProfileConsentedSnapshots() {
        val state = uiState.value
        val family = state.activeFamily ?: return
        val localProfile = state.activeLocalProfile ?: return
        val targetSlot = state.profileSlots.firstOrNull {
            it.id == localProfile.linkedSlotId || it.linkedMemberUid == currentUser.uid
        } ?: state.profileSlots.firstOrNull()
        if (targetSlot == null) {
            errorBanner.value = "Create or link a Family Circle profile slot first."
            return
        }
        viewModelScope.launch {
            isBusy.value = true
            try {
                val waterRecords = waterRepository.observeLast7Days(localProfile.id).first()
                val hydration = HydrationCalculator.summarize(waterRecords, localProfile.dailyWaterGoalMl)
                val stepsToday = allModulesRepository.observeTodaySteps(localProfile.id).first()?.stepCount ?: 0
                val sleepRecord = allModulesRepository.observeSleep(localProfile.id).first().firstOrNull()
                val sleepMin = sleepRecord?.let { ((it.wakeEpochMs - it.bedtimeEpochMs) / 60_000L).toInt().coerceAtLeast(0) } ?: 0
                val meals = allModulesRepository.observeDiet(localProfile.id).first()
                val caloriesToday = meals.sumOf { it.calories }
                val activeMeds = allModulesRepository.observeMedicines(localProfile.id).first().filter { it.isActive }
                val expectedDoses = activeMeds.sumOf { it.dosesPerDay.coerceAtLeast(1) }
                val takenDoses = allModulesRepository.observeTodayMedicineIntakes(localProfile.id).first()
                    .count { it.status == "Taken" }
                val periods = periodRepository.observeRecords(localProfile.id).first()
                val cycle = CycleCalculator.analyze(
                    recordsDesc = periods,
                    configuredCycleLength = localProfile.averageCycleLengthDays,
                    configuredPeriodLength = localProfile.averagePeriodDurationDays
                )
                val cycleLabel = cycle.currentCycleDay?.let { "Cycle Day $it (${cycle.currentPhase.label})" }

                val previewMap = FamilyCircleRepository.buildConsentedSnapshotsPreview(
                    waterTodayMl = hydration.totalTodayMl,
                    waterGoalMl = hydration.goalMl,
                    stepsToday = stepsToday,
                    sleepMinutes = sleepMin,
                    caloriesToday = caloriesToday,
                    mealsCount = meals.size,
                    takenDosesToday = takenDoses,
                    expectedDosesToday = expectedDoses,
                    cycleDayLabel = cycleLabel,
                    consent = targetSlot.consentSettings()
                )

                var syncedCount = 0
                SharedWellnessCategory.entries.forEach { category ->
                    val entry = previewMap[category]
                    familyRepo.syncSharedSnapshot(
                        family = family,
                        profileSlot = targetSlot,
                        category = category,
                        summaryLine = entry?.first.orEmpty(),
                        primaryMetricLabel = entry?.second.orEmpty()
                    ).onSuccess {
                        if (entry != null) syncedCount++
                    }
                }
                statusMessage.value = "Published $syncedCount consented wellness category snapshot(s) for ${targetSlot.displayName}."
            } finally {
                isBusy.value = false
            }
        }
    }

    fun createCaregiverReminder(
        targetSlot: FamilyProfileSlotDoc,
        title: String,
        category: ReminderCategory,
        scheduleTime24h: String,
        message: String
    ) {
        val family = uiState.value.activeFamily ?: return
        val creatorName = uiState.value.activeLocalProfile?.fullName ?: currentUser.displayName
        viewModelScope.launch {
            isBusy.value = true
            try {
                familyRepo.createCaregiverReminder(
                    family = family,
                    createdByName = creatorName,
                    targetSlot = targetSlot,
                    title = title,
                    category = category,
                    scheduleTime24h = scheduleTime24h,
                    message = message
                ).onSuccess {
                    statusMessage.value = "Caregiver reminder scheduled for ${targetSlot.displayName} at $scheduleTime24h."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun markReminderCompleted(reminder: CaregiverReminderDoc) {
        val familyId = reminder.familyId
        viewModelScope.launch {
            familyRepo.updateCaregiverReminderStatus(familyId, reminder.id, "COMPLETED")
                .onSuccess {
                    statusMessage.value = "Marked '${reminder.title}' as completed."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
        }
    }

    fun syncReminderToLocalDeviceAlarm(reminder: CaregiverReminderDoc) {
        val localProfileId = uiState.value.activeLocalProfile?.id ?: return
        viewModelScope.launch {
            val savedId = customAlarmRepository.saveAlarm(
                ReminderEntity(
                    profileId = localProfileId,
                    title = "${reminder.title} (Caregiver)",
                    moduleKey = when (ReminderCategory.fromId(reminder.category)) {
                        ReminderCategory.HYDRATION -> "water"
                        ReminderCategory.MEDICINE, ReminderCategory.APPOINTMENT -> "medical_care"
                        ReminderCategory.WELLNESS -> "wellness"
                    },
                    triggerTime24h = reminder.scheduleTime24h,
                    isEnabled = true,
                    alarmEmoji = ReminderCategory.fromId(reminder.category).emoji,
                    customMessage = reminder.message.ifBlank { "Shared caregiver reminder from ${reminder.createdByName}" }
                )
            )
            val saved = customAlarmRepository.getAllSnapshot(localProfileId).firstOrNull { it.id == savedId }
            if (saved != null) {
                alertSoundManager.scheduleCustomAlarm(saved)
            }
            statusMessage.value = "Added '${reminder.title}' to this device's local alarm schedule (${reminder.scheduleTime24h})."
        }
    }

    fun deleteCaregiverReminder(reminder: CaregiverReminderDoc) {
        viewModelScope.launch {
            familyRepo.deleteCaregiverReminder(reminder.familyId, reminder.id)
                .onFailure { err -> errorBanner.value = err.message }
        }
    }

    fun createSharedAppointment(
        patientSlot: FamilyProfileSlotDoc,
        doctorName: String,
        specialty: String,
        clinicOrHospital: String,
        daysFromNow: Int,
        notes: String,
        shareWithCircle: Boolean
    ) {
        val family = uiState.value.activeFamily ?: return
        val scheduledMs = System.currentTimeMillis() + daysFromNow.coerceIn(0, 365) * 86_400_000L
        viewModelScope.launch {
            isBusy.value = true
            try {
                familyRepo.createSharedAppointment(
                    family = family,
                    patientSlot = patientSlot,
                    doctorName = doctorName,
                    specialty = specialty,
                    clinicOrHospital = clinicOrHospital,
                    scheduledEpochMs = scheduledMs,
                    notes = notes,
                    shareWithCircle = shareWithCircle
                ).onSuccess {
                    statusMessage.value = "Appointment with $doctorName added for ${patientSlot.displayName}."
                }.onFailure { err ->
                    errorBanner.value = err.message
                }
            } finally {
                isBusy.value = false
            }
        }
    }

    fun completeAppointment(appointment: SharedAppointmentDoc) {
        viewModelScope.launch {
            familyRepo.updateAppointmentStatus(appointment.familyId, appointment.id, "COMPLETED")
                .onSuccess { statusMessage.value = "Appointment marked completed." }
                .onFailure { err -> errorBanner.value = err.message }
        }
    }

    fun deleteAppointment(appointment: SharedAppointmentDoc) {
        viewModelScope.launch {
            familyRepo.deleteSharedAppointment(appointment.familyId, appointment.id)
                .onFailure { err -> errorBanner.value = err.message }
        }
    }
}
