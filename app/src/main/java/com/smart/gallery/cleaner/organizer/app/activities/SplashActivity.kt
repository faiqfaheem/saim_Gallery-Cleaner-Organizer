package com.smart.gallery.cleaner.organizer.app.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.databinding.ActivitySplashBinding
import com.smart.gallery.cleaner.organizer.app.utils.UtilsPreferences
import com.smart.gallery.cleaner.organizer.app.utils.privacyDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateContent()
        navigateAfterDelay()
    }

    private fun animateContent() {

        binding.cardLogo.startAnimation(
            AnimationUtils.loadAnimation(
                this, R.anim.splash_scale_in
            )
        )

        binding.tvAppName.apply {
            alpha = 0f
            translationY = 20f

            animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(300).start()
        }

        binding.tvTagline.apply {
            alpha = 0f

            animate().alpha(1f).setDuration(400).setStartDelay(500).start()
        }

        binding.tvPrivacyNote.apply {
            alpha = 0f

            animate().alpha(1f).setDuration(400).setStartDelay(700).start()
        }
    }

    private fun navigateAfterDelay() {

        binding.root.postDelayed({

            lifecycleScope.launch {

                val preferences = applicationContext.privacyDataStore.data.first()

                val privacyAccepted = preferences[UtilsPreferences.PRIVACY_ACCEPTED] ?: false

                val intent = Intent(
                    this@SplashActivity, MainActivity::class.java
                ).apply {

                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                    putExtra(
                        MainActivity.EXTRA_OPEN_PRIVACY, !privacyAccepted
                    )
                }

                startActivity(intent)

                overridePendingTransition(
                    R.anim.fade_in, R.anim.fade_out
                )

                finish()
            }

        }, 2000L)
    }
}