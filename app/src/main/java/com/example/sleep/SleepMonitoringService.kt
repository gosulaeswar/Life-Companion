package com.example.sleep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.AllModulesRepository
import com.example.security.LocalPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Play-policy compliant Foreground Service (`microphone`) that performs real-time,
 * privacy-preserving on-device sleep quality, snoring, and bed-restlessness monitoring.
 *
 * - Raw audio PCM frames are analyzed transiently in memory via [SleepAcousticAnalyzer] and
 *   discarded immediately (zero audio recordings are written to disk or uploaded).
 * - Automatically logs completed sleep sessions with quality score, snore events, peak dB,
 *   restlessness index, and Deep/Light/Awake duration into Room `sleep_records`.
 */
class SleepMonitoringService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "com.example.sleep.ACTION_START_MONITOR"
        const val ACTION_STOP_AND_SAVE = "com.example.sleep.ACTION_STOP_AND_SAVE"
        const val ACTION_CANCEL_WITHOUT_SAVE = "com.example.sleep.ACTION_CANCEL_WITHOUT_SAVE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_WAKE_HOUR = "extra_wake_hour"
        const val EXTRA_WAKE_MINUTE = "extra_wake_minute"

        private const val CHANNEL_ID = "lifeweave_sleep_monitor"
        private const val NOTIFICATION_ID = 4401
        private const val MAX_WAKELOCK_MS = 12L * 3_600_000L

        fun hasMicrophonePermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        fun startMonitoring(
            context: Context,
            profileId: Long,
            wakeHour: Int = 7,
            wakeMinute: Int = 0
        ): Boolean {
            if (!hasMicrophonePermission(context)) {
                SleepMonitorStateStore.update {
                    it.copy(errorMessage = "Microphone permission is required for acoustic snoring & sleep monitoring.")
                }
                return false
            }
            val intent = Intent(context, SleepMonitoringService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_WAKE_HOUR, wakeHour)
                putExtra(EXTRA_WAKE_MINUTE, wakeMinute)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure { err ->
                SleepMonitorStateStore.update {
                    it.copy(
                        isMonitoring = false,
                        errorMessage = err.localizedMessage ?: "Unable to start sleep monitor service."
                    )
                }
            }.getOrDefault(false)
        }

        fun stopAndSave(context: Context) {
            val intent = Intent(context, SleepMonitoringService::class.java).apply {
                action = ACTION_STOP_AND_SAVE
            }
            runCatching { context.startService(intent) }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioCaptureJob: Job? = null
    private var uiTickerJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var analyzer: SleepAcousticAnalyzer? = null

    @Volatile
    private var activeProfileId: Long = 0L

    @Volatile
    private var sessionStartMs: Long = 0L

    @Volatile
    private var configuredWakeHour: Int = 7

    @Volatile
    private var configuredWakeMinute: Int = 0

    @Volatile
    private var sessionSaved: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_AND_SAVE -> {
                finalizeAndStop(saveToDatabase = true, reason = "Saved sleep session")
                return START_NOT_STICKY
            }

            ACTION_CANCEL_WITHOUT_SAVE -> {
                finalizeAndStop(saveToDatabase = false, reason = "Monitoring cancelled")
                return START_NOT_STICKY
            }

            ACTION_START, null -> {
                if (!hasMicrophonePermission(this)) {
                    SleepMonitorStateStore.update {
                        it.copy(
                            isMonitoring = false,
                            errorMessage = "Grant Microphone permission to monitor snoring & sleep quality."
                        )
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (SleepMonitorStateStore.state.value.isMonitoring && audioCaptureJob?.isActive == true) {
                    return START_STICKY
                }
                activeProfileId = intent?.getLongExtra(EXTRA_PROFILE_ID, 0L) ?: 0L
                configuredWakeHour = intent?.getIntExtra(EXTRA_WAKE_HOUR, 7) ?: 7
                configuredWakeMinute = intent?.getIntExtra(EXTRA_WAKE_MINUTE, 0) ?: 0
                startSession()
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun startSession() {
        ensureNotificationChannel()
        val initialNotification = buildNotification(
            contentText = "Listening locally for snoring & movement • Zero audio stored"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        sessionSaved = false
        sessionStartMs = System.currentTimeMillis()
        val sessionAnalyzer = SleepAcousticAnalyzer(
            sessionStartEpochMs = sessionStartMs,
            sampleRateHz = SleepAcousticAnalyzer.DEFAULT_SAMPLE_RATE_HZ
        )
        analyzer = sessionAnalyzer

        SleepMonitorStateStore.update {
            SleepMonitorLiveState(
                isMonitoring = true,
                profileId = activeProfileId,
                startedAtEpochMs = sessionStartMs,
                elapsedMinutes = 0,
                currentDb = 30.0,
                ambientFloorDb = 32.0,
                snoreEventsCount = 0,
                snoreDurationMinutes = 0,
                peakSnoreDb = 0.0,
                restlessnessIndex = 8,
                estimatedStage = EstimatedSleepStage.LIGHT,
                liveQualityScore = 88,
                lastEventLabel = "Monitoring active — place phone near bedside",
                recentDbWaveform = List(16) { 0.12f },
                lastSavedBanner = null,
                errorMessage = null
            )
        }

        acquirePartialWakeLock()
        registerAccelerometer()
        startAudioAnalysisLoop(sessionAnalyzer)
        startLiveStateTicker(sessionAnalyzer)
    }

    @SuppressLint("MissingPermission")
    private fun startAudioAnalysisLoop(sessionAnalyzer: SleepAcousticAnalyzer) {
        audioCaptureJob?.cancel()
        audioCaptureJob = serviceScope.launch {
            if (!hasMicrophonePermission(this@SleepMonitoringService)) {
                SleepMonitorStateStore.update {
                    it.copy(errorMessage = "Microphone permission revoked.")
                }
                finalizeAndStop(saveToDatabase = false, reason = "Permission revoked")
                return@launch
            }

            val sampleRate = SleepAcousticAnalyzer.DEFAULT_SAMPLE_RATE_HZ
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val frameShorts = sampleRate / 4 // 250ms analysis window (4000 shorts)
            val bufferBytes = maxOf(minBufferBytes * 2, frameShorts * 2 * 2)

            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferBytes
                )
            }.getOrNull() ?: runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferBytes
                )
            }.getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { recorder?.release() }
                SleepMonitorStateStore.update {
                    it.copy(
                        errorMessage = "Microphone is busy or unavailable on this device. Motion-only tracking active."
                    )
                }
                return@launch
            }

            audioRecord = recorder
            val pcmBuffer = ShortArray(frameShorts)
            try {
                recorder.startRecording()
                while (isActive) {
                    val readCount = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                    if (readCount > 0) {
                        val slice = if (readCount == pcmBuffer.size) {
                            pcmBuffer
                        } else {
                            pcmBuffer.copyOf(readCount)
                        }
                        sessionAnalyzer.analyzePcmFrame(slice, System.currentTimeMillis())
                    } else if (readCount < 0) {
                        delay(250L)
                    }
                }
            } catch (_: Exception) {
                // Handled gracefully on stop/release
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                if (audioRecord === recorder) {
                    audioRecord = null
                }
            }
        }
    }

    private fun startLiveStateTicker(sessionAnalyzer: SleepAcousticAnalyzer) {
        uiTickerJob?.cancel()
        uiTickerJob = serviceScope.launch {
            var tickCount = 0
            while (isActive) {
                delay(1_000L)
                tickCount++
                val now = System.currentTimeMillis()
                val elapsedMin = ((now - sessionStartMs).coerceAtLeast(0L) / 60_000L).toInt()
                val score = sessionAnalyzer.computeLiveQualityScore(now)
                val snoreEvents = sessionAnalyzer.snoreEventsCount
                val snoreMins = sessionAnalyzer.snoreDurationMinutes()
                val restlessness = sessionAnalyzer.computeRestlessnessIndex()

                SleepMonitorStateStore.update { prev ->
                    prev.copy(
                        isMonitoring = true,
                        elapsedMinutes = elapsedMin,
                        currentDb = sessionAnalyzer.currentDb,
                        ambientFloorDb = sessionAnalyzer.currentAmbientFloorDb(),
                        snoreEventsCount = snoreEvents,
                        snoreDurationMinutes = snoreMins,
                        peakSnoreDb = sessionAnalyzer.peakSnoreDb,
                        restlessnessIndex = restlessness,
                        estimatedStage = sessionAnalyzer.currentStage,
                        liveQualityScore = score,
                        lastEventLabel = sessionAnalyzer.lastEventLabel,
                        recentDbWaveform = sessionAnalyzer.recentWaveformNormalized()
                    )
                }

                // Refresh foreground notification every 15 seconds to keep battery minimal
                if (tickCount % 15 == 0) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(
                        NOTIFICATION_ID,
                        buildNotification(
                            contentText = String.format(
                                Locale.US,
                                "%dh %02dm • Quality %d/100 • %d snore(s) • %.0f dB",
                                elapsedMin / 60,
                                elapsedMin % 60,
                                score,
                                snoreEvents,
                                sessionAnalyzer.currentDb
                            )
                        )
                    )
                }

                // Automatic morning wake-up detection: if session >= 30m, past wake time, and awake >= 12m
                if (elapsedMin >= 30 && sessionAnalyzer.consecutiveAwakeMinutesCount() >= 12) {
                    val cal = Calendar.getInstance().apply { timeInMillis = now }
                    val nowMinOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    val wakeMinOfDay = configuredWakeHour * 60 + configuredWakeMinute
                    if (nowMinOfDay >= wakeMinOfDay) {
                        finalizeAndStop(
                            saveToDatabase = true,
                            reason = "Morning wake-up detected — auto-logged sleep session"
                        )
                        break
                    }
                }
            }
        }
    }

    private fun registerAccelerometer() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (event.values.size < 3) return
        analyzer?.recordAccelerometerSample(
            ax = event.values[0],
            ay = event.values[1],
            az = event.values[2],
            timestampMs = System.currentTimeMillis()
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun finalizeAndStop(saveToDatabase: Boolean, reason: String) {
        if (sessionSaved) {
            stopSelf()
            return
        }
        sessionSaved = true
        audioCaptureJob?.cancel()
        uiTickerJob?.cancel()
        unregisterSensorsAndAudio()

        val report = analyzer?.buildSessionReport(System.currentTimeMillis())
        if (saveToDatabase && report != null) {
            serviceScope.launch {
                val savedSummary = runCatching {
                    val db = AppDatabase.getInstance(applicationContext)
                    val targetProfileId = if (activeProfileId > 0L) {
                        activeProfileId
                    } else {
                        val prefsId = LocalPreferencesManager(applicationContext).preferencesFlow.first().activeProfileId
                        if (prefsId > 0L) prefsId else (db.userProfileDao().getActiveProfileSnapshot()?.id ?: 0L)
                    }
                    if (targetProfileId > 0L) {
                        AllModulesRepository(db).logAutomatedSleepSession(
                            profileId = targetProfileId,
                            bedtimeEpochMs = report.bedtimeEpochMs,
                            wakeEpochMs = report.wakeEpochMs,
                            qualityRating = report.qualityRating,
                            sleepQualityScore = report.sleepQualityScore,
                            snoreEventsCount = report.snoreEventsCount,
                            snoreDurationMinutes = report.snoreDurationMinutes,
                            peakSnoreDb = report.peakSnoreDb,
                            avgAmbientDb = report.avgAmbientDb,
                            restlessnessIndex = report.restlessnessIndex,
                            awakeInterruptionsCount = report.awakeInterruptionsCount,
                            deepSleepMinutes = report.deepSleepMinutes,
                            lightSleepMinutes = report.lightSleepMinutes,
                            notes = report.summaryNote
                        )
                        "✅ $reason • Quality ${report.sleepQualityScore}/100 • ${report.snoreEventsCount} snore event(s) logged to Sleep Metrics."
                    } else {
                        "No active profile found to log sleep metrics."
                    }
                }.getOrElse { err ->
                    "Could not save sleep session: ${err.localizedMessage ?: "storage error"}"
                }

                SleepMonitorStateStore.update {
                    it.copy(
                        isMonitoring = false,
                        lastEventLabel = reason,
                        lastSavedBanner = savedSummary
                    )
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            SleepMonitorStateStore.update {
                it.copy(
                    isMonitoring = false,
                    lastEventLabel = reason
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquirePartialWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LifeWeave::SleepMonitorWakeLock")
            lock.setReferenceCounted(false)
            lock.acquire(MAX_WAKELOCK_MS)
            wakeLock = lock
        }
    }

    private fun unregisterSensorsAndAudio() {
        runCatching { sensorManager?.unregisterListener(this) }
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Sleep & Snoring Monitor",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Ongoing indicator while LifeWeave monitors overnight sleep quality and snoring on-device."
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            4401,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SleepMonitoringService::class.java).apply {
            action = ACTION_STOP_AND_SAVE
        }
        val stopPi = PendingIntent.getService(
            this,
            4402,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("😴 LifeWeave Sleep & Snoring Monitor")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPi)
            .addAction(0, "Stop & Save Sleep", stopPi)
            .build()
    }

    override fun onDestroy() {
        unregisterSensorsAndAudio()
        serviceScope.cancel()
        super.onDestroy()
    }
}
