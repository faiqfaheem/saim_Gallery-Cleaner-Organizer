package com.smart.gallery.cleaner.organizer.app.fragments

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import coil.decode.VideoFrameDecoder
import coil.load
import coil.request.videoFrameMillis
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.ScanResult
import com.smart.gallery.cleaner.organizer.app.data.model.ScanState
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentHomeBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemFeatureCardBinding
import com.smart.gallery.cleaner.organizer.app.utils.PermissionHelper
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigate
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.HomeViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val settingsVM: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }

    private lateinit var aiOrganizerCard: ItemFeatureCardBinding
    private lateinit var darkPhotosCard: ItemFeatureCardBinding
    private lateinit var largeFilesCard: ItemFeatureCardBinding
    private lateinit var duplicatesCard: ItemFeatureCardBinding
    private lateinit var similarCard: ItemFeatureCardBinding
    private lateinit var blurryCard: ItemFeatureCardBinding
    private lateinit var screenshotsCard: ItemFeatureCardBinding
    private lateinit var videosCard: ItemFeatureCardBinding
    private lateinit var downloadsCard: ItemFeatureCardBinding
    private lateinit var whatsappCard: ItemFeatureCardBinding

    private var pendingNavigationAction: Int? = null
    private var storageWarningEnabled = true
    private var activeDialog: AlertDialog? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!isAdded || _binding == null) return@registerForActivityResult
        val granted = PermissionHelper.hasStoragePermission(requireContext())
        if (granted) {
            viewModel.loadStorageInfo()
            viewModel.startScan()
            pendingNavigationAction?.let { actionId ->
                pendingNavigationAction = null
                safeNavigate(actionId)
            }
        } else {
            pendingNavigationAction = null
            val showRationale = PermissionHelper.REQUIRED_PERMISSIONS.any {
                shouldShowRequestPermissionRationale(it)
            }
            if (showRationale) showPermissionRationale() else showSettingsDialog()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindIncludedCards()
        setupCardContents()
        setupClickListeners()
        setPlaceholders()
        observeViewModel()

        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewModel.loadInitialDataAndScan()
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            viewModel.loadInitialDataAndScan()
        }
    }

    private fun setPlaceholders() {
        binding.tvStorageUsed.text = getString(R.string.state_loading)
        binding.tvStorageOf.text = ""
        binding.tvUsagePercent.text = "–%"
        binding.progressCircle.progress = 0
        binding.tvPhotoCount.text = "–"
        binding.tvVideoCount.text = "–"
        binding.tvJunkCount.text = "–"
        binding.cardScanProgress.gone()
    }

    private fun bindIncludedCards() {
        aiOrganizerCard = ItemFeatureCardBinding.bind(binding.cardAiOrganizer.root)
        darkPhotosCard = ItemFeatureCardBinding.bind(binding.cardDarkPhotos.root)
        largeFilesCard = ItemFeatureCardBinding.bind(binding.cardLargeFiles.root)
        duplicatesCard = ItemFeatureCardBinding.bind(binding.cardDuplicates.root)
        similarCard = ItemFeatureCardBinding.bind(binding.cardSimilar.root)
        blurryCard = ItemFeatureCardBinding.bind(binding.cardBlurry.root)
        screenshotsCard = ItemFeatureCardBinding.bind(binding.cardScreenshots.root)
        videosCard = ItemFeatureCardBinding.bind(binding.cardVideos.root)
        downloadsCard = ItemFeatureCardBinding.bind(binding.cardDownloads.root)
        whatsappCard = ItemFeatureCardBinding.bind(binding.cardWhatsapp.root)
    }

    private fun setupCardContents() {
        data class CardInfo(
            val binding: ItemFeatureCardBinding,
            val iconRes: Int,
            val titleRes: Int,
            val colorRes: Int
        )

        listOf(
            CardInfo(aiOrganizerCard, R.drawable.ic_ai_category, R.string.ai_organizer_title, R.color.color_ai_organizer),
            CardInfo(darkPhotosCard, R.drawable.ic_dark_photo, R.string.dark_photos, R.color.color_dark_photos),
            CardInfo(largeFilesCard, R.drawable.ic_large_file, R.string.large_files, R.color.color_large_files),
            CardInfo(duplicatesCard, R.drawable.ic_duplicate, R.string.duplicates, R.color.color_duplicates),
            CardInfo(similarCard, R.drawable.ic_similar, R.string.similar_photos, R.color.color_similar),
            CardInfo(blurryCard, R.drawable.ic_blur, R.string.blurry_photos, R.color.color_blurry),
            CardInfo(screenshotsCard, R.drawable.ic_screenshot, R.string.screenshots, R.color.color_screenshots),
            CardInfo(videosCard, R.drawable.ic_video, R.string.videos, R.color.color_videos),
            CardInfo(downloadsCard, R.drawable.ic_download, R.string.downloads, R.color.color_downloads),
            CardInfo(whatsappCard, R.drawable.ic_whatsapp, R.string.whatsapp_short, R.color.color_whatsapp)
        ).forEach { card ->
            card.binding.tvFeatureTitle.text = getString(card.titleRes)
            card.binding.ivFeatureIcon.setImageResource(card.iconRes)
            card.binding.ivEmptyIcon.setImageResource(card.iconRes)

            val color = requireContext().getColor(card.colorRes)
            card.binding.ivFeatureIcon.setColorFilter(Color.WHITE)
            card.binding.flIconContainer.background?.mutate()?.setTint(
                Color.argb(235, Color.red(color), Color.green(color), Color.blue(color))
            )
            card.binding.ivEmptyIcon.setColorFilter(color)
            card.binding.viewEmptyTint.setBackgroundColor(
                Color.argb(46, Color.red(color), Color.green(color), Color.blue(color))
            )
            card.binding.root.tag = color

            card.binding.tvFilesSize.text = "—"
            card.binding.tvFilesCount.text = "—"
            card.binding.tvFeatureTitle.setTextColor(requireContext().getColor(R.color.on_surface))
            card.binding.tvFilesSize.setTextColor(color)
            card.binding.llCollage.gone()
            card.binding.viewScrim.gone()
            card.binding.ivEmptyIcon.visible()
        }
    }

    private fun setupClickListeners() {
        binding.cardAiSmartClean.setOnClickListener { navigateWithPermission(R.id.action_home_to_aiSmartClean) }
        binding.cardAiOrganizer.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_aiCategories) }
        binding.cardDarkPhotos.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_darkPhotos) }
        binding.cardLargeFiles.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_largeFiles) }
        binding.cardDuplicates.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_duplicates) }
        binding.cardSimilar.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_similar) }
        binding.cardBlurry.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_blurry) }
        binding.cardScreenshots.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_screenshots) }
        binding.cardVideos.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_videos) }
        binding.cardDownloads.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_downloads) }
        binding.cardWhatsapp.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_whatsapp) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.cardTrash.setOnClickListener { navigateWithPermission(R.id.action_home_to_trash) }
            binding.cardTrash.visible()
        } else {
            binding.cardTrash.gone()
        }
    }

    private fun navigateWithPermission(actionId: Int) {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            safeNavigate(actionId)
        } else {
            pendingNavigationAction = actionId
            runCatching { permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS) }
        }
    }

    private fun observeViewModel() {
        settingsVM.storageWarning.observe(viewLifecycleOwner) { enabled ->
            storageWarningEnabled = enabled
            viewModel.storageInfo.value?.let { updateStorageWarningBanner(it) }
        }

        viewModel.storageInfo.observe(viewLifecycleOwner) { info ->
            renderStorageCard(info)
        }

        viewModel.scanState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScanState.Idle -> binding.cardScanProgress.gone()
                is ScanState.Scanning -> {
                    binding.cardScanProgress.visible()
                    binding.progressScan.progress = state.progress
                    binding.tvScanMessage.text = state.message
                    binding.tvScanPercent.text = "${state.progress}%"
                    if (state.total > 0) {
                        binding.tvScanCounts.text = getString(
                            R.string.scan_files_progress_format,
                            state.processed,
                            state.total,
                            state.remaining
                        )
                        binding.tvScanCounts.visible()
                    } else {
                        binding.tvScanCounts.gone()
                    }
                }
                is ScanState.Complete -> {
                    binding.cardScanProgress.gone()
                    renderScanResult(state.result)
                }
                is ScanState.Error -> {
                    binding.cardScanProgress.gone()
                    Snackbar.make(binding.root, R.string.home_scan_failed, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            result?.let { renderScanResult(it) }
        }

        viewModel.trashedCount.observe(viewLifecycleOwner) { count ->
            if (count > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.tvTrashCount.text = resources.getQuantityString(R.plurals.items_count, count, count)
                binding.tvTrashCount.visible()
            } else {
                binding.tvTrashCount.gone()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let {
                Snackbar.make(binding.root, getString(R.string.error_prefix, it), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateStorageWarningBanner(info: StorageInfo) {
        binding.bannerStorageWarning.visibleIf(storageWarningEnabled && info.usagePercent >= 85)
    }

    private fun renderStorageCard(info: StorageInfo) {
        if (info.totalSpace == 0L) return

        updateStorageWarningBanner(info)

        binding.tvStorageUsed.text = info.formattedUsed
        binding.tvStorageOf.text = getString(R.string.storage_of_format, info.formattedTotal, info.formattedFree)
        binding.tvUsagePercent.text = "${info.usagePercent}%"
        binding.progressCircle.setProgressCompat(info.usagePercent, true)

        binding.tvPhotoCount.text = formatCount(info.photoCount)
        binding.tvVideoCount.text = formatCount(info.videoCount)

        if (binding.tvJunkCount.text.toString() in listOf("–", "−")) {
            binding.tvJunkCount.text = "0"
        }
    }

    private fun renderScanResult(result: ScanResult) {
        binding.tvPhotoCount.text = formatCount(result.totalPhotos)
        binding.tvVideoCount.text = formatCount(result.totalVideos)
        binding.tvJunkCount.text = formatCount(result.junkCount)

        binding.tvHealthPill.text = "${result.healthScore}/100"
        if (result.totalReclaimable > 0L) {
            binding.tvAiCleanSubtitle.text = getString(
                R.string.ai_reclaimable_summary,
                MediaFile.formatSize(result.totalReclaimable),
                result.junkCount
            )
        }

        // Render live 3-photo mini stack on AI Smart Clean card
        val aiStackUris = buildList {
            addAll(result.duplicatePreviewUris)
            addAll(result.blurryPreviewUris)
            addAll(result.similarPreviewUris)
            addAll(result.recentPhotoUris)
        }.distinct().take(3)
        renderAiPhotoStack(aiStackUris)

        val effectiveVideoSize = if (result.videosSize > 0L) {
            result.videosSize
        } else {
            viewModel.storageInfo.value?.videoSize ?: 0L
        }
        val totalImagesSize = viewModel.storageInfo.value?.imageSize ?: 0L

        updateCardVisuals(aiOrganizerCard, result.totalPhotos, totalImagesSize, result.recentPhotoUris)
        updateCardVisuals(darkPhotosCard, result.darkBadCount, result.darkBadSize, result.darkBadPreviewUris)
        updateCardVisuals(largeFilesCard, result.largeFilesCount, result.largeFilesSize, result.largeFilePreviewUris)
        updateCardVisuals(duplicatesCard, result.duplicatesCount, result.duplicatesSize, result.duplicatePreviewUris)
        updateCardVisuals(similarCard, result.similarCount, result.similarSize, result.similarPreviewUris)
        updateCardVisuals(blurryCard, result.blurryCount, result.blurrySize, result.blurryPreviewUris)
        updateCardVisuals(screenshotsCard, result.screenshotsCount, result.screenshotsSize, result.screenshotPreviewUris)
        updateCardVisuals(videosCard, result.totalVideos, effectiveVideoSize, result.videoPreviewUris)
        updateCardVisuals(downloadsCard, result.downloadsCount, result.downloadsSize, result.downloadPreviewUris)
        updateCardVisuals(whatsappCard, result.whatsappCount, result.whatsappSize, result.whatsappPreviewUris)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewModel.loadTrashedCount()
        }
    }

    private fun renderAiPhotoStack(uris: List<Uri>) {
        if (uris.isEmpty()) {
            binding.flAiPhotoStack.gone()
            binding.flAiFallbackIcon.visible()
            return
        }
        binding.flAiFallbackIcon.gone()
        binding.flAiPhotoStack.visible()

        loadThumbnail(binding.ivAiThumb1, uris[0])
        binding.cardAiThumb1.visible()

        if (uris.size >= 2) {
            loadThumbnail(binding.ivAiThumb2, uris[1])
            binding.cardAiThumb2.visible()
        } else {
            binding.cardAiThumb2.gone()
        }

        if (uris.size >= 3) {
            loadThumbnail(binding.ivAiThumb3, uris[2])
            binding.cardAiThumb3.visible()
        } else {
            binding.cardAiThumb3.gone()
        }
    }

    private fun updateCardVisuals(
        card: ItemFeatureCardBinding,
        count: Int,
        size: Long,
        previews: List<Uri>
    ) {
        card.tvFilesSize.text = if (size > 0L) MediaFile.formatSize(size) else "0 B"
        card.tvFilesCount.text = formatCount(count)
        val accentColor = (card.root.tag as? Int) ?: requireContext().getColor(R.color.primary)

        if (previews.isNotEmpty()) {
            card.llCollage.visible()
            card.viewScrim.visible()
            card.ivEmptyIcon.gone()
            card.tvFeatureTitle.setTextColor(Color.WHITE)
            card.tvFilesSize.setTextColor(requireContext().getColor(R.color.hero_ring))

            loadThumbnail(card.ivPreviewMain, previews[0])
            if (previews.size >= 2) {
                card.dividerCollage.visible()
                card.ivPreviewSecond.visible()
                loadThumbnail(card.ivPreviewSecond, previews[1])
            } else {
                card.dividerCollage.gone()
                card.ivPreviewSecond.gone()
            }
        } else {
            card.llCollage.gone()
            card.viewScrim.gone()
            card.ivEmptyIcon.visible()
            card.tvFeatureTitle.setTextColor(requireContext().getColor(R.color.on_surface))
            card.tvFilesSize.setTextColor(accentColor)
        }
    }

    private fun loadThumbnail(imageView: ImageView, uri: Uri) {
        val isVideo = uri.toString().contains("/video/", ignoreCase = true)
        imageView.load(uri) {
            crossfade(180)
            size(280)
            placeholder(R.drawable.ic_image_placeholder)
            error(R.drawable.ic_image_placeholder)
            if (isVideo) {
                videoFrameMillis(1000L)
                decoderFactory { result, options, _ ->
                    VideoFrameDecoder(result.source, options)
                }
            }
        }
    }

    private fun formatCount(n: Int): String = when {
        n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000f)}M"
        n >= 1_000 -> "${"%.1f".format(n / 1_000f)}K"
        else -> n.toString()
    }

    private fun showPermissionRationale() {
        if (!isAdded) return
        activeDialog?.dismiss()
        activeDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perm_title)
            .setMessage(R.string.perm_rationale_msg)
            .setPositiveButton(R.string.perm_grant) { _, _ ->
                runCatching { permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS) }
            }
            .setNegativeButton(R.string.perm_not_now, null)
            .show()
    }

    private fun showSettingsDialog() {
        if (!isAdded) return
        activeDialog?.dismiss()
        activeDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.perm_denied_title)
            .setMessage(R.string.perm_denied_msg)
            .setPositiveButton(R.string.perm_open_settings) { _, _ ->
                PermissionHelper.openAppSettings(requireContext())
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroyView()
        _binding = null
    }
}
