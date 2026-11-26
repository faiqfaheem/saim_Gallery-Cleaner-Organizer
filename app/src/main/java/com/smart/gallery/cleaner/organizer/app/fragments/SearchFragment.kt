package com.smart.gallery.cleaner.organizer.app.fragments

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentSearchBinding
import com.smart.gallery.cleaner.organizer.app.utils.adaptiveSpanCount
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.toFormattedDate
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class SearchFragment : Fragment() {

    private enum class SmartFilter {
        ALL, IMAGES, VIDEOS, GIFS, AI_BLURRY, AI_DARK, AI_DOCS, AI_BEST
    }

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()
    private val settingsVm: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }

    private var activeFilter = SmartFilter.ALL
    private var fullList: List<MediaFile> = emptyList()
    private var loaded = false
    private var aiEnriched = false

    private lateinit var adapter: MediaGridAdapter

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

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSearchBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        setupAdapter()
        setupSearch()
        setupChips()
        setupDeleteButton()
        observeViewModel()

        if (!vm.hasLoadedFiles && vm.loading.value != true) {
            vm.loadAllMedia()
        }
    }

    private fun setupAdapter() {
        adapter = MediaGridAdapter(
            onItemClick = { file ->
                if ((vm.selectedCount.value ?: 0) > 0) vm.toggleSelection(file)
                else MediaGridAdapter.openInDefaultViewer(requireContext(), file)
            },
            onSelectionChanged = { file -> vm.toggleSelection(file) },
            onLongClick = { file -> vm.toggleSelection(file) }
        )
        b.recyclerView.layoutManager = GridLayoutManager(requireContext(), requireContext().adaptiveSpanCount(3))
        b.recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        b.etSearch.doAfterTextChanged { editable ->
            val q = editable?.toString()?.trim() ?: ""
            b.btnClear.visibleIf(q.isNotEmpty())
            maybeTriggerAiEnrichment(q)
            filterAndShow(q)
        }

        b.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterAndShow(b.etSearch.text?.toString()?.trim() ?: "")
                true
            } else false
        }

        b.btnClear.setOnClickListener {
            b.etSearch.setText("")
            filterAndShow("")
        }
    }

    private fun setupChips() {
        if (vm.activeSearchChipId != -1) {
            b.chipGroupFilter.check(vm.activeSearchChipId)
            activeFilter = chipIdToFilter(vm.activeSearchChipId)
        }
        b.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: R.id.chip_all
            vm.activeSearchChipId = id
            activeFilter = chipIdToFilter(id)
            if (activeFilter in setOf(SmartFilter.AI_BLURRY, SmartFilter.AI_DARK, SmartFilter.AI_DOCS, SmartFilter.AI_BEST)) {
                triggerAiEnrichment()
            }
            filterAndShow(b.etSearch.text?.toString()?.trim() ?: "")
        }
    }

    private fun chipIdToFilter(id: Int): SmartFilter = when (id) {
        R.id.chip_images -> SmartFilter.IMAGES
        R.id.chip_videos -> SmartFilter.VIDEOS
        R.id.chip_gifs -> SmartFilter.GIFS
        R.id.chip_ai_blurry -> SmartFilter.AI_BLURRY
        R.id.chip_ai_dark -> SmartFilter.AI_DARK
        R.id.chip_ai_docs -> SmartFilter.AI_DOCS
        R.id.chip_ai_best -> SmartFilter.AI_BEST
        else -> SmartFilter.ALL
    }

    private fun setupDeleteButton() {
        b.btnDeleteSelected.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isNotEmpty()) vm.deleteFiles(selected)
        }
    }

    private fun maybeTriggerAiEnrichment(query: String) {
        val aiKeywords = listOf("blur", "dark", "doc", "note", "receipt", "nature", "best", "portrait", "selfie")
        if (aiKeywords.any { query.contains(it, ignoreCase = true) }) {
            triggerAiEnrichment()
        }
    }

    private fun triggerAiEnrichment() {
        if (!aiEnriched && fullList.isNotEmpty()) {
            aiEnriched = true
            vm.enrichMediaWithAiTags(fullList.take(350))
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            if (loading && !loaded) showLoading()
        }

        vm.files.observe(viewLifecycleOwner) { files ->
            fullList = files
            loaded = true
            filterAndShow(b.etSearch.text?.toString()?.trim() ?: "")
        }

        vm.selectedCount.observe(viewLifecycleOwner) { count ->
            b.btnDeleteSelected.visibleIf(count > 0)
            b.btnDeleteSelected.text = if (count > 0) {
                "${getString(R.string.action_delete)} ($count)"
            } else {
                getString(R.string.action_delete)
            }
        }

        vm.confirmDeleteEvent.observe(viewLifecycleOwner) { files ->
            files ?: return@observe
            val count = files.size
            val size = MediaFile.formatSize(files.sumOf { it.size })
            val isSafe = settingsVm.safeDelete.value == true
            val isTrash = isSafe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val message = if (isTrash) {
                getString(R.string.move_to_trash_dialog_message, count, size)
            } else {
                getString(R.string.delete_dialog_message, count, size)
            }
            MaterialAlertDialogBuilder(requireContext())
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

    private fun filterAndShow(query: String) {
        if (!loaded) return
        val byType: List<MediaFile> = when (activeFilter) {
            SmartFilter.IMAGES -> fullList.filter { it.isImage && !it.isGif }
            SmartFilter.VIDEOS -> fullList.filter { it.isVideo }
            SmartFilter.GIFS -> fullList.filter { it.isGif }
            SmartFilter.AI_BLURRY -> fullList.filter { it.isBlurry }
            SmartFilter.AI_DARK -> fullList.filter { it.isDarkOrOverexposed }
            SmartFilter.AI_DOCS -> fullList.filter { it.isDocumentLike }
            SmartFilter.AI_BEST -> fullList.filter { it.qualityScore >= 72f && !it.isBlurry && !it.isDarkOrOverexposed }
            SmartFilter.ALL -> fullList
        }

        val result: List<MediaFile> = if (query.isNotEmpty()) {
            val lower = query.lowercase()
            val now = System.currentTimeMillis()
            byType.filter { file ->
                when (lower) {
                    "today" -> now - file.dateAdded <= 86_400_000L
                    "week", "this week" -> now - file.dateAdded <= 7L * 86_400_000L
                    "month", "this month" -> now - file.dateAdded <= 30L * 86_400_000L
                    "large" -> file.size >= 5_242_880L
                    else -> {
                        file.name.lowercase().contains(lower) ||
                                file.album.lowercase().contains(lower) ||
                                file.dateAdded.toFormattedDate().lowercase().contains(lower) ||
                                file.aiTags.any { it.contains(lower) }
                    }
                }
            }
        } else {
            byType
        }

        if (result.isEmpty()) showEmpty(query)
        else showResults(result, query)
    }

    private fun showLoading() {
        b.layoutLoading.visible()
        b.layoutEmpty.gone()
        b.layoutResults.gone()
    }

    private fun showEmpty(query: String) {
        b.tvEmptyQuery.text = if (query.isNotEmpty()) {
            getString(R.string.search_empty_for_query, query)
        } else {
            getString(R.string.state_empty_desc)
        }
        b.layoutLoading.gone()
        b.layoutEmpty.visible()
        b.layoutResults.gone()
    }

    private fun showResults(files: List<MediaFile>, query: String) {
        adapter.submitList(files.toList())
        b.tvResultCount.text = if (query.isNotEmpty()) {
            getString(R.string.search_results_for, files.size, query)
        } else {
            getString(R.string.items_size_format, files.size, MediaFile.formatSize(files.sumOf { it.size }))
        }

        b.layoutLoading.gone()
        b.layoutEmpty.gone()
        b.layoutResults.visible()
    }

    private fun showSnack(msg: String) {
        if (_b != null) Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
