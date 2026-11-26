package com.smart.gallery.cleaner.organizer.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import com.smart.gallery.cleaner.organizer.app.notifications.DailyNotificationHelper
import com.smart.gallery.cleaner.organizer.app.utils.LocaleHelper

class GalleryCleanerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply cached theme immediately before any Activity draws to avoid day/night flash
        val cachedMode = SettingsRepository.getCachedThemeMode(this)
        val nightMode = when (cachedMode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // Ensure cached locale is applied
        val cachedLang = SettingsRepository.getCachedLanguage(this)
        if (cachedLang.isNotEmpty() && AppCompatDelegate.getApplicationLocales().isEmpty) {
            LocaleHelper.applyLanguage(cachedLang)
        }

        // Prepare localized notification channel & schedule daily reminder worker (no Firebase)
        DailyNotificationHelper.createNotificationChannel(this)
        DailyNotificationHelper.scheduleDailyReminder(this)
    }
}
