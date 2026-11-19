package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_documents",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["category"]),
        Index(value = ["documentEpochDay"])
    ]
)
data class HealthDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val category: String = "Medical Record", // "Prescription", "Lab Report", "Consultation Note", "Medical Record"
    val documentEpochDay: Long,
    val providerOrLabName: String = "",
    val notes: String = "",
    val mimeType: String = "",
    val originalFileName: String = "",
    val encryptedFilePath: String = "",
    val fileSizeBytes: Long = 0L,
    val aiExplanation: String = "",
    val aiExplainedAtEpochMs: Long? = null,
    val linkedConsultationId: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "lab_results",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["testName"]),
        Index(value = ["recordedEpochDay"])
    ]
)
data class LabResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val testName: String,
    val valueNumeric: Double? = null,
    val valueText: String = "",
    val unit: String = "",
    val referenceRangeText: String = "",
    val statusFlag: String = "Normal", // "Normal", "High", "Low", "Borderline", "Info"
    val recordedEpochDay: Long,
    val labName: String = "",
    val documentId: Long? = null,
    val notes: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "allergy_records",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class AllergyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val allergen: String,
    val reaction: String = "",
    val severity: String = "Moderate", // "Mild", "Moderate", "Severe"
    val notes: String = "",
    val recordedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "medical_history_entries",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class MedicalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val conditionOrEvent: String,
    val category: String = "Condition", // "Condition", "Surgery", "Immunization", "Family History"
    val diagnosedYearOrDate: String = "",
    val status: String = "Active", // "Active", "Ongoing", "Resolved"
    val notes: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "medicine_refill_logs",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["medicineId"])
    ]
)
data class MedicineRefillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val medicineId: Long,
    val medicineName: String,
    val quantityAdded: Float,
    val unitLabel: String = "tablets",
    val purchaseDateEpochDay: Long,
    val expiryEpochDay: Long? = null,
    val pharmacyOrSource: String = "",
    val costNote: String = "",
    val prescriptionDocId: Long? = null,
    val loggedAtEpochMs: Long = System.currentTimeMillis()
)
