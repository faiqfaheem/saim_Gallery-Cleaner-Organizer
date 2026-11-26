package com.smart.gallery.cleaner.organizer.app.fragments

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.model.AiCleanRecommendation
import com.smart.gallery.cleaner.organizer.app.data.model.AiCleanRecommendationType
import com.smart.gallery.cleaner.organizer.app.data.model.AiCleanReport
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentAiSmartCleanBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemAiRecommendationBinding
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class AiSmartCleanFragment : Fragment() {

    private var _b: FragmentAiSmartCleanBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()
    private val settingsVm: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }

    private var currentRecommendations: List<AiCleanRecommendation> = emptyList()

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onPermissionGrantedForDelete()
        } else {
            vm.clearDeleteResult()
            showSnack(getString(R.string.error_delete_failed))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentAiSmartCleanBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnBack.setOnClickListener { findNavController().navigateUp() }
        b.btnRescan.setOnClickListener { vm.loadAiSmartCleanReport() }

        b.btnAiCleanNow.setOnClickListener {
            val selectedFiles = currentRecommendations
                .filter { it.isSelected }
                .flatMap { it.files }
                .distinctBy { it.id }
            if (selectedFiles.isNotEmpty()) {
                vm.deleteFiles(selectedFiles)
            }
        }

        observeViewModel()
        if (!vm.hasLoadedAiReport && vm.loading.value != true) {
            vm.loadAiSmartCleanReport()
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            b.layoutLoading.visibleIf(loading)
            val hasReport = vm.aiCleanReport.value != null
            b.scrollContent.visibleIf(!loading && hasReport)
            if (loading) b.btnAiCleanNow.gone()
        }

        vm.scanProgress.observe(viewLifecycleOwner) { p ->
            b.progressAiScan.setProgress(p.percent, true)
            b.tvLoadingPercent.text = "${p.percent}%"
            b.tvLoadingCounts.text = if (p.total > 0) {
                getString(R.string.scan_files_progress_format, p.processed, p.total, p.remaining)
            } else ""
        }

        vm.aiCleanReport.observe(viewLifecycleOwner) { report ->
            report ?: return@observe
            renderReport(report)
        }

        vm.confirmDeleteEvent.observe(viewLifecycleOwner) { files ->
            files ?: return@observe
            val count = files.size
            val freed = MediaFile.formatSize(files.sumOf { it.size })
            val isSafe = settingsVm.safeDelete.value == true
            val isTrash = isSafe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val message = if (isTrash) {
                getString(R.string.move_to_trash_dialog_message, count, freed)
            } else {
                getString(R.string.delete_dialog_message, count, freed)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(resources.getQuantityString(R.plurals.delete_dialog_title, count, count))
                .setMessage(message)
                .setPositiveButton(R.string.action_delete) { _, _ -> vm.executeDelete(files) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            vm.clearConfirmDeleteEvent()
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess { freed ->
                showSnack(getString(R.string.freed_space_success, MediaFile.formatSize(freed)))
                vm.clearDeleteResult()
            }.onFailure { e ->
                if (e is PendingIntentException) {
                    deleteLauncher.launch(IntentSenderRequest.Builder(e.intentSender).build())
                } else {
                    showSnack(getString(R.string.error_delete_failed))
                    vm.clearDeleteResult()
                }
            }
        }
    }

    private fun renderReport(report: AiCleanReport) {
        currentRecommendations = report.recommendations
        b.scrollContent.visible()

        b.progressHealth.setProgressCompat(report.healthScore, true)
        b.tvHealthScore.text = report.healthScore.toString()
        b.tvHealthStatus.text = when {
            report.healthScore >= 85 -> getString(R.string.ai_health_excellent)
            report.healthScore >= 65 -> getString(R.string.ai_health_good)
            else -> getString(R.string.ai_health_needs_cleanup)
        }

        b.tvReclaimableSummary.text = if (report.totalJunkFiles > 0) {
            getString(
                R.string.ai_reclaimable_summary,
                MediaFile.formatSize(report.totalReclaimableBytes),
                report.totalJunkFiles
            )
        } else {
            getString(R.string.ai_gallery_optimized_desc)
        }

        b.llRecommendations.removeAllViews()
        if (report.recommendations.isEmpty()) {
            b.tvRecHeader.gone()
            b.layoutAllClean.visible()
            b.btnAiCleanNow.gone()
            return
        }

        b.tvRecHeader.visible()
        b.layoutAllClean.gone()

        val inflater = LayoutInflater.from(requireContext())
        report.recommendations.forEach { rec ->
            val card = ItemAiRecommendationBinding.inflate(inflater, b.llRecommendations, false)
            val (titleRes, iconRes, colorRes) = when (rec.type) {
                AiCleanRecommendationType.DUPLICATES -> Triple(R.string.duplicates, R.drawable.ic_duplicate, R.color.color_duplicates)
                AiCleanRecommendationType.BLURRY -> Triple(R.string.blurry_photos, R.drawable.ic_blur, R.color.color_blurry)
                AiCleanRecommendationType.BAD_LIGHTING -> Triple(R.string.dark_photos, R.drawable.ic_dark_photo, R.color.color_dark_photos)
                AiCleanRecommendationType.SIMILAR_EXTRAS -> Triple(R.string.similar_photos, R.drawable.ic_similar, R.color.color_similar)
                AiCleanRecommendationType.OLD_SCREENSHOTS -> Triple(R.string.ai_rec_old_screenshots, R.drawable.ic_screenshot, R.color.color_screenshots)
            }

            card.tvRecTitle.text = getString(titleRes)
            card.tvRecSubtitle.text = getString(
                R.string.items_size_format,
                rec.files.size,
                MediaFile.formatSize(rec.reclaimableBytes)
            )
            card.ivRecIcon.setImageResource(iconRes)
            val tint = requireContext().getColor(colorRes)
            card.ivRecIcon.setColorFilter(tint)
            card.flRecIcon.background?.mutate()?.setTint(
                Color.argb(36, Color.red(tint), Color.green(tint), Color.blue(tint))
            )

            val previews = listOf(card.ivPreview1, card.ivPreview2, card.ivPreview3, card.ivPreview4)
            previews.forEachIndexed { idx, imageView ->
                val f = rec.files.getOrNull(idx)
                if (f != null) {
                    imageView.visible()
                    imageView.load(f.uri) {
                        crossfade(true)
                        placeholder(R.drawable.ic_image_placeholder)
                    }
                } else {
                    imageView.gone()
                }
            }

            card.cbRec.isChecked = rec.isSelected
            card.cbRec.setOnCheckedChangeListener { _, isChecked ->
                rec.isSelected = isChecked
                updateCleanButton()
            }
            card.root.setOnClickListener {
                card.cbRec.toggle()
            }

            b.llRecommendations.addView(card.root)
        }

        updateCleanButton()
    }

    private fun updateCleanButton() {
        val selectedFiles = currentRecommendations
            .filter { it.isSelected }
            .flatMap { it.files }
            .distinctBy { it.id }

        if (selectedFiles.isEmpty()) {
            b.btnAiCleanNow.isEnabled = false
            b.btnAiCleanNow.alpha = 0.5f
            b.btnAiCleanNow.text = getString(R.string.ai_clean_now)
            b.btnAiCleanNow.visibleIf(currentRecommendations.isNotEmpty())
        } else {
            val totalBytes = selectedFiles.sumOf { it.size }
            b.btnAiCleanNow.isEnabled = true
            b.btnAiCleanNow.alpha = 1f
            b.btnAiCleanNow.text = getString(
                R.string.ai_clean_now_size,
                MediaFile.formatSize(totalBytes)
            )
            b.btnAiCleanNow.visible()
        }
    }

    private fun showSnack(msg: String) {
        if (_b != null) Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
