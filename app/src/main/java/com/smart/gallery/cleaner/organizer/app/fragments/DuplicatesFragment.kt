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
import com.smart.gallery.cleaner.organizer.app.adapter.DuplicateGroupAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.DuplicateGroup
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentDuplicatesBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel
import com.smart.gallery.cleaner.organizer.app.viewmodel.PendingIntentException
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class DuplicatesFragment : Fragment() {

    private var _binding: FragmentDuplicatesBinding? = null
    private val binding get() = _binding!!
    private val vm: MediaViewModel by viewModels()
    private val settingsVm: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }
    private lateinit var adapter: DuplicateGroupAdapter

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

        binding.tvTitle.text = getString(R.string.duplicates)
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        adapter = DuplicateGroupAdapter(
            onDeleteGroup = { group -> confirmDeleteGroup(group) },
            onKeepFile = { _, _ -> /* future: mark as keeper */ }
        )
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroups.adapter = adapter

        observeViewModel()
        vm.loadDuplicates()
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            binding.layoutLoading.visibleIf(loading)
            val groups = vm.duplicateGroups.value.orEmpty()
            binding.layoutEmpty.visibleIf(!loading && groups.isEmpty())
            binding.rvGroups.visibleIf(!loading && groups.isNotEmpty())
        }

        vm.duplicateGroups.observe(viewLifecycleOwner) { groups ->
            adapter.submitList(groups)
            val wasted = groups.sumOf { it.wastedSize }
            binding.tvSummary.text =
                "${groups.size} groups · ${MediaFile.formatSize(wasted)} wasted"
            val loading = vm.loading.value ?: false
            binding.layoutEmpty.visibleIf(!loading && groups.isEmpty())
            binding.rvGroups.visibleIf(!loading && groups.isNotEmpty())
        }

        vm.confirmDeleteEvent.observe(viewLifecycleOwner) { files ->
            files ?: return@observe

            val count   = files.size
            val freed   = MediaFile.formatSize(files.sumOf { it.size })
            val isSafe  = settingsVm.safeDelete.value == true
            val isTrash = isSafe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val message = if (isTrash) {
                """$count copies ($freed) will be moved to Trash.Recover within 30 days from your gallery Trash. """.trimIndent()
            } else {
                getString(R.string.delete_group_copies_message, freed)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(resources.getQuantityString(
                    R.plurals.delete_group_copies_title, count, count))
                .setMessage(message)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    vm.executeDelete(files)
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
            vm.clearConfirmDeleteEvent()
        }

        vm.deleteResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            result.onSuccess { freed ->
                showSnack("Freed ${MediaFile.formatSize(freed)}")
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

    private fun confirmDeleteGroup(group: DuplicateGroup) {
        val toDelete = group.files.drop(1)
        vm.deleteFiles(toDelete)
    }

    private fun showSnack(msg: String) =
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}