package com.smart.gallery.cleaner.organizer.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import com.smart.gallery.cleaner.organizer.app.databinding.ActivityPrivacyPolicyBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemPrivacySectionBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.UtilsPreferences
import com.smart.gallery.cleaner.organizer.app.utils.privacyDataStore
import kotlinx.coroutines.launch

class PrivacyPolicyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacyPolicyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If user already accepted Privacy Policy, proceed immediately to SplashActivity
        if (SettingsRepository.isPrivacyAccepted(this)) {
            openSplash(animate = false)
            return
        }

        InsetsHelper.enableEdgeToEdge(this)
        binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsHelper.applySystemBars(binding.rootPrivacy)

        populateSections()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        InsetsHelper.applyAutoBarIcons(this)
    }

    private fun setupButtons() {
        binding.btnAcceptContinue.setOnClickListener {
            binding.btnAcceptContinue.isEnabled = false
            binding.btnDecline.isEnabled = false
            SettingsRepository.setCachedPrivacyAccepted(this, true)
            lifecycleScope.launch {
                runCatching {
                    applicationContext.privacyDataStore.edit { prefs ->
                        prefs[UtilsPreferences.PRIVACY_ACCEPTED] = true
                    }
                }
                openSplash(animate = true)
            }
        }

        binding.btnDecline.setOnClickListener {
            finish()
        }
    }

    private fun openSplash(animate: Boolean) {
        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        if (animate) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        finish()
    }

    private fun populateSections() {
        val sections = listOf(
            "1. Data We Collect" to
                "We collect absolutely no personal data. This app does not transmit, upload, or share any of your photos, videos, or personal information with anyone. All file scanning and AI analysis is performed entirely on your device.",
            "2. Media & Storage Access" to
                "The app requests media access solely to scan, organize, and clean photos and videos stored on your device. Nothing ever leaves your phone.",
            "3. Zero Ads & Zero Tracking" to
                "No third-party analytics SDKs, crash tracking, or hidden cloud sync. Your gallery stays 100% private.",
            "4. Safe File Deletion" to
                "Deletions use Android's official MediaStore APIs with System Trash support (Android 12+) so you can recover files for 30 days.",
            "5. App Preferences" to
                "Theme, language, and scan settings are saved locally on your device only."
        )

        val inflater = LayoutInflater.from(this)
        sections.forEach { (title, body) ->
            val item = ItemPrivacySectionBinding.inflate(inflater, binding.llSections, false)
            item.tvSectionTitle.text = title
            item.tvSectionBody.text = body
            binding.llSections.addView(item.root)
        }
    }
}
