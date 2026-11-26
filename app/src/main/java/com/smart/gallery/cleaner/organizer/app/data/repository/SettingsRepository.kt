package com.smart.gallery.cleaner.organizer.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gallery_cleaner_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private const val FAST_CACHE_PREFS = "gallery_cleaner_fast_cache"
        private const val CACHE_THEME_MODE = "cached_theme_mode"
        private const val CACHE_LANGUAGE = "cached_language_tag"
        private const val CACHE_LANGUAGE_SELECTED = "cached_language_selected"
        private const val CACHE_PRIVACY_ACCEPTED = "cached_privacy_accepted"
        private const val CACHE_ONBOARDING_COMPLETED = "cached_onboarding_completed"

        val KEY_THEME = intPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_LANGUAGE_SELECTED = booleanPreferencesKey("language_selected")
        val KEY_CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val KEY_SAFE_DELETE = booleanPreferencesKey("safe_delete")
        val KEY_LARGE_FILE_THRESHOLD = longPreferencesKey("large_file_threshold_bytes")
        val KEY_LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
        val KEY_STORAGE_WARNING = booleanPreferencesKey("storage_warning")
        val KEY_SCAN_FREQUENCY = intPreferencesKey("scan_frequency")
        val KEY_PREVIEW_BEFORE_DELETE = booleanPreferencesKey("preview_before_delete")

        fun getCachedThemeMode(context: Context): Int =
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .getInt(CACHE_THEME_MODE, 0)

        fun setCachedThemeMode(context: Context, mode: Int) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().putInt(CACHE_THEME_MODE, mode).apply()
        }

        fun getCachedLanguage(context: Context): String =
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .getString(CACHE_LANGUAGE, "") ?: ""

        fun setCachedLanguage(context: Context, tag: String) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(CACHE_LANGUAGE, tag).apply()
        }

        fun isLanguageSelected(context: Context): Boolean =
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(CACHE_LANGUAGE_SELECTED, false)

        fun setCachedLanguageSelected(context: Context, selected: Boolean) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(CACHE_LANGUAGE_SELECTED, selected).apply()
        }

        fun isPrivacyAccepted(context: Context): Boolean =
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(CACHE_PRIVACY_ACCEPTED, false)

        fun setCachedPrivacyAccepted(context: Context, accepted: Boolean) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(CACHE_PRIVACY_ACCEPTED, accepted).apply()
        }

        fun isOnboardingCompleted(context: Context): Boolean =
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .getBoolean(CACHE_ONBOARDING_COMPLETED, false)

        fun setCachedOnboardingCompleted(context: Context, completed: Boolean) {
            context.getSharedPreferences(FAST_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(CACHE_ONBOARDING_COMPLETED, completed).apply()
        }
    }

    val themeMode: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_THEME] ?: 0 }

    val appLanguage: Flow<String> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_LANGUAGE] ?: "" }

    val confirmDelete: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_CONFIRM_DELETE] ?: true }

    val safeDelete: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_SAFE_DELETE] ?: true }

    val largeFileThreshold: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_LARGE_FILE_THRESHOLD] ?: 5_242_880L } // 5 MB default

    val lastScanTime: Flow<Long> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_LAST_SCAN_TIME] ?: 0L }

    val storageWarning: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_STORAGE_WARNING] ?: true }

    val previewBeforeDelete: Flow<Boolean> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_PREVIEW_BEFORE_DELETE] ?: false }

    val scanFrequency: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_SCAN_FREQUENCY] ?: 0 }

    suspend fun setThemeMode(mode: Int) {
        setCachedThemeMode(context, mode)
        context.dataStore.edit { it[KEY_THEME] = mode }
    }

    suspend fun setAppLanguage(languageTag: String) {
        setCachedLanguage(context, languageTag)
        context.dataStore.edit { it[KEY_LANGUAGE] = languageTag }
    }

    suspend fun setLanguageSelected(selected: Boolean) {
        setCachedLanguageSelected(context, selected)
        context.dataStore.edit { it[KEY_LANGUAGE_SELECTED] = selected }
    }

    suspend fun setConfirmDelete(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CONFIRM_DELETE] = enabled }
    }

    suspend fun setSafeDelete(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SAFE_DELETE] = enabled }
    }

    suspend fun setLargeFileThreshold(bytes: Long) {
        context.dataStore.edit { it[KEY_LARGE_FILE_THRESHOLD] = bytes }
    }

    suspend fun setLastScanTime(timestamp: Long) {
        context.dataStore.edit { it[KEY_LAST_SCAN_TIME] = timestamp }
    }

    suspend fun setStorageWarning(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STORAGE_WARNING] = enabled }
    }

    suspend fun setPreviewBeforeDelete(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PREVIEW_BEFORE_DELETE] = enabled }
    }

    suspend fun setScanFrequency(freq: Int) {
        context.dataStore.edit { it[KEY_SCAN_FREQUENCY] = freq }
    }
}
