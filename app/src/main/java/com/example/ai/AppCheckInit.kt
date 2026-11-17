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

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val META_PREFS_NAME = "lifeweave_appcheck_meta"
private const val META_HAS_INJECTED_TOKEN = "has_injected_debug_token"

internal enum class AppCheckProviderChoice {
    DEBUG_WITH_INTENT_TOKEN,
    DEBUG_PROVIDER,
    RECAPTCHA_ENTERPRISE,
    UNCONFIGURED
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            val initialized = FirebaseApp.initializeApp(appContext)
            if (initialized == null) {
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
