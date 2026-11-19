package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiReportExplanation
import com.example.ai.LifeWeaveAiService
import com.example.ai.SupportedVoiceLanguages
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.LabResultEntity
import com.example.data.local.entity.MedicalHistoryEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineRefillEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.HealthVaultRepository
import com.example.data.repository.UserProfileRepository
import com.example.domain.DateEpochUtils
import com.example.domain.DoctorSummaryPdfGenerator
import com.example.domain.HealthVaultAndRefillEngine
import com.example.domain.LabComparisonSummary
import com.example.domain.MedicineInventoryStatus
import com.example.domain.VaultDateFilter
import com.example.notifications.AlertSoundManager
import com.example.security.EncryptedDocumentVault
import com.example.security.LocalPreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HealthVaultUiState(
    val profile: UserProfileEntity? = null,
    val documents: List<HealthDocumentEntity> = emptyList(),
    val filteredDocuments: List<HealthDocumentEntity> = emptyList(),
    val labResults: List<LabResultEntity> = emptyList(),
    val labComparisons: List<LabComparisonSummary> = emptyList(),
    val allergies: List<AllergyRecordEntity> = emptyList(),
    val medicalHistory: List<MedicalHistoryEntity> = emptyList(),
    val medicines: List<MedicineEntity> = emptyList(),
    val medicineInventories: List<MedicineInventoryStatus> = emptyList(),
    val refillLogs: List<MedicineRefillEntity> = emptyList(),
    val consultations: List<DoctorConsultationEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val selectedDateFilter: VaultDateFilter = VaultDateFilter.ALL,
    val customEpochDay: Long? = null,
    val isExplainingDocument: Boolean = false,
    val explainingDocId: Long? = null,
    val aiStageLabel: String = "",
    val activeExplanation: AiReportExplanation? = null,
    val aiErrorMessage: String? = null,
    val isExportingPdf: Boolean = false,
    val vaultBannerMessage: String? = null,
    val previewDocument: HealthDocumentEntity? = null,
    val previewBitmap: Bitmap? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HealthVaultViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val healthVaultRepository: HealthVaultRepository,
    private val allModulesRepository: AllModulesRepository,
    private val encryptedDocumentVault: EncryptedDocumentVault,
    private val aiService: LifeWeaveAiService,
    private val alertSoundManager: AlertSoundManager,
    private val preferencesManager: LocalPreferencesManager
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val categoryFilterFlow = MutableStateFlow("All")
    private val dateFilterFlow = MutableStateFlow(VaultDateFilter.ALL)
    private val customEpochDayFlow = MutableStateFlow<Long?>(null)

    private val isExplainingFlow = MutableStateFlow(false)
    private val explainingDocIdFlow = MutableStateFlow<Long?>(null)
    private val aiStageFlow = MutableStateFlow("")
    private val activeExplanationFlow = MutableStateFlow<AiReportExplanation?>(null)
    private val aiErrorFlow = MutableStateFlow<String?>(null)
    private val isExportingPdfFlow = MutableStateFlow(false)
    private val bannerFlow = MutableStateFlow<String?>(null)
    private val previewPairFlow = MutableStateFlow<Pair<HealthDocumentEntity?, Bitmap?>>(null to null)

    private val filterAndUiOverlay = combine(
        combine(searchQueryFlow, categoryFilterFlow, dateFilterFlow, customEpochDayFlow) { q, cat, df, cd ->
            FilterParams(q, cat, df, cd)
        },
        combine(isExplainingFlow, explainingDocIdFlow, aiStageFlow, activeExplanationFlow, aiErrorFlow) { exp, docId, stage, report, err ->
            AiOverlayParams(exp, docId, stage, report, err)
        },
        combine(isExportingPdfFlow, bannerFlow, previewPairFlow) { pdf, banner, preview ->
            Triple(pdf, banner, preview)
        }
    ) { f, a, b ->
        OverlayBundle(f, a, b.first, b.second, b.third)
    }

    val uiState: StateFlow<HealthVaultUiState> = userProfileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(HealthVaultUiState())
            } else {
                val repoDataFlow = combine(
                    healthVaultRepository.observeDocuments(profile.id),
                    healthVaultRepository.observeLabResults(profile.id),
                    healthVaultRepository.observeAllergies(profile.id),
                    healthVaultRepository.observeMedicalHistory(profile.id),
                    healthVaultRepository.observeRefills(profile.id)
                ) { docs, labs, allergies, history, refills ->
                    VaultRepoTuple(docs, labs, allergies, history, refills)
                }

                combine(
                    repoDataFlow,
                    allModulesRepository.observeMedicines(profile.id),
                    allModulesRepository.observeConsultations(profile.id),
                    filterAndUiOverlay
                ) { tuple, meds, visits, overlay ->
                    val filtered = HealthVaultAndRefillEngine.filterDocuments(
                        documents = tuple.docs,
                        query = overlay.filters.query,
                        categoryFilter = overlay.filters.category,
                        dateFilter = overlay.filters.dateFilter,
                        customEpochDay = overlay.filters.customDay
                    )
                    val comparisons = HealthVaultAndRefillEngine.compareLabResults(tuple.labs)
                    val inventories = HealthVaultAndRefillEngine.evaluateAllMedicines(meds)
                    HealthVaultUiState(
                        profile = profile,
                        documents = tuple.docs,
                        filteredDocuments = filtered,
                        labResults = tuple.labs,
                        labComparisons = comparisons,
                        allergies = tuple.allergies,
                        medicalHistory = tuple.history,
                        medicines = meds,
                        medicineInventories = inventories,
                        refillLogs = tuple.refills,
                        consultations = visits,
                        searchQuery = overlay.filters.query,
                        selectedCategory = overlay.filters.category,
                        selectedDateFilter = overlay.filters.dateFilter,
                        customEpochDay = overlay.filters.customDay,
                        isExplainingDocument = overlay.ai.isExplaining,
                        explainingDocId = overlay.ai.docId,
                        aiStageLabel = overlay.ai.stage,
                        activeExplanation = overlay.ai.explanation,
                        aiErrorMessage = overlay.ai.error,
                        isExportingPdf = overlay.isExportingPdf,
                        vaultBannerMessage = overlay.banner,
                        previewDocument = overlay.preview.first,
                        previewBitmap = overlay.preview.second
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HealthVaultUiState()
        )

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun setCategoryFilter(category: String) {
        categoryFilterFlow.value = category
    }

    fun setDateFilter(filter: VaultDateFilter, customDay: Long? = null) {
        dateFilterFlow.value = filter
        customEpochDayFlow.value = customDay
    }

    fun clearBanner() {
        bannerFlow.value = null
    }

    fun dismissExplanation() {
        activeExplanationFlow.value = null
        aiErrorFlow.value = null
    }

    fun closePreview() {
        previewPairFlow.value = null to null
    }

    fun openDocumentPreview(doc: HealthDocumentEntity) {
        viewModelScope.launch {
            if (doc.encryptedFilePath.isBlank()) {
                previewPairFlow.value = doc to null
                return@launch
            }
            if (doc.mimeType.startsWith("image/", ignoreCase = true)) {
                val bmpResult = encryptedDocumentVault.decodeDecryptedBitmap(doc.encryptedFilePath)
                bmpResult.onSuccess { bmp ->
                    previewPairFlow.value = doc to bmp
                }.onFailure { err ->
                    bannerFlow.value = err.localizedMessage ?: "Unable to decrypt preview image."
                    previewPairFlow.value = doc to null
                }
            } else {
                // Non-image (e.g. PDF): verify Keystore decryption succeeds and open summary preview
                val verifyResult = encryptedDocumentVault.readDecryptedBytes(doc.encryptedFilePath)
                if (verifyResult.isSuccess) {
                    previewPairFlow.value = doc to null
                } else {
                    bannerFlow.value = verifyResult.exceptionOrNull()?.localizedMessage
                        ?: "Unable to decrypt file."
                }
            }
        }
    }

    fun saveDocument(
        title: String,
        category: String,
        documentEpochDay: Long,
        providerOrLabName: String,
        notes: String,
        fileUri: Uri?,
        linkedConsultationId: Long? = null
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            val result = healthVaultRepository.saveDocumentFromUri(
                profileId = pid,
                title = title,
                category = category,
                documentEpochDay = documentEpochDay,
                providerOrLabName = providerOrLabName,
                notes = notes,
                fileUri = fileUri,
                linkedConsultationId = linkedConsultationId
            )
            result.onSuccess { saved ->
                bannerFlow.value = if (saved.encryptedFilePath.isNotBlank()) {
                    "🔒 Encrypted & saved '${saved.title}' on device."
                } else {
                    "📄 Saved '${saved.title}' to your local vault."
                }
            }.onFailure { err ->
                bannerFlow.value = err.localizedMessage ?: "Failed to save encrypted document."
            }
        }
    }

    fun deleteDocument(doc: HealthDocumentEntity) {
        viewModelScope.launch {
            healthVaultRepository.deleteDocument(doc)
            if (previewPairFlow.value.first?.id == doc.id) {
                previewPairFlow.value = null to null
            }
            bannerFlow.value = "Deleted '${doc.title}' and wiped its encrypted blob."
        }
    }

    fun explainDocumentWithAi(doc: HealthDocumentEntity) {
        if (isExplainingFlow.value) return
        viewModelScope.launch {
            isExplainingFlow.value = true
            explainingDocIdFlow.value = doc.id
            aiErrorFlow.value = null
            aiStageFlow.value = "Decrypting local record in memory..."

            try {
                val fileBytes = if (doc.encryptedFilePath.isNotBlank()) {
                    healthVaultRepository.readDecryptedDocumentBytes(doc).getOrNull()
                } else null

                val prefs = preferencesManager.preferencesFlow.first()
                val lang = SupportedVoiceLanguages.options.firstOrNull {
                    it.tag.startsWith(prefs.language.code, ignoreCase = true)
                } ?: SupportedVoiceLanguages.options.first()
                val result = aiService.explainHealthDocument(
                    title = doc.title,
                    category = doc.category,
                    providerOrLab = doc.providerOrLabName,
                    notes = doc.notes,
                    fileBytes = fileBytes,
                    mimeType = doc.mimeType,
                    languageOption = lang,
                    onStageUpdate = { stage -> aiStageFlow.value = stage },
                    onPartialPreview = { preview -> aiStageFlow.value = preview }
                )
                result.onSuccess { explanation ->
                    activeExplanationFlow.value = explanation
                    val formattedSaved = buildString {
                        appendLine(explanation.plainLanguageSummary)
                        if (explanation.keyObservations.isNotEmpty()) {
                            appendLine()
                            explanation.keyObservations.forEach { appendLine("• $it") }
                        }
                        appendLine()
                        append(explanation.disclaimer)
                    }
                    healthVaultRepository.updateDocumentAiExplanation(doc.id, formattedSaved)
                }.onFailure { err ->
                    if (err is CancellationException) throw err
                    aiErrorFlow.value = err.localizedMessage ?: "Cloud AI unavailable."
                    val linkedLabs = uiState.value.labResults.filter { it.documentId == doc.id }
                    activeExplanationFlow.value = HealthVaultAndRefillEngine.buildLocalFallbackExplanation(
                        doc = doc,
                        linkedLabs = linkedLabs,
                        allergies = uiState.value.allergies,
                        history = uiState.value.medicalHistory
                    )
                }
            } finally {
                isExplainingFlow.value = false
                explainingDocIdFlow.value = null
                aiStageFlow.value = ""
            }
        }
    }

    fun addLabResult(
        testName: String,
        valueText: String,
        unit: String,
        referenceRangeText: String,
        statusFlag: String,
        recordedEpochDay: Long,
        labName: String,
        documentId: Long? = null,
        notes: String = ""
    ) {
        val pid = uiState.value.profile?.id ?: return
        if (testName.isBlank()) return
        viewModelScope.launch {
            healthVaultRepository.addLabResult(
                profileId = pid,
                testName = testName,
                valueText = valueText,
                unit = unit,
                referenceRangeText = referenceRangeText,
                statusFlag = statusFlag,
                recordedEpochDay = recordedEpochDay,
                labName = labName,
                documentId = documentId,
                notes = notes
            )
            bannerFlow.value = "🧪 Saved '$testName' measurement for historical comparison."
        }
    }

    fun deleteLabResult(id: Long) = viewModelScope.launch {
        healthVaultRepository.deleteLabResult(id)
    }

    fun addAllergy(allergen: String, reaction: String, severity: String, notes: String) {
        val pid = uiState.value.profile?.id ?: return
        if (allergen.isBlank()) return
        viewModelScope.launch {
            healthVaultRepository.addAllergy(pid, allergen, reaction, severity, notes)
            bannerFlow.value = "⚠️ Recorded allergy: $allergen ($severity)."
        }
    }

    fun deleteAllergy(id: Long) = viewModelScope.launch {
        healthVaultRepository.deleteAllergy(id)
    }

    fun addMedicalHistory(
        conditionOrEvent: String,
        category: String,
        diagnosedYearOrDate: String,
        status: String,
        notes: String
    ) {
        val pid = uiState.value.profile?.id ?: return
        if (conditionOrEvent.isBlank()) return
        viewModelScope.launch {
            healthVaultRepository.addMedicalHistory(
                profileId = pid,
                conditionOrEvent = conditionOrEvent,
                category = category,
                diagnosedYearOrDate = diagnosedYearOrDate,
                status = status,
                notes = notes
            )
            bannerFlow.value = "📋 Added '$conditionOrEvent' to medical history."
        }
    }

    fun deleteMedicalHistory(id: Long) = viewModelScope.launch {
        healthVaultRepository.deleteMedicalHistory(id)
    }

    fun logMedicinePurchaseAndRefill(
        medicine: MedicineEntity,
        quantityAdded: Float,
        unitLabel: String,
        purchaseDateEpochDay: Long,
        expiryEpochDay: Long?,
        lowStockThresholdDays: Int,
        pharmacyOrSource: String,
        costNote: String,
        prescriptionDocId: Long?
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            healthVaultRepository.logMedicinePurchaseAndRefill(
                profileId = pid,
                medicine = medicine,
                quantityAdded = quantityAdded,
                unitLabel = unitLabel,
                purchaseDateEpochDay = purchaseDateEpochDay,
                expiryEpochDay = expiryEpochDay,
                lowStockThresholdDays = lowStockThresholdDays,
                pharmacyOrSource = pharmacyOrSource,
                costNote = costNote,
                prescriptionDocId = prescriptionDocId
            )
            bannerFlow.value = "💊 Updated ${medicine.name} inventory & expiry details."
            notifyAttentionAlertsIfAny()
        }
    }

    fun exportDoctorReadyPdf(context: Context, destinationUri: Uri) {
        val snapshot = uiState.value
        viewModelScope.launch {
            isExportingPdfFlow.value = true
            val result = DoctorSummaryPdfGenerator.writeSummaryPdfToUri(
                context = context,
                uri = destinationUri,
                profile = snapshot.profile,
                allergies = snapshot.allergies,
                medicalHistory = snapshot.medicalHistory,
                medicines = snapshot.medicines,
                labComparisons = snapshot.labComparisons,
                consultations = snapshot.consultations,
                documents = snapshot.documents
            )
            isExportingPdfFlow.value = false
            result.onSuccess { bytes ->
                val kb = (bytes / 1024).coerceAtLeast(1)
                bannerFlow.value = "📄 Doctor-Ready Health Summary PDF exported (${kb} KB)."
            }.onFailure { err ->
                bannerFlow.value = err.localizedMessage ?: "Failed to export Doctor-Ready PDF."
            }
        }
    }

    fun notifyAttentionAlertsIfAny() {
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            val today = DateEpochUtils.todayEpochDay()
            val attention = HealthVaultAndRefillEngine.evaluateAllMedicines(uiState.value.medicines, today)
                .filter { it.medicine.isActive && it.medicine.refillReminderEnabled && it.needsAttention }
            val first = attention.firstOrNull() ?: return@launch
            val body = buildString {
                if (first.refillState.name == "REFILL_SOON" || first.refillState.name == "OUT_OF_STOCK") {
                    val leftStr = first.estimatedDaysRemaining?.let { "~${it.toInt()}d left" } ?: "out of stock"
                    append("${first.medicine.name} inventory is low ($leftStr). ")
                }
                if (first.expiryState.name == "EXPIRED" || first.expiryState.name == "EXPIRING_SOON") {
                    val expStr = first.daysUntilExpiry?.let { d ->
                        if (d < 0) "expired ${-d}d ago" else "expires in ${d}d"
                    }.orEmpty()
                    append("${first.medicine.name} $expStr.")
                }
            }.trim()
            if (body.isNotBlank()) {
                alertSoundManager.showMedicineRefillOrExpiryNotification(
                    title = "Medicine Refill / Expiry Alert",
                    body = body,
                    wellnessToneSetting = prefs.wellnessTone
                )
            }
        }
    }

    private data class FilterParams(
        val query: String,
        val category: String,
        val dateFilter: VaultDateFilter,
        val customDay: Long?
    )

    private data class AiOverlayParams(
        val isExplaining: Boolean,
        val docId: Long?,
        val stage: String,
        val explanation: AiReportExplanation?,
        val error: String?
    )

    private data class OverlayBundle(
        val filters: FilterParams,
        val ai: AiOverlayParams,
        val isExportingPdf: Boolean,
        val banner: String?,
        val preview: Pair<HealthDocumentEntity?, Bitmap?>
    )

    private data class VaultRepoTuple(
        val docs: List<HealthDocumentEntity>,
        val labs: List<LabResultEntity>,
        val allergies: List<AllergyRecordEntity>,
        val history: List<MedicalHistoryEntity>,
        val refills: List<MedicineRefillEntity>
    )
}
