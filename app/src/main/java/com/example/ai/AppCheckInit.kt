package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val META_PREFS_NAME = "lifeweave_appcheck_meta"
private const val META_HAS_INJECTED_TOKEN = "has_injected_debug_token"

internal enum class AppCheckProviderChoice(val displayLabel: String) {
    DEBUG_WITH_INTENT_TOKEN("Debug Provider (Injected Token)"),
    DEBUG_PROVIDER("Debug Provider"),
    RECAPTCHA_ENTERPRISE("reCAPTCHA Enterprise"),
    UNCONFIGURED("Unconfigured")
}

sealed interface AppCheckVerificationResult {
    val providerLabel: String

    data class Success(
        override val providerLabel: String,
        val latencyMs: Long,
        val expiresInSeconds: Long
    ) : AppCheckVerificationResult

    data class Timeout(
        override val providerLabel: String,
        val timeoutMs: Long,
        val diagnosticMessage: String
    ) : AppCheckVerificationResult

    data class Failure(
        override val providerLabel: String,
        val errorMessage: String,
        val diagnosticMessage: String
    ) : AppCheckVerificationResult
}

@Volatile
private var installedProviderChoice: AppCheckProviderChoice = AppCheckProviderChoice.UNCONFIGURED

internal fun currentAppCheckProviderChoice(): AppCheckProviderChoice = installedProviderChoice

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            val initialized = FirebaseApp.initializeApp(appContext)
            if (initialized == null) {
                installedProviderChoice = AppCheckProviderChoice.UNCONFIGURED
                Log.w(TAG, "FirebaseApp.initializeApp returned null; google-services config may not be bundled in this build.")
                return
            }
        }

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
        val metaPrefs = appContext.getSharedPreferences(META_PREFS_NAME, Context.MODE_PRIVATE)
        if (BuildConfig.DEBUG && !debugToken.isNullOrEmpty()) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = appContext.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            // CRITICAL: MUST use commit() instead of apply() so the secret is written
            // synchronously before DebugAppCheckProviderFactory reads it.
            prefs.edit()
                .putString(DEBUG_SECRET_KEY, debugToken)
                .commit()
            metaPrefs.edit()
                .putBoolean(META_HAS_INJECTED_TOKEN, true)
                .commit()
            Log.d(TAG, "App Check debug secret configured successfully.")
        }

        val hasInjectedToken = metaPrefs.getBoolean(META_HAS_INJECTED_TOKEN, false)
        val siteKey = resolveRecaptchaSiteKey(appContext)
        val choice = selectAppCheckProviderChoice(
            isDebugBuild = BuildConfig.DEBUG,
            hasIntentDebugToken = !debugToken.isNullOrEmpty(),
            hasPersistedInjectedToken = hasInjectedToken,
            isEmulator = isEmulatorDevice(),
            hasRecaptchaSiteKey = !siteKey.isNullOrBlank()
        )
        installedProviderChoice = choice

        when (choice) {
            AppCheckProviderChoice.DEBUG_WITH_INTENT_TOKEN,
            AppCheckProviderChoice.DEBUG_PROVIDER -> {
                if (BuildConfig.DEBUG && debugToken.isNullOrEmpty() && !hasInjectedToken) {
                    Log.w(TAG, "No debug token found in Intent extra; using DebugAppCheckProviderFactory.")
                }
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "DebugAppCheckProviderFactory installed.")
            }

            AppCheckProviderChoice.RECAPTCHA_ENTERPRISE -> {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey!!)
                )
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            }

            AppCheckProviderChoice.UNCONFIGURED -> {
                Log.e(TAG, "App Check could not be configured: recaptcha_site_key is missing or blank.")
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

suspend fun verifyAppCheckToken(
    context: Context?,
    timeoutMs: Long = 15_000L
): AppCheckVerificationResult {
    val provider = installedProviderChoice
    val providerLabel = provider.displayLabel
    val appCtx = context?.applicationContext
    if (appCtx != null && FirebaseApp.getApps(appCtx).isEmpty()) {
        val initialized = FirebaseApp.initializeApp(appCtx)
        if (initialized == null) {
            return AppCheckVerificationResult.Failure(
                providerLabel = providerLabel,
                errorMessage = "FirebaseApp is not initialized (missing google-services.json).",
                diagnosticMessage = "Firebase config is missing from this APK. Restore GOOGLE_SERVICES_JSON in GitHub Secrets and rebuild."
            )
        }
    }
    if (appCtx != null && provider == AppCheckProviderChoice.UNCONFIGURED) {
        configureAppCheck(appCtx, null)
    }

    val activeLabel = installedProviderChoice.displayLabel
    val startedAt = System.currentTimeMillis()
    return try {
        val tokenResult = withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val task = FirebaseAppCheck.getInstance().getAppCheckToken(false)
                task.addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result)
                }.addOnFailureListener { err ->
                    if (cont.isActive) cont.resumeWithException(err)
                }.addOnCanceledListener {
                    if (cont.isActive) cont.cancel()
                }
            }
        }
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val expiresInSec = ((tokenResult.expireTimeMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        AppCheckVerificationResult.Success(
            providerLabel = activeLabel,
            latencyMs = elapsed,
            expiresInSeconds = expiresInSec
        )
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "App Check token timed out after ${timeoutMs}ms using $activeLabel", e)
        AppCheckVerificationResult.Timeout(
            providerLabel = activeLabel,
            timeoutMs = timeoutMs,
            diagnosticMessage = buildTimeoutDiagnostic(activeLabel, timeoutMs)
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "App Check token acquisition failed using $activeLabel", e)
        val raw = e.localizedMessage?.trim().orEmpty().ifEmpty { e.javaClass.simpleName }
        AppCheckVerificationResult.Failure(
            providerLabel = activeLabel,
            errorMessage = raw,
            diagnosticMessage = buildFailureDiagnostic(activeLabel, raw)
        )
    }
}

internal fun buildTimeoutDiagnostic(providerLabel: String, timeoutMs: Long): String {
    val secs = (timeoutMs / 1000L).coerceAtLeast(1L)
    return "App Check ($providerLabel) timed out after ${secs}s before reaching Gemini. " +
        "On GitHub debug APKs, check mobile/Wi-Fi connectivity to Google Play Services & reCAPTCHA Enterprise, " +
        "or disable VPN/private DNS and tap Run Diagnostics."
}

internal fun buildFailureDiagnostic(providerLabel: String, rawError: String): String {
    return "App Check ($providerLabel) failed: $rawError. " +
        "Ensure GOOGLE_SERVICES_JSON matches this app package and reCAPTCHA/App Check is active."
}

internal fun selectAppCheckProviderChoice(
    isDebugBuild: Boolean,
    hasIntentDebugToken: Boolean,
    hasPersistedInjectedToken: Boolean,
    isEmulator: Boolean,
    hasRecaptchaSiteKey: Boolean
): AppCheckProviderChoice {
    if (!isDebugBuild) {
        return if (hasRecaptchaSiteKey) {
            AppCheckProviderChoice.RECAPTCHA_ENTERPRISE
        } else {
            AppCheckProviderChoice.UNCONFIGURED
        }
    }
    if (hasIntentDebugToken) {
        return AppCheckProviderChoice.DEBUG_WITH_INTENT_TOKEN
    }
    if (!isEmulator && !hasPersistedInjectedToken && hasRecaptchaSiteKey) {
        return AppCheckProviderChoice.RECAPTCHA_ENTERPRISE
    }
    return AppCheckProviderChoice.DEBUG_PROVIDER
}

internal fun resolveRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return null
    }
    val siteKey = context.getString(resId).trim()
    if (siteKey.isEmpty()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return null
    }
    return siteKey
}

internal fun isEmulatorDevice(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    val manufacturer = Build.MANUFACTURER.orEmpty()
    val brand = Build.BRAND.orEmpty()
    val device = Build.DEVICE.orEmpty()
    val product = Build.PRODUCT.orEmpty()
    val hardware = Build.HARDWARE.orEmpty()
    val board = Build.BOARD.orEmpty()
    return fingerprint.startsWith("generic", ignoreCase = true) ||
        fingerprint.startsWith("unknown", ignoreCase = true) ||
        fingerprint.contains("emulator", ignoreCase = true) ||
        fingerprint.equals("robolectric", ignoreCase = true) ||
        (fingerprint.contains("test-keys", ignoreCase = true) && hardware.contains("ranchu", ignoreCase = true)) ||
        model.contains("google_sdk", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK built for", ignoreCase = true) ||
        manufacturer.contains("Genymotion", ignoreCase = true) ||
        hardware.contains("goldfish", ignoreCase = true) ||
        hardware.contains("ranchu", ignoreCase = true) ||
        hardware.contains("vbox86", ignoreCase = true) ||
        product.contains("sdk_gphone", ignoreCase = true) ||
        product.contains("google_sdk", ignoreCase = true) ||
        (product.contains("sdk", ignoreCase = true) && manufacturer.equals("Google", ignoreCase = true) && !model.contains("Pixel", ignoreCase = true)) ||
        product.contains("emulator", ignoreCase = true) ||
        product.contains("simulator", ignoreCase = true) ||
        (brand.startsWith("generic", ignoreCase = true) && device.startsWith("generic", ignoreCase = true)) ||
        board.equals("unknown", ignoreCase = true)
}
