package com.smart.gallery.cleaner.organizer.app.viewmodel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    val themeMode = repo.themeMode.asLiveData()
    val confirmDelete = repo.confirmDelete.asLiveData()
    val safeDelete = repo.safeDelete.asLiveData()
    val largeFileThreshold = repo.largeFileThreshold.asLiveData()
    val lastScanTime = repo.lastScanTime.asLiveData()
    val storageWarning = repo.storageWarning.asLiveData()
    val previewBeforeDelete = repo.previewBeforeDelete.asLiveData()
    val scanFrequency = repo.scanFrequency.asLiveData()

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            repo.setThemeMode(mode)
            val nightMode = when (mode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    fun setConfirmDelete(enabled: Boolean) = viewModelScope.launch {
        repo.setConfirmDelete(enabled)
    }

    fun setSafeDelete(enabled: Boolean) = viewModelScope.launch {
        repo.setSafeDelete(enabled)
    }

    fun setLargeFileThreshold(bytes: Long) = viewModelScope.launch {
        repo.setLargeFileThreshold(bytes)
    }

    fun setPreviewBeforeDelete(enabled: Boolean) = viewModelScope.launch {
        repo.setPreviewBeforeDelete(enabled)
    }

    fun setStorageWarning(enabled: Boolean) = viewModelScope.launch {
        repo.setStorageWarning(enabled)
    }

    fun setScanFrequency(freq: Int) = viewModelScope.launch {
        repo.setScanFrequency(freq)
    }
}