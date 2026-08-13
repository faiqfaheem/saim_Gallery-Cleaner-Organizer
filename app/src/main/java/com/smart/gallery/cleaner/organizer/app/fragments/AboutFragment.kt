package com.smart.gallery.cleaner.organizer.app.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.smart.gallery.cleaner.organizer.app.BuildConfig
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentAboutBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper

class AboutFragment : Fragment() {

    private var _b: FragmentAboutBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAboutBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.btnBack.setOnClickListener { findNavController().navigateUp() }

        b.tvAppName.text = "Gallery Cleaner & Organizer"
        b.tvVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        b.tvVersionRow.text = BuildConfig.VERSION_NAME
        b.tvPackage.text    = requireContext().packageName
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}