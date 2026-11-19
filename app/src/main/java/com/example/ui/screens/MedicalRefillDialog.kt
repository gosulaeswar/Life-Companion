package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.MedicineEntity
import com.example.domain.DateEpochUtils
import com.example.domain.HealthVaultAndRefillEngine
import com.example.ui.components.CalendarDatePickerDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestockAndExpiryDialog(
    medicine: MedicineEntity,
    prescriptionDocs: List<HealthDocumentEntity>,
    onDismiss: () -> Unit,
    onSave: (
        quantityAdded: Float,
        unitLabel: String,
        purchaseDateEpochDay: Long,
        expiryEpochDay: Long?,
        lowStockThresholdDays: Int,
        pharmacyOrSource: String,
        costNote: String,
        prescriptionDocId: Long?
    ) -> Unit
) {
    val today = remember { DateEpochUtils.todayEpochDay() }
    var quantityAddedText by remember { mutableStateOf("30") }
    var unitLabel by remember { mutableStateOf(medicine.stockUnit.ifBlank { "tablets" }) }
    var purchaseEpochDay by remember { mutableLongStateOf(today) }
    var hasExpiry by remember { mutableStateOf(medicine.expiryEpochDay != null) }
    var expiryEpochDay by remember { mutableLongStateOf(medicine.expiryEpochDay ?: (today + 180L)) }
    var thresholdDays by remember { mutableIntStateOf(medicine.lowStockThresholdDays.coerceIn(1, 30)) }
    var pharmacy by remember { mutableStateOf("") }
    var costNote by remember { mutableStateOf("") }
    var selectedDocId by remember { mutableStateOf(medicine.linkedPrescriptionDocId) }
    var showPurchaseCalendar by remember { mutableStateOf(false) }
    var showExpiryCalendar by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💊 Refill & Expiry: ${medicine.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Current stock: ${HealthVaultAndRefillEngine.formatStockQuantity(medicine.stockQuantity, medicine.stockUnit)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityAddedText,
                        onValueChange = { quantityAddedText = it },
                        label = { Text("Quantity Added") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("refill_quantity_input")
                    )
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Text("Quick Pack Sizes", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("10", "15", "30", "60", "90", "0").forEach { qty ->
                        FilterChip(
                            selected = quantityAddedText == qty,
                            onClick = { quantityAddedText = qty },
                            label = { Text(if (qty == "0") "Update Meta Only" else "+$qty") }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showPurchaseCalendar = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Purchase Date: ${DateEpochUtils.formatEpochDayFull(purchaseEpochDay)}")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Track Expiry Date", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = hasExpiry, onCheckedChange = { hasExpiry = it })
                }

                if (hasExpiry) {
                    OutlinedButton(
                        onClick = { showExpiryCalendar = true },
                        modifier = Modifier.fillMaxWidth().testTag("refill_expiry_date_button")
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Expires: ${DateEpochUtils.formatEpochDayFull(expiryEpochDay)}")
                    }
                }

                Text("Alert Threshold (days before empty)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 7, 14).forEach { days ->
                        FilterChip(
                            selected = thresholdDays == days,
                            onClick = { thresholdDays = days },
                            label = { Text("${days}d") }
                        )
                    }
                }

                if (prescriptionDocs.isNotEmpty()) {
                    Text("Attach Vault Prescription", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedDocId == null,
                            onClick = { selectedDocId = null },
                            label = { Text("None") }
                        )
                        prescriptionDocs.take(4).forEach { doc ->
                            FilterChip(
                                selected = selectedDocId == doc.id,
                                onClick = { selectedDocId = doc.id },
                                label = { Text(doc.title.take(18)) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = pharmacy,
                    onValueChange = { pharmacy = it },
                    label = { Text("Pharmacy / Source (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = costNote,
                    onValueChange = { costNote = it },
                    label = { Text("Batch or Cost Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityAddedText.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
                    onSave(
                        qty,
                        unitLabel.ifBlank { "tablets" },
                        purchaseEpochDay,
                        if (hasExpiry) expiryEpochDay else null,
                        thresholdDays,
                        pharmacy,
                        costNote,
                        selectedDocId
                    )
                },
                modifier = Modifier.testTag("save_refill_dialog_button")
            ) {
                Text("Save Refill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showPurchaseCalendar) {
        CalendarDatePickerDialog(
            initialEpochDay = purchaseEpochDay,
            onDismiss = { showPurchaseCalendar = false },
            onDateSelected = { picked ->
                purchaseEpochDay = picked
                showPurchaseCalendar = false
            }
        )
    }

    if (showExpiryCalendar) {
        CalendarDatePickerDialog(
            initialEpochDay = expiryEpochDay,
            onDismiss = { showExpiryCalendar = false },
            onDateSelected = { picked ->
                expiryEpochDay = picked
                showExpiryCalendar = false
            }
        )
    }
}
