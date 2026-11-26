package com.smart.gallery.cleaner.organizer.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.smart.gallery.cleaner.organizer.app.R

object LocaleHelper {

    data class LanguageOption(
        val tag: String,
        val nativeName: String,
        val englishName: String,
        val flagEmoji: String
    )

    fun getSupportedLanguages(context: Context): List<LanguageOption> = listOf(
        LanguageOption("", context.getString(R.string.lang_system_default), "System Default", "🌐"),
        LanguageOption("en", "English", "English", "🇺🇸"),
        LanguageOption("ur", "اردو", "Urdu", "🇵🇰"),
        LanguageOption("ar", "العربية", "Arabic", "🇸🇦"),
        LanguageOption("hi", "हिन्दी", "Hindi", "🇮🇳"),
        LanguageOption("zh-CN", "中文 (简体)", "Chinese (Simplified)", "🇨🇳"),
        LanguageOption("es", "Español", "Spanish", "🇪🇸"),
        LanguageOption("fr", "Français", "French", "🇫🇷"),
        LanguageOption("pt", "Português", "Portuguese", "🇧🇷"),
        LanguageOption("de", "Deutsch", "German", "🇩🇪"),
        LanguageOption("ru", "Русский", "Russian", "🇷🇺"),
        LanguageOption("tr", "Türkçe", "Turkish", "🇹🇷"),
        LanguageOption("id", "Bahasa Indonesia", "Indonesian", "🇮🇩"),
        LanguageOption("ja", "日本語", "Japanese", "🇯🇵"),
        LanguageOption("ko", "한국어", "Korean", "🇰🇷"),
        LanguageOption("it", "Italiano", "Italian", "🇮🇹"),
        LanguageOption("bn", "বাংলা", "Bengali", "🇧🇩"),
        LanguageOption("fa", "فارسی", "Persian (Farsi)", "🇮🇷"),
        LanguageOption("vi", "Tiếng Việt", "Vietnamese", "🇻🇳"),
        LanguageOption("th", "ไทย", "Thai", "🇹🇭"),
        LanguageOption("ms", "Bahasa Melayu", "Malay", "🇲🇾")
    )

    fun applyLanguage(languageTag: String) {
        val locales = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun getCurrentLanguageTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return ""
        val raw = locales.toLanguageTags().substringBefore(",")
        return when {
            raw.startsWith("zh", ignoreCase = true) -> "zh-CN"
            raw.startsWith("in", ignoreCase = true) -> "id"
            else -> raw.substringBefore("-")
        }
    }

    fun getDisplayNameForTag(context: Context, tag: String): String {
        return getSupportedLanguages(context)
            .firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?.nativeName
            ?: context.getString(R.string.lang_system_default)
    }
}
