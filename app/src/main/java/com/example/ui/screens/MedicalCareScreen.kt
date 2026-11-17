package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.MedicineEntity
import com.example.domain.DateEpochUtils
import com.example.ui.components.QuickAmountChip
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.AiTaskStatus
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.ui.viewmodel.ModulesHubViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicalCareScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showMedDialog by remember { mutableStateOf(false) }
    var showVisitDialog by remember { mutableStateOf(false) }
    val prescriptionPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.scanAndImportPrescription(context, uri)
        }
    }

    val activeMedicines = uiState.medicines.filter { it.isActive }
    val totalDailyTarget = activeMedicines.sumOf { it.dosesPerDay.coerceAtLeast(1) }
    val totalTakenToday = uiState.todayMedicineIntakes.count { it.status == "Taken" }
    val progress = if (totalDailyTarget > 0) {
        (totalTakenToday.toFloat() / totalDailyTarget.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val todayEpochDay = remember { DateEpochUtils.todayEpochDay() }
    val pendingFollowUps = uiState.consultations
        .filter { it.followUpEpochDay != null && !it.isFollowUpCompleted }
        .sortedBy { it.followUpEpochDay }

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        "💊 Doctors & Medicines",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Daily doses, intervals, intake check-offs & doctor follow-ups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // AI Prescription Photo Auto-Import Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📄 Upload Prescription Photo (AI Auto-Fill)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pick a prescription photo and AI automatically adds medicines, daily doses, intervals & doctor follow-ups here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            prescriptionPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = uiState.aiTaskStatus !is AiTaskStatus.Loading,
                        modifier = Modifier.fillMaxWidth().testTag("medical_scan_prescription_button")
                    ) {
                        if (uiState.aiTaskStatus is AiTaskStatus.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(" Reading prescription...")
                        } else {
                            Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
                            Text(" Scan & Auto-Add Prescription 💊")
                        }
                    }
                    if (uiState.aiTaskStatus is AiTaskStatus.Error) {
                        Text(
                            text = (uiState.aiTaskStatus as AiTaskStatus.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    uiState.lastPrescriptionResult?.let { rx ->
                        Text(
                            text = "✅ Imported ${rx.medicines.size} medicine(s)" +
                                if (rx.doctorName.isNotBlank()) " & Dr. ${rx.doctorName}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Daily Dose Progress Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🩺 Today's Medicine Progress", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (totalDailyTarget > 0) "$totalTakenToday / $totalDailyTarget doses" else "No active meds",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoMetricFont),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                    )
                    val summaryMsg = when {
                        totalDailyTarget == 0 -> "Add your prescriptions, vitamins, or syrups below."
                        totalTakenToday >= totalDailyTarget -> "🎉 All scheduled doses completed for today!"
                        else -> "⏰ ${totalDailyTarget - totalTakenToday} scheduled dose(s) remaining today."
                    }
                    Text(
                        text = summaryMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Upcoming Doctor Follow-Ups Highlight Card
        if (pendingFollowUps.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "📅 Upcoming Doctor Follow-Ups (${pendingFollowUps.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        pendingFollowUps.forEach { visit ->
                            val followDay = visit.followUpEpochDay ?: todayEpochDay
                            val diff = followDay - todayEpochDay
                            val countdownLabel = when {
                                diff < 0 -> "⚠️ Overdue by ${-diff}d"
                                diff == 0L -> "🔔 Due Today!"
                                diff == 1L -> "⏰ Tomorrow"
                                else -> "🗓️ In ${diff}d (${DateEpochUtils.formatEpochDayShort(followDay)})"
                            }
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "🩺 Dr. ${visit.doctorName} • ${visit.specialty}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "$countdownLabel" + if (visit.followUpNotes.isNotBlank()) " • ${visit.followUpNotes}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.toggleConsultationFollowUp(visit, true) }
                                    ) {
                                        Text("Done ✅")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Medicines & Dose Intervals Section Header + Quick Templates
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💊 Medicines & Intervals", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { showMedDialog = true },
                            modifier = Modifier.testTag("medical_add_medicine_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("Add Med")
                        }
                    }

                    Text(
                        "Quick Add Presets:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickAmountChip("🌿 Vitamin D3 • 1x") {
                            viewModel.addMedicine(
                                name = "Vitamin D3",
                                dosage = "1000 IU",
                                time24h = "09:00",
                                frequency = "Daily (1x)",
                                dosesPerDay = 1,
                                intervalHours = 24,
                                mealRelation = "With Food",
                                categoryEmoji = "🌿"
                            )
                        }
                        QuickAmountChip("💊 Course • Every 8h (3x)") {
                            viewModel.addMedicine(
                                name = "Prescribed Tablet",
                                dosage = "500mg",
                                time24h = "08:00",
                                frequency = "Every 8h (3x/day)",
                                dosesPerDay = 3,
                                intervalHours = 8,
                                mealRelation = "After Meal",
                                categoryEmoji = "💊"
                            )
                        }
                        QuickAmountChip("❤️ Morning + Night (2x)") {
                            viewModel.addMedicine(
                                name = "Iron & Folate",
                                dosage = "1 tab",
                                time24h = "08:30",
                                frequency = "Every 12h (2x/day)",
                                dosesPerDay = 2,
                                intervalHours = 12,
                                mealRelation = "After Meal",
                                categoryEmoji = "💊"
                            )
                        }
                    }
                }
            }
        }

        // Individual Medicine Cards with Dose Slots & Intake Logging
        items(uiState.medicines, key = { "med_${it.id}" }) { med ->
            val medIntakes = uiState.todayMedicineIntakes.filter { it.medicineId == med.id }
            val takenCount = medIntakes.count { it.status == "Taken" }
            val scheduleSlots = remember(med.scheduleTime24h, med.dosesPerDay, med.intervalHours) {
                computeDoseTimes(med.scheduleTime24h, med.dosesPerDay, med.intervalHours)
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(med.categoryEmoji, style = MaterialTheme.typography.titleMedium)
                            }
                            Column {
                                Text(
                                    "${med.name} • ${med.dosage}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "⏰ ${scheduleSlots.joinToString(" • ")}  (Every ${med.intervalHours}h)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "🍽️ ${med.mealRelation} • ${med.dosesPerDay}x daily",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = med.isActive,
                                onCheckedChange = { viewModel.toggleMedicine(med, it) }
                            )
                            IconButton(onClick = { viewModel.deleteMedicine(med.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete medicine")
                            }
                        }
                    }

                    if (med.isActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val nextSlotNum = (medIntakes.size + 1).coerceAtMost(med.dosesPerDay.coerceAtLeast(1))
                            val nextSlotLabel = scheduleSlots.getOrNull(nextSlotNum - 1) ?: med.scheduleTime24h
                            Text(
                                text = "Today: $takenCount/${med.dosesPerDay} taken",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (takenCount >= med.dosesPerDay) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.logMedicineIntake(
                                            med = med,
                                            slotLabel = "Dose $nextSlotNum/${med.dosesPerDay} ($nextSlotLabel)",
                                            status = "Skipped"
                                        )
                                    }
                                ) {
                                    Text("⏭️ Skip")
                                }
                                Button(
                                    onClick = {
                                        viewModel.logMedicineIntake(
                                            med = med,
                                            slotLabel = "Dose $nextSlotNum/${med.dosesPerDay} ($nextSlotLabel)",
                                            status = "Taken"
                                        )
                                    },
                                    modifier = Modifier.testTag("take_dose_btn_${med.id}")
                                ) {
                                    Text("✅ Take")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Today's Intake History
        if (uiState.todayMedicineIntakes.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                    Text(
                        "📋 Today's Intake Log (${uiState.todayMedicineIntakes.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            items(uiState.todayMedicineIntakes, key = { "intake_${it.id}" }) { intake ->
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val icon = if (intake.status == "Taken") "✅" else "⏭️"
                            Text(
                                "$icon ${intake.medicineName} (${intake.dosageTaken}) • ${intake.status}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "${intake.slotLabel} • ${DateEpochUtils.formatTime(intake.takenAtEpochMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteMedicineIntake(intake.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Undo intake")
                        }
                    }
                }
            }
        }

        // Doctor Consultations & Follow-Ups Header
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🩺 Doctor Visits & Follow-Ups", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { showVisitDialog = true },
                            modifier = Modifier.testTag("medical_add_visit_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("Log Visit")
                        }
                    }
                    if (uiState.consultations.isEmpty()) {
                        Text(
                            "Store doctor advice, prescriptions, clinics, and follow-up dates privately on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(uiState.consultations, key = { "visit_${it.id}" }) { visit ->
            ConsultationCard(
                visit = visit,
                todayEpochDay = todayEpochDay,
                onToggleFollowUp = { completed ->
                    viewModel.toggleConsultationFollowUp(visit, completed)
                },
                onDelete = { viewModel.deleteConsultation(visit.id) }
            )
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showMedDialog) {
        AddMedicineDialog(
            onDismiss = { showMedDialog = false },
            onSave = { name, dosage, time, dosesPerDay, intervalHrs, mealRel, emoji, notes ->
                viewModel.addMedicine(
                    name = name,
                    dosage = dosage,
                    time24h = time,
                    frequency = if (dosesPerDay == 1) "Daily (1x)" else "Every ${intervalHrs}h (${dosesPerDay}x/day)",
                    dosesPerDay = dosesPerDay,
                    intervalHours = intervalHrs,
                    mealRelation = mealRel,
                    categoryEmoji = emoji,
                    notes = notes
                )
                showMedDialog = false
            }
        )
    }

    if (showVisitDialog) {
        AddConsultationDialog(
            todayEpochDay = todayEpochDay,
            onDismiss = { showVisitDialog = false },
            onSave = { doctor, specialty, clinic, summary, followUpDay, followUpNotes ->
                viewModel.addConsultation(
                    doctorName = doctor,
                    specialty = specialty,
                    clinicOrHospital = clinic,
                    summary = summary,
                    followUpEpochDay = followUpDay,
                    followUpNotes = followUpNotes
                )
                showVisitDialog = false
            }
        )
    }
}

@Composable
private fun ConsultationCard(
    visit: DoctorConsultationEntity,
    todayEpochDay: Long,
    onToggleFollowUp: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "🩺 Dr. ${visit.doctorName} (${visit.specialty})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (visit.clinicOrHospital.isNotBlank()) {
                        Text(
                            "🏥 ${visit.clinicOrHospital}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (visit.summary.isNotBlank()) {
                        Text(
                            visit.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete visit")
                }
            }

            if (visit.followUpEpochDay != null) {
                val diff = visit.followUpEpochDay - todayEpochDay
                val dateStr = DateEpochUtils.formatEpochDayFull(visit.followUpEpochDay)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (visit.isFollowUpCompleted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (visit.isFollowUpCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.EventAvailable,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (visit.isFollowUpCompleted) {
                                        "Follow-up completed ($dateStr)"
                                    } else {
                                        val rel = when {
                                            diff < 0 -> "${-diff}d overdue"
                                            diff == 0L -> "Today"
                                            else -> "in ${diff}d"
                                        }
                                        "Follow-up: $dateStr ($rel)"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (visit.followUpNotes.isNotBlank()) {
                                    Text(
                                        visit.followUpNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { onToggleFollowUp(!visit.isFollowUpCompleted) }) {
                            Text(if (visit.isFollowUpCompleted) "Reopen" else "Done ✅")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMedicineDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, Int, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("500mg") }
    var firstDoseTime by remember { mutableStateOf("08:00") }
    var dosesPerDay by remember { mutableIntStateOf(2) }
    var intervalHours by remember { mutableIntStateOf(12) }
    var mealRelation by remember { mutableStateOf("After Meal") }
    var emoji by remember { mutableStateOf("💊") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💊 Add Medicine & Dose Interval") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("💊" to "💊 Pill", "🌿" to "🌿 Herb", "🧴" to "🧴 Syrup", "💧" to "💧 Drops", "💉" to "💉 Shot").forEach { (e, lbl) ->
                        FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(lbl) })
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medicine / Supplement Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosage (e.g., 500mg, 5ml, 1 tab)") },
                    singleLine = true
                )

                Text("Daily Doses & Interval", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to 24, 2 to 12, 3 to 8, 4 to 6).forEach { (doses, hrs) ->
                        FilterChip(
                            selected = dosesPerDay == doses,
                            onClick = {
                                dosesPerDay = doses
                                intervalHours = hrs
                            },
                            label = { Text("${doses}x (${hrs}h)") }
                        )
                    }
                }

                Text("First Dose Time", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("07:00", "08:00", "09:00", "13:00", "21:00").forEach { t ->
                        FilterChip(selected = firstDoseTime == t, onClick = { firstDoseTime = t }, label = { Text(t) })
                    }
                }

                Text("Meal Timing", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("After Meal", "Before Meal", "With Food", "Anytime").forEach { m ->
                        FilterChip(selected = mealRelation == m, onClick = { mealRelation = m }, label = { Text(m) })
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Instructions (optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.ifBlank { "Daily Medicine" },
                        dosage.ifBlank { "1 dose" },
                        firstDoseTime,
                        dosesPerDay,
                        intervalHours,
                        mealRelation,
                        emoji,
                        notes
                    )
                },
                modifier = Modifier.testTag("save_medicine_dialog_button")
            ) {
                Text("Save Medicine")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddConsultationDialog(
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Long?, String) -> Unit
) {
    var doctor by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("General Wellness") }
    var clinic by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var hasFollowUp by remember { mutableStateOf(true) }
    var followUpDay by remember { mutableLongStateOf(todayEpochDay + 7) }
    var followUpNotes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🩺 Doctor Consultation & Follow-Up") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = doctor,
                    onValueChange = { doctor = it },
                    label = { Text("Doctor Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = clinic,
                    onValueChange = { clinic = it },
                    label = { Text("Clinic / Hospital (optional)") },
                    singleLine = true
                )
                Text("Specialty", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "General Wellness" to "🩺 General",
                        "Cardiology" to "❤️ Cardio",
                        "Dental" to "🦷 Dental",
                        "OB/GYN" to "🌸 OB/GYN",
                        "Eye Care" to "👁️ Eye",
                        "Dermatology" to "✨ Derm"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = specialty == value,
                            onClick = { specialty = value },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Visit Summary / Prescription Notes") },
                    minLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📅 Schedule Follow-Up", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = hasFollowUp, onCheckedChange = { hasFollowUp = it })
                }

                if (hasFollowUp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = followUpDay == todayEpochDay + days,
                                onClick = { followUpDay = todayEpochDay + days },
                                label = { Text("+${days}d") }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            DateEpochUtils.formatEpochDayFull(followUpDay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            OutlinedButton(onClick = { if (followUpDay > todayEpochDay) followUpDay -= 1 }) { Text("-1d") }
                            Spacer(modifier = Modifier.size(4.dp))
                            OutlinedButton(onClick = { followUpDay += 1 }) { Text("+1d") }
                        }
                    }
                    OutlinedTextField(
                        value = followUpNotes,
                        onValueChange = { followUpNotes = it },
                        label = { Text("Prep notes (e.g., Bring blood test)") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        doctor.ifBlank { "Family Physician" },
                        specialty,
                        clinic,
                        summary,
                        if (hasFollowUp) followUpDay else null,
                        followUpNotes
                    )
                },
                modifier = Modifier.testTag("save_consultation_dialog_button")
            ) {
                Text("Save Visit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun computeDoseTimes(
    firstTime24h: String,
    dosesPerDay: Int,
    intervalHours: Int
): List<String> {
    val parts = firstTime24h.split(":")
    val startHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val startMin = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    val count = dosesPerDay.coerceIn(1, 6)
    val step = intervalHours.coerceIn(1, 24)
    return (0 until count).map { idx ->
        val h = (startHour + idx * step) % 24
        String.format(Locale.US, "%02d:%02d", h, startMin)
    }
}
