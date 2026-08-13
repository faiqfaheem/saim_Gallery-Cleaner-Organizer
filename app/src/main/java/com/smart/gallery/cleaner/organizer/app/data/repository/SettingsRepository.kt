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
        val KEY_THEME = intPreferencesKey("theme_mode")
        val KEY_CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val KEY_SAFE_DELETE = booleanPreferencesKey("safe_delete")
        val KEY_LARGE_FILE_THRESHOLD = longPreferencesKey("large_file_threshold_bytes")
        val KEY_LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
        val KEY_STORAGE_WARNING = booleanPreferencesKey("storage_warning")
        val KEY_SCAN_FREQUENCY = intPreferencesKey("scan_frequency")
        val KEY_PREVIEW_BEFORE_DELETE = booleanPreferencesKey("preview_before_delete")
    }

    val themeMode: Flow<Int> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[KEY_THEME] ?: 0 }

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
        context.dataStore.edit { it[KEY_THEME] = mode }
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