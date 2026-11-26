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
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentMediaListBinding
import com.smart.gallery.cleaner.organizer.app.utils.adaptiveSpanCount
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigateUp
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
    private var activeDialog: AlertDialog? = null

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

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        if (!vm.hasLoadedFiles && vm.loading.value != true) {
            loadData(vm)
        }
    }

    private fun setupToolbar() {
        binding.tvToolbarTitle.text = getString(titleRes)
        binding.btnBack.setOnClickListener { safeNavigateUp() }
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaGridAdapter(
            onItemClick = ::onItemClick,
            onSelectionChanged = { file -> vm.toggleSelection(file) },
            onLongClick = { file -> vm.toggleSelection(file) }
        )
        val adaptiveSpan = requireContext().adaptiveSpanCount(spanCount)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), adaptiveSpan)
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
            binding.tvItemCount.text = getString(
                R.string.items_size_format,
                files.size,
                MediaFile.formatSize(files.sumOf { it.size })
            )
            val loading = vm.loading.value == true
            binding.layoutEmpty.visibleIf(!loading && vm.hasLoadedFiles && files.isEmpty())
            binding.recyclerView.visibleIf(!loading && files.isNotEmpty())
        }

        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.layoutLoading.visibleIf(loading)
            if (loading) {
                binding.layoutEmpty.visibility = View.GONE
                binding.recyclerView.visibility = View.GONE
            } else {
                val files = vm.files.value.orEmpty()
                binding.layoutEmpty.visibleIf(vm.hasLoadedFiles && files.isEmpty())
                binding.recyclerView.visibleIf(files.isNotEmpty())
            }
        }

        vm.scanProgress.observe(viewLifecycleOwner) { p ->
            val showDetailed = p.total > 0
            binding.tvLoadingPercent.visibleIf(showDetailed)
            binding.progressLoading.visibleIf(showDetailed)
            binding.tvLoadingCounts.visibleIf(showDetailed)
            if (showDetailed) {
                binding.progressLoading.setProgress(p.percent, true)
                binding.tvLoadingPercent.text = "${p.percent}%"
                binding.tvLoadingCounts.text = getString(
                    R.string.scan_files_progress_format,
                    p.processed,
                    p.total,
                    p.remaining
                )
            }
        }

        vm.selectedCount.observe(viewLifecycleOwner) { count ->
            val hasSelection = count > 0
            binding.layoutSelectionBar.visibleIf(hasSelection)
            binding.btnDeleteSelected.visibleIf(hasSelection)
            binding.tvSelectedCount.text = resources.getQuantityString(
                R.plurals.selected_files_count, count, count
            )
            val total = vm.files.value?.size ?: 0
            binding.tvSelectAllLabel.text = getString(
                if (hasSelection && count == total) R.string.deselect_all
                else R.string.select_all
            )
        }

        vm.totalSelectedSize.observe(viewLifecycleOwner) { size ->
            binding.tvSelectedSize.text = getString(R.string.will_be_freed_format, MediaFile.formatSize(size))
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
            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
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
                    getString(R.string.moved_to_trash_success, MediaFile.formatSize(freed))
                } else {
                    getString(R.string.freed_space_success, MediaFile.formatSize(freed))
                }
                showSnack(msg)
                vm.clearDeleteResult()
            }.onFailure { e ->
                if (e is PendingIntentException) {
                    runCatching {
                        deleteLauncher.launch(IntentSenderRequest.Builder(e.intentSender).build())
                    }.onFailure {
                        showSnack(getString(R.string.error_delete_failed))
                        vm.clearDeleteResult()
                    }
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
        val orders = SortOrder.entries.toTypedArray()
        activeDialog?.dismiss()
        activeDialog = MaterialAlertDialogBuilder(requireContext())
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
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroyView()
        _binding = null
    }
}
