package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.family.FamilyProfileSlotDoc
import com.example.family.FamilyRole
import com.example.family.ReminderCategory
import com.example.family.SharingConsentSettings

@Composable
fun CreateFamilyCircleDialog(
    eligibleForFiveProfiles: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("My Family Circle") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Family Circle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (eligibleForFiveProfiles) {
                        "Family Plan active: supports up to 5 individual wellness profiles, role-based access & caregiver reminders."
                    } else {
                        "Free Plan: starts with 1 profile. Upgrade anytime in Family Circle to unlock 5 profiles & member invites."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Circle Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("family_circle_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_family_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFamilyProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        displayName: String,
        relationLabel: String,
        age: Int,
        genderIdentity: String,
        dailyWaterGoalMl: Int,
        managedByCaregiver: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Parent") }
    var ageText by remember { mutableStateOf("58") }
    var waterText by remember { mutableStateOf("2200") }
    var gender by remember { mutableStateOf("Prefer not to say") }
    var managedByCaregiver by remember { mutableStateOf(true) }

    val genderChoices = listOf("Female", "Male", "Non-Binary", "Prefer not to say")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Individual Wellness Profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_profile_name_input")
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("Relation (e.g. Mother, Child, Partner)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Age") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = waterText,
                        onValueChange = { waterText = it.filter(Char::isDigit).take(4) },
                        label = { Text("Water (ml)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Gender Identity", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genderChoices.forEach { option ->
                        FilterChip(
                            selected = gender == option,
                            onClick = { gender = option },
                            label = { Text(option) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Caregiver Managed", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Allows Caregivers to update stats & schedule reminders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = managedByCaregiver,
                        onCheckedChange = { managedByCaregiver = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim(),
                        relation.trim().ifEmpty { "Family" },
                        (ageText.toIntOrNull() ?: 30).coerceIn(1, 120),
                        gender,
                        (waterText.toIntOrNull() ?: 2200).coerceIn(500, 6000),
                        managedByCaregiver
                    )
                },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_add_profile_button")
            ) {
                Text("Add Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConsentEditorDialog(
    slot: FamilyProfileSlotDoc,
    onDismiss: () -> Unit,
    onSave: (SharingConsentSettings) -> Unit
) {
    var consent by remember(slot.id) { mutableStateOf(slot.consentSettings()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sharing Consent • ${slot.displayName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Only consented categories are readable by other circle members. Turning off a switch immediately deletes its shared cloud snapshot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ConsentToggleRow("💧 Hydration & Daily Water", consent.shareHydration) {
                    consent = consent.copy(shareHydration = it)
                }
                ConsentToggleRow("🏃 Fitness, Steps & Sleep", consent.shareFitnessSleep) {
                    consent = consent.copy(shareFitnessSleep = it)
                }
                ConsentToggleRow("🥗 Nutrition & Meals", consent.shareNutrition) {
                    consent = consent.copy(shareNutrition = it)
                }
                ConsentToggleRow("💊 Medicine Adherence", consent.shareMedicines) {
                    consent = consent.copy(shareMedicines = it)
                }
                ConsentToggleRow("🩺 Shared Appointments", consent.shareAppointments) {
                    consent = consent.copy(shareAppointments = it)
                }
                ConsentToggleRow("🌸 Cycle Care (Private Default)", consent.shareCycle) {
                    consent = consent.copy(shareCycle = it)
                }
                ConsentToggleRow("⏰ Allow Caregiver Reminders", consent.allowCaregiverReminders) {
                    consent = consent.copy(allowCaregiverReminders = it)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(consent) },
                modifier = Modifier.testTag("save_consent_button")
            ) {
                Text("Save Consent")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConsentToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SendFamilyInviteDialog(
    onDismiss: () -> Unit,
    onSend: (email: String, role: FamilyRole, note: String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(FamilyRole.CAREGIVER) }
    var note by remember { mutableStateOf("") }

    val invitableRoles = listOf(FamilyRole.CAREGIVER, FamilyRole.MEMBER, FamilyRole.DEPENDENT_VIEW)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Family Member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Member Google Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("invite_email_input")
                )
                Text("Assign Role", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    invitableRoles.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.displayLabel) }
                        )
                    }
                }
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Personal Note (Optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(email.trim(), role, note.trim()) },
                enabled = email.contains("@") && email.contains("."),
                modifier = Modifier.testTag("confirm_send_invite_button")
            ) {
                Text("Send Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCaregiverReminderDialog(
    slots: List<FamilyProfileSlotDoc>,
    onDismiss: () -> Unit,
    onCreate: (
        targetSlot: FamilyProfileSlotDoc,
        title: String,
        category: ReminderCategory,
        scheduleTime24h: String,
        message: String
    ) -> Unit
) {
    val consentingSlots = remember(slots) { slots.filter { it.allowCaregiverReminders } }
    var selectedSlot by remember(consentingSlots) { mutableStateOf(consentingSlots.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ReminderCategory.MEDICINE) }
    var time24h by remember { mutableStateOf("08:30") }
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Caregiver Reminder") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (consentingSlots.isEmpty()) {
                    Text(
                        text = "No members currently allow caregiver reminders via consent settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("For Profile", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        consentingSlots.forEach { slot ->
                            FilterChip(
                                selected = selectedSlot?.id == slot.id,
                                onClick = { selectedSlot = slot },
                                label = { Text("${slot.displayName} (${slot.relationLabel})") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Reminder Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("caregiver_reminder_title_input")
                    )
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReminderCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = category == cat,
                                onClick = { category = cat },
                                label = { Text("${cat.emoji} ${cat.label}") }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = time24h,
                        onValueChange = { time24h = it.take(5) },
                        label = { Text("Daily Time (24h HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Caregiver Note") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val slot = selectedSlot ?: return@Button
                    onCreate(slot, title.trim(), category, time24h.trim(), message.trim())
                },
                enabled = selectedSlot != null && title.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_reminder_button")
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSharedAppointmentDialog(
    slots: List<FamilyProfileSlotDoc>,
    onDismiss: () -> Unit,
    onCreate: (
        patientSlot: FamilyProfileSlotDoc,
        doctorName: String,
        specialty: String,
        clinicOrHospital: String,
        daysFromNow: Int,
        notes: String,
        shareWithCircle: Boolean
    ) -> Unit
) {
    var selectedSlot by remember(slots) { mutableStateOf(slots.firstOrNull()) }
    var doctorName by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("General Physician") }
    var clinic by remember { mutableStateOf("") }
    var daysFromNow by remember { mutableIntStateOf(3) }
    var notes by remember { mutableStateOf("") }
    var shareWithCircle by remember(selectedSlot) {
        mutableStateOf(selectedSlot?.shareAppointments ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Shared Appointment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Patient Profile", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.forEach { slot ->
                        FilterChip(
                            selected = selectedSlot?.id == slot.id,
                            onClick = {
                                selectedSlot = slot
                                shareWithCircle = slot.shareAppointments
                            },
                            label = { Text(slot.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = doctorName,
                    onValueChange = { doctorName = it },
                    label = { Text("Doctor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("appt_doctor_input")
                )
                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Specialty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clinic,
                    onValueChange = { clinic = it },
                    label = { Text("Clinic or Hospital") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Schedule In: +$daysFromNow day(s)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 7, 14, 30).forEach { d ->
                        FilterChip(
                            selected = daysFromNow == d,
                            onClick = { daysFromNow = d },
                            label = { Text("+${d}d") }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Preparation") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                ConsentToggleRow(
                    label = "Share with Circle (requires patient appointment consent)",
                    checked = shareWithCircle,
                    onCheckedChange = { shareWithCircle = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val slot = selectedSlot ?: return@Button
                    onCreate(
                        slot,
                        doctorName.trim(),
                        specialty.trim().ifEmpty { "General" },
                        clinic.trim(),
                        daysFromNow,
                        notes.trim(),
                        shareWithCircle
                    )
                },
                enabled = selectedSlot != null && doctorName.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_appt_button")
            ) {
                Text("Save Appointment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
