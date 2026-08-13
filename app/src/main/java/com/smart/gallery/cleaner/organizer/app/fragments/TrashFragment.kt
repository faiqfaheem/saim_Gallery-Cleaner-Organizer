package com.smart.gallery.cleaner.organizer.app.fragments

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentTrashBinding
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException

class TrashFragment : Fragment() {

    private var _b: FragmentTrashBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()
    private lateinit var adapter: MediaGridAdapter

    private val actionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onPermissionGrantedForDelete()
            Log.d(
                "TrashDebug",
                "TrashFragment: calling loadTrashedMedia, API=${Build.VERSION.SDK_INT}")
            vm.loadTrashedMedia()
        } else {
            vm.clearDeleteResult()
            showSnack("Action cancelled")
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

        b.btnBack.setOnClickListener { findNavController().navigateUp() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            b.layoutLoading.gone()
            b.layoutUnsupported.visible()
            return
        }

        setupAdapter()
        setupClickListeners()
        observeViewModel()
        Log.d("TrashDebug", "TrashFragment: calling loadTrashedMedia, API=${Build.VERSION.SDK_INT}")
        vm.loadTrashedMedia()
    }

    private fun setupAdapter() {
        adapter = MediaGridAdapter(
            onItemClick = { file ->
            if ((vm.selectedCount.value ?: 0) > 0) vm.toggleSelection(file)
            else MediaGridAdapter.openInDefaultViewer(requireContext(), file)
        },
            onSelectionChanged = { file -> vm.toggleSelection(file) },
            onLongClick = { file -> vm.toggleSelection(file) })
        b.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        b.recyclerView.adapter = adapter
    }

    private fun setupClickListeners() {

        b.btnEmptyTrash.setOnClickListener {
            val all = vm.files.value.orEmpty()
            if (all.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext()).setTitle("Empty Trash?").setMessage(
                    "${all.size} items (${MediaFile.formatSize(all.sumOf { it.size })}) " + "will be permanently deleted and cannot be recovered."
                ).setPositiveButton("Delete All") { _, _ -> vm.permanentlyDelete(all) }
                .setNegativeButton("Cancel", null).show()
        }

        b.btnRestore.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext()).setTitle("Restore ${selected.size} item${if (selected.size == 1) "" else "s"}?")
                .setMessage("Files will be restored to their original location.")
                .setPositiveButton("Restore") { _, _ -> vm.restoreFromTrash(selected) }
                .setNegativeButton("Cancel", null).show()
        }

        b.btnDeletePermanent.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isEmpty()) return@setOnClickListener
            val size = MediaFile.formatSize(selected.sumOf { it.size })
            MaterialAlertDialogBuilder(requireContext()).setTitle("Permanently delete ${selected.size} item${if (selected.size == 1) "" else "s"}?")
                .setMessage("$size will be permanently deleted and cannot be recovered.")
                .setPositiveButton("Delete") { _, _ -> vm.permanentlyDelete(selected) }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            Log.d("TrashDebug", "loading=$loading")
            b.layoutLoading.visibleIf(loading)
            if (loading) {
                b.recyclerView.gone(); b.layoutEmpty.gone()
            }
        }

        vm.files.observe(viewLifecycleOwner) { files ->
            Log.d("TrashDebug", "files arrived: count=${files.size}")
            adapter.submitList(files.toList())
            b.layoutLoading.gone()
            val hasFiles = files.isNotEmpty()
            b.recyclerView.visibleIf(hasFiles)
            b.layoutEmpty.visibleIf(!hasFiles)
            b.layoutBanner.visibleIf(hasFiles)
            b.btnEmptyTrash.visibleIf(hasFiles)
            val size = MediaFile.formatSize(files.sumOf { it.size })
            b.tvSubtitle.text =
                if (hasFiles) "${files.size} item${if (files.size == 1) "" else "s"} \u00b7 $size \u00b7 auto-deleted in 30 days"
                else "Trash is empty"
        }

        vm.selectedCount.observe(viewLifecycleOwner) { count ->
            b.bottomActionBar.visibleIf(count > 0)
            b.tvSelectedCount.text = "$count item${if (count == 1) "" else "s"} selected"
        }

        vm.totalSelectedSize.observe(viewLifecycleOwner) { size ->
            b.tvSelectedSize.text = MediaFile.formatSize(size)
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess {
                vm.clearDeleteResult()
                vm.deselectAll()
                Log.d("TrashDebug",
                    "TrashFragment: calling loadTrashedMedia, API=${Build.VERSION.SDK_INT}")
                vm.loadTrashedMedia()
            }.onFailure { e ->
                if (e is PendingIntentException) {
                    actionLauncher.launch(IntentSenderRequest.Builder(e.intentSender).build())
                } else {
                    showSnack(e.localizedMessage ?: "Action failed")
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

    private fun showSnack(msg: String) = Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
    override fun onDestroyView() {
        super.onDestroyView(); _b = null
    }
}