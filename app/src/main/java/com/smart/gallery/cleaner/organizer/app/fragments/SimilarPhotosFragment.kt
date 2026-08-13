package com.smart.gallery.cleaner.organizer.app.fragments

import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel

class SimilarPhotosFragment : BaseMediaListFragment() {
    override val titleRes: Int  get() = R.string.similar_photos
    override val spanCount: Int get() = 3
    override fun loadData(vm: MediaViewModel) = vm.loadAllImages()
}