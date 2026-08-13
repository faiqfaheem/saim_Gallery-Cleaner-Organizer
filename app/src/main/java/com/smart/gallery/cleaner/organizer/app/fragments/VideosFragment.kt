package com.smart.gallery.cleaner.organizer.app.fragments

import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel

class VideosFragment : BaseMediaListFragment() {
    override val titleRes: Int get() = R.string.videos
    override val spanCount: Int get() = 2
    override fun loadData(vm: MediaViewModel) = vm.loadVideos()
}