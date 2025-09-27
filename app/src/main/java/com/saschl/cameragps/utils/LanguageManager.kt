package com.saschl.cameragps.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageManager {
    
    /**
     * Supported languages
     */
    enum class Language(val code: String, val displayName: String) {
        SYSTEM("system", "System Default"),
        ENGLISH("en", "English"),
        GERMAN("de", "Deutsch"),
        CHINESE_SIMPLIFIED("zh", "简体中文")
    }

    /**
     * Apply language setting to the given context
     */
    fun applyLanguage(context: Context): Context {
        val languageCode = PreferencesManager.getLanguageCode(context)
        return if (languageCode != null) {
            setLocale(context, languageCode)
        } else {
            context
        }
    }

    /**
     * Set the locale for the given context
     */
    private fun setLocale(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "en" -> Locale.ENGLISH
            "de" -> Locale.GERMAN
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.getDefault()
        }

        return updateConfiguration(context, locale)
    }

    /**
     * Update the configuration with the new locale
     */
    private fun updateConfiguration(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }

    /**
     * Apply language immediately to the current activity
     */
    fun applyLanguageToActivity(activity: Activity, languageCode: String?) {
        PreferencesManager.setLanguageCode(activity, languageCode)
        
        // Recreate activity to apply new language
        activity.recreate()
    }

    /**
     * Get the currently selected language
     */
    fun getCurrentLanguage(context: Context): Language {
        val languageCode = PreferencesManager.getLanguageCode(context)
        return Language.values().find { it.code == languageCode } ?: Language.SYSTEM
    }

    /**
     * Get all available languages
     */
    fun getAvailableLanguages(): List<Language> {
        return Language.values().toList()
    }

    /**
     * Check if the app should use system language
     */
    fun shouldUseSystemLanguage(context: Context): Boolean {
        return PreferencesManager.getLanguageCode(context) == null
    }
}

/**
 * Context wrapper to apply language changes
 */
class LanguageContextWrapper(base: Context) : ContextWrapper(base) {
    companion object {
        fun wrap(context: Context): ContextWrapper {
            return LanguageContextWrapper(LanguageManager.applyLanguage(context))
        }
    }
}
