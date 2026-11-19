package com.example.domain

import com.example.ai.AiReportExplanation
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.LabResultEntity
import com.example.data.local.entity.MedicalHistoryEntity
import com.example.data.local.entity.MedicineEntity
import java.util.Locale
import kotlin.math.abs

enum class RefillAlertState(val label: String) {
    UNTRACKED("Inventory unset"),
    OK("Stocked"),
    REFILL_SOON("Refill soon"),
    OUT_OF_STOCK("Out of stock")
}

enum class ExpiryAlertState(val label: String) {
    NO_EXPIRY("No expiry set"),
    OK("Valid"),
    EXPIRING_SOON("Expiring soon"),
    EXPIRED("Expired")
}

data class MedicineInventoryStatus(
    val medicine: MedicineEntity,
    val dailyConsumptionUnits: Float,
    val estimatedDaysRemaining: Float?,
    val refillState: RefillAlertState,
    val expiryState: ExpiryAlertState,
    val daysUntilExpiry: Long?
) {
    val needsAttention: Boolean
        get() = refillState == RefillAlertState.REFILL_SOON ||
            refillState == RefillAlertState.OUT_OF_STOCK ||
            expiryState == ExpiryAlertState.EXPIRING_SOON ||
            expiryState == ExpiryAlertState.EXPIRED
}

data class LabComparisonSummary(
    val testKey: String,
    val displayTestName: String,
    val latest: LabResultEntity,
    val previous: LabResultEntity?,
    val numericDelta: Double?,
    val directionSymbol: String,
    val historyCount: Int,
    val allEntries: List<LabResultEntity>
)

enum class VaultDateFilter(val label: String) {
    ALL("All Dates"),
    LAST_30_DAYS("Last 30d"),
    LAST_90_DAYS("Last 90d"),
    CUSTOM_DATE("Specific Day")
}

object HealthVaultAndRefillEngine {

    const val STANDARD_AI_MEDICAL_DISCLAIMER =
        "Informational summary only — not a medical diagnosis, prescription change, or substitute for a qualified healthcare professional."

    fun evaluateMedicineInventory(
        medicine: MedicineEntity,
        todayEpochDay: Long = DateEpochUtils.todayEpochDay()
    ): MedicineInventoryStatus {
        val dailyConsumption = (medicine.dosesPerDay.coerceAtLeast(1) * medicine.unitsPerDose.coerceAtLeast(0.25f))
        val hasTrackedStock = medicine.stockQuantity > 0f || medicine.linkedPrescriptionDocId != null || medicine.expiryEpochDay != null

        val daysLeft: Float? = if (medicine.stockQuantity > 0f) {
            medicine.stockQuantity / dailyConsumption
        } else {
            null
        }

        val refillState = when {
            medicine.stockQuantity <= 0f && !medicine.refillReminderEnabled -> RefillAlertState.UNTRACKED
            medicine.stockQuantity <= 0f && hasTrackedStock -> RefillAlertState.OUT_OF_STOCK
            medicine.stockQuantity <= 0f -> RefillAlertState.UNTRACKED
            daysLeft != null && daysLeft <= medicine.lowStockThresholdDays.coerceAtLeast(1).toFloat() ->
                RefillAlertState.REFILL_SOON
            else -> RefillAlertState.OK
        }

        val daysUntilExpiry = medicine.expiryEpochDay?.minus(todayEpochDay)
        val expiryState = when {
            daysUntilExpiry == null -> ExpiryAlertState.NO_EXPIRY
            daysUntilExpiry < 0L -> ExpiryAlertState.EXPIRED
            daysUntilExpiry <= 30L -> ExpiryAlertState.EXPIRING_SOON
            else -> ExpiryAlertState.OK
        }

        return MedicineInventoryStatus(
            medicine = medicine,
            dailyConsumptionUnits = dailyConsumption,
            estimatedDaysRemaining = daysLeft,
            refillState = refillState,
            expiryState = expiryState,
            daysUntilExpiry = daysUntilExpiry
        )
    }

    fun evaluateAllMedicines(
        medicines: List<MedicineEntity>,
        todayEpochDay: Long = DateEpochUtils.todayEpochDay()
    ): List<MedicineInventoryStatus> {
        return medicines.map { evaluateMedicineInventory(it, todayEpochDay) }
    }

    fun compareLabResults(results: List<LabResultEntity>): List<LabComparisonSummary> {
        if (results.isEmpty()) return emptyList()
        return results
            .groupBy { it.testName.trim().lowercase(Locale.US) }
            .filterKeys { it.isNotBlank() }
            .map { (key, entries) ->
                val sorted = entries.sortedWith(
                    compareByDescending<LabResultEntity> { it.recordedEpochDay }
                        .thenByDescending { it.id }
                )
                val latest = sorted.first()
                val previous = sorted.getOrNull(1)
                val delta = if (latest.valueNumeric != null && previous?.valueNumeric != null) {
                    latest.valueNumeric - previous.valueNumeric
                } else {
                    null
                }
                val direction = when {
                    delta == null -> "•"
                    abs(delta) < 0.0001 -> "→"
                    delta > 0 -> "↑"
                    else -> "↓"
                }
                LabComparisonSummary(
                    testKey = key,
                    displayTestName = latest.testName.trim(),
                    latest = latest,
                    previous = previous,
                    numericDelta = delta,
                    directionSymbol = direction,
                    historyCount = sorted.size,
                    allEntries = sorted
                )
            }
            .sortedByDescending { it.latest.recordedEpochDay }
    }

    fun filterDocuments(
        documents: List<HealthDocumentEntity>,
        query: String,
        categoryFilter: String,
        dateFilter: VaultDateFilter,
        customEpochDay: Long? = null,
        todayEpochDay: Long = DateEpochUtils.todayEpochDay()
    ): List<HealthDocumentEntity> {
        val cleanQuery = query.trim().lowercase(Locale.US)
        return documents.filter { doc ->
            val matchesCategory = categoryFilter == "All" ||
                doc.category.equals(categoryFilter, ignoreCase = true)

            val matchesDate = when (dateFilter) {
                VaultDateFilter.ALL -> true
                VaultDateFilter.LAST_30_DAYS -> doc.documentEpochDay >= todayEpochDay - 30L
                VaultDateFilter.LAST_90_DAYS -> doc.documentEpochDay >= todayEpochDay - 90L
                VaultDateFilter.CUSTOM_DATE -> customEpochDay == null || doc.documentEpochDay == customEpochDay
            }

            val matchesQuery = cleanQuery.isEmpty() ||
                doc.title.lowercase(Locale.US).contains(cleanQuery) ||
                doc.providerOrLabName.lowercase(Locale.US).contains(cleanQuery) ||
                doc.notes.lowercase(Locale.US).contains(cleanQuery) ||
                doc.category.lowercase(Locale.US).contains(cleanQuery) ||
                doc.originalFileName.lowercase(Locale.US).contains(cleanQuery) ||
                doc.aiExplanation.lowercase(Locale.US).contains(cleanQuery)

            matchesCategory && matchesDate && matchesQuery
        }
    }

    fun formatStockQuantity(quantity: Float, unit: String): String {
        val qtyStr = if (abs(quantity - quantity.toInt()) < 0.05f) {
            quantity.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", quantity)
        }
        return "$qtyStr $unit"
    }

    fun formatDelta(delta: Double, unit: String): String {
        val sign = if (delta > 0) "+" else ""
        val num = if (abs(delta - delta.toInt()) < 0.01) {
            delta.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", delta)
        }
        return if (unit.isNotBlank()) "$sign$num $unit" else "$sign$num"
    }

    fun buildLocalFallbackExplanation(
        doc: HealthDocumentEntity,
        linkedLabs: List<LabResultEntity> = emptyList(),
        allergies: List<AllergyRecordEntity> = emptyList(),
        history: List<MedicalHistoryEntity> = emptyList()
    ): AiReportExplanation {
        val observations = buildList {
            add("Category: ${doc.category} • Date: ${DateEpochUtils.formatEpochDayFull(doc.documentEpochDay)}")
            if (doc.providerOrLabName.isNotBlank()) {
                add("Provider / Lab: ${doc.providerOrLabName}")
            }
            if (doc.notes.isNotBlank()) {
                add("Your recorded notes: ${doc.notes}")
            }
            linkedLabs.take(4).forEach { lab ->
                val valDisplay = lab.valueText.ifBlank { lab.valueNumeric?.toString() ?: "—" }
                val rangeNote = if (lab.referenceRangeText.isNotBlank()) " (Ref: ${lab.referenceRangeText})" else ""
                add("${lab.testName}: $valDisplay ${lab.unit}$rangeNote [${lab.statusFlag}]")
            }
        }
        val questions = buildList {
            add("Which findings or values in '${doc.title}' should we monitor over time?")
            if (allergies.isNotEmpty()) {
                add("Do any treatments conflict with my recorded allergies (${allergies.joinToString { it.allergen }})?")
            }
            if (history.isNotEmpty()) {
                add("How does this report relate to my recorded history (${history.first().conditionOrEvent})?")
            }
            add("When should I schedule my next follow-up test or review?")
        }
        val summary = buildString {
            append("Offline Grounded Summary for '${doc.title}': ")
            if (doc.encryptedFilePath.isNotBlank()) {
                append("Your original ${doc.mimeType.ifBlank { "document" }} is encrypted locally in your device vault. ")
            }
            if (linkedLabs.isNotEmpty()) {
                append("${linkedLabs.size} lab measurement(s) are linked for historical comparison. ")
            } else {
                append("Connect to the internet and tap 'Explain with AI' for a plain-language cloud analysis of the attached report image or PDF.")
            }
        }
        return AiReportExplanation(
            title = doc.title,
            plainLanguageSummary = summary,
            keyObservations = observations,
            questionsForDoctor = questions,
            disclaimer = STANDARD_AI_MEDICAL_DISCLAIMER,
            isFromCloudAi = false
        )
    }
}
