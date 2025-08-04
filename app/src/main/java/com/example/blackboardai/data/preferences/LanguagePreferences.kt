package com.example.blackboardai.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supported languages for AI responses
 */
enum class SupportedLanguage(
    val displayName: String,
    val code: String,
    val aiPromptSuffix: String
) {
    HINDI("हिंदी (Hindi)", "hi", "Please respond in Hindi (हिंदी) language."),
    ENGLISH("English", "en", "Please respond in English language."),
    TAMIL("தமிழ் (Tamil)", "ta", "Please respond in Tamil (தமிழ்) language."),
    MARATHI("मराठी (Marathi)", "mr", "Please respond in Marathi (मराठी) language."),
    PUNJABI("ਪੰਜਾਬੀ (Punjabi)", "pa", "Please respond in Punjabi (ਪੰਜਾਬੀ) language.")
}

@Singleton
class LanguagePreferencesService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "language_preferences"
        private const val KEY_SELECTED_LANGUAGE = "selected_language"
        private const val DEFAULT_LANGUAGE = "hi" // Hindi as default
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _selectedLanguage = MutableStateFlow(getCurrentLanguage())
    val selectedLanguage: StateFlow<SupportedLanguage> = _selectedLanguage.asStateFlow()
    
    /**
     * Get the currently selected language
     */
    private fun getCurrentLanguage(): SupportedLanguage {
        val savedCode = sharedPreferences.getString(KEY_SELECTED_LANGUAGE, DEFAULT_LANGUAGE)
        return SupportedLanguage.values().find { it.code == savedCode } ?: SupportedLanguage.HINDI
    }
    
    /**
     * Set the selected language and persist it
     */
    fun setSelectedLanguage(language: SupportedLanguage) {
        sharedPreferences.edit()
            .putString(KEY_SELECTED_LANGUAGE, language.code)
            .apply()
        
        _selectedLanguage.value = language
    }
    
    /**
     * Get the AI prompt suffix for the current language
     */
    fun getLanguagePromptSuffix(): String {
        return _selectedLanguage.value.aiPromptSuffix
    }
    
    /**
     * Get all supported languages
     */
    fun getAllSupportedLanguages(): List<SupportedLanguage> {
        return SupportedLanguage.values().toList()
    }
    
    /**
     * Check if a language is the currently selected one
     */
    fun isLanguageSelected(language: SupportedLanguage): Boolean {
        return _selectedLanguage.value == language
    }
} 