package com.smart.gallery.cleaner.organizer.app.fragments

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentMediaListBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.SortOrder

abstract class BaseMediaListFragment : Fragment() {

    @get:StringRes
    abstract val titleRes: Int
    open val spanCount: Int get() = 3
    abstract fun loadData(vm: MediaViewModel)

    private var _binding: FragmentMediaListBinding? = null
    protected val binding get() = _binding!!

    val vm: MediaViewModel by viewModels()
    private val settingsVm: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }

    private lateinit var mediaAdapter: MediaGridAdapter

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
        _binding = FragmentMediaListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        InsetsHelper.applyNavigationBar(binding.contentFrame)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadData(vm)
    }

    private fun setupToolbar() {
        binding.tvToolbarTitle.text = getString(titleRes)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaGridAdapter(
            onItemClick = ::onItemClick,
            onSelectionChanged = { file -> vm.toggleSelection(file) },
            onLongClick = { file -> vm.toggleSelection(file) }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.recyclerView.adapter = mediaAdapter
    }

    open fun onItemClick(file: MediaFile) {
        if ((vm.selectedCount.value ?: 0) > 0) {
            vm.toggleSelection(file)
        } else {
            MediaGridAdapter.openInDefaultViewer(requireContext(), file)
        }
    }

    private fun setupClickListeners() {
        binding.btnSort.setOnClickListener { showSortDialog() }

        binding.btnSelectAll.setOnClickListener {
            val allSelected = (vm.selectedCount.value ?: 0) == (vm.files.value?.size ?: 0) &&
                    (vm.files.value?.isNotEmpty() == true)
            if (allSelected) vm.deselectAll() else vm.selectAll()
        }

        binding.btnDeleteSelected.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isNotEmpty()) vm.deleteFiles(selected)
        }
    }

    private fun observeViewModel() {
        vm.files.observe(viewLifecycleOwner) { files ->
            mediaAdapter.submitList(files.toList())
            binding.tvItemCount.text = buildString {
                append(files.size)
                append(" items · ")
                append(MediaFile.formatSize(files.sumOf { it.size }))
            }
            val idle = vm.loading.value == false
            binding.layoutEmpty.visibleIf(files.isEmpty() && idle)
            binding.recyclerView.visibleIf(files.isNotEmpty())
        }

        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.layoutLoading.visibleIf(loading)
            if (!loading && vm.files.value.isNullOrEmpty()) binding.layoutEmpty.visible()
        }

        vm.selectedCount.observe(viewLifecycleOwner) { count ->
            val hasSelection = count > 0
            binding.layoutSelectionBar.visibleIf(hasSelection)
            val label = resources.getQuantityString(
                R.plurals.selected_files_count, count, count
            )
            binding.tvSelectedCount.text = label
            val total = vm.files.value?.size ?: 0
            binding.tvSelectAllLabel.text = getString(
                if (hasSelection && count == total) R.string.deselect_all
                else R.string.select_all
            )
        }

        vm.totalSelectedSize.observe(viewLifecycleOwner) { size ->
            val label = "${MediaFile.formatSize(size)} will be freed"
            binding.tvSelectedSize.text = label
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
                .setTitle(
                    resources.getQuantityString(
                        R.plurals.delete_dialog_title,
                        count,
                        count
                    )
                )
                .setMessage(message)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    vm.executeDelete(files)
                }
                .setNegativeButton(R.string.action_cancel) { _, _ ->
                    vm.deselectAll()
                }
                .setOnCancelListener { vm.deselectAll() }
                .show()
            vm.clearConfirmDeleteEvent()
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess { freed ->
                val isSafe = settingsVm.safeDelete.value == true
                val isTrash = isSafe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val msg = if (isTrash) {
                    "Moved to Trash · ${MediaFile.formatSize(freed)} recoverable for 30 days"
                } else {
                    getString(R.string.freed_space_success, MediaFile.formatSize(freed))
                }
                showSnack(msg)
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

    private fun showSortDialog() {
        val labels = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_largest),
            getString(R.string.sort_smallest),
            getString(R.string.sort_name)
        )
        val orders = SortOrder.values()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_dialog_title)
            .setItems(labels) { _, index ->
                if (index in orders.indices) {
                    vm.setSortOrder(orders[index])
                }
            }
            .show()
    }

    fun showSnack(message: String) {
        if (_binding != null) {
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}