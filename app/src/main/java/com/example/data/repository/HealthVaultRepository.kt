package com.example.data.repository

import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.LabResultEntity
import com.example.data.local.entity.MedicalHistoryEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineRefillEntity
import com.example.domain.DateEpochUtils
import com.example.security.EncryptedDocumentVault
import kotlinx.coroutines.flow.Flow

class HealthVaultRepository(
    private val database: AppDatabase,
    private val encryptedVault: EncryptedDocumentVault
) {
    private val vaultDao = database.healthVaultDao()
    private val medicalDao = database.medicalCareDao()

    fun observeDocuments(profileId: Long): Flow<List<HealthDocumentEntity>> =
        vaultDao.observeDocuments(profileId)

    fun observeLabResults(profileId: Long): Flow<List<LabResultEntity>> =
        vaultDao.observeLabResults(profileId)

    fun observeAllergies(profileId: Long): Flow<List<AllergyRecordEntity>> =
        vaultDao.observeAllergies(profileId)

    fun observeMedicalHistory(profileId: Long): Flow<List<MedicalHistoryEntity>> =
        vaultDao.observeMedicalHistory(profileId)

    fun observeRefills(profileId: Long): Flow<List<MedicineRefillEntity>> =
        vaultDao.observeRefills(profileId)

    suspend fun saveDocumentFromUri(
        profileId: Long,
        title: String,
        category: String,
        documentEpochDay: Long,
        providerOrLabName: String,
        notes: String,
        fileUri: Uri?,
        linkedConsultationId: Long? = null
    ): Result<HealthDocumentEntity> {
        return runCatching {
            val storedFile = if (fileUri != null) {
                encryptedVault.importAndEncryptUri(fileUri).getOrThrow()
            } else null

            val cleanTitle = title.trim().ifBlank {
                storedFile?.originalFileName?.substringBeforeLast('.')?.ifBlank { category } ?: category
            }
            val entity = HealthDocumentEntity(
                profileId = profileId,
                title = cleanTitle,
                category = category,
                documentEpochDay = documentEpochDay,
                providerOrLabName = providerOrLabName.trim(),
                notes = notes.trim(),
                mimeType = storedFile?.mimeType ?: "",
                originalFileName = storedFile?.originalFileName ?: "",
                encryptedFilePath = storedFile?.relativePath ?: "",
                fileSizeBytes = storedFile?.plainSizeBytes ?: 0L,
                linkedConsultationId = linkedConsultationId
            )
            val newId = vaultDao.insertDocument(entity)
            entity.copy(id = newId)
        }
    }

    suspend fun updateDocumentAiExplanation(docId: Long, explanation: String) {
        val existing = vaultDao.getDocumentById(docId) ?: return
        vaultDao.updateDocument(
            existing.copy(
                aiExplanation = explanation.trim(),
                aiExplainedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun readDecryptedDocumentBytes(doc: HealthDocumentEntity): Result<ByteArray> {
        if (doc.encryptedFilePath.isBlank()) {
            return Result.failure(IllegalStateException("This record has no attached encrypted file."))
        }
        return encryptedVault.readDecryptedBytes(doc.encryptedFilePath)
    }

    suspend fun deleteDocument(doc: HealthDocumentEntity) {
        if (doc.encryptedFilePath.isNotBlank()) {
            encryptedVault.deleteEncryptedFile(doc.encryptedFilePath)
        }
        vaultDao.deleteDocument(doc.id)
    }

    suspend fun addLabResult(
        profileId: Long,
        testName: String,
        valueText: String,
        unit: String,
        referenceRangeText: String,
        statusFlag: String,
        recordedEpochDay: Long,
        labName: String,
        documentId: Long? = null,
        notes: String = ""
    ): Long {
        val cleanText = valueText.trim()
        val numeric = cleanText.replace(",", ".").toDoubleOrNull()
        return vaultDao.insertLabResult(
            LabResultEntity(
                profileId = profileId,
                testName = testName.trim(),
                valueNumeric = numeric,
                valueText = cleanText.ifBlank { numeric?.toString() ?: "—" },
                unit = unit.trim(),
                referenceRangeText = referenceRangeText.trim(),
                statusFlag = statusFlag,
                recordedEpochDay = recordedEpochDay,
                labName = labName.trim(),
                documentId = documentId,
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteLabResult(id: Long) = vaultDao.deleteLabResult(id)

    suspend fun addAllergy(
        profileId: Long,
        allergen: String,
        reaction: String,
        severity: String,
        notes: String
    ): Long {
        return vaultDao.insertAllergy(
            AllergyRecordEntity(
                profileId = profileId,
                allergen = allergen.trim(),
                reaction = reaction.trim(),
                severity = severity,
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteAllergy(id: Long) = vaultDao.deleteAllergy(id)

    suspend fun addMedicalHistory(
        profileId: Long,
        conditionOrEvent: String,
        category: String,
        diagnosedYearOrDate: String,
        status: String,
        notes: String
    ): Long {
        return vaultDao.insertMedicalHistory(
            MedicalHistoryEntity(
                profileId = profileId,
                conditionOrEvent = conditionOrEvent.trim(),
                category = category,
                diagnosedYearOrDate = diagnosedYearOrDate.trim(),
                status = status,
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteMedicalHistory(id: Long) = vaultDao.deleteMedicalHistory(id)

    /**
     * Records a medicine purchase / restock and updates the medicine's current stock,
     * expiry date, refill threshold, and optional prescription document link atomically.
     */
    suspend fun logMedicinePurchaseAndRefill(
        profileId: Long,
        medicine: MedicineEntity,
        quantityAdded: Float,
        unitLabel: String,
        purchaseDateEpochDay: Long = DateEpochUtils.todayEpochDay(),
        expiryEpochDay: Long? = medicine.expiryEpochDay,
        lowStockThresholdDays: Int = medicine.lowStockThresholdDays,
        pharmacyOrSource: String = "",
        costNote: String = "",
        prescriptionDocId: Long? = medicine.linkedPrescriptionDocId
    ) {
        val safeAdded = quantityAdded.coerceAtLeast(0f)
        database.withTransaction {
            val freshMed = medicalDao.getMedicineById(medicine.id) ?: medicine
            val updatedMed = freshMed.copy(
                stockQuantity = (freshMed.stockQuantity + safeAdded).coerceAtLeast(0f),
                stockUnit = unitLabel.trim().ifBlank { freshMed.stockUnit },
                lowStockThresholdDays = lowStockThresholdDays.coerceIn(1, 30),
                expiryEpochDay = expiryEpochDay ?: freshMed.expiryEpochDay,
                linkedPrescriptionDocId = prescriptionDocId ?: freshMed.linkedPrescriptionDocId,
                refillReminderEnabled = true
            )
            medicalDao.updateMedicine(updatedMed)

            if (safeAdded > 0f) {
                vaultDao.insertRefill(
                    MedicineRefillEntity(
                        profileId = profileId,
                        medicineId = freshMed.id,
                        medicineName = freshMed.name,
                        quantityAdded = safeAdded,
                        unitLabel = updatedMed.stockUnit,
                        purchaseDateEpochDay = purchaseDateEpochDay,
                        expiryEpochDay = expiryEpochDay,
                        pharmacyOrSource = pharmacyOrSource.trim(),
                        costNote = costNote.trim(),
                        prescriptionDocId = prescriptionDocId
                    )
                )
            }
        }
    }

    suspend fun linkPrescriptionDocumentToMedicine(medicine: MedicineEntity, docId: Long?) {
        val fresh = medicalDao.getMedicineById(medicine.id) ?: medicine
        medicalDao.updateMedicine(fresh.copy(linkedPrescriptionDocId = docId))
    }

    suspend fun deleteRefillLog(id: Long) = vaultDao.deleteRefill(id)
}
