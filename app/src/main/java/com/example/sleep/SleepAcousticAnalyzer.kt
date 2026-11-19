package com.example.sleep

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class EstimatedSleepStage(val label: String, val emoji: String) {
    DEEP("Deep Sleep", "🌌"),
    LIGHT("Light Sleep", "🌙"),
    AWAKE("Awake / Restless", "🌤️")
}

data class AcousticFrameMetrics(
    val dbSpl: Double,
    val ambientFloorDb: Double,
    val zeroCrossingRate: Double,
    val lowBandEnergyRatio: Double,
    val isSnoreLikeFrame: Boolean,
    val snoreEventJustCompleted: Boolean
)

data class SleepSessionAnalysisReport(
    val bedtimeEpochMs: Long,
    val wakeEpochMs: Long,
    val durationMinutes: Int,
    val sleepQualityScore: Int,
    val qualityRating: Int,
    val snoreEventsCount: Int,
    val snoreDurationMinutes: Int,
    val peakSnoreDb: Double,
    val avgAmbientDb: Double,
    val restlessnessIndex: Int,
    val awakeInterruptionsCount: Int,
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val awakeMinutes: Int,
    val summaryNote: String
)

/**
 * Pure on-device digital signal processing (DSP) and sensor-fusion analyzer for overnight sleep
 * monitoring. Raw audio PCM buffers are processed transiently in memory and immediately discarded;
 * no raw audio is ever recorded or stored.
 *
 * Technique:
 * 1. Computes frame RMS -> estimated SPL dB alongside an adaptive ambient noise floor so steady
 *    fan/AC hum is filtered out automatically.
 * 2. Evaluates spectral energy concentration in the characteristic human snoring band
 *    (100-450 Hz) vs higher non-snore frequencies (1200-3800 Hz) via Goertzel DFT filters,
 *    combined with Zero-Crossing Rate (ZCR) to reject clicks, claps, and sibilant speech.
 * 3. Runs a temporal snore-episode state machine requiring realistic breathing burst durations
 *    (>= 0.45s, <= 5.0s) to count distinct snore events and accumulate snoring duration.
 * 4. Fuses 3-axis accelerometer linear acceleration variance into 1-minute sleep epochs to
 *    classify Deep, Light, and Awake/Restless intervals, count wake interruptions, and compute
 *    a 0-100 Sleep Quality Score.
 */
class SleepAcousticAnalyzer(
    private val sessionStartEpochMs: Long = System.currentTimeMillis(),
    private val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE_HZ = 16_000
        private const val GRAVITY_MS2 = 9.80665
        private const val MIN_SNORE_ABSOLUTE_DB = 45.0
        private const val MIN_SNORE_ABOVE_FLOOR_DB = 8.5
        private const val MIN_SNORE_LOW_BAND_RATIO = 0.56
        private const val MAX_SNORE_ZCR = 0.16
        private const val MIN_SNORE_BURST_MS = 450L
        private const val MAX_SNORE_BURST_MS = 5_500L
        private const val SNORE_GAP_TOLERANCE_MS = 400L

        private val SNORE_BAND_FREQS_HZ = doubleArrayOf(110.0, 180.0, 270.0, 390.0)
        private val HIGH_BAND_FREQS_HZ = doubleArrayOf(1250.0, 2200.0, 3400.0)

        /**
         * Goertzel power estimate at a specific frequency bin for a PCM buffer.
         */
        fun goertzelPower(samples: ShortArray, sampleRateHz: Int, targetFreqHz: Double): Double {
            if (samples.isEmpty() || sampleRateHz <= 0) return 0.0
            val n = samples.size
            val k = (0.5 + (n * targetFreqHz) / sampleRateHz.toDouble()).toInt()
            val omega = (2.0 * PI * k) / n.toDouble()
            val coeff = 2.0 * cos(omega)
            var sPrev = 0.0
            var sPrev2 = 0.0
            for (i in 0 until n) {
                val x = samples[i] / 32768.0
                val s = x + coeff * sPrev - sPrev2
                sPrev2 = sPrev
                sPrev = s
            }
            val power = sPrev2 * sPrev2 + sPrev * sPrev - coeff * sPrev * sPrev2
            return max(0.0, power) / (n.toDouble() * n.toDouble())
        }

        fun computeDbSpl(samples: ShortArray): Double {
            if (samples.isEmpty()) return 25.0
            var sumSq = 0.0
            for (s in samples) {
                val norm = s / 32768.0
                sumSq += norm * norm
            }
            val rms = sqrt(sumSq / samples.size.toDouble())
            if (rms <= 1e-6) return 24.0
            val dbFs = 20.0 * log10(rms) // -96..0 dBFS
            // Calibrate approximate microphone SPL (quiet room ~30 dB, snore ~55-75 dB)
            return (dbFs + 96.0).coerceIn(22.0, 105.0)
        }

        fun computeZeroCrossingRate(samples: ShortArray): Double {
            if (samples.size < 2) return 0.0
            var crossings = 0
            var prevSign = samples[0] >= 0
            for (i in 1 until samples.size) {
                val currSign = samples[i] >= 0
                if (currSign != prevSign) {
                    crossings++
                    prevSign = currSign
                }
            }
            return crossings.toDouble() / (samples.size - 1).toDouble()
        }
    }

    // Acoustic state
    private var ambientFloorDb: Double = 34.0
    private var ambientFloorInitialized: Boolean = false
    private var ambientDbSum: Double = 0.0
    private var ambientSampleCount: Long = 0L

    private var inSnoreBurst: Boolean = false
    private var burstStartMs: Long = 0L
    private var burstLastMatchMs: Long = 0L
    private var burstPeakDb: Double = 0.0

    var snoreEventsCount: Int = 0
        private set
    private var totalSnoreMs: Long = 0L
    var peakSnoreDb: Double = 0.0
        private set
    var currentDb: Double = 30.0
        private set
    var currentStage: EstimatedSleepStage = EstimatedSleepStage.LIGHT
        private set
    var lastEventLabel: String = "Calibrating room acoustics…"
        private set

    // Motion & 1-minute epoch state
    private var currentEpochMinuteIndex: Int = 0
    private var epochMotionSum: Double = 0.0
    private var epochMotionCount: Int = 0
    private var epochSnoreMs: Long = 0L
    private var epochMaxDb: Double = 30.0
    private var totalMotionSum: Double = 0.0
    private var totalMotionCount: Long = 0L

    private var deepMinutes: Int = 0
    private var lightMinutes: Int = 0
    private var awakeMinutes: Int = 0
    private var awakeInterruptions: Int = 0
    private var consecutiveAwakeMinutes: Int = 0
    private var hasEnteredSleep: Boolean = false
    private var lastFinalizedStage: EstimatedSleepStage = EstimatedSleepStage.AWAKE

    private val waveformRing = ArrayDeque<Float>(24)

    fun currentAmbientFloorDb(): Double = ambientFloorDb

    fun snoreDurationMinutes(): Int {
        if (totalSnoreMs <= 0L) return 0
        return ((totalSnoreMs + 29_999L) / 60_000L).toInt().coerceAtLeast(if (snoreEventsCount > 0) 1 else 0)
    }

    fun consecutiveAwakeMinutesCount(): Int = consecutiveAwakeMinutes

    fun recentWaveformNormalized(): List<Float> = waveformRing.toList()

    fun analyzePcmFrame(
        samples: ShortArray,
        timestampMs: Long = System.currentTimeMillis()
    ): AcousticFrameMetrics {
        advanceEpochIfNeeded(timestampMs)

        val frameDurationMs = max(50L, (samples.size * 1000L) / sampleRateHz.coerceAtLeast(8_000))
        val db = computeDbSpl(samples)
        val zcr = computeZeroCrossingRate(samples)

        var lowPower = 0.0
        for (f in SNORE_BAND_FREQS_HZ) {
            lowPower += goertzelPower(samples, sampleRateHz, f)
        }
        var highPower = 0.0
        for (f in HIGH_BAND_FREQS_HZ) {
            highPower += goertzelPower(samples, sampleRateHz, f)
        }
        val lowBandRatio = if (lowPower + highPower > 1e-12) {
            lowPower / (lowPower + highPower)
        } else {
            0.0
        }

        currentDb = db
        if (db > epochMaxDb) epochMaxDb = db

        val normalizedLevel = ((db - 24.0) / 56.0).toFloat().coerceIn(0.05f, 1f)
        if (waveformRing.size >= 20) waveformRing.removeFirst()
        waveformRing.addLast(normalizedLevel)

        if (!ambientFloorInitialized) {
            ambientFloorDb = db.coerceIn(24.0, 58.0)
            ambientFloorInitialized = true
        }

        val isElevatedAboveFloor = db >= max(MIN_SNORE_ABSOLUTE_DB, ambientFloorDb + MIN_SNORE_ABOVE_FLOOR_DB)
        val isSnoreLike = isElevatedAboveFloor &&
            lowBandRatio >= MIN_SNORE_LOW_BAND_RATIO &&
            zcr <= MAX_SNORE_ZCR

        // Adapt ambient noise floor slowly when NOT actively snoring (so steady fan/AC hum becomes baseline)
        if (!isSnoreLike) {
            val alpha = if (db < ambientFloorDb) 0.14 else 0.03
            ambientFloorDb = (ambientFloorDb * (1.0 - alpha) + db * alpha).coerceIn(22.0, 68.0)
            ambientDbSum += db
            ambientSampleCount++
        }

        var snoreCompleted = false
        if (isSnoreLike) {
            if (!inSnoreBurst) {
                inSnoreBurst = true
                burstStartMs = timestampMs
                burstLastMatchMs = timestampMs + frameDurationMs
                burstPeakDb = db
            } else {
                burstLastMatchMs = timestampMs + frameDurationMs
                if (db > burstPeakDb) burstPeakDb = db
                // If an abnormally long sustained hum lasts > MAX_SNORE_BURST_MS, treat as steady noise, not snore
                if ((burstLastMatchMs - burstStartMs) > MAX_SNORE_BURST_MS) {
                    inSnoreBurst = false
                    ambientFloorDb = (ambientFloorDb * 0.7 + db * 0.3).coerceIn(24.0, 72.0)
                }
            }
        } else if (inSnoreBurst) {
            val gapMs = timestampMs - burstLastMatchMs
            if (gapMs >= SNORE_GAP_TOLERANCE_MS) {
                val burstDurationMs = (burstLastMatchMs - burstStartMs).coerceAtLeast(0L)
                if (burstDurationMs in MIN_SNORE_BURST_MS..MAX_SNORE_BURST_MS) {
                    snoreEventsCount++
                    totalSnoreMs += burstDurationMs
                    epochSnoreMs += burstDurationMs
                    if (burstPeakDb > peakSnoreDb) peakSnoreDb = burstPeakDb
                    snoreCompleted = true
                    lastEventLabel = String.format(
                        Locale.US,
                        "Snore detected • %.0f dB (%.1fs)",
                        burstPeakDb,
                        burstDurationMs / 1000.0
                    )
                }
                inSnoreBurst = false
            }
        }

        updateLiveStageEstimate()

        return AcousticFrameMetrics(
            dbSpl = db,
            ambientFloorDb = ambientFloorDb,
            zeroCrossingRate = zcr,
            lowBandEnergyRatio = lowBandRatio,
            isSnoreLikeFrame = isSnoreLike,
            snoreEventJustCompleted = snoreCompleted
        )
    }

    fun recordAccelerometerSample(
        ax: Float,
        ay: Float,
        az: Float,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        advanceEpochIfNeeded(timestampMs)
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble())
        val linearDelta = abs(magnitude - GRAVITY_MS2)
        epochMotionSum += linearDelta
        epochMotionCount++
        totalMotionSum += linearDelta
        totalMotionCount++
        if (linearDelta >= 1.2) {
            lastEventLabel = "Bed movement detected"
        }
        updateLiveStageEstimate()
    }

    fun computeRestlessnessIndex(): Int {
        if (totalMotionCount == 0L) return 12
        val avgLinearMotion = totalMotionSum / totalMotionCount.toDouble()
        // 0.05 m/s^2 = very still (~6), 0.35 = moderate (~40), 0.90+ = very restless (~95)
        return (avgLinearMotion * 105.0).roundToInt().coerceIn(4, 100)
    }

    fun computeLiveQualityScore(nowMs: Long = System.currentTimeMillis()): Int {
        val elapsedMin = ((nowMs - sessionStartEpochMs).coerceAtLeast(60_000L) / 60_000L).toInt()
        val restlessness = computeRestlessnessIndex()
        val snoreMins = snoreDurationMinutes()
        val totalClassified = (deepMinutes + lightMinutes + awakeMinutes).coerceAtLeast(1)
        val deepRatio = deepMinutes.toDouble() / totalClassified.toDouble()
        val awakeRatio = awakeMinutes.toDouble() / totalClassified.toDouble()

        var score = 88.0
        // Reward deep sleep proportion once epochs exist
        if (deepMinutes + lightMinutes + awakeMinutes >= 3) {
            score += ((deepRatio - 0.20) * 28.0).coerceIn(-10.0, 12.0)
            score -= (awakeRatio * 42.0).coerceIn(0.0, 30.0)
        }
        // Restlessness penalty
        score -= (max(0, restlessness - 18) * 0.35).coerceIn(0.0, 22.0)
        // Snoring burden penalty
        val snoreRatio = snoreMins.toDouble() / elapsedMin.coerceAtLeast(1).toDouble()
        score -= (snoreRatio * 45.0).coerceIn(0.0, 24.0)
        if (peakSnoreDb >= 68.0) score -= 5.0
        // Wake interruptions penalty
        score -= (awakeInterruptions * 3.5).coerceIn(0.0, 18.0)
        // Duration adequacy adjustment (only penalize very short overnight sessions once >= 20 min)
        if (elapsedMin in 20..299) {
            score -= ((300 - elapsedMin) * 0.05).coerceIn(0.0, 12.0)
        }
        return score.roundToInt().coerceIn(15, 100)
    }

    fun buildSessionReport(endTimeMs: Long = System.currentTimeMillis()): SleepSessionAnalysisReport {
        // Flush any in-progress snore burst cleanly
        if (inSnoreBurst) {
            val burstDurationMs = (burstLastMatchMs - burstStartMs).coerceAtLeast(0L)
            if (burstDurationMs in MIN_SNORE_BURST_MS..MAX_SNORE_BURST_MS) {
                snoreEventsCount++
                totalSnoreMs += burstDurationMs
                epochSnoreMs += burstDurationMs
                if (burstPeakDb > peakSnoreDb) peakSnoreDb = burstPeakDb
            }
            inSnoreBurst = false
        }

        // Flush final partial epoch
        finalizeCurrentEpoch()

        val safeEndMs = max(endTimeMs, sessionStartEpochMs + 60_000L)
        val totalMinutes = ((safeEndMs - sessionStartEpochMs) / 60_000L).toInt().coerceAtLeast(1)

        // Scale classified epoch minutes proportionally if sensor callbacks were sparse
        val classifiedTotal = deepMinutes + lightMinutes + awakeMinutes
        val finalDeep: Int
        val finalLight: Int
        val finalAwake: Int
        if (classifiedTotal <= 0) {
            finalAwake = 0
            finalDeep = (totalMinutes * 0.25).roundToInt()
            finalLight = (totalMinutes - finalDeep).coerceAtLeast(0)
        } else if (classifiedTotal != totalMinutes) {
            val scale = totalMinutes.toDouble() / classifiedTotal.toDouble()
            finalDeep = (deepMinutes * scale).roundToInt().coerceIn(0, totalMinutes)
            finalAwake = (awakeMinutes * scale).roundToInt().coerceIn(0, totalMinutes - finalDeep)
            finalLight = (totalMinutes - finalDeep - finalAwake).coerceAtLeast(0)
        } else {
            finalDeep = deepMinutes
            finalLight = lightMinutes
            finalAwake = awakeMinutes
        }

        val qualityScore = computeLiveQualityScore(safeEndMs)
        val starRating = when {
            qualityScore >= 86 -> 5
            qualityScore >= 72 -> 4
            qualityScore >= 56 -> 3
            qualityScore >= 40 -> 2
            else -> 1
        }
        val restlessness = computeRestlessnessIndex()
        val snoreMins = snoreDurationMinutes()
        val avgAmbient = if (ambientSampleCount > 0L) {
            ambientDbSum / ambientSampleCount.toDouble()
        } else {
            ambientFloorDb
        }

        val snoreText = if (snoreEventsCount == 0) {
            "No snoring detected"
        } else {
            String.format(
                Locale.US,
                "%d snore event(s) (%dm, peak %.0f dB)",
                snoreEventsCount,
                snoreMins,
                peakSnoreDb
            )
        }
        val summary = String.format(
            Locale.US,
            "Auto Sensor • Quality %d/100 • %s • Deep %dm, Light %dm • Restlessness %d/100 • Avg room %.0f dB",
            qualityScore,
            snoreText,
            finalDeep,
            finalLight,
            restlessness,
            avgAmbient
        )

        return SleepSessionAnalysisReport(
            bedtimeEpochMs = sessionStartEpochMs,
            wakeEpochMs = safeEndMs,
            durationMinutes = totalMinutes,
            sleepQualityScore = qualityScore,
            qualityRating = starRating,
            snoreEventsCount = snoreEventsCount,
            snoreDurationMinutes = snoreMins,
            peakSnoreDb = peakSnoreDb,
            avgAmbientDb = avgAmbient,
            restlessnessIndex = restlessness,
            awakeInterruptionsCount = awakeInterruptions,
            deepSleepMinutes = finalDeep,
            lightSleepMinutes = finalLight,
            awakeMinutes = finalAwake,
            summaryNote = summary
        )
    }

    private fun advanceEpochIfNeeded(timestampMs: Long) {
        val minuteIdx = ((timestampMs - sessionStartEpochMs).coerceAtLeast(0L) / 60_000L).toInt()
        if (minuteIdx > currentEpochMinuteIndex) {
            val gap = (minuteIdx - currentEpochMinuteIndex).coerceAtMost(30)
            for (step in 0 until gap) {
                finalizeCurrentEpoch()
                currentEpochMinuteIndex++
            }
            if (minuteIdx > currentEpochMinuteIndex) {
                currentEpochMinuteIndex = minuteIdx
            }
        }
    }

    private fun finalizeCurrentEpoch() {
        val avgMotion = if (epochMotionCount > 0) epochMotionSum / epochMotionCount.toDouble() else 0.08
        val stage = classifyEpoch(avgMotion, epochSnoreMs, epochMaxDb)
        when (stage) {
            EstimatedSleepStage.DEEP -> {
                deepMinutes++
                hasEnteredSleep = true
                consecutiveAwakeMinutes = 0
            }
            EstimatedSleepStage.LIGHT -> {
                lightMinutes++
                hasEnteredSleep = true
                consecutiveAwakeMinutes = 0
            }
            EstimatedSleepStage.AWAKE -> {
                awakeMinutes++
                consecutiveAwakeMinutes++
                if (hasEnteredSleep && lastFinalizedStage != EstimatedSleepStage.AWAKE) {
                    awakeInterruptions++
                }
            }
        }
        lastFinalizedStage = stage
        currentStage = stage

        epochMotionSum = 0.0
        epochMotionCount = 0
        epochSnoreMs = 0L
        epochMaxDb = ambientFloorDb
    }

    private fun updateLiveStageEstimate() {
        val avgMotion = if (epochMotionCount > 0) epochMotionSum / epochMotionCount.toDouble() else 0.08
        currentStage = classifyEpoch(avgMotion, epochSnoreMs, epochMaxDb)
    }

    private fun classifyEpoch(
        avgMotion: Double,
        snoreMs: Long,
        maxDb: Double
    ): EstimatedSleepStage {
        return when {
            avgMotion >= 0.85 || (avgMotion >= 0.45 && maxDb >= 72.0 && snoreMs == 0L) ->
                EstimatedSleepStage.AWAKE
            avgMotion >= 0.22 || snoreMs >= 8_000L ->
                EstimatedSleepStage.LIGHT
            else ->
                EstimatedSleepStage.DEEP
        }
    }
}
