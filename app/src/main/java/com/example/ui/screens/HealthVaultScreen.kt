package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.local.entity.HealthDocumentEntity
import com.example.domain.DateEpochUtils
import com.example.domain.HealthVaultAndRefillEngine
import com.example.domain.LabComparisonSummary
import com.example.domain.VaultDateFilter
import com.example.ui.components.CalendarDatePickerDialog
import com.example.ui.theme.HeroForestDark
import com.example.ui.theme.HeroTealDeep
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.HealthVaultUiState
import com.example.ui.viewmodel.HealthVaultViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthVaultScreen(
    uiState: HealthVaultUiState,
    viewModel: HealthVaultViewModel,
    onBack: () -> Unit,
    onOpenMedicines: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0=Docs & Rx, 1=Labs, 2=Allergies & History
    var showAddDocDialog by remember { mutableStateOf(false) }
    var addDocDefaultCategory by remember { mutableStateOf("Prescription") }
    var showAddLabDialog by remember { mutableStateOf(false) }
    var showAddAllergyDialog by remember { mutableStateOf(false) }
    var showAddHistoryDialog by remember { mutableStateOf(false) }
    var showFilterDateCalendar by remember { mutableStateOf(false) }
    var lastExplainedDoc by remember { mutableStateOf<HealthDocumentEntity?>(null) }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            viewModel.exportDoctorReadyPdf(context, destUri)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vault_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = "🗂️ ${stringResource(id = R.string.vault_screen_title)}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "AES-256-GCM local vault • Prescriptions, labs & Doctor PDF",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val fileName = "LifeWeave_Health_Summary_${DateEpochUtils.todayDayKey()}.pdf"
                        pdfExportLauncher.launch(fileName)
                    },
                    enabled = !uiState.isExportingPdf,
                    modifier = Modifier.testTag("vault_export_pdf_button")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export Doctor PDF", modifier = Modifier.size(18.dp))
                    Text(" PDF")
                }
            }
        }

        // Encrypted Vault Hero Card
        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = HeroForestDark)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(HeroForestDark, HeroTealDeep)
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔒 Private On-Device Vault",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Hardware Keystore encrypted files • Zero external storage permissions",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.84f)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2CE5B5).copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, Color(0xFF2CE5B5).copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = "${uiState.documents.size} Docs • ${uiState.labComparisons.size} Labs",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoMetricFont),
                                color = Color(0xFF9DF7DF),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                addDocDefaultCategory = "Prescription"
                                showAddDocDialog = true
                            },
                            modifier = Modifier.testTag("vault_add_document_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" Add Record / Rx")
                        }
                        OutlinedButton(
                            onClick = { showAddLabDialog = true },
                            modifier = Modifier.testTag("vault_add_lab_button")
                        ) {
                            Text("🧪 Log Lab", color = Color.White)
                        }
                        OutlinedButton(
                            onClick = {
                                val fileName = "LifeWeave_Health_Summary_${DateEpochUtils.todayDayKey()}.pdf"
                                pdfExportLauncher.launch(fileName)
                            },
                            enabled = !uiState.isExportingPdf
                        ) {
                            Text("📄 Doctor PDF", color = Color.White)
                        }
                    }
                }
            }
        }

        // Status Banner & AI Progress
        uiState.vaultBannerMessage?.let { banner ->
            item {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearBanner) { Text("OK") }
                    }
                }
            }
        }

        if (uiState.isExplainingDocument) {
            item {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = uiState.aiStageLabel.ifBlank { "Analyzing document..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Section Tabs
        item {
            FlowRow(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text("📄 Documents & Rx (${uiState.documents.size})") },
                    modifier = Modifier.testTag("vault_tab_documents")
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text("🧪 Lab Compare (${uiState.labComparisons.size})") },
                    modifier = Modifier.testTag("vault_tab_labs")
                )
                FilterChip(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    label = { Text("⚠️ Allergies & History (${uiState.allergies.size + uiState.medicalHistory.size})") },
                    modifier = Modifier.testTag("vault_tab_history")
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Search & Category / Date Filters
                item {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::setSearchQuery,
                                label = { Text("Search documents, doctors, labs, notes...") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("vault_search_input")
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("All", "Prescription", "Lab Report", "Consultation Note", "Medical Record").forEach { cat ->
                                    FilterChip(
                                        selected = uiState.selectedCategory == cat,
                                        onClick = { viewModel.setCategoryFilter(cat) },
                                        label = { Text(cat) }
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VaultDateFilter.entries.forEach { df ->
                                    val label = if (df == VaultDateFilter.CUSTOM_DATE && uiState.customEpochDay != null) {
                                        DateEpochUtils.formatEpochDayShort(uiState.customEpochDay)
                                    } else df.label
                                    FilterChip(
                                        selected = uiState.selectedDateFilter == df,
                                        onClick = {
                                            if (df == VaultDateFilter.CUSTOM_DATE) {
                                                showFilterDateCalendar = true
                                            } else {
                                                viewModel.setDateFilter(df, null)
                                            }
                                        },
                                        leadingIcon = if (df == VaultDateFilter.CUSTOM_DATE) {
                                            { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.filteredDocuments.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📂 No matching records in vault", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Store prescriptions, blood test PDFs, X-ray summaries, or consultation notes encrypted locally on your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { showAddDocDialog = true }) {
                                    Text("🔒 Add First Health Document")
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.filteredDocuments, key = { "vault_doc_${it.id}" }) { doc ->
                        VaultDocumentCard(
                            doc = doc,
                            isExplainingThis = uiState.isExplainingDocument && uiState.explainingDocId == doc.id,
                            onPreview = { viewModel.openDocumentPreview(doc) },
                            onExplainAi = {
                                lastExplainedDoc = doc
                                viewModel.explainDocumentWithAi(doc)
                            },
                            onDelete = { viewModel.deleteDocument(doc) }
                        )
                    }
                }

                // Linked Prescription History Summary
                val rxDocs = uiState.documents.filter { it.category.equals("Prescription", ignoreCase = true) }
                val linkedMeds = uiState.medicines.filter { it.linkedPrescriptionDocId != null }
                if (rxDocs.isNotEmpty() || uiState.consultations.isNotEmpty() || linkedMeds.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("💊 Prescription & Visit History", style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = onOpenMedicines) { Text("Meds & Refills →") }
                                }
                                Text(
                                    text = "${rxDocs.size} prescription doc(s) • ${linkedMeds.size} linked medicine(s) • ${uiState.consultations.size} consultation(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                uiState.consultations.take(4).forEach { visit ->
                                    Text(
                                        text = "• ${DateEpochUtils.formatEpochDayShort(visit.consultationEpochMs / 86_400_000L)} — Dr. ${visit.doctorName} (${visit.specialty})",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                if (uiState.labComparisons.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🧪 Track & Compare Lab Tests", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Log markers like Hemoglobin, HbA1c, Vitamin D, or TSH multiple times to compare latest vs. previous measurements automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { showAddLabDialog = true }) {
                                    Text("🧪 Add First Lab Result")
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.labComparisons, key = { "lab_comp_${it.testKey}" }) { comp ->
                        LabComparisonCard(
                            comparison = comp,
                            onDeleteLatest = { viewModel.deleteLabResult(comp.latest.id) }
                        )
                    }
                }
            }

            2 -> {
                item {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                Text("⚠️ Allergies & Adverse Reactions", style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = { showAddAllergyDialog = true },
                                    modifier = Modifier.testTag("vault_add_allergy_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" Add")
                                }
                            }
                            if (uiState.allergies.isEmpty()) {
                                Text(
                                    "Record medication, food, or seasonal allergies to keep ready for doctor visits and PDF summaries.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                uiState.allergies.forEach { allergy ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${allergy.allergen} • ${allergy.severity}",
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                if (allergy.reaction.isNotBlank()) {
                                                    Text(
                                                        text = "Reaction: ${allergy.reaction}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { viewModel.deleteAllergy(allergy.id) }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete allergy")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                Text("📋 Medical History", style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = { showAddHistoryDialog = true },
                                    modifier = Modifier.testTag("vault_add_history_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(" Add")
                                }
                            }
                            if (uiState.medicalHistory.isEmpty()) {
                                Text(
                                    "Log past conditions, surgeries, immunizations, or family history.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                uiState.medicalHistory.forEach { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${entry.conditionOrEvent} (${entry.category})",
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                val meta = listOfNotNull(
                                                    entry.status.takeIf { it.isNotBlank() },
                                                    entry.diagnosedYearOrDate.takeIf { it.isNotBlank() }
                                                ).joinToString(" • ")
                                                if (meta.isNotBlank()) {
                                                    Text(
                                                        text = meta,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                if (entry.notes.isNotBlank()) {
                                                    Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                            IconButton(onClick = { viewModel.deleteMedicalHistory(entry.id) }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete history entry")
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

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showAddDocDialog) {
        AddHealthDocumentDialog(
            initialCategory = addDocDefaultCategory,
            onDismiss = { showAddDocDialog = false },
            onSave = { title, cat, day, provider, notes, uri ->
                viewModel.saveDocument(title, cat, day, provider, notes, uri)
                showAddDocDialog = false
            }
        )
    }

    if (showAddLabDialog) {
        AddLabResultDialog(
            documents = uiState.documents,
            onDismiss = { showAddLabDialog = false },
            onSave = { name, value, unit, ref, flag, day, lab, docId, notes ->
                viewModel.addLabResult(name, value, unit, ref, flag, day, lab, docId, notes)
                showAddLabDialog = false
            }
        )
    }

    if (showAddAllergyDialog) {
        AddAllergyDialog(
            onDismiss = { showAddAllergyDialog = false },
            onSave = { allergen, reaction, severity, notes ->
                viewModel.addAllergy(allergen, reaction, severity, notes)
                showAddAllergyDialog = false
            }
        )
    }

    if (showAddHistoryDialog) {
        AddMedicalHistoryDialog(
            onDismiss = { showAddHistoryDialog = false },
            onSave = { cond, cat, date, status, notes ->
                viewModel.addMedicalHistory(cond, cat, date, status, notes)
                showAddHistoryDialog = false
            }
        )
    }

    if (showFilterDateCalendar) {
        CalendarDatePickerDialog(
            initialEpochDay = uiState.customEpochDay ?: DateEpochUtils.todayEpochDay(),
            onDismiss = { showFilterDateCalendar = false },
            onDateSelected = { picked ->
                viewModel.setDateFilter(VaultDateFilter.CUSTOM_DATE, picked)
                showFilterDateCalendar = false
            }
        )
    }

    uiState.activeExplanation?.let { explanation ->
        AiDocumentExplanationDialog(
            explanation = explanation,
            cloudErrorMessage = uiState.aiErrorMessage,
            onRetryCloudAi = {
                lastExplainedDoc?.let { viewModel.explainDocumentWithAi(it) }
            },
            onDismiss = viewModel::dismissExplanation
        )
    }

    uiState.previewDocument?.let { previewDoc ->
        DocumentPreviewDialog(
            doc = previewDoc,
            bitmap = uiState.previewBitmap,
            onExplainAi = {
                lastExplainedDoc = previewDoc
                viewModel.closePreview()
                viewModel.explainDocumentWithAi(previewDoc)
            },
            onDismiss = viewModel::closePreview
        )
    }
}

@Composable
private fun VaultDocumentCard(
    doc: HealthDocumentEntity,
    isExplainingThis: Boolean,
    onPreview: () -> Unit,
    onExplainAi: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(doc.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${doc.category} • ${DateEpochUtils.formatEpochDayFull(doc.documentEpochDay)}" +
                            if (doc.providerOrLabName.isNotBlank()) " • ${doc.providerOrLabName}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (doc.encryptedFilePath.isNotBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = "Encrypted", modifier = Modifier.size(13.dp))
                            Text("AES-256", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (doc.notes.isNotBlank()) {
                Text(
                    text = doc.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPreview, modifier = Modifier.testTag("vault_view_doc_${doc.id}")) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" View")
                    }
                    Button(
                        onClick = onExplainAi,
                        enabled = !isExplainingThis,
                        modifier = Modifier.testTag("vault_explain_doc_${doc.id}")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Explain AI")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete document")
                }
            }
        }
    }
}

@Composable
private fun LabComparisonCard(
    comparison: LabComparisonSummary,
    onDeleteLatest: () -> Unit
) {
    val latest = comparison.latest
    val prev = comparison.previous
    val latestVal = latest.valueText.ifBlank { latest.valueNumeric?.toString() ?: "—" }

    Surface(
        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(comparison.displayTestName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${DateEpochUtils.formatEpochDayShort(latest.recordedEpochDay)}" +
                            if (latest.labName.isNotBlank()) " • ${latest.labName}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = when (latest.statusFlag) {
                            "High", "Low" -> MaterialTheme.colorScheme.errorContainer
                            "Borderline" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = latest.statusFlag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(onClick = onDeleteLatest) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete latest lab measurement")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$latestVal ${latest.unit}".trim(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = JetBrainsMonoMetricFont,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                if (latest.referenceRangeText.isNotBlank()) {
                    Text(
                        text = "Ref: ${latest.referenceRangeText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (prev != null) {
                val prevVal = prev.valueText.ifBlank { prev.valueNumeric?.toString() ?: "—" }
                val deltaText = comparison.numericDelta?.let { d ->
                    " (${comparison.directionSymbol} ${HealthVaultAndRefillEngine.formatDelta(d, latest.unit)})"
                }.orEmpty()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "Previous (${DateEpochUtils.formatEpochDayShort(prev.recordedEpochDay)}): $prevVal ${prev.unit}$deltaText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}
