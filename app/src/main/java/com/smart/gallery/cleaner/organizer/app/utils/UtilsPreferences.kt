package com.smart.gallery.cleaner.organizer.app.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.privacyDataStore by preferencesDataStore(
    name = "privacy_preferences"
)

object UtilsPreferences {

    val PRIVACY_ACCEPTED =
        booleanPreferencesKey("privacy_accepted")
}