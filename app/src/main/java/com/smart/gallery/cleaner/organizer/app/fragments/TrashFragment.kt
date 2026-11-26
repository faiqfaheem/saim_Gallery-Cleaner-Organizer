package com.smart.gallery.cleaner.organizer.app.fragments

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentTrashBinding
import com.smart.gallery.cleaner.organizer.app.utils.adaptiveSpanCount
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigateUp
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException

class TrashFragment : Fragment() {

    private var _b: FragmentTrashBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()
    private lateinit var adapter: MediaGridAdapter
    private var activeDialog: AlertDialog? = null

    private val actionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onPermissionGrantedForDelete()
            vm.loadTrashedMedia()
        } else {
            vm.clearDeleteResult()
            showSnack(getString(R.string.action_cancelled))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, c: ViewGroup?, s: Bundle?
    ): View {
        _b = FragmentTrashBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        b.btnBack.setOnClickListener { safeNavigateUp() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            b.layoutLoading.gone()
            b.layoutUnsupported.visible()
            return
        }

        setupAdapter()
        setupClickListeners()
        observeViewModel()
        if (!vm.hasLoadedFiles && vm.loading.value != true) {
            vm.loadTrashedMedia()
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

    private fun setupClickListeners() {
        b.btnSelectAll.setOnClickListener {
            val total = vm.files.value?.size ?: 0
            val selected = vm.selectedCount.value ?: 0
            if (total > 0 && selected == total) vm.deselectAll() else vm.selectAll()
        }

        b.btnEmptyTrash.setOnClickListener {
            val all = vm.files.value.orEmpty()
            if (all.isEmpty()) return@setOnClickListener
            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.trash_empty_dialog_title)
                .setMessage(
                    getString(
                        R.string.delete_dialog_message,
                        all.size,
                        MediaFile.formatSize(all.sumOf { it.size })
                    )
                )
                .setPositiveButton(R.string.action_delete_all) { _, _ -> vm.permanentlyDelete(all) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        b.btnRestore.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isEmpty()) return@setOnClickListener
            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.trash_restore_dialog_title, selected.size))
                .setMessage(R.string.trash_restore_dialog_msg)
                .setPositiveButton(R.string.action_restore) { _, _ -> vm.restoreFromTrash(selected) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        b.btnDeletePermanent.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isEmpty()) return@setOnClickListener
            val size = MediaFile.formatSize(selected.sumOf { it.size })
            activeDialog?.dismiss()
            activeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(resources.getQuantityString(R.plurals.delete_dialog_title, selected.size, selected.size))
                .setMessage(getString(R.string.delete_dialog_message, selected.size, size))
                .setPositiveButton(R.string.action_delete) { _, _ -> vm.permanentlyDelete(selected) }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun renderState() {
        val loading = vm.loading.value == true
        val files = vm.files.value.orEmpty()
        val hasFiles = files.isNotEmpty()

        b.layoutLoading.visibleIf(loading)
        if (loading) {
            b.recyclerView.gone()
            b.layoutEmpty.gone()
            return
        }

        b.recyclerView.visibleIf(hasFiles)
        b.layoutEmpty.visibleIf(vm.hasLoadedFiles && !hasFiles)
        b.layoutBanner.visibleIf(hasFiles)
        b.btnEmptyTrash.visibleIf(hasFiles)
        b.btnSelectAll.visibleIf(hasFiles)

        val size = MediaFile.formatSize(files.sumOf { it.size })
        b.tvSubtitle.text = if (hasFiles) {
            getString(R.string.items_size_format, files.size, size)
        } else {
            getString(R.string.trash_empty_title)
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { renderState() }

        vm.files.observe(viewLifecycleOwner) { files ->
            adapter.submitList(files.toList())
            renderState()
        }

        vm.selectedCount.observe(viewLifecycleOwner) { count ->
            b.bottomActionBar.visibleIf(count > 0)
            b.tvSelectedCount.text = resources.getQuantityString(R.plurals.selected_files_count, count, count)
            val total = vm.files.value?.size ?: 0
            b.btnSelectAll.text = getString(
                if (count > 0 && count == total) R.string.deselect_all else R.string.select_all
            )
        }

        vm.totalSelectedSize.observe(viewLifecycleOwner) { size ->
            b.tvSelectedSize.text = MediaFile.formatSize(size)
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess {
                vm.clearDeleteResult()
                vm.deselectAll()
                vm.loadTrashedMedia()
            }.onFailure { e ->
                if (e is PendingIntentException) {
                    runCatching {
                        actionLauncher.launch(IntentSenderRequest.Builder(e.intentSender).build())
                    }.onFailure {
                        showSnack(getString(R.string.error_delete_failed))
                        vm.clearDeleteResult()
                    }
                } else {
                    showSnack(e.localizedMessage ?: getString(R.string.error_delete_failed))
                    vm.clearDeleteResult()
                }
            }
        }

        vm.error.observe(viewLifecycleOwner) { msg ->
            msg ?: return@observe
            showSnack(msg)
            vm.clearError()
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
