package com.smart.gallery.cleaner.organizer.app.fragments

import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel

class DownloadsFragment : BaseMediaListFragment() {
    override val titleRes: Int get() = R.string.downloads
    override val spanCount: Int get() = 3
    override fun loadData(vm: MediaViewModel) = vm.loadDownloads()
}