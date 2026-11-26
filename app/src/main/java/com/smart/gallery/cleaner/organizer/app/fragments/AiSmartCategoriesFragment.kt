package com.smart.gallery.cleaner.organizer.app.fragments

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.AiCategory
import com.smart.gallery.cleaner.organizer.app.data.model.AiCategoryBucket
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentAiCategoriesBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemAiCategoryCardBinding
import com.smart.gallery.cleaner.organizer.app.utils.adaptiveSpanCount
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigateUp
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class AiSmartCategoriesFragment : Fragment() {

    private var _b: FragmentAiCategoriesBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()
    private val settingsVm: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }

    private lateinit var mediaAdapter: MediaGridAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private var activeBucket: AiCategoryBucket? = null
    private var activeDialog: androidx.appcompat.app.AlertDialog? = null

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
        _b = FragmentAiCategoriesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (activeBucket != null) {
                        closeCategoryDetail()
                    } else {
                        isEnabled = false
                        findNavController().navigateUp()
                    }
                }
            }
        )

        b.btnBack.setOnClickListener {
            if (activeBucket != null) closeCategoryDetail()
            else findNavController().navigateUp()
        }

        mediaAdapter = MediaGridAdapter(
            onItemClick = { file ->
                if ((vm.selectedCount.value ?: 0) > 0) vm.toggleSelection(file)
                else MediaGridAdapter.openInDefaultViewer(requireContext(), file)
            },
            onSelectionChanged = { file -> vm.toggleSelection(file) },
            onLongClick = { file -> vm.toggleSelection(file) }
        )

        categoryAdapter = CategoryAdapter { openCategoryDetail(it) }
        b.rvCategories.layoutManager = GridLayoutManager(requireContext(), requireContext().adaptiveSpanCount(2, 2))
        b.rvCategories.adapter = categoryAdapter
        b.rvDetailMedia.layoutManager = GridLayoutManager(requireContext(), requireContext().adaptiveSpanCount(3, 2))
        b.rvDetailMedia.adapter = mediaAdapter

        b.btnDeleteSelected.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isNotEmpty()) vm.deleteFiles(selected)
        }

        observeViewModel()
        if (!vm.hasLoadedAiCategories && vm.loading.value != true) {
            vm.loadAiSmartCategories()
        }
    }

    private fun renderCategoryOverview() {
        val loading = vm.loading.value == true
        val buckets = vm.aiCategories.value.orEmpty()

        if (activeBucket == null) {
            val savedCategory = vm.activeAiCategory
            if (!loading && savedCategory != null) {
                val match = buckets.firstOrNull { it.category == savedCategory }
                if (match != null) {
                    openCategoryDetail(match, preserveSelection = true)
                    return
                }
            }
        } else {
            // Keep active bucket reference in sync if items were deleted
            val updated = buckets.firstOrNull { it.category == activeBucket?.category }
            if (updated != null) activeBucket = updated
            return
        }

        b.layoutLoading.visibleIf(loading)
        b.rvDetailMedia.gone()
        b.btnDeleteSelected.gone()

        if (loading) {
            b.rvCategories.gone()
            b.layoutEmpty.gone()
        } else {
            categoryAdapter.submitList(buckets)
            b.rvCategories.visibleIf(buckets.isNotEmpty())
            b.layoutEmpty.visibleIf(buckets.isEmpty() && vm.hasLoadedAiCategories)
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) {
            renderCategoryOverview()
        }

        vm.scanProgress.observe(viewLifecycleOwner) { p ->
            b.progressCategories.setProgress(p.percent, true)
            b.tvLoadingPercent.text = "${p.percent}%"
            b.tvLoadingCounts.text = if (p.total > 0) {
                getString(R.string.scan_files_progress_format, p.processed, p.total, p.remaining)
            } else ""
        }

        vm.aiCategories.observe(viewLifecycleOwner) {
            renderCategoryOverview()
        }

        vm.files.observe(viewLifecycleOwner) { files ->
            if (activeBucket != null) {
                mediaAdapter.submitList(files.toList())
                b.rvDetailMedia.visibleIf(files.isNotEmpty())
                b.layoutEmpty.visibleIf(files.isEmpty())
                b.tvToolbarSubtitle.text = getString(
                    R.string.items_size_format,
                    files.size,
                    MediaFile.formatSize(files.sumOf { it.size })
                )
            }
        }

        vm.selectedCount.observe(viewLifecycleOwner) { count ->
            if (activeBucket != null) {
                b.btnDeleteSelected.visibleIf(count > 0)
                b.btnDeleteSelected.text = if (count > 0) {
                    "${getString(R.string.action_delete)} ($count)"
                } else {
                    getString(R.string.action_delete)
                }
            } else {
                b.btnDeleteSelected.gone()
            }
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

            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(resources.getQuantityString(R.plurals.delete_dialog_title, count, count))
                .setMessage(message)
                .setPositiveButton(R.string.action_delete) { _, _ -> vm.executeDelete(files) }
                .setNegativeButton(R.string.action_cancel) { _, _ -> vm.deselectAll() }
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

    private fun openCategoryDetail(bucket: AiCategoryBucket, preserveSelection: Boolean = false) {
        activeBucket = bucket
        vm.activeAiCategory = bucket.category
        b.tvToolbarTitle.text = getCategoryTitle(bucket.category)
        b.tvToolbarSubtitle.text = getString(
            R.string.items_size_format,
            bucket.files.size,
            MediaFile.formatSize(bucket.totalSize)
        )
        b.rvCategories.gone()
        b.rvDetailMedia.visibleIf(bucket.files.isNotEmpty())
        b.layoutEmpty.visibleIf(bucket.files.isEmpty())
        if (!preserveSelection || vm.files.value.isNullOrEmpty()) {
            vm.setDirectFiles(bucket.files)
        }
    }

    private fun closeCategoryDetail() {
        activeBucket = null
        vm.activeAiCategory = null
        vm.deselectAll()
        b.tvToolbarTitle.text = getString(R.string.ai_organizer_title)
        b.tvToolbarSubtitle.text = getString(R.string.ai_organizer_subtitle)
        renderCategoryOverview()
    }

    private fun getCategoryTitle(category: AiCategory): String = when (category) {
        AiCategory.PORTRAITS -> getString(R.string.ai_cat_portraits)
        AiCategory.DOCUMENTS -> getString(R.string.ai_cat_documents)
        AiCategory.BAD_LIGHTING -> getString(R.string.dark_photos)
        AiCategory.NATURE -> getString(R.string.ai_cat_nature)
        AiCategory.MEMES_SOCIAL -> getString(R.string.ai_cat_social)
        AiCategory.BEST_SHOTS -> getString(R.string.ai_cat_best_shots)
    }

    private fun getCategoryIcon(category: AiCategory): Int = when (category) {
        AiCategory.PORTRAITS -> R.drawable.ic_portrait
        AiCategory.DOCUMENTS -> R.drawable.ic_document
        AiCategory.BAD_LIGHTING -> R.drawable.ic_dark_photo
        AiCategory.NATURE -> R.drawable.ic_nature
        AiCategory.MEMES_SOCIAL -> R.drawable.ic_whatsapp
        AiCategory.BEST_SHOTS -> R.drawable.ic_star
    }

    private inner class CategoryAdapter(
        private val onClick: (AiCategoryBucket) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        private var items: List<AiCategoryBucket> = emptyList()

        fun submitList(newItems: List<AiCategoryBucket>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemAiCategoryCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val vb = ItemAiCategoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(vb)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val bucket = items[position]
            holder.binding.tvCatTitle.text = getCategoryTitle(bucket.category)
            holder.binding.tvCatCount.text = bucket.files.size.toString()
            holder.binding.tvCatDesc.text = MediaFile.formatSize(bucket.totalSize)
            holder.binding.ivCatIcon.setImageResource(getCategoryIcon(bucket.category))

            val cover = bucket.files.firstOrNull()
            if (cover != null) {
                holder.binding.ivCover.load(cover.uri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image_placeholder)
                }
            } else {
                holder.binding.ivCover.setImageResource(R.drawable.ic_image_placeholder)
            }

            holder.binding.root.setOnClickListener { onClick(bucket) }
        }
    }

    private fun showSnack(msg: String) {
        if (_b != null) Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroyView()
        _b = null
    }
}
