package com.smart.gallery.cleaner.organizer.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.activities.MainActivity
import com.smart.gallery.cleaner.organizer.app.databinding.FragmentPrivacyBinding
import com.smart.gallery.cleaner.organizer.app.databinding.ItemPrivacySectionBinding
import com.smart.gallery.cleaner.organizer.app.utils.UtilsPreferences
import com.smart.gallery.cleaner.organizer.app.utils.privacyDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrivacyFragment : Fragment() {

    private var _binding: FragmentPrivacyBinding? = null

    private val binding
        get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        _binding = FragmentPrivacyBinding.inflate(
            inflater, container, false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {

        super.onViewCreated(
            view, savedInstanceState
        )

        setupToolbar()

        populateSections()

        setupButtons()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBack()
        }
    }

    private fun setupButtons() {
        viewLifecycleOwner.lifecycleScope.launch {
            val preferences = requireContext().privacyDataStore.data.first()
            val isAccepted = preferences[UtilsPreferences.PRIVACY_ACCEPTED] ?: false

            if (_binding == null) {
                return@launch
            }

            binding.btnAcceptContinue.visibility = View.VISIBLE
            binding.btnAcceptContinue.text = if (isAccepted) "Continue" else "Accept & Continue"
            binding.btnDecline.visibility = if (isAccepted) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        binding.btnAcceptContinue.setOnClickListener {

            binding.btnAcceptContinue.isEnabled = false

            binding.btnDecline.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {

                requireContext().privacyDataStore.edit { preferences ->

                    preferences[UtilsPreferences.PRIVACY_ACCEPTED] = true
                }

                if (_binding == null) {
                    return@launch
                }

                continueToApp()
            }
        }

        binding.btnDecline.setOnClickListener {
            handleDecline()
        }
    }

    private fun continueToApp() {

        val activity = requireActivity() as? MainActivity ?: return

        val navController = findNavController()

        if (navController.currentDestination?.id != R.id.privacyFragment) {
            return
        }

        activity.onPrivacyAccepted()

        navController.navigate(

            R.id.homeFragment,

            null,

            NavOptions.Builder()

                .setPopUpTo(
                    R.id.privacyFragment, true
                )

                .setLaunchSingleTop(true)

                .build()
        )
    }

    private fun handleDecline() {

        viewLifecycleOwner.lifecycleScope.launch {

            val preferences = requireContext().privacyDataStore.data.first()

            val isAccepted = preferences[UtilsPreferences.PRIVACY_ACCEPTED] ?: false

            if (_binding == null) {
                return@launch
            }

            if (isAccepted) {
                findNavController().navigateUp()
            } else {
                requireActivity().finish()
            }
        }
    }

    private fun handleBack() {
        viewLifecycleOwner.lifecycleScope.launch {
            val preferences = requireContext().privacyDataStore.data.first()
            val isAccepted = preferences[UtilsPreferences.PRIVACY_ACCEPTED] ?: false
            if (_binding == null) {
                return@launch
            }

            if (isAccepted) {
                findNavController().navigateUp()
            } else {
                requireActivity().finish()
            }
        }
    }

    private fun populateSections() {

        val sections = listOf(

            PolicySection(
                "1. Data We Collect",
                "We collect absolutely no personal data. " + "This app does not transmit, upload, or share " + "any of your photos, videos, or personal information " + "with anyone. All file scanning and analysis is " + "performed entirely on your device and stays there."
            ),

            PolicySection(
                "2. Storage Permission",
                "The app requests media access solely to scan, " + "organize, and display photos and videos stored " + "on your device. This permission is not used to " + "upload or transmit your files to external servers."
            ),

            PolicySection(
                "3. Analytics & Advertising",
                "This app contains NO third-party analytics SDKs, " + "crash reporting services, or advertising networks. " + "There are zero ads. No usage data is recorded or " + "sent anywhere."
            ),

            PolicySection(
                "4. App Preferences",
                "Your app preferences are stored locally on your device. " + "These preferences are never transmitted, uploaded, " + "or shared with any external server."
            ),

            PolicySection(
                "5. File Deletion",
                "When you delete files through this app, deletion is " + "performed using Android's standard MediaStore APIs. " + "Depending on the Android version and device, deleted " + "files may temporarily remain in the system Trash."
            ),

            PolicySection(
                "6. Children's Privacy",
                "This app does not knowingly collect personal information " + "from children or adults. Since the app does not collect " + "personal data, no personal information is transmitted " + "to external services."
            ),

            PolicySection(
                "7. Contact",
                "If you have questions or concerns about this privacy policy, " + "please contact us through the Google Play Store listing " + "page for Gallery Cleaner & Organizer."
            )
        )

        val inflater = LayoutInflater.from(
            requireContext()
        )

        sections.forEach { section ->

            val sectionBinding = ItemPrivacySectionBinding.inflate(
                inflater, binding.llSections, false
            )

            sectionBinding.tvSectionTitle.text = section.title

            sectionBinding.tvSectionBody.text = section.body

            binding.llSections.addView(
                sectionBinding.root
            )
        }
    }

    private data class PolicySection(
        val title: String, val body: String
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}