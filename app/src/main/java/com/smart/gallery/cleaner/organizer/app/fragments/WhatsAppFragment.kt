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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentWhatsappBinding
import com.smart.gallery.cleaner.organizer.app.utils.adaptiveSpanCount
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigateUp
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class WhatsAppFragment : Fragment() {

    private var _b: FragmentWhatsappBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()
    private val settingsVm: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }
    private lateinit var adapter: MediaGridAdapter

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onPermissionGrantedForDelete()
        else {
            vm.clearDeleteResult()
            showSnack(getString(R.string.error_delete_failed))
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentWhatsappBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)

        setupAdapter()
        setupToolbar()
        setupTabs()
        setupClickListeners()
        observeViewModel()

        if (!vm.hasLoadedFiles && vm.loading.value != true) {
            if (vm.activeWhatsAppTab == 1) vm.loadWhatsAppVideos() else vm.loadWhatsAppImages()
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

    private fun setupToolbar() {
        b.btnBack.setOnClickListener { safeNavigateUp() }
    }

    private fun setupTabs() {
        b.tabLayout.addTab(b.tabLayout.newTab().setText(R.string.filter_images))
        b.tabLayout.addTab(b.tabLayout.newTab().setText(R.string.videos))

        if (vm.activeWhatsAppTab in 0..1) {
            b.tabLayout.getTabAt(vm.activeWhatsAppTab)?.select()
        }

        b.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (vm.activeWhatsAppTab == tab.position && vm.hasLoadedFiles) return
                vm.activeWhatsAppTab = tab.position
                vm.deselectAll()
                when (tab.position) {
                    0 -> vm.loadWhatsAppImages()
                    1 -> vm.loadWhatsAppVideos()
                }
            }

            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    private fun setupClickListeners() {
        b.btnSelectAll.setOnClickListener {
            val allSelected = (vm.selectedCount.value ?: 0) == (vm.files.value?.size ?: 0) &&
                    vm.files.value?.isNotEmpty() == true
            if (allSelected) vm.deselectAll() else vm.selectAll()
        }
        b.btnDeleteSelected.setOnClickListener {
            val selected = vm.getSelectedFiles()
            if (selected.isNotEmpty()) vm.deleteFiles(selected)
        }
    }

    private fun observeViewModel() {
        vm.files.observe(viewLifecycleOwner) { files ->
            adapter.submitList(files.toList())
            val size = MediaFile.formatSize(files.sumOf { it.size })
            b.tvItemCount.text = getString(R.string.items_size_format, files.size, size)
            val loading = vm.loading.value == true
            b.layoutEmpty.visibleIf(!loading && vm.hasLoadedFiles && files.isEmpty())
            b.recyclerView.visibleIf(!loading && files.isNotEmpty())
        }

        vm.loading.observe(viewLifecycleOwner) { loading ->
            b.layoutLoading.visibleIf(loading)
            if (loading) {
                b.layoutEmpty.visibility = View.GONE
                b.recyclerView.visibility = View.GONE
            } else {
                val files = vm.files.value.orEmpty()
                b.layoutEmpty.visibleIf(vm.hasLoadedFiles && files.isEmpty())
                b.recyclerView.visibleIf(files.isNotEmpty())
            }
        }

        vm.selectedCount.observe(viewLifecycleOwner) { count ->
            val hasSelection = count > 0
            b.bottomActionBar.visibleIf(hasSelection)

            val total = vm.files.value?.size ?: 0
            b.tvSelectionBar.text = if (hasSelection) {
                getString(R.string.selected_of_total_format, count, total)
            } else {
                resources.getQuantityString(R.plurals.items_count, total, total)
            }

            b.tvSelectedCount.text = resources.getQuantityString(
                R.plurals.selected_files_count, count, count
            )

            val allSelected = hasSelection && count == total
            b.tvSelectAllLabel.text = getString(
                if (allSelected) R.string.deselect_all else R.string.select_all
            )
        }

        vm.totalSelectedSize.observe(viewLifecycleOwner) { size ->
            b.tvSelectedSize.text = getString(R.string.will_be_freed_format, MediaFile.formatSize(size))
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
                .setOnCancelListener { vm.deselectAll() }
                .show()
            vm.clearConfirmDeleteEvent()
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess { freed ->
                val isSafe = settingsVm.safeDelete.value == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val msg = if (isSafe) {
                    getString(R.string.moved_to_trash_success, MediaFile.formatSize(freed))
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

    private fun showSnack(msg: String) {
        if (_b != null) Snackbar.make(b.root, msg, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
