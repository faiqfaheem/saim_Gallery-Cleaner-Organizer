package com.smart.gallery.cleaner.organizer.app.fragments

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.ScanResult
import com.smart.gallery.cleaner.organizer.app.data.model.ScanState
import com.smart.gallery.cleaner.organizer.app.data.model.StorageInfo
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentHomeBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemFeatureCardBinding
import com.smart.gallery.cleaner.organizer.app.utils.PermissionHelper
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.viewmodel.HomeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var largeFilesCard: ItemFeatureCardBinding
    private lateinit var duplicatesCard: ItemFeatureCardBinding
    private lateinit var similarCard: ItemFeatureCardBinding
    private lateinit var blurryCard: ItemFeatureCardBinding
    private lateinit var screenshotsCard: ItemFeatureCardBinding
    private lateinit var videosCard: ItemFeatureCardBinding
    private lateinit var downloadsCard: ItemFeatureCardBinding
    private lateinit var whatsappCard: ItemFeatureCardBinding

    private var pendingNavigationAction: Int? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = PermissionHelper.hasStoragePermission(requireContext())
        if (granted) {
            viewModel.loadStorageInfo()
            viewModel.startScan()
            pendingNavigationAction?.let { actionId ->
                pendingNavigationAction = null
                findNavController().navigate(actionId)
            }
        } else {
            pendingNavigationAction = null
            val showRationale = PermissionHelper.REQUIRED_PERMISSIONS.any {
                shouldShowRequestPermissionRationale(it)
            }
            if (showRationale) {
                showPermissionRationale()
            } else {
                showSettingsDialog()
            }
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
            viewModel.loadStorageInfo()
        }
    }

    override fun onResume() {
        super.onResume()
        // If user returns from settings with granted permissions
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            if (viewModel.storageInfo.value == null) {
                viewModel.loadStorageInfo()
            }
        }
    }

    private fun setPlaceholders() {
        binding.tvStorageUsed.text = "Loading…"
        binding.tvStorageOf.text = ""
        binding.tvUsagePercent.text = "–%"
        binding.tvFreeStorage.text = ""
        binding.progressCircle.progress = 0
        binding.progressStorage.progress = 0
        binding.tvPhotoCount.text = "–"
        binding.tvVideoCount.text = "–"
        binding.tvJunkCount.text = "–"
        binding.tvLastScan.text = "Tap Scan to start"
        binding.cardScanProgress.gone()
    }

    private fun bindIncludedCards() {
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
            val title: String,
            val defaultDesc: String,
            val colorRes: Int
        )

        listOf(
            CardInfo(largeFilesCard, R.drawable.ic_large_file, "Large Files", "Biggest photos & videos", R.color.color_large_files),
            CardInfo(duplicatesCard, R.drawable.ic_duplicate, "Duplicates", "Exact copies wasting space", R.color.color_duplicates),
            CardInfo(similarCard, R.drawable.ic_similar, "Similar", "Visually alike images", R.color.color_similar),
            CardInfo(blurryCard, R.drawable.ic_blur, "Blurry Photos", "Out-of-focus or low quality", R.color.color_blurry),
            CardInfo(screenshotsCard, R.drawable.ic_screenshot, "Screenshots", "Captured screens", R.color.color_screenshots),
            CardInfo(videosCard, R.drawable.ic_video, "Videos", "All video files", R.color.color_videos),
            CardInfo(downloadsCard, R.drawable.ic_download, "Downloads", "Downloaded media", R.color.color_downloads),
            CardInfo(whatsappCard, R.drawable.ic_whatsapp, "WhatsApp", "Images & videos", R.color.color_whatsapp)
        ).forEach { card ->
            card.binding.tvFeatureTitle.text = card.title
            card.binding.tvFeatureDefaultDesc.text = card.defaultDesc
            card.binding.ivFeatureIcon.setImageResource(card.iconRes)

            val color = requireContext().getColor(card.colorRes)
            card.binding.ivFeatureIcon.setColorFilter(color)
            card.binding.flIconContainer.background?.mutate()?.setTint(
                Color.argb(
                    38,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
            )

            // Hide horizontal divider and stats row at initial launch
            val rootLayout = card.binding.tvFeatureTitle.parent as? ViewGroup
            rootLayout?.getChildAt(3)?.gone()
            val filesRowView = (card.binding.tvFilesSize.parent as? View)?.parent as? View
            filesRowView?.gone()
        }
    }

    private fun setupClickListeners() {
        binding.btnScan.setOnClickListener {
            checkPermissionAndPerform { viewModel.startScan() }
        }

        binding.cardLargeFiles.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_largeFiles) }
        binding.cardDuplicates.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_duplicates) }
        binding.cardSimilar.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_similar) }
        binding.cardBlurry.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_blurry) }
        binding.cardScreenshots.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_screenshots) }
        binding.cardVideos.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_videos) }
        binding.cardDownloads.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_downloads) }
        binding.cardWhatsapp.root.setOnClickListener { navigateWithPermission(R.id.action_home_to_whatsapp) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.rowTrash.setOnClickListener { navigateWithPermission(R.id.action_home_to_trash) }
            binding.rowTrash.visible()
            binding.dividerTrash.visible()
        } else {
            binding.rowTrash.gone()
            binding.dividerTrash.gone()
        }
    }

    private fun navigateWithPermission(actionId: Int) {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            findNavController().navigate(actionId)
        } else {
            pendingNavigationAction = actionId
            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
        }
    }

    private fun checkPermissionAndPerform(action: () -> Unit) {
        if (PermissionHelper.hasStoragePermission(requireContext())) {
            action()
        } else {
            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
        }
    }

    private fun observeViewModel() {
        viewModel.storageInfo.observe(viewLifecycleOwner) { info ->
            renderStorageCard(info)
        }

        viewModel.scanState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ScanState.Idle -> {
                    binding.cardScanProgress.gone()
                }
                is ScanState.Scanning -> {
                    binding.cardScanProgress.visible()
                    binding.progressScan.progress = state.progress
                    binding.tvScanMessage.text = state.message
                    binding.btnScan.isEnabled = false
                    binding.btnScan.alpha = 0.6f
                }
                is ScanState.Complete -> {
                    binding.cardScanProgress.gone()
                    binding.tvLastScan.text = "Just scanned"
                    binding.btnScan.isEnabled = true
                    binding.btnScan.alpha = 1.0f
                    renderScanResult(state.result)
                }
                is ScanState.Error -> {
                    binding.cardScanProgress.gone()
                    binding.tvLastScan.text = "Scan failed — tap to retry"
                    binding.btnScan.isEnabled = true
                    binding.btnScan.alpha = 1.0f
                }
            }
        }

        viewModel.scanResult.observe(viewLifecycleOwner) { result ->
            result?.let { renderScanResult(it) }
        }

        viewModel.trashedCount.observe(viewLifecycleOwner) { count ->
            if (count > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.tvTrashCount.text = "$count item${if (count == 1) "" else "s"}"
                binding.tvTrashCount.visible()
            } else {
                binding.tvTrashCount.gone()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { binding.tvLastScan.text = "Error: $it" }
        }
    }

    private fun renderStorageCard(info: StorageInfo) {
        if (info.totalSpace == 0L) return

        binding.tvStorageUsed.text = info.formattedUsed
        binding.tvStorageOf.text = "of ${info.formattedTotal} · ${info.formattedFree} free"
        binding.tvUsagePercent.text = "${info.usagePercent}%"
        binding.tvFreeStorage.text = "${info.formattedFree} available"
        binding.progressCircle.setProgressCompat(info.usagePercent, true)
        binding.progressStorage.setProgress(info.usagePercent, true)

        binding.tvPhotoCount.text = formatCount(info.photoCount)
        binding.tvVideoCount.text = formatCount(info.videoCount)

        if (binding.tvJunkCount.text.toString() in listOf("–", "−")) {
            binding.tvJunkCount.text = "Scan"
        }
    }

    private fun renderScanResult(result: ScanResult) {
        binding.tvPhotoCount.text = formatCount(result.totalPhotos)
        binding.tvVideoCount.text = formatCount(result.totalVideos)
        binding.tvJunkCount.text = formatCount(result.junkCount)

        fun updateCardDetails(
            card: ItemFeatureCardBinding,
            count: Int,
            size: Long
        ) {
            val rootLayout = card.tvFeatureTitle.parent as? ViewGroup
            val dividerView = rootLayout?.getChildAt(3)
            val filesRowView = (card.tvFilesSize.parent as? View)?.parent as? View

            if (count > 0) {
                val formattedSize = if (size > 0) MediaFile.formatSize(size) else "0 B"
                card.tvFilesSize.text = formattedSize
                card.tvFilesCount.text = if (count == 1) "1" else "$count"

                dividerView?.visible()
                filesRowView?.visible()
            } else {
                dividerView?.gone()
                filesRowView?.gone()
            }
        }

        val effectiveVideoSize = if (result.videosSize > 0L) {
            result.videosSize
        } else {
            viewModel.storageInfo.value?.videoSize ?: 0L
        }

        updateCardDetails(largeFilesCard, result.largeFilesCount, result.largeFilesSize)
        updateCardDetails(duplicatesCard, result.duplicatesCount, result.duplicatesSize)
        updateCardDetails(similarCard, result.similarCount, result.similarSize)
        updateCardDetails(blurryCard, result.blurryCount, result.blurrySize)
        updateCardDetails(screenshotsCard, result.screenshotsCount, result.screenshotsSize)
        updateCardDetails(videosCard, result.totalVideos, effectiveVideoSize)
        updateCardDetails(downloadsCard, result.downloadsCount, result.downloadsSize)
        updateCardDetails(whatsappCard, result.whatsappCount, result.whatsappSize)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewModel.loadTrashedCount()
        }
    }

    private fun formatCount(n: Int): String = when {
        n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000f)}M"
        n >= 1_000 -> "${"%.1f".format(n / 1_000f)}K"
        else -> n.toString()
    }

    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Photos & Videos Permission Required")
            .setMessage("Gallery Cleaner needs access to your photos and videos to scan for large files, duplicates, and other junk.")
            .setPositiveButton("Grant") { _, _ ->
                permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    private fun showSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission Denied")
            .setMessage("Photos and Videos permission is required to scan files. Please turn on permission in App Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                PermissionHelper.openAppSettings(requireContext())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}