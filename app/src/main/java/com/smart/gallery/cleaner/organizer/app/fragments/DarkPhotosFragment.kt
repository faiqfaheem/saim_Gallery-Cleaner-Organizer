package com.smart.gallery.cleaner.organizer.app.fragments

import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel

class DarkPhotosFragment : BaseMediaListFragment() {
    override val titleRes: Int get() = R.string.dark_photos
    override val spanCount: Int get() = 3
    override fun loadData(vm: MediaViewModel) = vm.loadDarkBadPhotos()
}
