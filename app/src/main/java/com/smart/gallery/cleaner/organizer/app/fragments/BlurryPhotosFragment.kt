package com.smart.gallery.cleaner.organizer.app.fragments

import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.viewmodel.MediaViewModel

class BlurryPhotosFragment : BaseMediaListFragment() {
    override val titleRes: Int get() = R.string.blurry_photos
    override val spanCount: Int get() = 3
    override fun loadData(vm: MediaViewModel) = vm.loadBlurryPhotos()
}