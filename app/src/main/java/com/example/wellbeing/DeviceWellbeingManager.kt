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
)

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
    val recommendation: String
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

        val dominantApp = topApps.maxByOrNull { it.minutesUsed }
            ?: record?.takeIf { it.topAppName.isNotBlank() && it.topAppMinutes > 0 }?.let {
                AppUsageMetric("", it.topAppName, "Top App", it.topAppMinutes)
            }
        val dominantNote = dominantApp?.let {
            " Top app: ${it.appName} (${it.minutesUsed}m)."
        }.orEmpty()

        return when {
            ratio >= 1.5f -> WellbeingOveruseAlert(
                severity = OveruseSeverity.CRITICAL,
                stageKey = "critical_150",
                title = "Heavy Screen Overuse (${(ratio * 100).roundToInt()}% of cap)",
                message = "You've logged ${screenMins}m today (${overBy}m over your ${capMins}m limit).$dominantNote",
                usageRatio = ratio,
                remainingMinutes = 0,
                recommendation = "Put your phone down for a 20-minute screen-free walk or hydrate."
            )

            ratio >= 1.0f -> WellbeingOveruseAlert(
                severity = OveruseSeverity.EXCEEDED,
                stageKey = "exceeded_100",
                title = "Daily Screen Limit Exceeded (+${overBy}m)",
                message = "Screen time reached ${screenMins}m against your ${capMins}m goal.$dominantNote",
                usageRatio = ratio,
                remainingMinutes = 0,
                recommendation = "Start a Deep Focus block or silence distracting apps now."
            )

            longestSession >= 60 -> WellbeingOveruseAlert(
                severity = OveruseSeverity.EXCEEDED,
                stageKey = "binge_60",
                title = "Nonstop ${longestSession}m Screen Session",
                message = "You've had a continuous ${longestSession}-minute screen stretch without a break.$dominantNote",
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Follow the 20-20-20 rule: rest your eyes for 20 seconds and stretch."
            )

            (ratio * 100f) >= safeWarnPct -> WellbeingOveruseAlert(
                severity = OveruseSeverity.APPROACHING,
                stageKey = "approaching_$safeWarnPct",
                title = "Approaching Screen Limit (${remaining}m left)",
                message = "${screenMins}m of ${capMins}m used (${(ratio * 100).roundToInt()}%).$dominantNote",
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Wrap up active tasks and pause social/video scrolling."
            )

            nightMins >= 30 -> WellbeingOveruseAlert(
                severity = OveruseSeverity.APPROACHING,
                stageKey = "night_use",
                title = "Late-Night Screen Use (${nightMins}m)",
                message = "${nightMins}m used during wind-down hours (10 PM–6 AM), which can delay deep sleep.",
                usageRatio = ratio,
                remainingMinutes = remaining,
                recommendation = "Switch to Wind-Down mode and dim blue light before bed."
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
                recommendation = "Great pacing—keep taking short visual breaks every 30 minutes."
            )
        }
    }
}

class DeviceWellbeingManager(private val context: Context) {

    fun hasUsageAccessPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

    fun createUsageAccessSettingsIntent(): Intent {
        val packageSpecific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pm = context.packageManager
        return if (packageSpecific.resolveActivity(pm) != null) {
            packageSpecific
        } else {
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
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
            val events = usageManager.queryEvents(startMs, endMs)
            val appTotalsMs = mutableMapOf<String, Long>()
            val appLaunches = mutableMapOf<String, Int>()
            val activeStarts = mutableMapOf<String, Long>()

            var unlockCount = 0
            var totalLaunchCount = 0
            var nightMs = 0L

            var sessionStartMs: Long? = null
            var lastActiveEndMs = startMs
            var longestSessionMs = 0L

            val event = UsageEvents.Event()
            var sawAnyUsageEvent = false

            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                sawAnyUsageEvent = true
                val pkg = event.packageName ?: continue
                val ts = event.timeStamp.coerceIn(startMs, endMs)

                when (event.eventType) {
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        unlockCount++
                    }

                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        if (sessionStartMs == null) {
                            sessionStartMs = ts
                        }
                    }

                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        // Close any open foreground app slices when screen turns off
                        activeStarts.entries.toList().forEach { (openPkg, openedAt) ->
                            val slice = (ts - openedAt).coerceIn(0L, 4 * 3_600_000L)
                            appTotalsMs[openPkg] = (appTotalsMs[openPkg] ?: 0L) + slice
                            nightMs += computeNightOverlapMs(openedAt, openedAt + slice)
                            activeStarts.remove(openPkg)
                        }
                        sessionStartMs?.let { sStart ->
                            longestSessionMs = max(longestSessionMs, ts - sStart)
                        }
                        sessionStartMs = null
                        lastActiveEndMs = ts
                    }

                    UsageEvents.Event.ACTIVITY_RESUMED,
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (pkg !in ignoredPackages) {
                            if (activeStarts[pkg] == null) {
                                appLaunches[pkg] = (appLaunches[pkg] ?: 0) + 1
                                totalLaunchCount++
                            }
                            activeStarts[pkg] = ts

                            if (sessionStartMs == null) {
                                sessionStartMs = if (ts - lastActiveEndMs <= 120_000L) {
                                    ts
                                } else {
                                    ts
                                }
                            }
                            lastActiveEndMs = ts
                        }
                    }

                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    @Suppress("DEPRECATION")
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val startedAt = activeStarts.remove(pkg)
                        if (startedAt != null && pkg !in ignoredPackages) {
                            val slice = (ts - startedAt).coerceIn(0L, 4 * 3_600_000L)
                            appTotalsMs[pkg] = (appTotalsMs[pkg] ?: 0L) + slice
                            nightMs += computeNightOverlapMs(startedAt, startedAt + slice)
                            lastActiveEndMs = ts
                            sessionStartMs?.let { sStart ->
                                longestSessionMs = max(longestSessionMs, ts - sStart)
                            }
                        }
                    }
                }
            }

            // Close any still-foreground apps at endMs
            activeStarts.forEach { (pkg, startedAt) ->
                if (pkg !in ignoredPackages) {
                    val slice = (endMs - startedAt).coerceIn(0L, 4 * 3_600_000L)
                    appTotalsMs[pkg] = (appTotalsMs[pkg] ?: 0L) + slice
                    nightMs += computeNightOverlapMs(startedAt, startedAt + slice)
                    sessionStartMs?.let { sStart ->
                        longestSessionMs = max(longestSessionMs, endMs - sStart)
                    }
                }
            }

            // Fallback to queryUsageStats if UsageEvents produced zero foreground time
            if ((!sawAnyUsageEvent || appTotalsMs.values.sum() == 0L)) {
                val stats = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
                stats?.forEach { stat ->
                    val pkg = stat.packageName ?: return@forEach
                    val fgMs = stat.totalTimeInForeground
                    if (pkg !in ignoredPackages && fgMs >= 60_000L && stat.lastTimeUsed >= startMs) {
                        val cappedMs = fgMs.coerceAtMost(endMs - startMs)
                        appTotalsMs[pkg] = max(appTotalsMs[pkg] ?: 0L, cappedMs)
                        if ((appLaunches[pkg] ?: 0) == 0) {
                            appLaunches[pkg] = 1
                            totalLaunchCount++
                        }
                    }
                }
            }

            val topApps = appTotalsMs.entries
                .mapNotNull { (pkg, totalMs) ->
                    val mins = (totalMs / 60_000L).toInt()
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
                .take(8)

            val totalMinutesFromSum = (appTotalsMs.values.sum() / 60_000L).toInt()
            val totalScreenMinutes = max(totalMinutesFromSum, topApps.sumOf { it.minutesUsed })
                .coerceIn(0, 1440)
            val computedUnlocks = if (unlockCount > 0) unlockCount else (totalLaunchCount / 2)

            DeviceUsageSnapshot(
                dayKey = dayKey,
                totalScreenMinutes = totalScreenMinutes,
                unlockCount = computedUnlocks,
                appLaunchCount = totalLaunchCount,
                nightScreenMinutes = (nightMs / 60_000L).toInt().coerceIn(0, totalScreenMinutes),
                longestSessionMinutes = (longestSessionMs / 60_000L).toInt().coerceIn(0, totalScreenMinutes),
                topApps = topApps,
                categoryMinutes = WellbeingMetricsCalculator.categoryBreakdown(topApps),
                syncedAtEpochMs = nowMs
            )
        }
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
            "com.oneplus.launcher"
        )
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(homeIntent, 0).forEach { res ->
                res.activityInfo?.packageName?.let { ignored.add(it) }
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
