package com.example.family

import com.example.data.local.entity.UserProfileEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FamilyCircleRepository(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth
) {
    private fun requireUid(): String =
        firebaseAuth.currentUser?.uid ?: error("Sign in with Google first to use Family Circle.")

    private fun requireEmailLower(): String =
        firebaseAuth.currentUser?.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: error("Verified Google email is required for Family Circle.")

    private fun DocumentSnapshot.getIntCompat(key: String, default: Int = 0): Int =
        (get(key) as? Number)?.toInt() ?: default

    private fun DocumentSnapshot.getLongCompat(key: String, default: Long = 0L): Long =
        (get(key) as? Number)?.toLong() ?: default

    private fun DocumentSnapshot.getStringListCompat(key: String): List<String> =
        (get(key) as? List<*>)?.mapNotNull { it as? String }.orEmpty()

    private fun DocumentSnapshot.getStringMapCompat(key: String): Map<String, String> =
        (get(key) as? Map<*, *>)?.entries
            ?.mapNotNull { (k, v) ->
                val ks = k as? String
                val vs = v as? String
                if (ks != null && vs != null) ks to vs else null
            }
            ?.toMap()
            .orEmpty()

    private fun DocumentSnapshot.toUserAccountDoc(): UserAccountDoc? {
        if (!exists()) return null
        return UserAccountDoc(
            id = id,
            uid = getString("uid") ?: id,
            emailLower = getString("emailLower").orEmpty(),
            displayName = getString("displayName").orEmpty(),
            subscriptionTier = getString("subscriptionTier") ?: "free",
            eligibleForFiveProfiles = getBoolean("eligibleForFiveProfiles") ?: false,
            activeFamilyId = getString("activeFamilyId"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toFamilyCircleDoc(): FamilyCircleDoc? {
        if (!exists()) return null
        return FamilyCircleDoc(
            id = id,
            ownerUid = getString("ownerUid").orEmpty(),
            familyName = getString("familyName").orEmpty(),
            subscriptionTier = getString("subscriptionTier") ?: "free",
            eligibleForFiveProfiles = getBoolean("eligibleForFiveProfiles") ?: false,
            maxProfiles = getIntCompat("maxProfiles", 1),
            activeProfileCount = getIntCompat("activeProfileCount", 1),
            memberUids = getStringListCompat("memberUids"),
            memberRoles = getStringMapCompat("memberRoles"),
            lastAcceptedInviteId = getString("lastAcceptedInviteId"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toFamilyMemberDoc(): FamilyMemberDoc? {
        if (!exists()) return null
        return FamilyMemberDoc(
            id = id,
            uid = getString("uid") ?: id,
            familyId = getString("familyId").orEmpty(),
            displayName = getString("displayName").orEmpty(),
            relationLabel = getString("relationLabel") ?: "Member",
            role = getString("role") ?: FamilyRole.MEMBER.id,
            memberUids = getStringListCompat("memberUids"),
            shareHydration = getBoolean("shareHydration") ?: true,
            shareFitnessSleep = getBoolean("shareFitnessSleep") ?: true,
            shareNutrition = getBoolean("shareNutrition") ?: true,
            shareMedicines = getBoolean("shareMedicines") ?: true,
            shareAppointments = getBoolean("shareAppointments") ?: true,
            shareCycle = getBoolean("shareCycle") ?: false,
            allowCaregiverReminders = getBoolean("allowCaregiverReminders") ?: true,
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toProfileSlotDoc(): FamilyProfileSlotDoc? {
        if (!exists()) return null
        return FamilyProfileSlotDoc(
            id = id,
            slotIndex = getIntCompat("slotIndex", FamilyCircleValidator.slotIndexFromId(id)),
            familyId = getString("familyId").orEmpty(),
            ownerUid = getString("ownerUid").orEmpty(),
            linkedMemberUid = getString("linkedMemberUid"),
            displayName = getString("displayName").orEmpty(),
            relationLabel = getString("relationLabel") ?: "Self",
            age = getIntCompat("age", 30),
            genderIdentity = getString("genderIdentity") ?: "Prefer not to say",
            dailyWaterGoalMl = getIntCompat("dailyWaterGoalMl", 2500),
            managedByCaregiver = getBoolean("managedByCaregiver") ?: false,
            memberUids = getStringListCompat("memberUids"),
            shareHydration = getBoolean("shareHydration") ?: true,
            shareFitnessSleep = getBoolean("shareFitnessSleep") ?: true,
            shareNutrition = getBoolean("shareNutrition") ?: true,
            shareMedicines = getBoolean("shareMedicines") ?: true,
            shareAppointments = getBoolean("shareAppointments") ?: true,
            shareCycle = getBoolean("shareCycle") ?: false,
            allowCaregiverReminders = getBoolean("allowCaregiverReminders") ?: true,
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toInvitationDoc(): FamilyInvitationDoc? {
        if (!exists()) return null
        return FamilyInvitationDoc(
            id = id,
            familyId = getString("familyId").orEmpty(),
            familyName = getString("familyName").orEmpty(),
            inviterUid = getString("inviterUid").orEmpty(),
            inviterName = getString("inviterName").orEmpty(),
            inviteeEmailLower = getString("inviteeEmailLower").orEmpty(),
            acceptedByUid = getString("acceptedByUid"),
            assignedRole = getString("assignedRole") ?: FamilyRole.MEMBER.id,
            status = getString("status") ?: InviteStatus.PENDING.id,
            inviteNote = getString("inviteNote").orEmpty(),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toReminderDoc(): CaregiverReminderDoc? {
        if (!exists()) return null
        return CaregiverReminderDoc(
            id = id,
            familyId = getString("familyId").orEmpty(),
            createdByUid = getString("createdByUid").orEmpty(),
            createdByName = getString("createdByName").orEmpty(),
            targetProfileSlotId = getString("targetProfileSlotId") ?: "slot_1",
            targetMemberUid = getString("targetMemberUid").orEmpty(),
            targetDisplayName = getString("targetDisplayName").orEmpty(),
            title = getString("title").orEmpty(),
            category = getString("category") ?: ReminderCategory.WELLNESS.id,
            scheduleTime24h = getString("scheduleTime24h") ?: "08:00",
            message = getString("message").orEmpty(),
            status = getString("status") ?: "ACTIVE",
            memberUids = getStringListCompat("memberUids"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toAppointmentDoc(): SharedAppointmentDoc? {
        if (!exists()) return null
        return SharedAppointmentDoc(
            id = id,
            familyId = getString("familyId").orEmpty(),
            createdByUid = getString("createdByUid").orEmpty(),
            patientProfileSlotId = getString("patientProfileSlotId") ?: "slot_1",
            patientMemberUid = getString("patientMemberUid").orEmpty(),
            patientName = getString("patientName").orEmpty(),
            doctorName = getString("doctorName").orEmpty(),
            specialty = getString("specialty").orEmpty(),
            clinicOrHospital = getString("clinicOrHospital").orEmpty(),
            scheduledEpochMs = getLongCompat("scheduledEpochMs", 0L),
            notes = getString("notes").orEmpty(),
            status = getString("status") ?: "SCHEDULED",
            consentGranted = getBoolean("consentGranted") ?: true,
            visibleMemberUids = getStringListCompat("visibleMemberUids"),
            createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    private fun DocumentSnapshot.toSharedSnapshotDoc(): SharedWellnessSnapshotDoc? {
        if (!exists()) return null
        return SharedWellnessSnapshotDoc(
            id = id,
            familyId = getString("familyId").orEmpty(),
            profileSlotId = getString("profileSlotId") ?: "slot_1",
            ownerUid = getString("ownerUid").orEmpty(),
            profileName = getString("profileName").orEmpty(),
            category = getString("category") ?: SharedWellnessCategory.HYDRATION.id,
            summaryLine = getString("summaryLine").orEmpty(),
            primaryMetricLabel = getString("primaryMetricLabel").orEmpty(),
            consentEnabled = getBoolean("consentEnabled") ?: false,
            memberUids = getStringListCompat("memberUids"),
            updatedAt = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
    }

    // --- LIVE SNAPSHOT LISTENERS (CALL ONLY WHEN SIGNED IN) ---

    fun observeUserAccount(uid: String): Flow<UserAccountDoc?> = callbackFlow {
        val reg = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snap?.toUserAccountDoc())
            }
        awaitClose { reg.remove() }
    }

    fun observeMyFamilies(uid: String): Flow<List<FamilyCircleDoc>> = callbackFlow {
        val reg = firestore.collection("families")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty().mapNotNull { it.toFamilyCircleDoc() }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    fun observeFamilyMembers(familyId: String, uid: String): Flow<List<FamilyMemberDoc>> = callbackFlow {
        val reg = firestore.collection("families").document(familyId)
            .collection("members")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty().mapNotNull { it.toFamilyMemberDoc() }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    fun observeProfileSlots(familyId: String, uid: String): Flow<List<FamilyProfileSlotDoc>> = callbackFlow {
        val reg = firestore.collection("families").document(familyId)
            .collection("profiles")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                    .mapNotNull { it.toProfileSlotDoc() }
                    .sortedBy { it.slotIndex }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    fun observeCaregiverReminders(familyId: String, uid: String): Flow<List<CaregiverReminderDoc>> = callbackFlow {
        val reg = firestore.collection("families").document(familyId)
            .collection("caregiverReminders")
            .whereArrayContains("memberUids", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                    .mapNotNull { it.toReminderDoc() }
                    .sortedBy { it.scheduleTime24h }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    fun observeSharedAppointments(familyId: String, uid: String): Flow<List<SharedAppointmentDoc>> = callbackFlow {
        val reg = firestore.collection("families").document(familyId)
            .collection("appointments")
            .whereArrayContains("visibleMemberUids", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty()
                    .mapNotNull { it.toAppointmentDoc() }
                    .filter { it.patientMemberUid == uid || it.consentGranted }
                    .sortedBy { it.scheduledEpochMs }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    fun observeSharedWellnessSnapshots(familyId: String, uid: String): Flow<List<SharedWellnessSnapshotDoc>> = callbackFlow {
        val reg = firestore.collection("families").document(familyId)
            .collection("sharedWellnessSnapshots")
            .whereArrayContains("memberUids", uid)
            .whereEqualTo("consentEnabled", true)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty().mapNotNull { it.toSharedSnapshotDoc() }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    fun observeIncomingInvitations(emailLower: String): Flow<List<FamilyInvitationDoc>> = callbackFlow {
        val cleanEmail = emailLower.trim().lowercase()
        val reg = firestore.collection("familyInvitations")
            .whereEqualTo("inviteeEmailLower", cleanEmail)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty().mapNotNull { it.toInvitationDoc() }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    fun observeOutgoingInvitations(uid: String): Flow<List<FamilyInvitationDoc>> = callbackFlow {
        val reg = firestore.collection("familyInvitations")
            .whereEqualTo("inviterUid", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val docs = snap?.documents.orEmpty().mapNotNull { it.toInvitationDoc() }
                trySend(docs)
            }
        awaitClose { reg.remove() }
    }

    // --- MUTATIONS ---

    suspend fun syncUserAccount(
        user: AuthenticatedUserSnapshot,
        eligibleFamilySubscription: Boolean
    ): Result<UserAccountDoc> {
        val path = "users/${user.uid}"
        return try {
            val docRef = firestore.collection("users").document(user.uid)
            val snap = docRef.get().await()
            val cleanName = user.displayName.trim().ifEmpty { "LifeWeave Member" }.take(60)
            val cleanEmail = user.email.trim().lowercase().ifEmpty { "${user.uid}@local.invalid" }.take(120)
            val effectiveEligible = eligibleFamilySubscription || (snap.getBoolean("eligibleForFiveProfiles") == true)
            val tier = if (effectiveEligible) "family" else "free"

            if (!snap.exists()) {
                val payload = mapOf(
                    "uid" to user.uid,
                    "emailLower" to cleanEmail,
                    "displayName" to cleanName,
                    "subscriptionTier" to tier,
                    "eligibleForFiveProfiles" to effectiveEligible,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "emailLower" to cleanEmail,
                        "displayName" to cleanName,
                        "subscriptionTier" to tier,
                        "eligibleForFiveProfiles" to effectiveEligible,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val refreshed = docRef.get().await().toUserAccountDoc()
                ?: UserAccountDoc(
                    id = user.uid,
                    uid = user.uid,
                    emailLower = cleanEmail,
                    displayName = cleanName,
                    subscriptionTier = tier,
                    eligibleForFiveProfiles = effectiveEligible
                )
            Result.success(refreshed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.WRITE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun setFamilySubscriptionEligibility(
        eligibleForFiveProfiles: Boolean,
        ownedFamily: FamilyCircleDoc?
    ): Result<Unit> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "users/$uid"
        return try {
            val tier = if (eligibleForFiveProfiles) "family" else "free"
            val maxProfiles = if (eligibleForFiveProfiles) 5 else 1
            firestore.collection("users").document(uid).update(
                mapOf(
                    "subscriptionTier" to tier,
                    "eligibleForFiveProfiles" to eligibleForFiveProfiles,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (ownedFamily != null && ownedFamily.ownerUid == uid) {
                val safeCount = ownedFamily.activeProfileCount.coerceIn(1, maxProfiles)
                if (eligibleForFiveProfiles || ownedFamily.memberUids.size <= 1) {
                    firestore.collection("families").document(ownedFamily.id).update(
                        mapOf(
                            "subscriptionTier" to tier,
                            "eligibleForFiveProfiles" to eligibleForFiveProfiles,
                            "maxProfiles" to maxProfiles,
                            "activeProfileCount" to safeCount,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun createFamilyCircle(
        familyName: String,
        ownerLocalProfile: UserProfileEntity?,
        eligibleForFiveProfiles: Boolean
    ): Result<FamilyCircleDoc> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val user = firebaseAuth.currentUser
        val cleanFamilyName = familyName.trim().take(60)
        if (cleanFamilyName.length < 2) {
            return Result.failure(IllegalArgumentException("Family Circle name must be at least 2 characters."))
        }
        val familyId = "fam_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "families/$familyId"
        return try {
            val userSnap = AuthenticatedUserSnapshot(
                uid = uid,
                email = user?.email.orEmpty(),
                emailVerified = user?.isEmailVerified ?: false,
                displayName = ownerLocalProfile?.fullName ?: user?.displayName ?: "Owner",
                photoUrl = null
            )
            syncUserAccount(userSnap, eligibleForFiveProfiles).getOrThrow()

            val tier = if (eligibleForFiveProfiles) "family" else "free"
            val maxProfiles = if (eligibleForFiveProfiles) 5 else 1
            val ownerName = (ownerLocalProfile?.fullName ?: userSnap.displayName)
                .trim().ifEmpty { "Circle Owner" }.take(60)
            val age = (ownerLocalProfile?.age ?: 30).coerceIn(1, 120)
            val gender = (ownerLocalProfile?.genderIdentity ?: "Prefer not to say").take(40)
            val waterGoal = (ownerLocalProfile?.dailyWaterGoalMl ?: 2500).coerceIn(500, 6000)

            val batch = firestore.batch()
            val familyRef = firestore.collection("families").document(familyId)
            batch.set(
                familyRef,
                mapOf(
                    "ownerUid" to uid,
                    "familyName" to cleanFamilyName,
                    "subscriptionTier" to tier,
                    "eligibleForFiveProfiles" to eligibleForFiveProfiles,
                    "maxProfiles" to maxProfiles,
                    "activeProfileCount" to 1,
                    "memberUids" to listOf(uid),
                    "memberRoles" to mapOf(uid to FamilyRole.OWNER.id),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                familyRef.collection("members").document(uid),
                mapOf(
                    "uid" to uid,
                    "familyId" to familyId,
                    "displayName" to ownerName,
                    "relationLabel" to "Self",
                    "role" to FamilyRole.OWNER.id,
                    "memberUids" to listOf(uid),
                    "shareHydration" to true,
                    "shareFitnessSleep" to true,
                    "shareNutrition" to true,
                    "shareMedicines" to true,
                    "shareAppointments" to true,
                    "shareCycle" to false,
                    "allowCaregiverReminders" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                familyRef.collection("profiles").document("slot_1"),
                mapOf(
                    "slotIndex" to 1,
                    "familyId" to familyId,
                    "ownerUid" to uid,
                    "linkedMemberUid" to uid,
                    "displayName" to ownerName,
                    "relationLabel" to "Self",
                    "age" to age,
                    "genderIdentity" to gender,
                    "dailyWaterGoalMl" to waterGoal,
                    "managedByCaregiver" to false,
                    "memberUids" to listOf(uid),
                    "shareHydration" to true,
                    "shareFitnessSleep" to true,
                    "shareNutrition" to true,
                    "shareMedicines" to true,
                    "shareAppointments" to true,
                    "shareCycle" to false,
                    "allowCaregiverReminders" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                firestore.collection("users").document(uid),
                mapOf(
                    "activeFamilyId" to familyId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            val created = familyRef.get().await().toFamilyCircleDoc()
                ?: FamilyCircleDoc(
                    id = familyId,
                    ownerUid = uid,
                    familyName = cleanFamilyName,
                    subscriptionTier = tier,
                    eligibleForFiveProfiles = eligibleForFiveProfiles,
                    maxProfiles = maxProfiles,
                    activeProfileCount = 1,
                    memberUids = listOf(uid),
                    memberRoles = mapOf(uid to FamilyRole.OWNER.id)
                )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.CREATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun createFamilyProfileSlot(
        family: FamilyCircleDoc,
        existingSlots: List<FamilyProfileSlotDoc>,
        displayName: String,
        relationLabel: String,
        age: Int,
        genderIdentity: String,
        dailyWaterGoalMl: Int,
        managedByCaregiver: Boolean,
        linkedMemberUid: String? = null
    ): Result<String> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val cleanName = displayName.trim().take(60)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Profile display name is required."))
        }
        val effectiveCap = if (family.eligibleForFiveProfiles && family.subscriptionTier == "family") 5 else 1
        val nextSlot = FamilyCircleValidator.nextAvailableSlotId(existingSlots, effectiveCap)
            ?: return Result.failure(
                IllegalStateException(
                    if (effectiveCap <= 1) {
                        "Free plan allows 1 profile. Upgrade to Family Plan for up to 5 profiles."
                    } else {
                        "All 5 Family Circle profile slots are already in use."
                    }
                )
            )
        val slotIndex = FamilyCircleValidator.slotIndexFromId(nextSlot)
        val path = "families/${family.id}/profiles/$nextSlot"
        return try {
            val batch = firestore.batch()
            val familyRef = firestore.collection("families").document(family.id)
            batch.set(
                familyRef.collection("profiles").document(nextSlot),
                mapOf(
                    "slotIndex" to slotIndex,
                    "familyId" to family.id,
                    "ownerUid" to family.ownerUid.ifEmpty { uid },
                    "linkedMemberUid" to linkedMemberUid?.takeIf { it.isNotBlank() },
                    "displayName" to cleanName,
                    "relationLabel" to relationLabel.trim().ifEmpty { "Family" }.take(40),
                    "age" to age.coerceIn(1, 120),
                    "genderIdentity" to genderIdentity.trim().ifEmpty { "Prefer not to say" }.take(40),
                    "dailyWaterGoalMl" to dailyWaterGoalMl.coerceIn(500, 6000),
                    "managedByCaregiver" to managedByCaregiver,
                    "memberUids" to family.memberUids.ifEmpty { listOf(uid) }.take(5),
                    "shareHydration" to true,
                    "shareFitnessSleep" to true,
                    "shareNutrition" to true,
                    "shareMedicines" to true,
                    "shareAppointments" to true,
                    "shareCycle" to false,
                    "allowCaregiverReminders" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
            )
            batch.update(
                familyRef,
                mapOf(
                    "activeProfileCount" to (existingSlots.size + 1).coerceIn(1, effectiveCap),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(nextSlot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.CREATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun sendInvitation(
        family: FamilyCircleDoc,
        inviterName: String,
        inviteeEmail: String,
        assignedRole: FamilyRole,
        inviteNote: String
    ): Result<String> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val cleanEmail = inviteeEmail.trim().lowercase()
        if (!FamilyCircleValidator.isValidEmail(cleanEmail)) {
            return Result.failure(IllegalArgumentException("Enter a valid member Google email address."))
        }
        if (assignedRole == FamilyRole.OWNER) {
            return Result.failure(IllegalArgumentException("Invited members cannot be assigned OWNER role."))
        }
        if (!family.eligibleForFiveProfiles || family.maxProfiles < 5) {
            return Result.failure(IllegalStateException("Upgrade to the 5-Profile Family Circle plan to invite family members."))
        }
        if (family.memberUids.size >= family.maxProfiles) {
            return Result.failure(IllegalStateException("Family Circle is at full capacity (5 members)."))
        }
        val inviteId = "inv_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "familyInvitations/$inviteId"
        return try {
            firestore.collection("familyInvitations").document(inviteId).set(
                mapOf(
                    "familyId" to family.id,
                    "familyName" to family.familyName.ifBlank { "Family Circle" }.take(60),
                    "inviterUid" to uid,
                    "inviterName" to inviterName.trim().ifEmpty { "Circle Owner" }.take(60),
                    "inviteeEmailLower" to cleanEmail,
                    "assignedRole" to assignedRole.id,
                    "status" to InviteStatus.PENDING.id,
                    "inviteNote" to inviteNote.trim().take(200),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(inviteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.CREATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun acceptInvitation(
        invitation: FamilyInvitationDoc,
        joinerDisplayName: String,
        relationLabel: String
    ): Result<Unit> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "familyInvitations/${invitation.id}"
        return try {
            val familyRef = firestore.collection("families").document(invitation.familyId)
            val familySnap = familyRef.get().await()
            val family = familySnap.toFamilyCircleDoc()
                ?: return Result.failure(IllegalStateException("Family Circle could not be found."))

            val updatedUids = (family.memberUids + uid).distinct()
            if (updatedUids.size > family.maxProfiles || updatedUids.size > 5) {
                return Result.failure(IllegalStateException("This Family Circle has reached its 5-member capacity."))
            }
            val updatedRoles = family.memberRoles.toMutableMap().apply {
                put(uid, invitation.assignedRole)
            }
            val newActiveCount = maxOf(family.activeProfileCount, updatedUids.size).coerceAtMost(5)
            val cleanJoinerName = joinerDisplayName.trim().ifEmpty { "Family Member" }.take(60)
            val cleanRelation = relationLabel.trim().ifEmpty { "Family" }.take(40)

            val existingSlotsSnap = familyRef.collection("profiles").get().await()
            val existingSlots = existingSlotsSnap.documents.mapNotNull { it.toProfileSlotDoc() }
            val nextSlotId = if (existingSlots.none { it.linkedMemberUid == uid }) {
                FamilyCircleValidator.nextAvailableSlotId(existingSlots, family.maxProfiles)
            } else {
                null
            }

            val batch = firestore.batch()
            batch.update(
                firestore.collection("familyInvitations").document(invitation.id),
                mapOf(
                    "status" to InviteStatus.ACCEPTED.id,
                    "acceptedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                familyRef,
                mapOf(
                    "memberUids" to updatedUids,
                    "memberRoles" to updatedRoles,
                    "activeProfileCount" to newActiveCount,
                    "lastAcceptedInviteId" to invitation.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                familyRef.collection("members").document(uid),
                mapOf(
                    "uid" to uid,
                    "familyId" to invitation.familyId,
                    "displayName" to cleanJoinerName,
                    "relationLabel" to cleanRelation,
                    "role" to invitation.assignedRole,
                    "memberUids" to updatedUids,
                    "shareHydration" to true,
                    "shareFitnessSleep" to true,
                    "shareNutrition" to true,
                    "shareMedicines" to true,
                    "shareAppointments" to true,
                    "shareCycle" to false,
                    "allowCaregiverReminders" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (nextSlotId != null) {
                batch.set(
                    familyRef.collection("profiles").document(nextSlotId),
                    mapOf(
                        "slotIndex" to FamilyCircleValidator.slotIndexFromId(nextSlotId),
                        "familyId" to invitation.familyId,
                        "ownerUid" to family.ownerUid,
                        "linkedMemberUid" to uid,
                        "displayName" to cleanJoinerName,
                        "relationLabel" to cleanRelation,
                        "age" to 30,
                        "genderIdentity" to "Prefer not to say",
                        "dailyWaterGoalMl" to 2500,
                        "managedByCaregiver" to (invitation.assignedRole == FamilyRole.DEPENDENT_VIEW.id),
                        "memberUids" to updatedUids,
                        "shareHydration" to true,
                        "shareFitnessSleep" to true,
                        "shareNutrition" to true,
                        "shareMedicines" to true,
                        "shareAppointments" to true,
                        "shareCycle" to false,
                        "allowCaregiverReminders" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                firestore.collection("users").document(uid),
                mapOf(
                    "activeFamilyId" to invitation.familyId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun declineInvitation(invitation: FamilyInvitationDoc): Result<Unit> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "familyInvitations/${invitation.id}"
        return try {
            firestore.collection("familyInvitations").document(invitation.id).update(
                mapOf(
                    "status" to InviteStatus.DECLINED.id,
                    "acceptedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun revokeInvitation(invitationId: String): Result<Unit> {
        runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "familyInvitations/$invitationId"
        return try {
            firestore.collection("familyInvitations").document(invitationId).update(
                mapOf(
                    "status" to InviteStatus.REVOKED.id,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun updateMemberRole(
        family: FamilyCircleDoc,
        targetMemberUid: String,
        newRole: FamilyRole
    ): Result<Unit> {
        runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        if (targetMemberUid == family.ownerUid || newRole == FamilyRole.OWNER) {
            return Result.failure(IllegalArgumentException("Owner role cannot be reassigned here."))
        }
        val path = "families/${family.id}/members/$targetMemberUid"
        return try {
            val updatedRoles = family.memberRoles.toMutableMap().apply {
                put(targetMemberUid, newRole.id)
            }
            val batch = firestore.batch()
            val familyRef = firestore.collection("families").document(family.id)
            batch.update(
                familyRef,
                mapOf(
                    "memberRoles" to updatedRoles,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                familyRef.collection("members").document(targetMemberUid),
                mapOf(
                    "role" to newRole.id,
                    "memberUids" to family.memberUids,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun updateConsentSettings(
        family: FamilyCircleDoc,
        targetProfileSlot: FamilyProfileSlotDoc?,
        consent: SharingConsentSettings,
        updateOwnMemberRecord: Boolean
    ): Result<Unit> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "families/${family.id}"
        return try {
            val familyRef = firestore.collection("families").document(family.id)
            val batch = firestore.batch()
            val consentFields = mapOf(
                "memberUids" to family.memberUids,
                "shareHydration" to consent.shareHydration,
                "shareFitnessSleep" to consent.shareFitnessSleep,
                "shareNutrition" to consent.shareNutrition,
                "shareMedicines" to consent.shareMedicines,
                "shareAppointments" to consent.shareAppointments,
                "shareCycle" to consent.shareCycle,
                "allowCaregiverReminders" to consent.allowCaregiverReminders,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (updateOwnMemberRecord) {
                batch.update(familyRef.collection("members").document(uid), consentFields)
            }
            if (targetProfileSlot != null) {
                batch.update(familyRef.collection("profiles").document(targetProfileSlot.id), consentFields)
                // Immediately purge revoked shared snapshots for that profile slot
                SharedWellnessCategory.entries.forEach { category ->
                    if (!consent.isCategoryConsented(category)) {
                        val snapId = "${targetProfileSlot.id}_${category.id}"
                        batch.delete(familyRef.collection("sharedWellnessSnapshots").document(snapId))
                    }
                }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun syncSharedSnapshot(
        family: FamilyCircleDoc,
        profileSlot: FamilyProfileSlotDoc,
        category: SharedWellnessCategory,
        summaryLine: String,
        primaryMetricLabel: String
    ): Result<Unit> {
        runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val snapId = "${profileSlot.id}_${category.id}"
        val docRef = firestore.collection("families").document(family.id)
            .collection("sharedWellnessSnapshots").document(snapId)
        val path = docRef.path
        return try {
            val consented = profileSlot.consentSettings().isCategoryConsented(category)
            if (!consented) {
                docRef.delete().await()
                return Result.success(Unit)
            }
            val ownerUid = profileSlot.linkedMemberUid ?: profileSlot.ownerUid
            val memberList = (family.memberUids + ownerUid).distinct().take(5)
            val existing = docRef.get().await()
            val payload = mapOf(
                "familyId" to family.id,
                "profileSlotId" to profileSlot.id,
                "ownerUid" to ownerUid,
                "profileName" to profileSlot.displayName.ifBlank { "Member" }.take(60),
                "category" to category.id,
                "summaryLine" to summaryLine.trim().ifEmpty { "No entries yet" }.take(200),
                "primaryMetricLabel" to primaryMetricLabel.trim().ifEmpty { "Synced" }.take(60),
                "consentEnabled" to true,
                "memberUids" to memberList,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "profileName" to payload["profileName"],
                        "summaryLine" to payload["summaryLine"],
                        "primaryMetricLabel" to payload["primaryMetricLabel"],
                        "consentEnabled" to true,
                        "memberUids" to payload["memberUids"],
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.WRITE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun createCaregiverReminder(
        family: FamilyCircleDoc,
        createdByName: String,
        targetSlot: FamilyProfileSlotDoc,
        title: String,
        category: ReminderCategory,
        scheduleTime24h: String,
        message: String
    ): Result<String> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val cleanTitle = title.trim().take(80)
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Reminder title must be at least 2 characters."))
        }
        if (!FamilyCircleValidator.isValidTime24h(scheduleTime24h)) {
            return Result.failure(IllegalArgumentException("Use 24h time format HH:mm (e.g. 08:30)."))
        }
        val targetMemberUid = targetSlot.linkedMemberUid ?: family.ownerUid
        if (targetMemberUid != uid && !targetSlot.allowCaregiverReminders) {
            return Result.failure(
                IllegalStateException("${targetSlot.displayName} has disabled caregiver reminders via privacy consent.")
            )
        }
        val reminderId = "rem_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "families/${family.id}/caregiverReminders/$reminderId"
        return try {
            firestore.collection("families").document(family.id)
                .collection("caregiverReminders").document(reminderId)
                .set(
                    mapOf(
                        "familyId" to family.id,
                        "createdByUid" to uid,
                        "createdByName" to createdByName.trim().ifEmpty { "Caregiver" }.take(60),
                        "targetProfileSlotId" to targetSlot.id,
                        "targetMemberUid" to targetMemberUid,
                        "targetDisplayName" to targetSlot.displayName.ifBlank { "Member" }.take(60),
                        "title" to cleanTitle,
                        "category" to category.id,
                        "scheduleTime24h" to scheduleTime24h.trim(),
                        "message" to message.trim().take(240),
                        "status" to "ACTIVE",
                        "memberUids" to family.memberUids.ifEmpty { listOf(uid) }.take(5),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(reminderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.CREATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun updateCaregiverReminderStatus(
        familyId: String,
        reminderId: String,
        newStatus: String
    ): Result<Unit> {
        runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "families/$familyId/caregiverReminders/$reminderId"
        return try {
            firestore.collection("families").document(familyId)
                .collection("caregiverReminders").document(reminderId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun deleteCaregiverReminder(familyId: String, reminderId: String): Result<Unit> {
        runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "families/$familyId/caregiverReminders/$reminderId"
        return try {
            firestore.collection("families").document(familyId)
                .collection("caregiverReminders").document(reminderId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.DELETE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun createSharedAppointment(
        family: FamilyCircleDoc,
        patientSlot: FamilyProfileSlotDoc,
        doctorName: String,
        specialty: String,
        clinicOrHospital: String,
        scheduledEpochMs: Long,
        notes: String,
        shareWithCircle: Boolean
    ): Result<String> {
        val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val cleanDoctor = doctorName.trim().take(80)
        if (cleanDoctor.length < 2) {
            return Result.failure(IllegalArgumentException("Doctor name must be at least 2 characters."))
        }
        val patientMemberUid = patientSlot.linkedMemberUid ?: family.ownerUid
        val consentGranted = shareWithCircle && patientSlot.shareAppointments
        if (shareWithCircle && !patientSlot.shareAppointments) {
            return Result.failure(
                IllegalStateException("${patientSlot.displayName} has disabled shared appointment visibility.")
            )
        }
        val visibleUids = if (consentGranted) {
            (family.memberUids + patientMemberUid).distinct().take(5)
        } else {
            listOf(patientMemberUid)
        }
        val apptId = "appt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "families/${family.id}/appointments/$apptId"
        return try {
            firestore.collection("families").document(family.id)
                .collection("appointments").document(apptId)
                .set(
                    mapOf(
                        "familyId" to family.id,
                        "createdByUid" to uid,
                        "patientProfileSlotId" to patientSlot.id,
                        "patientMemberUid" to patientMemberUid,
                        "patientName" to patientSlot.displayName.ifBlank { "Member" }.take(60),
                        "doctorName" to cleanDoctor,
                        "specialty" to specialty.trim().ifEmpty { "General" }.take(60),
                        "clinicOrHospital" to clinicOrHospital.trim().take(100),
                        "scheduledEpochMs" to scheduledEpochMs.coerceAtLeast(1L),
                        "notes" to notes.trim().take(300),
                        "status" to "SCHEDULED",
                        "consentGranted" to consentGranted,
                        "visibleMemberUids" to visibleUids,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(apptId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.CREATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun updateAppointmentStatus(
        familyId: String,
        appointmentId: String,
        newStatus: String
    ): Result<Unit> {
        runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "families/$familyId/appointments/$appointmentId"
        return try {
            firestore.collection("families").document(familyId)
                .collection("appointments").document(appointmentId)
                .update(
                    mapOf(
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.UPDATE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    suspend fun deleteSharedAppointment(familyId: String, appointmentId: String): Result<Unit> {
        runCatching { requireUid() }.getOrElse { return Result.failure(it) }
        val path = "families/$familyId/appointments/$appointmentId"
        return try {
            firestore.collection("families").document(familyId)
                .collection("appointments").document(appointmentId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = FirestoreErrorHandler.handle(e, OperationType.DELETE, path, firebaseAuth)
            Result.failure(IllegalStateException(msg, e))
        }
    }

    /**
     * Builds consent-filtered snapshot summaries from local Room data without inventing metrics.
     */
    companion object {
        fun buildConsentedSnapshotsPreview(
            waterTodayMl: Int,
            waterGoalMl: Int,
            stepsToday: Int,
            sleepMinutes: Int,
            caloriesToday: Int,
            mealsCount: Int,
            takenDosesToday: Int,
            expectedDosesToday: Int,
            cycleDayLabel: String?,
            consent: SharingConsentSettings
        ): Map<SharedWellnessCategory, Pair<String, String>> {
            val result = linkedMapOf<SharedWellnessCategory, Pair<String, String>>()
            if (consent.shareHydration) {
                val pct = if (waterGoalMl > 0) ((waterTodayMl * 100f) / waterGoalMl).toInt().coerceIn(0, 200) else 0
                result[SharedWellnessCategory.HYDRATION] =
                    "$waterTodayMl / $waterGoalMl ml logged today" to "$pct% goal"
            }
            if (consent.shareFitnessSleep) {
                val sleepHours = if (sleepMinutes > 0) String.format("%.1fh sleep", sleepMinutes / 60f) else "No sleep logged"
                result[SharedWellnessCategory.FITNESS_SLEEP] =
                    "$stepsToday steps • $sleepHours" to "$stepsToday steps"
            }
            if (consent.shareNutrition) {
                result[SharedWellnessCategory.NUTRITION] =
                    "$caloriesToday kcal across $mealsCount meal(s)" to "$caloriesToday kcal"
            }
            if (consent.shareMedicines) {
                result[SharedWellnessCategory.MEDICINES] =
                    "$takenDosesToday / $expectedDosesToday scheduled dose(s) taken" to "$takenDosesToday/$expectedDosesToday meds"
            }
            if (consent.shareCycle && !cycleDayLabel.isNullOrBlank()) {
                result[SharedWellnessCategory.CYCLE] =
                    "Shared explicitly: $cycleDayLabel" to cycleDayLabel
            }
            return result
        }
    }
}
