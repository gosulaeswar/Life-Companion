package com.example.security

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

object AppLocaleManager {

    fun buildLocalizedConfiguration(baseConfig: Configuration, language: SupportedLanguage): Configuration {
        val locale = Locale.forLanguageTag(language.code)
        return Configuration(baseConfig).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
    }

    fun wrapContext(baseContext: Context, language: SupportedLanguage): Context {
        val locale = Locale.forLanguageTag(language.code)
        Locale.setDefault(locale)
        val localizedConfig = buildLocalizedConfiguration(baseContext.resources.configuration, language)
        val localizedResources = baseContext.createConfigurationContext(localizedConfig).resources
        return LocalizedContextWrapper(baseContext, localizedResources)
    }
}

@Composable
fun ProvideLocalizedLocale(
    language: SupportedLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(baseContext, language) {
        AppLocaleManager.wrapContext(baseContext, language)
    }
    val localizedConfig = remember(baseConfiguration, language) {
        AppLocaleManager.buildLocalizedConfiguration(baseConfiguration, language)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfig
    ) {
        content()
    }
}
