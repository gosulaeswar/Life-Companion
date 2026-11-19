package com.example.family

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class FamilyRole(
    val id: String,
    val displayLabel: String,
    val summary: String,
    val canManageMembersAndReminders: Boolean
) {
    OWNER(
        id = "OWNER",
        displayLabel = "Circle Owner",
        summary = "Manages subscription, member roles, profiles, reminders & appointments",
        canManageMembersAndReminders = true
    ),
    CAREGIVER(
        id = "CAREGIVER",
        displayLabel = "Caregiver",
        summary = "Creates reminders, views consented metrics & manages shared appointments",
        canManageMembersAndReminders = true
    ),
    MEMBER(
        id = "MEMBER",
        displayLabel = "Member",
        summary = "Full personal wellness vault with granular consent sharing switches",
        canManageMembersAndReminders = false
    ),
    DEPENDENT_VIEW(
        id = "DEPENDENT_VIEW",
        displayLabel = "Dependent View",
        summary = "Guided profile managed alongside caregivers with shared reminders",
        canManageMembersAndReminders = false
    );

    companion object {
        fun fromId(raw: String?): FamilyRole =
            entries.firstOrNull { it.id == raw } ?: MEMBER
    }
}

enum class InviteStatus(val id: String, val label: String) {
    PENDING("PENDING", "Pending"),
    ACCEPTED("ACCEPTED", "Accepted"),
    DECLINED("DECLINED", "Declined"),
    REVOKED("REVOKED", "Revoked");

    companion object {
        fun fromId(raw: String?): InviteStatus =
            entries.firstOrNull { it.id == raw } ?: PENDING
    }
}

enum class ReminderCategory(val id: String, val label: String, val emoji: String) {
    HYDRATION("HYDRATION", "Hydration", "💧"),
    MEDICINE("MEDICINE", "Medicine", "💊"),
    APPOINTMENT("APPOINTMENT", "Appointment", "🩺"),
    WELLNESS("WELLNESS", "Wellness", "🌿");

    companion object {
        fun fromId(raw: String?): ReminderCategory =
            entries.firstOrNull { it.id == raw } ?: WELLNESS
    }
}

enum class SharedWellnessCategory(
    val id: String,
    val label: String,
    val emoji: String,
    val defaultPrivate: Boolean = false
) {
    HYDRATION("HYDRATION", "Hydration", "💧"),
    FITNESS_SLEEP("FITNESS_SLEEP", "Fitness & Sleep", "🏃"),
    NUTRITION("NUTRITION", "Nutrition", "🥗"),
    MEDICINES("MEDICINES", "Medicines", "💊"),
    CYCLE("CYCLE", "Cycle Care", "🌸", defaultPrivate = true);

    companion object {
        fun fromId(raw: String?): SharedWellnessCategory =
            entries.firstOrNull { it.id == raw } ?: HYDRATION
    }
}

data class SharingConsentSettings(
    val shareHydration: Boolean = true,
    val shareFitnessSleep: Boolean = true,
    val shareNutrition: Boolean = true,
    val shareMedicines: Boolean = true,
    val shareAppointments: Boolean = true,
    val shareCycle: Boolean = false, // Always explicit opt-in, defaults OFF
    val allowCaregiverReminders: Boolean = true
) {
    fun isCategoryConsented(category: SharedWellnessCategory): Boolean = when (category) {
        SharedWellnessCategory.HYDRATION -> shareHydration
        SharedWellnessCategory.FITNESS_SLEEP -> shareFitnessSleep
        SharedWellnessCategory.NUTRITION -> shareNutrition
        SharedWellnessCategory.MEDICINES -> shareMedicines
        SharedWellnessCategory.CYCLE -> shareCycle
    }
}

@Keep
@IgnoreExtraProperties
data class UserAccountDoc(
    @DocumentId val id: String = "",
    val uid: String = "",
    val emailLower: String = "",
    val displayName: String = "",
    val subscriptionTier: String = "free",
    val eligibleForFiveProfiles: Boolean = false,
    val activeFamilyId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FamilyCircleDoc(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val familyName: String = "",
    val subscriptionTier: String = "free",
    val eligibleForFiveProfiles: Boolean = false,
    val maxProfiles: Int = 1,
    val activeProfileCount: Int = 1,
    val memberUids: List<String> = emptyList(),
    val memberRoles: Map<String, String> = emptyMap(),
    val lastAcceptedInviteId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleOf(uid: String): FamilyRole = FamilyRole.fromId(memberRoles[uid])
}

@Keep
@IgnoreExtraProperties
data class FamilyMemberDoc(
    @DocumentId val id: String = "",
    val uid: String = "",
    val familyId: String = "",
    val displayName: String = "",
    val relationLabel: String = "Member",
    val role: String = FamilyRole.MEMBER.id,
    val memberUids: List<String> = emptyList(),
    val shareHydration: Boolean = true,
    val shareFitnessSleep: Boolean = true,
    val shareNutrition: Boolean = true,
    val shareMedicines: Boolean = true,
    val shareAppointments: Boolean = true,
    val shareCycle: Boolean = false,
    val allowCaregiverReminders: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun consentSettings(): SharingConsentSettings = SharingConsentSettings(
        shareHydration = shareHydration,
        shareFitnessSleep = shareFitnessSleep,
        shareNutrition = shareNutrition,
        shareMedicines = shareMedicines,
        shareAppointments = shareAppointments,
        shareCycle = shareCycle,
        allowCaregiverReminders = allowCaregiverReminders
    )
}

@Keep
@IgnoreExtraProperties
data class FamilyProfileSlotDoc(
    @DocumentId val id: String = "",
    val slotIndex: Int = 1,
    val familyId: String = "",
    val ownerUid: String = "",
    val linkedMemberUid: String? = null,
    val displayName: String = "",
    val relationLabel: String = "Self",
    val age: Int = 30,
    val genderIdentity: String = "Prefer not to say",
    val dailyWaterGoalMl: Int = 2500,
    val managedByCaregiver: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val shareHydration: Boolean = true,
    val shareFitnessSleep: Boolean = true,
    val shareNutrition: Boolean = true,
    val shareMedicines: Boolean = true,
    val shareAppointments: Boolean = true,
    val shareCycle: Boolean = false,
    val allowCaregiverReminders: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun consentSettings(): SharingConsentSettings = SharingConsentSettings(
        shareHydration = shareHydration,
        shareFitnessSleep = shareFitnessSleep,
        shareNutrition = shareNutrition,
        shareMedicines = shareMedicines,
        shareAppointments = shareAppointments,
        shareCycle = shareCycle,
        allowCaregiverReminders = allowCaregiverReminders
    )
}

@Keep
@IgnoreExtraProperties
data class FamilyInvitationDoc(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val familyName: String = "",
    val inviterUid: String = "",
    val inviterName: String = "",
    val inviteeEmailLower: String = "",
    val acceptedByUid: String? = null,
    val assignedRole: String = FamilyRole.MEMBER.id,
    val status: String = InviteStatus.PENDING.id,
    val inviteNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CaregiverReminderDoc(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val targetProfileSlotId: String = "slot_1",
    val targetMemberUid: String = "",
    val targetDisplayName: String = "",
    val title: String = "",
    val category: String = ReminderCategory.WELLNESS.id,
    val scheduleTime24h: String = "08:00",
    val message: String = "",
    val status: String = "ACTIVE",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SharedAppointmentDoc(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val createdByUid: String = "",
    val patientProfileSlotId: String = "slot_1",
    val patientMemberUid: String = "",
    val patientName: String = "",
    val doctorName: String = "",
    val specialty: String = "",
    val clinicOrHospital: String = "",
    val scheduledEpochMs: Long = 0L,
    val notes: String = "",
    val status: String = "SCHEDULED",
    val consentGranted: Boolean = true,
    val visibleMemberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SharedWellnessSnapshotDoc(
    @DocumentId val id: String = "",
    val familyId: String = "",
    val profileSlotId: String = "slot_1",
    val ownerUid: String = "",
    val profileName: String = "",
    val category: String = SharedWellnessCategory.HYDRATION.id,
    val summaryLine: String = "",
    val primaryMetricLabel: String = "",
    val consentEnabled: Boolean = true,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object FamilyCircleValidator {
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val timeRegex = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

    val validSlotIds = listOf("slot_1", "slot_2", "slot_3", "slot_4", "slot_5")

    fun nextAvailableSlotId(existingSlots: List<FamilyProfileSlotDoc>, maxProfiles: Int): String? {
        val used = existingSlots.map { it.id }.toSet()
        return validSlotIds.take(maxProfiles.coerceIn(1, 5)).firstOrNull { it !in used }
    }

    fun slotIndexFromId(slotId: String): Int = when (slotId) {
        "slot_1" -> 1
        "slot_2" -> 2
        "slot_3" -> 3
        "slot_4" -> 4
        "slot_5" -> 5
        else -> 0
    }

    fun isValidEmail(email: String): Boolean =
        email.trim().lowercase().let { it.length in 5..120 && emailRegex.matches(it) }

    fun isValidTime24h(time: String): Boolean = timeRegex.matches(time.trim())
}
