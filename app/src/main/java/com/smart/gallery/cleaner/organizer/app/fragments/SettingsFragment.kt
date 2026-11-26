package com.smart.gallery.cleaner.organizer.app.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentSettingsBinding
import com.smart.gallery.cleaner.organizer.app.utils.LocaleHelper
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigate
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val vm: SettingsViewModel by viewModels()
    private var activeDialog: AlertDialog? = null

    override fun onCreateView(
        i: LayoutInflater, c: ViewGroup?, s: Bundle?
    ): View {
        _b = FragmentSettingsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            b.switchSafeDelete.isEnabled = false
            b.switchSafeDelete.alpha = 0.4f
        }
        observeSettings()
        setupClickListeners()
    }

    private fun observeSettings() {
        vm.themeMode.observe(viewLifecycleOwner) { mode ->
            b.tvThemeValue.text = when (mode) {
                1 -> getString(R.string.theme_light)
                2 -> getString(R.string.theme_dark)
                else -> getString(R.string.theme_system)
            }
        }

        vm.appLanguage.observe(viewLifecycleOwner) { tag ->
            val effectiveTag = tag.ifBlank { LocaleHelper.getCurrentLanguageTag() }
            b.tvLanguageValue.text = LocaleHelper.getDisplayNameForTag(requireContext(), effectiveTag)
        }

        vm.confirmDelete.observe(viewLifecycleOwner) { enabled ->
            if (b.switchConfirmDelete.isChecked != enabled) {
                b.switchConfirmDelete.isChecked = enabled
            }
        }

        vm.safeDelete.observe(viewLifecycleOwner) { enabled ->
            if (b.switchSafeDelete.isChecked != enabled) {
                b.switchSafeDelete.isChecked = enabled
            }
        }

        vm.storageWarning.observe(viewLifecycleOwner) { enabled ->
            if (b.switchStorageWarning.isChecked != enabled) {
                b.switchStorageWarning.isChecked = enabled
            }
        }

        vm.largeFileThreshold.observe(viewLifecycleOwner) { bytes ->
            b.tvLargeFileValue.text = when {
                bytes >= 1_048_576L -> "${bytes / 1_048_576L} MB"
                else -> "${bytes / 1_024L} KB"
            }
        }
    }

    private fun setupClickListeners() {
        b.rowTheme.setOnClickListener {
            val options = arrayOf(
                getString(R.string.theme_follow_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
            )
            val current = vm.themeMode.value ?: 0
            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(options, current) { d, i ->
                    vm.setThemeMode(i)
                    d.dismiss()
                }
                .show()
        }

        b.rowLanguage.setOnClickListener {
            val intent = Intent(requireContext(), com.smart.gallery.cleaner.organizer.app.activities.LanguageActivity::class.java).apply {
                putExtra(com.smart.gallery.cleaner.organizer.app.activities.LanguageActivity.EXTRA_IS_ONBOARDING, false)
            }
            startActivity(intent)
        }

        b.switchConfirmDelete.setOnCheckedChangeListener { _, checked ->
            vm.setConfirmDelete(checked)
        }

        b.switchSafeDelete.setOnCheckedChangeListener { _, checked ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && checked) {
                b.switchSafeDelete.isChecked = false
                Snackbar.make(
                    b.root,
                    getString(R.string.safe_delete_requires_android_12),
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                vm.setSafeDelete(checked)
            }
        }

        b.switchStorageWarning.setOnCheckedChangeListener { _, checked ->
            vm.setStorageWarning(checked)
        }

        b.rowLargeFileThreshold.setOnClickListener {
            val options = arrayOf("1 MB", "5 MB", "10 MB", "25 MB", "50 MB")
            val bytes = longArrayOf(
                1_048_576L, 5_242_880L, 10_485_760L, 26_214_400L, 52_428_800L
            )
            val currentBytes = vm.largeFileThreshold.value ?: 5_242_880L
            val currentIndex = bytes.indexOfFirst { it == currentBytes }.coerceAtLeast(0)

            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_large_threshold)
                .setSingleChoiceItems(options, currentIndex) { d, i ->
                    vm.setLargeFileThreshold(bytes[i])
                    d.dismiss()
                }
                .show()
        }

        b.rowAbout.setOnClickListener {
            safeNavigate(R.id.action_settings_to_about)
        }

        b.rowPrivacy.setOnClickListener {
            safeNavigate(R.id.action_settings_to_privacy)
        }

        b.rowRateApp.setOnClickListener {
            runCatching {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "market://details?id=${requireContext().packageName}".toUri()
                    )
                )
            }.onFailure {
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=${requireContext().packageName}".toUri()
                        )
                    )
                }
            }
        }

        b.rowShareApp.setOnClickListener {
            runCatching {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://play.google.com/store/apps/details?id=${requireContext().packageName}"
                            )
                        },
                        getString(R.string.settings_share_app)
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroyView()
        _b = null
    }
}
