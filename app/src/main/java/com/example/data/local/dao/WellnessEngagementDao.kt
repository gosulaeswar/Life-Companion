package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.WellnessChallengeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WellnessEngagementDao {

    // --- Daily Routines ---

    @Query("SELECT * FROM daily_routines WHERE profileId = :profileId ORDER BY sortOrder ASC, triggerTime24h ASC, id ASC")
    fun observeRoutines(profileId: Long): Flow<List<DailyRoutineEntity>>

    @Query("SELECT * FROM daily_routines WHERE profileId = :profileId ORDER BY sortOrder ASC, id ASC")
    suspend fun getRoutinesSnapshot(profileId: Long): List<DailyRoutineEntity>

    @Query("SELECT * FROM daily_routines WHERE id = :id LIMIT 1")
    suspend fun getRoutineById(id: Long): DailyRoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: DailyRoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: DailyRoutineEntity)

    @Query("DELETE FROM daily_routines WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("DELETE FROM daily_routines")
    suspend fun deleteAllRoutines()

    // --- Routine Completions ---

    @Query("SELECT * FROM routine_completions WHERE profileId = :profileId AND dayKey >= :startDayKey ORDER BY completedAtEpochMs DESC")
    fun observeCompletionsSince(profileId: Long, startDayKey: String): Flow<List<RoutineCompletionEntity>>

    @Query("SELECT * FROM routine_completions WHERE profileId = :profileId ORDER BY completedAtEpochMs DESC")
    suspend fun getCompletionsSnapshot(profileId: Long): List<RoutineCompletionEntity>

    @Query("SELECT * FROM routine_completions WHERE profileId = :profileId AND routineId = :routineId AND dayKey = :dayKey LIMIT 1")
    suspend fun getCompletionForDay(profileId: Long, routineId: Long, dayKey: String): RoutineCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: RoutineCompletionEntity): Long

    @Query("DELETE FROM routine_completions WHERE profileId = :profileId AND routineId = :routineId AND dayKey = :dayKey")
    suspend fun deleteCompletionForDay(profileId: Long, routineId: Long, dayKey: String)

    @Query("DELETE FROM routine_completions")
    suspend fun deleteAllCompletions()

    // --- Mood Journal ---

    @Query("SELECT * FROM mood_journal_entries WHERE profileId = :profileId ORDER BY loggedAtEpochMs DESC")
    fun observeMoodEntries(profileId: Long): Flow<List<MoodJournalEntryEntity>>

    @Query("SELECT * FROM mood_journal_entries WHERE profileId = :profileId ORDER BY loggedAtEpochMs DESC")
    suspend fun getMoodEntriesSnapshot(profileId: Long): List<MoodJournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoodEntry(entry: MoodJournalEntryEntity): Long

    @Update
    suspend fun updateMoodEntry(entry: MoodJournalEntryEntity)

    @Query("DELETE FROM mood_journal_entries WHERE id = :id")
    suspend fun deleteMoodEntry(id: Long)

    @Query("DELETE FROM mood_journal_entries")
    suspend fun deleteAllMoodEntries()

    // --- Wellness Challenges (Opt-In, Non-Manipulative) ---

    @Query("SELECT * FROM wellness_challenges WHERE profileId = :profileId ORDER BY id ASC")
    fun observeChallenges(profileId: Long): Flow<List<WellnessChallengeEntity>>

    @Query("SELECT * FROM wellness_challenges WHERE profileId = :profileId ORDER BY id ASC")
    suspend fun getChallengesSnapshot(profileId: Long): List<WellnessChallengeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChallengesIfMissing(challenges: List<WellnessChallengeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(challenge: WellnessChallengeEntity): Long

    @Update
    suspend fun updateChallenge(challenge: WellnessChallengeEntity)

    @Query("DELETE FROM wellness_challenges WHERE id = :id")
    suspend fun deleteChallenge(id: Long)

    @Query("DELETE FROM wellness_challenges")
    suspend fun deleteAllChallenges()

    // --- Achievement Badges ---

    @Query("SELECT * FROM achievement_badges WHERE profileId = :profileId ORDER BY CASE WHEN earnedAtEpochMs IS NOT NULL THEN 0 ELSE 1 END, id ASC")
    fun observeBadges(profileId: Long): Flow<List<AchievementBadgeEntity>>

    @Query("SELECT * FROM achievement_badges WHERE profileId = :profileId ORDER BY id ASC")
    suspend fun getBadgesSnapshot(profileId: Long): List<AchievementBadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBadgesIfMissing(badges: List<AchievementBadgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBadge(badge: AchievementBadgeEntity): Long

    @Query("DELETE FROM achievement_badges")
    suspend fun deleteAllBadges()
}
