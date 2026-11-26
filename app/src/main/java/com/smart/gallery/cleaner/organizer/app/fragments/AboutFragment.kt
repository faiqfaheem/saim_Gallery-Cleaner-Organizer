package com.smart.gallery.cleaner.organizer.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.smart.gallery.cleaner.organizer.app.BuildConfig
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentAboutBinding
import com.smart.gallery.cleaner.organizer.app.utils.safeNavigateUp

class AboutFragment : Fragment() {

    private var _b: FragmentAboutBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentAboutBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, s: Bundle?) {
        super.onViewCreated(view, s)
        b.btnBack.setOnClickListener { safeNavigateUp() }

        b.tvAppName.text = getString(R.string.app_name)
        b.tvVersion.text = getString(R.string.about_version_format, BuildConfig.VERSION_NAME)
        b.tvVersionRow.text = "v${BuildConfig.VERSION_NAME}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
