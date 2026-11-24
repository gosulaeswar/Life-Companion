package com.example.wellbeing

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.domain.DateEpochUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AppUsageMetric(
    val packageName: String,
    val appName: String,
    val categoryLabel: String,
    val minutesUsed: Int,
    val launchCount: Int = 1
) {
    fun isLongUsed(dailyCapMinutes: Int): Boolean {
        val capThreshold = (dailyCapMinutes.coerceAtLeast(30) * 0.30f).roundToInt().coerceIn(20, 45)
        return minutesUsed >= capThreshold
    }
}

enum class OveruseSeverity {
    BALANCED,
    APPROACHING,
    EXCEEDED,
    CRITICAL
}

data class WellbeingOveruseAlert(
    val severity: OveruseSeverity,
    val stageKey: String,
    val title: String,
    val message: String,
    val usageRatio: Float,
    val remainingMinutes: Int,
    val recommendation: String,
    val flaggedApps: List<AppUsageMetric> = emptyList()
)

data class DeviceUsageSnapshot(
    val dayKey: String,
    val totalScreenMinutes: Int,
    val unlockCount: Int,
    val appLaunchCount: Int,
    val nightScreenMinutes: Int,
    val longestSessionMinutes: Int,
    val topApps: List<AppUsageMetric>,
    val categoryMinutes: Map<String, Int>,
    val eventsScannedCount: Int = 0,
    val syncDiagnostic: String = "",
    val syncedAtEpochMs: Long = System.currentTimeMillis()
)

object WellbeingMetricsCalculator {

    fun encodeTopApps(apps: List<AppUsageMetric>): String {
        if (apps.isEmpty()) return ""
        return runCatching {
            val arr = JSONArray()
            apps.forEach { app ->
                arr.put(
                    JSONObject().apply {
                        put("pkg", app.packageName)
                        put("name", app.appName)
                        put("cat", app.categoryLabel)
                        put("mins", app.minutesUsed)
                        put("launches", app.launchCount)
                    }
                )
            }
            arr.toString()
        }.getOrDefault("")
    }

    fun decodeTopApps(rawJson: String): List<AppUsageMetric> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        AppUsageMetric(
                            packageName = obj.optString("pkg", ""),
                            appName = obj.optString("name", "App"),
                            categoryLabel = obj.optString("cat", "Other"),
                            minutesUsed = obj.optInt("mins", 0),
                            launchCount = obj.optInt("launches", 1)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun categoryBreakdown(apps: List<AppUsageMetric>): Map<String, Int> {
        if (apps.isEmpty()) return emptyMap()
        return apps.groupBy { it.categoryLabel }
            .mapValues { (_, items) -> items.sumOf { it.minutesUsed } }
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
    }

    fun longUsedApps(
        apps: List<AppUsageMetric>,
        dailyCapMinutes: Int = 120
    ): List<AppUsageMetric> {
        return apps.filter { it.isLongUsed(dailyCapMinutes) }
            .sortedByDescending { it.minutesUsed }
    }

    fun computeMindfulBalanceScore(record: DigitalWellbeingRecordEntity?): Int {
        if (record == null) return 100
        val cap = record.mindfulGoalMinutes.coerceAtLeast(30).toFloat()
        val ratio = record.screenTimeMinutes.toFloat() / cap
        var score = 100f
        score -= when {
            ratio <= 0.7f -> ratio * 12f
            ratio <= 1.0f -> 8.4f + (ratio - 0.7f) * 65f
            else -> 28f + min(55f, (ratio - 1.0f) * 60f)
        }
        if (record.nightScreenMinutes > 15) {
            score -= min(15f, (record.nightScreenMinutes - 15) * 0.35f)
        }
        if (record.unlockCount > 45) {
            score -= min(12f, (record.unlockCount - 45) * 0.2f)
        }
        if (record.longestSessionMinutes >= 45) {
            score -= min(10f, (record.longestSessionMinutes - 40) * 0.25f)
        }
        score += min(15f, record.focusSessionsCount * 5f)
        return score.roundToInt().coerceIn(5, 100)
    }

    fun evaluateOveruse(
        record: DigitalWellbeingRecordEntity?,
        warningPercent: Int = 80,
        topApps: List<AppUsageMetric> = emptyList()
    ): WellbeingOveruseAlert {
        val screenMins = record?.screenTimeMinutes ?: 0
        val capMins = (record?.mindfulGoalMinutes ?: 120).coerceAtLeast(30)
        val safeWarnPct = warningPercent.coerceIn(50, 95)
        val ratio = screenMins.toFloat() / capMins.toFloat()
        val remaining = (capMins - screenMins).coerceAtLeast(0)
        val overBy = (screenMins - capMins).coerceAtLeast(0)
        val longestSession = record?.longestSessionMinutes ?: 0
        val nightMins = record?.nightScreenMinutes ?: 0

        val heavyApps = longUsedApps(topApps, capMins)
        val dominantApp = heavyApps.firstOrNull()
            ?: topApps.maxByOrNull { it.minutesUsed }
            ?: record?.takeIf { it.topAppName.isNotBlank() && it.topAppMinutes > 0 }?.let {
                AppUsageMetric("", it.topAppName, "Top App", it.topAppMinutes)
            }
        val dominantNote = dominantApp?.let {
            " Long-used app: ${it.appName} (${it.minutesUsed}m)."
        }.orEmpty()

        return when {
            ratio >= 1.5f -> WellbeingOveruseAlert(
                severity = OveruseSeverity.CRITICAL,
                stageKey = "critical_150",
                title = "Heavy Screen Overuse (${(ratio * 100).roundToInt()}% of cap)",
                message = "Logged ${screenMins}m today (${overBy}m over your ${capMins}m limit).$dominantNote",
                usageRatio = ratio,
                remainingMinutes = 0,
                recommendation = "Put your phone down for a 20-minute screen-free walk or hydrate.",
                flaggedApps = heavyApps
            )

            ratio >= 1.0f -> WellbeingOveruseAlert(
                severity = OveruseSeverity.EXCEEDED,
                stageKey = "exceeded_100",
                title = "Daily Screen Limit Exceeded (+${overBy}m)",
                message = "Screen time reached ${screenMins}m against your ${capMins}m cap.$dominantNote",
                usageRatio = ratio,
                remainingMinutes = 0,
                recommendation = "Start a Deep Focus block or pause distracting apps now.",
                flaggedApps = heavyApps
            )

            dominantApp != null && dominantApp.minutesUsed >= 45 -> WellbeingOveruseAlert(
                severity = OveruseSeverity.EXCEEDED,
                stageKey = "long_app_${dominantApp.appName.lowercase(Locale.US).take(16)}",
                title = "Long App Session: ${dominantApp.appName} (${dominantApp.minutesUsed}m)",
                message = "You've spent ${dominantApp.minutesUsed}m in ${dominantApp.appName} alone today ($screenMins/${capMins}m total).",
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Close ${dominantApp.appName} for 15 minutes and give your eyes a rest.",
                flaggedApps = heavyApps
            )

            longestSession >= 60 -> WellbeingOveruseAlert(
                severity = OveruseSeverity.EXCEEDED,
                stageKey = "binge_60",
                title = "Nonstop ${longestSession}m Screen Session",
                message = "You've had a continuous ${longestSession}-minute screen stretch without a break.$dominantNote",
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Follow the 20-20-20 rule: rest your eyes for 20 seconds and stretch.",
                flaggedApps = heavyApps
            )

            (ratio * 100f) >= safeWarnPct -> WellbeingOveruseAlert(
                severity = OveruseSeverity.APPROACHING,
                stageKey = "approaching_$safeWarnPct",
                title = "Approaching Screen Limit (${remaining}m left)",
                message = "${screenMins}m of ${capMins}m used (${(ratio * 100).roundToInt()}%).$dominantNote",
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Wrap up active tasks and pause social/video scrolling.",
                flaggedApps = heavyApps
            )

            nightMins >= 30 -> WellbeingOveruseAlert(
                severity = OveruseSeverity.APPROACHING,
                stageKey = "night_use",
                title = "Late-Night Screen Use (${nightMins}m)",
                message = "${nightMins}m used during wind-down hours (10 PM–6 AM), which can delay deep sleep.",
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Switch to Wind-Down mode and dim blue light before bed.",
                flaggedApps = heavyApps
            )

            else -> WellbeingOveruseAlert(
                severity = OveruseSeverity.BALANCED,
                stageKey = "balanced",
                title = "Healthy Screen Balance",
                message = if (screenMins == 0) {
                    "No excessive screen usage detected today. Grant Usage Access to sync automatically."
                } else {
                    "${screenMins}m used today with ${remaining}m remaining in your ${capMins}m budget."
                },
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Great pacing—keep taking short visual breaks every 30 minutes.",
                flaggedApps = heavyApps
            )
        }
    }
}

class DeviceWellbeingManager(private val context: Context) {

    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun createUsageAccessSettingsIntent(includePackageUri: Boolean = true): Intent {
        if (includePackageUri) {
            val packageSpecific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (packageSpecific.resolveActivity(context.packageManager) != null) {
                return packageSpecific
            }
        }
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun fetchDayUsageSnapshot(
        daysAgo: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): Result<DeviceUsageSnapshot> {
        if (!hasUsageAccessPermission()) {
            return Result.failure(
                SecurityException("Usage Access permission is required to read Android Digital Wellbeing metrics.")
            )
        }
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return Result.failure(IllegalStateException("UsageStatsManager is unavailable on this device."))

        val (startMs, endMs, dayKey) = dayBounds(daysAgo, nowMs)
        val ignoredPackages = resolveIgnoredSystemPackages()

        return runCatching {
            // Query 4h before midnight so apps opened before 00:00 are tracked accurately
            val queryStartMs = startMs - 4 * 3_600_000L
            val events = usageManager.queryEvents(queryStartMs, endMs)

            // pkg -> (componentKey -> startTimestampMs)
            val activeComponents = mutableMapOf<String, MutableMap<String, Long>>()
            val appIntervals = mutableMapOf<String, MutableList<Pair<Long, Long>>>()
            val appLaunches = mutableMapOf<String, Int>()

            var unlockCount = 0
            var totalLaunchCount = 0
            var sessionStartMs: Long? = null
            var lastActiveEndMs = startMs
            var longestSessionMs = 0L
            var eventsScanned = 0

            fun closeSlice(pkg: String, rawStart: Long, rawEnd: Long) {
                if (pkg in ignoredPackages) return
                val clampedStart = max(rawStart, startMs)
                val clampedEnd = min(rawEnd, endMs)
                if (clampedEnd > clampedStart) {
                    val slice = min(clampedEnd - clampedStart, 6 * 3_600_000L)
                    appIntervals.getOrPut(pkg) { mutableListOf() }
                        .add(clampedStart to (clampedStart + slice))
                }
            }

            fun closeAllActive(atTs: Long) {
                activeComponents.entries.toList().forEach { (openPkg, components) ->
                    components.values.forEach { openedAt ->
                        closeSlice(openPkg, openedAt, atTs)
                    }
                    activeComponents.remove(openPkg)
                }
            }

            val event = UsageEvents.Event()
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                eventsScanned++
                val pkg = event.packageName ?: continue
                val componentKey = event.className?.takeIf { it.isNotBlank() } ?: "_default_"
                val ts = event.timeStamp
                val inDay = ts >= startMs

                when (event.eventType) {
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        if (inDay) unlockCount++
                    }

                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (inDay && sessionStartMs == null) {
                            sessionStartMs = ts
                        }
                    }

                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        closeAllActive(ts)
                        if (inDay) {
                            sessionStartMs?.let { sStart ->
                                longestSessionMs = max(longestSessionMs, ts - max(sStart, startMs))
                            }
                            lastActiveEndMs = ts
                        }
                        sessionStartMs = null
                    }

                    UsageEvents.Event.ACTIVITY_RESUMED,
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (pkg !in ignoredPackages) {
                            val pkgMap = activeComponents.getOrPut(pkg) { mutableMapOf() }
                            if (inDay && pkgMap.isEmpty()) {
                                appLaunches[pkg] = (appLaunches[pkg] ?: 0) + 1
                                totalLaunchCount++
                            }
                            if (componentKey !in pkgMap) {
                                pkgMap[componentKey] = ts
                            }
                            if (inDay) {
                                if (sessionStartMs == null) {
                                    sessionStartMs = if (ts - lastActiveEndMs <= 120_000L) {
                                        lastActiveEndMs
                                    } else {
                                        ts
                                    }
                                }
                                lastActiveEndMs = ts
                            }
                        }
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val pkgMap = activeComponents[pkg]
                        val startedAt = pkgMap?.remove(componentKey)
                            ?: pkgMap?.entries?.firstOrNull()?.let { first ->
                                val key = first.key
                                val v = first.value
                                pkgMap.remove(key)
                                v
                            }
                        if (startedAt != null && pkg !in ignoredPackages) {
                            closeSlice(pkg, startedAt, ts)
                            if (inDay) {
                                lastActiveEndMs = ts
                                sessionStartMs?.let { sStart ->
                                    longestSessionMs = max(longestSessionMs, ts - max(sStart, startMs))
                                }
                            }
                        }
                        if (pkgMap != null && pkgMap.isEmpty()) {
                            activeComponents.remove(pkg)
                        }
                    }
                }
            }

            // Close any still-foreground activities at endMs
            closeAllActive(endMs)
            sessionStartMs?.let { sStart ->
                longestSessionMs = max(longestSessionMs, endMs - max(sStart, startMs))
            }

            // Merge overlapping intervals per package & compute night overlap
            val appTotalsMs = mutableMapOf<String, Long>()
            var nightMs = 0L
            appIntervals.forEach { (pkg, rawIntervals) ->
                val merged = mergeIntervals(rawIntervals)
                var sumMs = 0L
                merged.forEach { (s, e) ->
                    sumMs += (e - s)
                    nightMs += computeNightOverlapMs(s, e)
                }
                if (sumMs > 0L) {
                    appTotalsMs[pkg] = sumMs
                }
            }

            // Fallback / OEM supplement using UsageStats (INTERVAL_BEST & INTERVAL_DAILY)
            if (appTotalsMs.values.sum() < 60_000L) {
                val statsBest = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startMs, endMs).orEmpty()
                val statsDaily = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs).orEmpty()
                val combinedStats = statsBest + statsDaily
                val maxPossibleDayMs = (endMs - startMs).coerceAtLeast(60_000L)

                combinedStats.forEach { stat ->
                    val pkg = stat.packageName ?: return@forEach
                    if (pkg in ignoredPackages) return@forEach
                    val fgMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        max(stat.totalTimeInForeground, stat.totalTimeVisible)
                    } else {
                        stat.totalTimeInForeground
                    }
                    // Guard against stale multi-day buckets leaking yesterday's totals
                    val belongsToToday = stat.lastTimeUsed in startMs..endMs &&
                        (stat.firstTimeStamp >= startMs - 3_600_000L || fgMs <= maxPossibleDayMs)
                    if (belongsToToday && fgMs >= 15_000L) {
                        val cappedMs = fgMs.coerceAtMost(maxPossibleDayMs)
                        val existingMs = appTotalsMs[pkg] ?: 0L
                        if (cappedMs > existingMs) {
                            appTotalsMs[pkg] = cappedMs
                        }
                        if ((appLaunches[pkg] ?: 0) == 0) {
                            appLaunches[pkg] = 1
                            totalLaunchCount++
                        }
                    }
                }
            }

            val topApps = appTotalsMs.entries
                .mapNotNull { (pkg, totalMs) ->
                    val mins = when {
                        totalMs >= 60_000L -> ((totalMs + 29_000L) / 60_000L).toInt()
                        totalMs >= 15_000L -> 1
                        else -> 0
                    }
                    if (mins <= 0) return@mapNotNull null
                    val (label, category) = resolveAppInfo(pkg)
                    AppUsageMetric(
                        packageName = pkg,
                        appName = label,
                        categoryLabel = category,
                        minutesUsed = mins,
                        launchCount = (appLaunches[pkg] ?: 1).coerceAtLeast(1)
                    )
                }
                .sortedByDescending { it.minutesUsed }
                .take(10)

            val totalMinutesFromSum = ((appTotalsMs.values.sum() + 29_000L) / 60_000L).toInt()
            val totalScreenMinutes = max(totalMinutesFromSum, topApps.sumOf { it.minutesUsed })
                .coerceIn(0, 1440)
            val computedUnlocks = if (unlockCount > 0) unlockCount else (totalLaunchCount / 2)

            val diagnostic = if (topApps.isEmpty()) {
                "Scanned $eventsScanned system events for $dayKey • Open any app briefly and tap Sync."
            } else {
                "Synced $eventsScanned events • ${topApps.size} active apps ($dayKey)"
            }

            DeviceUsageSnapshot(
                dayKey = dayKey,
                totalScreenMinutes = totalScreenMinutes,
                unlockCount = computedUnlocks,
                appLaunchCount = totalLaunchCount,
                nightScreenMinutes = (nightMs / 60_000L).toInt().coerceIn(0, totalScreenMinutes),
                longestSessionMinutes = (longestSessionMs / 60_000L).toInt().coerceIn(0, totalScreenMinutes),
                topApps = topApps,
                categoryMinutes = WellbeingMetricsCalculator.categoryBreakdown(topApps),
                eventsScannedCount = eventsScanned,
                syncDiagnostic = diagnostic,
                syncedAtEpochMs = nowMs
            )
        }
    }

    private fun mergeIntervals(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.first }
        val result = mutableListOf<Pair<Long, Long>>()
        var curStart = sorted.first().first
        var curEnd = sorted.first().second
        for (i in 1 until sorted.size) {
            val (s, e) = sorted[i]
            if (s <= curEnd) {
                curEnd = max(curEnd, e)
            } else {
                result.add(curStart to curEnd)
                curStart = s
                curEnd = e
            }
        }
        result.add(curStart to curEnd)
        return result
    }

    private fun dayBounds(daysAgo: Int, nowMs: Long): Triple<Long, Long, String> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMs = cal.timeInMillis
        val endMs = if (daysAgo == 0) {
            nowMs
        } else {
            Calendar.getInstance().apply {
                timeInMillis = startMs
                add(Calendar.DAY_OF_YEAR, 1)
                add(Calendar.MILLISECOND, -1)
            }.timeInMillis
        }
        return Triple(startMs, endMs, DateEpochUtils.todayDayKey(startMs))
    }

    private fun resolveIgnoredSystemPackages(): Set<String> {
        val ignored = mutableSetOf(
            "com.android.systemui",
            "android",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.oneplus.launcher"
        )
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(homeIntent, 0).forEach { res ->
                val pkg = res.activityInfo?.packageName
                if (!pkg.isNullOrBlank() && pkg != context.packageName && !pkg.contains("resolver", ignoreCase = true)) {
                    ignored.add(pkg)
                }
            }
        }
        return ignored
    }

    private fun resolveAppInfo(packageName: String): Pair<String, String> {
        val pm = context.packageManager
        val appInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()

        val label = appInfo?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: knownPackageDisplayName(packageName)
            ?: humanizePackageName(packageName)

        val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                ApplicationInfo.CATEGORY_GAME -> "Games"
                ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                ApplicationInfo.CATEGORY_NEWS -> "Reading & News"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                ApplicationInfo.CATEGORY_MAPS -> "Navigation"
                else -> inferCategoryFromPackage(packageName, label)
            }
        } else {
            inferCategoryFromPackage(packageName, label)
        }

        return label to category
    }

    private fun knownPackageDisplayName(pkg: String): String? {
        return when (pkg) {
            "com.google.android.youtube" -> "YouTube"
            "com.instagram.android" -> "Instagram"
            "com.whatsapp" -> "WhatsApp"
            "com.facebook.katana" -> "Facebook"
            "com.facebook.orca" -> "Messenger"
            "com.snapchat.android" -> "Snapchat"
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
            "com.twitter.android" -> "X"
            "com.reddit.frontpage" -> "Reddit"
            "com.netflix.mediaclient" -> "Netflix"
            "com.spotify.music" -> "Spotify"
            "com.android.chrome" -> "Chrome"
            "com.google.android.gm" -> "Gmail"
            context.packageName -> "LifeWeave"
            else -> null
        }
    }

    private fun inferCategoryFromPackage(pkg: String, label: String): String {
        val p = "${pkg.lowercase(Locale.US)} ${label.lowercase(Locale.US)}"
        return when {
            "instagram" in p || "facebook" in p || "snapchat" in p || "reddit" in p ||
                "twitter" in p || "threads" in p || "tiktok" in p || "linkedin" in p -> "Social"
            "whatsapp" in p || "telegram" in p || "messenger" in p || "discord" in p ||
                "messages" in p || "signal" in p || "slack" in p || "teams" in p -> "Messaging"
            "youtube" in p || "netflix" in p || "primevideo" in p || "hotstar" in p ||
                "disney" in p || "twitch" in p -> "Video"
            "chrome" in p || "firefox" in p || "browser" in p || "opera" in p -> "Browser"
            "spotify" in p || "music" in p || "podcast" in p -> "Audio"
            "game" in p || "pubg" in p || "clash" in p || "roblox" in p -> "Games"
            "gmail" in p || "docs" in p || "sheets" in p || "notes" in p ||
                "calendar" in p || "lifeweave" in p -> "Productivity"
            else -> "Other"
        }
    }

    private fun humanizePackageName(pkg: String): String {
        val token = pkg.split('.')
            .lastOrNull { it !in setOf("android", "app", "mobile", "client", "com", "org", "net") }
            ?: pkg.substringAfterLast('.')
        return token.replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun computeNightOverlapMs(startMs: Long, endMs: Long): Long {
        if (endMs <= startMs) return 0L
        var total = 0L
        var cursor = startMs
        val cal = Calendar.getInstance()
        while (cursor < endMs) {
            cal.timeInMillis = cursor
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val nextBoundary = Calendar.getInstance().apply {
                timeInMillis = cursor
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val sliceEnd = min(endMs, nextBoundary)
            if (hour >= 22 || hour < 6) {
                total += (sliceEnd - cursor)
            }
            cursor = sliceEnd
        }
        return total
    }
}
