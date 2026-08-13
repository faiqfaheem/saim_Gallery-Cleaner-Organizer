package com.smart.gallery.cleaner.organizer.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.adapter.MediaGridAdapter
import com.smart.gallery.cleaner.organizer.app.data.model.MediaFile
import com.smart.gallery.cleaner.organizer.app.data.model.MediaType
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentSearchBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.gone
import com.smart.gallery.cleaner.organizer.app.utils.visible
import com.smart.gallery.cleaner.organizer.app.utils.visibleIf
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel

class SearchFragment : Fragment() {

    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private val vm: MediaViewModel by viewModels()
    private var filterType: MediaType? = null
    private var fullList: List<MediaFile> = emptyList()
    private var loaded = false

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSearchBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        setupAdapter()
        setupSearch()
        setupChips()
        observeViewModel()

        vm.loadAllMedia()

        b.etSearch.post {
            b.etSearch.requestFocus()
            ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
                ?.showSoftInput(b.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private lateinit var adapter: MediaGridAdapter

    private fun setupAdapter() {
        adapter = MediaGridAdapter(
            onItemClick = { file ->
                if ((vm.selectedCount.value ?: 0) > 0) vm.toggleSelection(file)
                else MediaGridAdapter.openInDefaultViewer(requireContext(), file)
            },
            onSelectionChanged = { file -> vm.toggleSelection(file) },
            onLongClick        = { file -> vm.toggleSelection(file) }
        )
        b.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        b.recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        b.etSearch.doAfterTextChanged { editable ->
            val q = editable?.toString()?.trim() ?: ""
            b.btnClear.visibleIf(q.isNotEmpty())
            filterAndShow(q)   // instant — filters fullList in memory
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
        b.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            filterType = when (checkedIds.firstOrNull()) {
                R.id.chip_images -> MediaType.IMAGE
                R.id.chip_videos -> MediaType.VIDEO
                R.id.chip_gifs   -> MediaType.GIF
                else             -> null
            }
            filterAndShow(b.etSearch.text?.toString()?.trim() ?: "")
        }
    }

    private fun observeViewModel() {
        vm.loading.observe(viewLifecycleOwner) { loading ->
            if (loading && !loaded) showLoading()
        }

        vm.files.observe(viewLifecycleOwner) { files ->
            fullList = files
            loaded   = true
            filterAndShow(b.etSearch.text?.toString()?.trim() ?: "")
        }
    }

    private fun filterAndShow(query: String) {
        if (!loaded) return
        val byType: List<MediaFile> = when (filterType) {
            MediaType.IMAGE -> fullList.filter { it.isImage && !it.isGif }
            MediaType.VIDEO -> fullList.filter { it.isVideo }
            MediaType.GIF   -> fullList.filter { it.isGif }
            else            -> fullList
        }

        val result: List<MediaFile> = if (query.isNotEmpty()) {
            val lower = query.lowercase()
            byType.filter { it.name.lowercase().contains(lower) }
        } else {
            byType
        }

        if (result.isEmpty() && query.isNotEmpty()) showEmpty(query)
        else showResults(result, query)
    }

    private fun showLoading() {
        b.layoutLoading.visible()
        b.layoutEmpty.gone()
        b.layoutResults.gone()
    }

    private fun showEmpty(query: String) {
        b.tvEmptyQuery.text = "No files found for \u201c$query\u201d"
        b.layoutLoading.gone()
        b.layoutEmpty.visible()
        b.layoutResults.gone()
    }

    private fun showResults(files: List<MediaFile>, query: String) {
        adapter.submitList(files)

        b.tvResultCount.text = when {
            query.isNotEmpty() -> {
                val t = when (filterType) {
                    MediaType.IMAGE -> "photo"
                    MediaType.VIDEO -> "video"
                    MediaType.GIF   -> "GIF"
                    else            -> "file"
                }
                "${files.size} ${t}${if (files.size == 1) "" else "s"} for \u201c$query\u201d"
            }
            filterType != null -> {
                val t = when (filterType) {
                    MediaType.IMAGE -> "photos"
                    MediaType.VIDEO -> "videos"
                    MediaType.GIF   -> "GIFs"
                    else            -> "files"
                }
                "${files.size} $t"
            }
            else -> "All \u00b7 ${files.size} files"
        }

        b.layoutLoading.gone()
        b.layoutEmpty.gone()
        b.layoutResults.visible()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}