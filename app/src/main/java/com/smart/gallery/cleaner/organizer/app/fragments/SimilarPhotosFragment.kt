package com.smart.gallery.cleaner.organizer.app.fragments

import android.app.Activity
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.SimilarGroupAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.SimilarGroup
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentDuplicatesBinding
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class SimilarPhotosFragment : Fragment() {

    private var _binding: FragmentDuplicatesBinding? = null
    private val binding get() = _binding!!
    private val vm: MediaViewModel by viewModels()
    private val settingsVm: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }
    private lateinit var adapter: SimilarGroupAdapter

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
        _binding = FragmentDuplicatesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = getString(R.string.similar_photos)
        binding.tvTipBanner.text = getString(R.string.similar_tip_banner)
        binding.tvLoadingTitle.text = getString(R.string.similar_loading_title)
        binding.tvLoadingDesc.text = getString(R.string.similar_loading_desc)
        binding.tvEmptyTitle.text = getString(R.string.similar_empty_title)
        binding.tvEmptyDesc.text = getString(R.string.similar_empty_desc)
        binding.btnDeleteAllDuplicates.text = getString(R.string.action_delete_all_similar)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        adapter = SimilarGroupAdapter(
            onDeleteGroup = { group -> confirmDeleteGroup(group) },
            onKeepFile = { file, group -> vm.setSimilarGroupKeeper(group.groupId, file.id) }
        )
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroups.adapter = adapter

        binding.btnDeleteAllDuplicates.setOnClickListener {
            val allExtras = vm.similarGroups.value.orEmpty().flatMap { g ->
                g.files.filter { it.id != g.keepFileId }
            }
            if (allExtras.isNotEmpty()) {
                vm.deleteFiles(allExtras)
            }
        }

        observeViewModel()
        if (!vm.hasLoadedSimilar && vm.loading.value != true) {
            vm.loadSimilarPhotos()
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.layoutLoading.visibleIf(loading)
            val groups = vm.similarGroups.value.orEmpty()
            binding.layoutEmpty.visibleIf(!loading && vm.hasLoadedSimilar && groups.isEmpty())
            binding.rvGroups.visibleIf(!loading && groups.isNotEmpty())
            binding.btnDeleteAllDuplicates.visibleIf(!loading && groups.isNotEmpty())
        }

        vm.scanProgress.observe(viewLifecycleOwner) { p ->
            binding.progressLoading.setProgress(p.percent, true)
            binding.tvLoadingPercent.text = "${p.percent}%"
            binding.tvLoadingCounts.text = if (p.total > 0) {
                getString(R.string.scan_files_progress_format, p.processed, p.total, p.remaining)
            } else ""
        }

        vm.similarGroups.observe(viewLifecycleOwner) { groups ->
            adapter.submitList(groups)
            val wasted = groups.sumOf { it.wastedSize }
            binding.tvSummary.text = getString(
                R.string.groups_wasted_summary,
                groups.size,
                MediaFile.formatSize(wasted)
            )
            val loading = vm.loading.value == true
            binding.layoutEmpty.visibleIf(!loading && vm.hasLoadedSimilar && groups.isEmpty())
            binding.rvGroups.visibleIf(!loading && groups.isNotEmpty())
            binding.btnDeleteAllDuplicates.visibleIf(!loading && groups.isNotEmpty())
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
                getString(R.string.delete_group_copies_message, freed)
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

        vm.error.observe(viewLifecycleOwner) { msg ->
            msg ?: return@observe
            showSnack(getString(R.string.error_prefix, msg))
            vm.clearError()
        }
    }

    private fun confirmDeleteGroup(group: SimilarGroup) {
        val toDelete = group.files.filter { it.id != group.keepFileId }
        if (toDelete.isNotEmpty()) {
            vm.deleteFiles(toDelete)
        }
    }

    private fun showSnack(msg: String) {
        if (_binding != null) Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
