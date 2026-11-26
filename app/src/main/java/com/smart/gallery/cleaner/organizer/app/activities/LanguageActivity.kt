package com.smart.gallery.cleaner.organizer.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import com.smart.gallery.cleaner.organizer.app.databinding.ActivityLanguageBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemLanguageBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.LocaleHelper
import com.smart.gallery.cleaner.organizer.app.utils.UtilsPreferences
import com.smart.gallery.cleaner.organizer.app.utils.dpToPx
import com.smart.gallery.cleaner.organizer.app.utils.privacyDataStore
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var allLanguages: List<LocaleHelper.LanguageOption>
    private lateinit var adapter: LanguageAdapter

    private var isOnboarding = false
    private var selectedTag = ""

    companion object {
        const val EXTRA_IS_ONBOARDING = "extra_is_onboarding"
        private const val STATE_SELECTED_TAG = "state_selected_tag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        InsetsHelper.enableEdgeToEdge(this)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsHelper.applySystemBars(binding.rootLanguage)

        settingsRepo = SettingsRepository(this)
        isOnboarding = intent.getBooleanExtra(EXTRA_IS_ONBOARDING, false)
        allLanguages = LocaleHelper.getSupportedLanguages(this)

        selectedTag = savedInstanceState?.getString(STATE_SELECTED_TAG)
            ?: SettingsRepository.getCachedLanguage(this).ifBlank { LocaleHelper.getCurrentLanguageTag() }

        setupUi()
        setupSearch()
        setupRecyclerView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED_TAG, selectedTag)
    }

    private fun setupUi() {
        binding.btnBack.visibleIf(!isOnboarding)
        binding.btnBack.setOnClickListener { finish() }

        binding.btnApplyLanguage.text = getString(
            if (isOnboarding) R.string.action_continue else R.string.action_apply_language
        )

        binding.btnApplyLanguage.setOnClickListener {
            applyAndProceed()
        }
    }

    private fun setupSearch() {
        binding.etSearchLanguage.doAfterTextChanged { editable ->
            val q = editable?.toString()?.trim() ?: ""
            binding.btnClearSearch.visibleIf(q.isNotEmpty())
            filterLanguages(q)
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchLanguage.setText("")
        }
    }

    private fun setupRecyclerView() {
        adapter = LanguageAdapter(allLanguages) { option ->
            selectedTag = option.tag
            adapter.notifyDataSetChanged()
        }
        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter
    }

    private fun filterLanguages(query: String) {
        val filtered = if (query.isBlank()) {
            allLanguages
        } else {
            val lower = query.lowercase()
            allLanguages.filter {
                it.nativeName.lowercase().contains(lower) ||
                        it.englishName.lowercase().contains(lower) ||
                        it.tag.lowercase().contains(lower)
            }
        }
        adapter.updateItems(filtered)
        binding.layoutEmptyLanguage.visibleIf(filtered.isEmpty())
        binding.rvLanguages.visibleIf(filtered.isNotEmpty())
    }

    private fun applyAndProceed() {
        binding.btnApplyLanguage.isEnabled = false
        lifecycleScope.launch {
            settingsRepo.setAppLanguage(selectedTag)
            settingsRepo.setLanguageSelected(true)
            LocaleHelper.applyLanguage(selectedTag)
            com.smart.gallery.cleaner.organizer.app.notifications.DailyNotificationHelper.createNotificationChannel(this@LanguageActivity)

            if (isOnboarding) {
                val onboardingDone = SettingsRepository.isOnboardingCompleted(this@LanguageActivity)
                val hasPerm = com.smart.gallery.cleaner.organizer.app.utils.PermissionHelper.hasStoragePermission(this@LanguageActivity)
                val nextClass = when {
                    !onboardingDone -> OnboardingActivity::class.java
                    !hasPerm -> PermissionsActivity::class.java
                    else -> MainActivity::class.java
                }
                val nextIntent = Intent(this@LanguageActivity, nextClass).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(nextIntent)
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                finish()
            } else {
                finish()
            }
        }
    }

    private inner class LanguageAdapter(
        private var items: List<LocaleHelper.LanguageOption>,
        private val onClick: (LocaleHelper.LanguageOption) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.VH>() {

        inner class VH(val b: ItemLanguageBinding) : RecyclerView.ViewHolder(b.root)

        fun updateItems(newItems: List<LocaleHelper.LanguageOption>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val vb = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(vb)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val option = items[position]
            val isSelected = option.tag.equals(selectedTag, ignoreCase = true)
            val ctx = holder.b.root.context

            holder.b.tvFlag.text = option.flagEmoji
            holder.b.tvNativeName.text = option.nativeName
            holder.b.tvEnglishName.text = option.englishName
            holder.b.ivSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE

            holder.b.cardLanguage.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    if (isSelected) R.color.primary_container else R.color.card_background
                )
            )
            holder.b.cardLanguage.strokeColor = ContextCompat.getColor(
                ctx,
                if (isSelected) R.color.primary else R.color.card_stroke
            )
            holder.b.cardLanguage.strokeWidth = (if (isSelected) 2 else 1).dpToPx(ctx)

            holder.b.root.setOnClickListener { onClick(option) }
        }
    }
}
