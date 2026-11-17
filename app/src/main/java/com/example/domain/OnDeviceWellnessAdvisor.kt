package com.example.domain

import com.example.data.local.entity.AiPreferenceEntity
import com.example.data.local.entity.ConnectedDeviceSourceEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.HealthVitalMetricEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineIntakeEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity

data class LocalAiMessage(
    val id: Long,
    val isFromUser: Boolean,
    val text: String,
    val actionBadges: List<String> = emptyList(),
    val isError: Boolean = false
)

/**
 * Local wellness context synthesizer + deterministic offline fallback helper.
 */
object OnDeviceWellnessAdvisor {

    fun buildDailyBriefing(
        profile: UserProfileEntity?,
        hydration: HydrationSummary,
        cycle: CycleInsights?,
        exercises: List<ExerciseRecordEntity>,
        diet: List<DietRecordEntity>,
        sleep: List<SleepRecordEntity>,
        steps: StepRecordEntity?,
        wellbeing: DigitalWellbeingRecordEntity?,
        aiPref: AiPreferenceEntity?,
        medicines: List<MedicineEntity> = emptyList(),
        todayIntakes: List<MedicineIntakeEntity> = emptyList(),
        consultations: List<DoctorConsultationEntity> = emptyList(),
        connectedSources: List<ConnectedDeviceSourceEntity> = emptyList(),
        vitalMetrics: List<HealthVitalMetricEntity> = emptyList()
    ): String {
        val name = profile?.fullName ?: "friend"
        val tone = aiPref?.preferredTone ?: "Gentle"
        val prefix = when (tone) {
            "Energetic" -> "⚡ Hey $name! Here is your energetic wellness pulse check:"
            "Concise" -> "📌 $name — Today's wellness snapshot:"
            else -> "🌿 Warm check-in, $name:"
        }

        val hydrationLine = if (hydration.remainingMl == 0) {
            "💧 Hydration goal crushed (${hydration.totalTodayMl} ml)! Keep sipping gently."
        } else {
            "💧 ${hydration.remainingMl} ml left to reach your ${hydration.goalMl} ml water goal."
        }

        val activeMeds = medicines.filter { it.isActive }
        val medLine = if (activeMeds.isNotEmpty()) {
            val expected = activeMeds.sumOf { it.dosesPerDay.coerceAtLeast(1) }
            val taken = todayIntakes.count { it.status == "Taken" }
            "💊 Medicines: $taken/$expected scheduled doses logged today."
        } else {
            null
        }

        val nextFollowUp = consultations
            .filter { it.followUpEpochDay != null && !it.isFollowUpCompleted }
            .minByOrNull { it.followUpEpochDay ?: Long.MAX_VALUE }
        val doctorLine = nextFollowUp?.let { visit ->
            val fDay = visit.followUpEpochDay ?: DateEpochUtils.todayEpochDay()
            "🩺 Next doctor follow-up: Dr. ${visit.doctorName} on ${DateEpochUtils.formatEpochDayShort(fDay)}."
        }

        val latestHr = vitalMetrics.firstOrNull { it.metricType == "HEART_RATE" }
        val deviceLine = if (connectedSources.isNotEmpty() || latestHr != null) {
            val srcText = connectedSources.joinToString(", ") { "${it.emoji} ${it.displayName}" }
            val hrText = latestHr?.let { " • ❤️ ${it.valuePrimary.toInt()} bpm" }.orEmpty()
            "⌚ Connected devices: ${srcText.ifBlank { "Synced" }}$hrText"
        } else {
            null
        }

        val movementLine = if (exercises.isNotEmpty() || (steps?.stepCount ?: 0) > 2000) {
            "🏃 Active momentum: ${exercises.sumOf { it.durationMinutes }}m movement & ${steps?.stepCount ?: 0} steps logged."
        } else {
            "🧘 Even a 10-minute gentle stretch or walk boosts circulation and focus."
        }

        val cycleLine = if (profile?.cycleTrackingEnabled == true && cycle?.currentCycleDay != null) {
            "🌸 Cycle Day ${cycle.currentCycleDay} (${cycle.currentPhase.label}): ${cycle.currentPhase.guidance}"
        } else {
            null
        }

        val sleepLine = sleep.firstOrNull()?.let { s ->
            val hrs = ((s.wakeEpochMs - s.bedtimeEpochMs) / 3_600_000f)
            String.format("😴 Last sleep: %.1f hrs (quality %d/5).", hrs, s.qualityRating)
        }

        val nutritionLine = if (diet.isNotEmpty()) {
            "🥗 Logged ${diet.size} meal(s) totaling ${diet.sumOf { it.calories }} kcal."
        } else {
            null
        }

        val focusLine = wellbeing?.let {
            "📵 Mindful screen budget: ${it.screenTimeMinutes}/${it.mindfulGoalMinutes} min • ${it.focusSessionsCount} focus blocks."
        }

        return listOfNotNull(
            prefix,
            hydrationLine,
            medLine,
            doctorLine,
            deviceLine,
            movementLine,
            cycleLine,
            sleepLine,
            nutritionLine,
            focusLine
        ).joinToString("\n\n")
    }

    fun answerPrompt(
        prompt: String,
        profile: UserProfileEntity?,
        hydration: HydrationSummary,
        cycle: CycleInsights?,
        exercises: List<ExerciseRecordEntity>,
        diet: List<DietRecordEntity>,
        sleep: List<SleepRecordEntity>,
        steps: StepRecordEntity?,
        wellbeing: DigitalWellbeingRecordEntity?,
        medicines: List<MedicineEntity> = emptyList(),
        todayIntakes: List<MedicineIntakeEntity> = emptyList(),
        consultations: List<DoctorConsultationEntity> = emptyList()
    ): String {
        val q = prompt.lowercase()
        val name = profile?.fullName ?: "friend"
        return when {
            "med" in q || "dose" in q || "pill" in q || "doctor" in q || "follow" in q -> {
                val active = medicines.filter { it.isActive }
                val expected = active.sumOf { it.dosesPerDay.coerceAtLeast(1) }
                val taken = todayIntakes.count { it.status == "Taken" }
                val pendingVisits = consultations.count { it.followUpEpochDay != null && !it.isFollowUpCompleted }
                "💊 You have ${active.size} active medicine(s) ($taken/$expected daily doses logged today) and $pendingVisits upcoming doctor follow-up(s) in Doctors & Medicines."
            }
            "water" in q || "hydrat" in q || "drink" in q -> {
                if (hydration.remainingMl == 0) {
                    "🐳 Amazing job, $name! You've hit ${hydration.totalTodayMl} ml today (${hydration.streakDays}-day streak). Try herbal tea or infused water later this evening."
                } else {
                    "💧 You're at ${hydration.totalTodayMl}/${hydration.goalMl} ml (${(hydration.progressFraction * 100).toInt()}%). A quick +250 ml glass right now leaves just ${(hydration.remainingMl - 250).coerceAtLeast(0)} ml for the day!"
                }
            }
            "cycle" in q || "period" in q || "phase" in q -> {
                if (profile?.cycleTrackingEnabled != true) {
                    "🌸 Cycle tracking is currently hidden in your profile, and can be toggled on anytime for free in Settings."
                } else if (cycle?.currentCycleDay != null) {
                    "🌸 You're on Cycle Day ${cycle.currentCycleDay} (${cycle.currentPhase.label}). ${cycle.currentPhase.guidance}"
                } else {
                    "🌸 Log your first period start date in the Cycle tab and I'll tailor your phase tips 100% offline!"
                }
            }
            "yoga" in q || "workout" in q || "fitness" in q || "step" in q || "exercise" in q -> {
                val mins = exercises.sumOf { it.durationMinutes }
                val stepCount = steps?.stepCount ?: 0
                "🧘 Today's movement: $mins active minutes and $stepCount steps. A 15-minute restorative Sun Salutation or brisk walk fits great right now!"
            }
            "diet" in q || "food" in q || "meal" in q || "eat" in q || "nutrition" in q -> {
                val kcal = diet.sumOf { it.calories }
                val prot = diet.sumOf { it.proteinGrams }
                "🥗 You've logged $kcal kcal and ${prot}g protein across ${diet.size} entries. Pair colorful greens, steady protein, and water for sustained energy!"
            }
            "sleep" in q || "focus" in q || "screen" in q || "wellbeing" in q -> {
                val screen = wellbeing?.screenTimeMinutes ?: 0
                val focus = wellbeing?.focusSessionsCount ?: 0
                val lastSleep = sleep.firstOrNull()?.let {
                    String.format("%.1f hrs", (it.wakeEpochMs - it.bedtimeEpochMs) / 3_600_000f)
                } ?: "not logged yet"
                "🌙 Last sleep: $lastSleep. Today's screen time: $screen min with $focus focus sessions. Try a 20-minute screen-free wind-down tonight!"
            }
            else -> {
                "✨ Everything here runs 100% locally on your device! Ask me about your 💧 hydration, 💊 medicines & follow-ups, 🌸 cycle phase, 🧘 yoga & steps, 🥗 nutrition, or 🌙 sleep & digital wellbeing."
            }
        }
    }
}
