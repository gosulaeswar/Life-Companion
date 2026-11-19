package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.LabResultEntity
import com.example.data.local.entity.MedicalHistoryEntity
import com.example.data.local.entity.MedicineRefillEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthVaultDao {
    // --- Encrypted Health Documents ---
    @Query("SELECT * FROM health_documents WHERE profileId = :profileId ORDER BY documentEpochDay DESC, createdAtEpochMs DESC")
    fun observeDocuments(profileId: Long): Flow<List<HealthDocumentEntity>>

    @Query("SELECT * FROM health_documents WHERE profileId = :profileId ORDER BY documentEpochDay DESC, createdAtEpochMs DESC")
    suspend fun getDocumentsSnapshot(profileId: Long): List<HealthDocumentEntity>

    @Query("SELECT * FROM health_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): HealthDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: HealthDocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: HealthDocumentEntity)

    @Query("DELETE FROM health_documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Query("DELETE FROM health_documents")
    suspend fun deleteAllDocuments()

    // --- Laboratory Test Results ---
    @Query("SELECT * FROM lab_results WHERE profileId = :profileId ORDER BY recordedEpochDay DESC, id DESC")
    fun observeLabResults(profileId: Long): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_results WHERE profileId = :profileId ORDER BY recordedEpochDay DESC, id DESC")
    suspend fun getLabResultsSnapshot(profileId: Long): List<LabResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabResult(result: LabResultEntity): Long

    @Query("DELETE FROM lab_results WHERE id = :id")
    suspend fun deleteLabResult(id: Long)

    @Query("DELETE FROM lab_results")
    suspend fun deleteAllLabResults()

    // --- Allergies & Adverse Reactions ---
    @Query("SELECT * FROM allergy_records WHERE profileId = :profileId ORDER BY recordedAtEpochMs DESC")
    fun observeAllergies(profileId: Long): Flow<List<AllergyRecordEntity>>

    @Query("SELECT * FROM allergy_records WHERE profileId = :profileId ORDER BY recordedAtEpochMs DESC")
    suspend fun getAllergiesSnapshot(profileId: Long): List<AllergyRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllergy(record: AllergyRecordEntity): Long

    @Query("DELETE FROM allergy_records WHERE id = :id")
    suspend fun deleteAllergy(id: Long)

    @Query("DELETE FROM allergy_records")
    suspend fun deleteAllAllergies()

    // --- Medical History ---
    @Query("SELECT * FROM medical_history_entries WHERE profileId = :profileId ORDER BY updatedAtEpochMs DESC")
    fun observeMedicalHistory(profileId: Long): Flow<List<MedicalHistoryEntity>>

    @Query("SELECT * FROM medical_history_entries WHERE profileId = :profileId ORDER BY updatedAtEpochMs DESC")
    suspend fun getMedicalHistorySnapshot(profileId: Long): List<MedicalHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicalHistory(entry: MedicalHistoryEntity): Long

    @Query("DELETE FROM medical_history_entries WHERE id = :id")
    suspend fun deleteMedicalHistory(id: Long)

    @Query("DELETE FROM medical_history_entries")
    suspend fun deleteAllMedicalHistory()

    // --- Medicine Purchase & Refill History ---
    @Query("SELECT * FROM medicine_refill_logs WHERE profileId = :profileId ORDER BY purchaseDateEpochDay DESC, loggedAtEpochMs DESC")
    fun observeRefills(profileId: Long): Flow<List<MedicineRefillEntity>>

    @Query("SELECT * FROM medicine_refill_logs WHERE profileId = :profileId ORDER BY purchaseDateEpochDay DESC, loggedAtEpochMs DESC")
    suspend fun getRefillsSnapshot(profileId: Long): List<MedicineRefillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefill(refill: MedicineRefillEntity): Long

    @Query("DELETE FROM medicine_refill_logs WHERE id = :id")
    suspend fun deleteRefill(id: Long)

    @Query("DELETE FROM medicine_refill_logs")
    suspend fun deleteAllRefills()
}
