package com.smart.gallery.cleaner.organizer.app.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentSettingsBinding
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val vm: SettingsViewModel by viewModels()

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
                1 -> "Light"
                2 -> "Dark"
                else -> "System"
            }
        }

        vm.confirmDelete.observe(viewLifecycleOwner) { enabled ->
            if (b.switchConfirmDelete.isChecked != enabled) b.switchConfirmDelete.isChecked =
                enabled
        }

        vm.safeDelete.observe(viewLifecycleOwner) { enabled ->
            if (b.switchSafeDelete.isChecked != enabled) b.switchSafeDelete.isChecked = enabled
        }

        vm.storageWarning.observe(viewLifecycleOwner) { enabled ->
            if (b.switchStorageWarning.isChecked != enabled) b.switchStorageWarning.isChecked =
                enabled
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
            val options = arrayOf("Follow system", "Light", "Dark")
            val current = vm.themeMode.value ?: 0
            MaterialAlertDialogBuilder(requireContext()).setTitle("Theme")
                .setSingleChoiceItems(options, current) { d, i ->
                    vm.setThemeMode(i)
                    d.dismiss()
                }.show()
        }

        b.switchConfirmDelete.setOnCheckedChangeListener { _, checked ->
            vm.setConfirmDelete(checked)
        }

        b.switchSafeDelete.setOnCheckedChangeListener { _, checked ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && checked) {
                b.switchSafeDelete.isChecked = false
                Snackbar.make(
                    b.root, "Safe delete (Trash) requires Android 12 or later", Snackbar.LENGTH_LONG
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

            MaterialAlertDialogBuilder(requireContext()).setTitle("Large file threshold")
                .setSingleChoiceItems(options, currentIndex) { d, i ->
                    vm.setLargeFileThreshold(bytes[i])
                    d.dismiss()
                }.show()
        }

        b.rowAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_about)
        }
        b.rowPrivacy.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_privacy)
        }

        b.rowRateApp.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "market://details?id=${requireContext().packageName}".toUri()
                    )
                )
            } catch (_: Exception) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=${requireContext().packageName}".toUri()
                    )
                )
            }
        }

        b.rowShareApp.setOnClickListener {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "https://play.google.com/store/apps/details?id=${requireContext().packageName}"
                        )
                    }, "Share app"
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView(); _b = null
    }
}