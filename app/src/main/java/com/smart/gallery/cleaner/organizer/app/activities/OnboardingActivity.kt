package com.smart.gallery.cleaner.organizer.app.activities

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import com.smart.gallery.cleaner.organizer.app.databinding.ActivityOnboardingBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemOnboardingPageBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.PermissionHelper
import com.smart.gallery.cleaner.organizer.app.utils.dpToPx

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var slides: List<OnboardingSlide>

    companion object {
        private const val STATE_CURRENT_PAGE = "state_current_page"
    }

    data class FeatureBullet(
        val iconRes: Int,
        val title: String,
        val subtitle: String
    )

    data class OnboardingSlide(
        val imageRes: Int,
        val badge: String,
        val title: String,
        val description: String,
        val feature1: FeatureBullet,
        val feature2: FeatureBullet,
        val feature3: FeatureBullet
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        InsetsHelper.enableEdgeToEdge(this)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsHelper.applySystemBars(binding.rootOnboarding)

        slides = listOf(
            OnboardingSlide(
                imageRes = R.drawable.img_onboarding_ai_clean,
                badge = "ON-DEVICE AI CLEANER",
                title = "AI Smart Clean & Health Score",
                description = "Scan your entire photo & video library to calculate a live Gallery Health Score and free up gigabytes safely.",
                feature1 = FeatureBullet(
                    R.drawable.ic_similar,
                    "Exact Duplicates & Similar Burst Shots",
                    "Groups copies & burst photos and automatically picks the sharpest Best Shot"
                ),
                feature2 = FeatureBullet(
                    R.drawable.ic_blur,
                    "Blurry & Bad Lighting Detector",
                    "Finds out-of-focus, too dark, and overexposed photos using on-device vision"
                ),
                feature3 = FeatureBullet(
                    R.drawable.ic_large_file,
                    "Large Videos, Screenshots & WhatsApp",
                    "Quickly clean heavy files, old screenshots, downloads & forwarded media"
                )
            ),
            OnboardingSlide(
                imageRes = R.drawable.img_onboarding_ai_organize,
                badge = "AI SMART ORGANIZER",
                title = "Auto-Sorted Smart Photo Albums",
                description = "Keep thousands of photos neatly organized into intelligent visual albums without manual tagging.",
                feature1 = FeatureBullet(
                    R.drawable.ic_portrait,
                    "Portraits, Nature & Documents",
                    "Automatically separates people, scenic outdoor shots, receipts & notes"
                ),
                feature2 = FeatureBullet(
                    R.drawable.ic_star,
                    "AI Best Shots & Social Media",
                    "Highlights your highest-quality photos and isolates memes & social saves"
                ),
                feature3 = FeatureBullet(
                    R.drawable.ic_search,
                    "AI Smart Gallery Search",
                    "Instantly filter by blurry, dark, documents, best shots, videos, GIFs or name"
                )
            ),
            OnboardingSlide(
                imageRes = R.drawable.img_onboarding_storage,
                badge = "VISUAL STORAGE ANALYTICS",
                title = "Complete Storage Breakdown",
                description = "Understand exactly what is taking up space on your phone with interactive charts and live photo previews.",
                feature1 = FeatureBullet(
                    R.drawable.ic_storage,
                    "Interactive Donut Chart & Bar",
                    "Tap any slice to inspect Photos, Videos, Screenshots, Downloads & WhatsApp"
                ),
                feature2 = FeatureBullet(
                    R.drawable.ic_photos,
                    "Live Photo-Collage Dashboard",
                    "Preview real thumbnails, exact file counts, and reclaimable size at a glance"
                ),
                feature3 = FeatureBullet(
                    R.drawable.ic_radar,
                    "Real-Time Scan Progress",
                    "See exact percentage, processed file count, and remaining files as you scan"
                )
            ),
            OnboardingSlide(
                imageRes = R.drawable.img_onboarding_privacy,
                badge = "100% PRIVATE & SAFE",
                title = "Offline Privacy & 30-Day Safe Trash",
                description = "Your personal memories never leave your phone — zero cloud uploads, zero ads, and full recovery protection.",
                feature1 = FeatureBullet(
                    R.drawable.ic_privacy_policy,
                    "100% On-Device Processing",
                    "All AI analysis runs locally on your phone with zero internet required"
                ),
                feature2 = FeatureBullet(
                    R.drawable.ic_restore_trash,
                    "30-Day Safe Trash Recovery",
                    "Move files to System Trash and restore accidentally deleted photos anytime"
                ),
                feature3 = FeatureBullet(
                    R.drawable.ic_language,
                    "Day/Night Studio Theme & 20 Languages",
                    "Switch themes or pick your preferred language anytime from Settings"
                )
            )
        )

        binding.viewPager.adapter = SlideAdapter(slides)
        val restoredPage = savedInstanceState?.getInt(STATE_CURRENT_PAGE, 0) ?: 0
        binding.viewPager.setCurrentItem(restoredPage.coerceIn(0, slides.lastIndex), false)

        updateIndicators(binding.viewPager.currentItem)
        updateButtons(binding.viewPager.currentItem)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButtons(position)
            }
        })

        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < slides.lastIndex) {
                binding.viewPager.currentItem = current + 1
            } else {
                finishOnboarding()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        InsetsHelper.applyAutoBarIcons(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_CURRENT_PAGE, binding.viewPager.currentItem)
    }

    private fun updateIndicators(activeIndex: Int) {
        binding.layoutDots.removeAllViews()
        val activeColor = ContextCompat.getColor(this, R.color.primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.card_stroke)
        val heightPx = 8.dpToPx(this)
        val activeW = 24.dpToPx(this)
        val inactiveW = 8.dpToPx(this)
        val marginPx = 4.dpToPx(this)

        for (i in slides.indices) {
            val dot = View(this)
            val isSelected = i == activeIndex
            dot.layoutParams = LinearLayout.LayoutParams(
                if (isSelected) activeW else inactiveW,
                heightPx
            ).apply {
                setMargins(marginPx, 0, marginPx, 0)
            }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = heightPx / 2f
                setColor(if (isSelected) activeColor else inactiveColor)
            }
            binding.layoutDots.addView(dot)
        }
    }

    private fun updateButtons(position: Int) {
        val isLast = position == slides.lastIndex
        binding.btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE
        binding.btnNext.text = if (isLast) getString(R.string.action_continue) else "Next"
    }

    private fun finishOnboarding() {
        SettingsRepository.setCachedOnboardingCompleted(this, true)
        val nextClass = if (PermissionHelper.hasStoragePermission(this)) {
            MainActivity::class.java
        } else {
            PermissionsActivity::class.java
        }
        val intent = Intent(this, nextClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }

    private class SlideAdapter(
        private val items: List<OnboardingSlide>
    ) : RecyclerView.Adapter<SlideAdapter.VH>() {

        inner class VH(val b: ItemOnboardingPageBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val vb = ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(vb)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val slide = items[position]
            holder.b.ivSlideImage.setImageResource(slide.imageRes)
            holder.b.tvSlideBadge.text = slide.badge
            holder.b.tvSlideTitle.text = slide.title
            holder.b.tvSlideDesc.text = slide.description

            holder.b.ivFeat1.setImageResource(slide.feature1.iconRes)
            holder.b.tvFeat1Title.text = slide.feature1.title
            holder.b.tvFeat1Desc.text = slide.feature1.subtitle

            holder.b.ivFeat2.setImageResource(slide.feature2.iconRes)
            holder.b.tvFeat2Title.text = slide.feature2.title
            holder.b.tvFeat2Desc.text = slide.feature2.subtitle

            holder.b.ivFeat3.setImageResource(slide.feature3.iconRes)
            holder.b.tvFeat3Title.text = slide.feature3.title
            holder.b.tvFeat3Desc.text = slide.feature3.subtitle
        }
    }
}
