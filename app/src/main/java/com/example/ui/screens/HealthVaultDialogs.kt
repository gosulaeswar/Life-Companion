package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AiReportExplanation
import com.example.data.local.entity.HealthDocumentEntity
import com.example.domain.DateEpochUtils
import com.example.domain.HealthVaultAndRefillEngine
import com.example.ui.components.CalendarDatePickerDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddHealthDocumentDialog(
    initialCategory: String = "Prescription",
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        category: String,
        documentEpochDay: Long,
        providerOrLabName: String,
        notes: String,
        fileUri: Uri?
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory) }
    var provider by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var docEpochDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pickedUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔒 Add Encrypted Health Record") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Prescription", "Lab Report", "Consultation Note", "Medical Record").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title (e.g., Lipid Panel Oct)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vault_doc_title_input")
                )

                OutlinedTextField(
                    value = provider,
                    onValueChange = { provider = it },
                    label = { Text("Doctor / Lab / Clinic (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Date: ${DateEpochUtils.formatEpochDayFull(docEpochDay)}")
                }

                Text("Attach Photo or PDF (Zero-Permission Picker)", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("vault_pick_photo_btn")
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Photo")
                    }
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(arrayOf("application/pdf", "image/*"))
                        },
                        modifier = Modifier.weight(1f).testTag("vault_pick_pdf_btn")
                    ) {
                        Icon(Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" PDF/File")
                    }
                }
                if (pickedUri != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "✅ File attached — will be AES-256-GCM encrypted locally on save.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Key Takeaways (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.ifBlank { category },
                        category,
                        docEpochDay,
                        provider,
                        notes,
                        pickedUri
                    )
                },
                modifier = Modifier.testTag("vault_save_document_button")
            ) {
                Text("Encrypt & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        CalendarDatePickerDialog(
            initialEpochDay = docEpochDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { picked ->
                docEpochDay = picked
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddLabResultDialog(
    documents: List<HealthDocumentEntity>,
    onDismiss: () -> Unit,
    onSave: (
        testName: String,
        valueText: String,
        unit: String,
        referenceRangeText: String,
        statusFlag: String,
        recordedEpochDay: Long,
        labName: String,
        documentId: Long?,
        notes: String
    ) -> Unit
) {
    var testName by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var refRange by remember { mutableStateOf("") }
    var statusFlag by remember { mutableStateOf("Normal") }
    var labName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var recordedDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var linkedDocId by remember { mutableStateOf<Long?>(null) }

    val presets = listOf(
        Triple("Hemoglobin", "g/dL", "12.0 - 16.0"),
        Triple("HbA1c", "%", "4.0 - 5.6"),
        Triple("Vitamin D (25-OH)", "ng/mL", "30 - 100"),
        Triple("Fasting Glucose", "mg/dL", "70 - 99"),
        Triple("TSH", "mIU/L", "0.4 - 4.0"),
        Triple("LDL Cholesterol", "mg/dL", "< 100")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🧪 Log Lab Test Measurement") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Quick Test Presets", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (presetName, presetUnit, presetRef) ->
                        FilterChip(
                            selected = testName.equals(presetName, ignoreCase = true),
                            onClick = {
                                testName = presetName
                                unit = presetUnit
                                refRange = presetRef
                            },
                            label = { Text(presetName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = testName,
                    onValueChange = { testName = it },
                    label = { Text("Test Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vault_lab_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text("Result Value") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("vault_lab_value_input")
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }
                OutlinedTextField(
                    value = refRange,
                    onValueChange = { refRange = it },
                    label = { Text("Reference Range (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Result Flag", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Normal", "High", "Low", "Borderline", "Info").forEach { flag ->
                        FilterChip(
                            selected = statusFlag == flag,
                            onClick = { statusFlag = flag },
                            label = { Text(flag) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Test Date: ${DateEpochUtils.formatEpochDayFull(recordedDay)}")
                }

                OutlinedTextField(
                    value = labName,
                    onValueChange = { labName = it },
                    label = { Text("Lab / Clinic Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (documents.isNotEmpty()) {
                    Text("Link Vault Document (optional)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = linkedDocId == null,
                            onClick = { linkedDocId = null },
                            label = { Text("None") }
                        )
                        documents.take(4).forEach { doc ->
                            FilterChip(
                                selected = linkedDocId == doc.id,
                                onClick = { linkedDocId = doc.id },
                                label = { Text(doc.title.take(18)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        testName.ifBlank { "Lab Marker" },
                        valueText.ifBlank { "0" },
                        unit,
                        refRange,
                        statusFlag,
                        recordedDay,
                        labName,
                        linkedDocId,
                        notes
                    )
                },
                modifier = Modifier.testTag("vault_save_lab_button")
            ) {
                Text("Save Lab Result")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        CalendarDatePickerDialog(
            initialEpochDay = recordedDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { picked ->
                recordedDay = picked
                showDatePicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddAllergyDialog(
    onDismiss: () -> Unit,
    onSave: (allergen: String, reaction: String, severity: String, notes: String) -> Unit
) {
    var allergen by remember { mutableStateOf("") }
    var reaction by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("Moderate") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ Add Allergy / Adverse Reaction") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = allergen,
                    onValueChange = { allergen = it },
                    label = { Text("Allergen (e.g., Penicillin, Peanuts, Pollen)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vault_allergen_input")
                )
                OutlinedTextField(
                    value = reaction,
                    onValueChange = { reaction = it },
                    label = { Text("Reaction (e.g., Hives, Swelling, Rash)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Severity", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Mild", "Moderate", "Severe").forEach { sev ->
                        FilterChip(
                            selected = severity == sev,
                            onClick = { severity = sev },
                            label = { Text(sev) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Emergency Plan (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(allergen.ifBlank { "Recorded Allergy" }, reaction, severity, notes)
                },
                modifier = Modifier.testTag("vault_save_allergy_button")
            ) {
                Text("Save Allergy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMedicalHistoryDialog(
    onDismiss: () -> Unit,
    onSave: (
        conditionOrEvent: String,
        category: String,
        diagnosedYearOrDate: String,
        status: String,
        notes: String
    ) -> Unit
) {
    var condition by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Condition") }
    var yearOrDate by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Active") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📋 Add Medical History Entry") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Entry Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Condition", "Surgery", "Immunization", "Family History").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text("Condition, Procedure, or Vaccine") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vault_history_condition_input")
                )
                OutlinedTextField(
                    value = yearOrDate,
                    onValueChange = { yearOrDate = it },
                    label = { Text("Year / Date (e.g., 2021 or Oct 2024)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Current Status", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Active", "Ongoing", "Resolved").forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Clinician / Treatment Notes (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(condition.ifBlank { "Medical Entry" }, category, yearOrDate, status, notes)
                },
                modifier = Modifier.testTag("vault_save_history_button")
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AiDocumentExplanationDialog(
    explanation: AiReportExplanation,
    cloudErrorMessage: String?,
    onRetryCloudAi: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (explanation.isFromCloudAi) {
                    "✨ Plain-Language Explanation"
                } else {
                    "📊 Grounded Local Summary"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!cloudErrorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Cloud AI unavailable — showing grounded local summary",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = cloudErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Text(
                    text = explanation.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = explanation.plainLanguageSummary,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (explanation.keyObservations.isNotEmpty()) {
                    Text(
                        text = "Key Items Noted",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    explanation.keyObservations.forEach { obs ->
                        Text("• $obs", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (explanation.questionsForDoctor.isNotEmpty()) {
                    Text(
                        text = "Questions You Might Ask Your Doctor",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    explanation.questionsForDoctor.forEach { q ->
                        Text("• $q", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "⚕️ ${explanation.disclaimer.ifBlank { HealthVaultAndRefillEngine.STANDARD_AI_MEDICAL_DISCLAIMER }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (!explanation.isFromCloudAi) {
                TextButton(onClick = onRetryCloudAi) { Text("🔄 Retry AI") }
            }
        }
    )
}

@Composable
fun DocumentPreviewDialog(
    doc: HealthDocumentEntity,
    bitmap: Bitmap?,
    onExplainAi: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔓 ${doc.title}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${doc.category} • ${DateEpochUtils.formatEpochDayFull(doc.documentEpochDay)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (doc.providerOrLabName.isNotBlank()) {
                    Text("Provider/Lab: ${doc.providerOrLabName}", style = MaterialTheme.typography.bodySmall)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = doc.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else if (doc.encryptedFilePath.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "🔒 Decrypted in memory (${doc.originalFileName.ifBlank { doc.mimeType }}, ${(doc.fileSizeBytes / 1024).coerceAtLeast(1)} KB). Tap 'Explain with AI' for plain-language insights.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                if (doc.notes.isNotBlank()) {
                    Text("Notes: ${doc.notes}", style = MaterialTheme.typography.bodySmall)
                }
                if (doc.aiExplanation.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Saved AI Explanation (Informational Only)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(doc.aiExplanation, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onExplainAi) { Text("✨ Explain with AI") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
