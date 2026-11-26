package com.smart.gallery.cleaner.organizer.app.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import com.smart.gallery.cleaner.organizer.app.databinding.ActivitySplashBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        InsetsHelper.enableEdgeToEdge(this)
        InsetsHelper.setLightBackgroundIcons(this, lightBackground = false)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsHelper.applySystemBars(binding.root)

        animateContent()
        navigateAfterDelay()
    }

    private fun animateContent() {
        binding.cardLogo.startAnimation(
            AnimationUtils.loadAnimation(this, R.anim.splash_scale_in)
        )

        binding.tvAppName.apply {
            alpha = 0f
            translationY = 20f
            animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(250).start()
        }

        binding.tvTagline.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(400).setStartDelay(450).start()
        }

        binding.tvPrivacyNote.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(400).setStartDelay(650).start()
        }
    }

    private fun navigateAfterDelay() {
        lifecycleScope.launch {
            delay(1400L)
            val languageSelected = SettingsRepository.isLanguageSelected(this@SplashActivity)
            val onboardingDone = SettingsRepository.isOnboardingCompleted(this@SplashActivity)
            val hasPerm = PermissionHelper.hasStoragePermission(this@SplashActivity)

            val intent = when {
                !languageSelected -> Intent(this@SplashActivity, LanguageActivity::class.java).apply {
                    putExtra(LanguageActivity.EXTRA_IS_ONBOARDING, true)
                }
                !onboardingDone -> Intent(this@SplashActivity, OnboardingActivity::class.java)
                !hasPerm -> Intent(this@SplashActivity, PermissionsActivity::class.java)
                else -> Intent(this@SplashActivity, MainActivity::class.java)
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }
    }
}
