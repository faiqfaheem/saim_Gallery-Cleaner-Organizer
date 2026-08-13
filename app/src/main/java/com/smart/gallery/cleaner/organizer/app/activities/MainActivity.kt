package com.smart.gallery.cleaner.organizer.app.activities

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.databinding.ActivityMainBinding
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.PermissionHelper
import com.smart.gallery.cleaner.organizer.app.viewmodel.SettingsViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val settingsVM by lazy {
        ViewModelProvider(this)[SettingsViewModel::class.java]
    }

    private var privacyFlowActive = false
    private var isPermissionDialogShowing = false
    private var hasUserDismissedPermission = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        handlePermissionResult()
    }

    companion object {
        const val EXTRA_OPEN_PRIVACY = "open_privacy"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        InsetsHelper.enableEdgeToEdge(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        InsetsHelper.applyStatusBar(binding.root)
        privacyFlowActive = intent.getBooleanExtra(EXTRA_OPEN_PRIVACY, false)

        observeTheme()
        setupNavigation()
        handlePrivacyNavigation()
    }

    override fun onResume() {
        super.onResume()
        // If user granted permission in system settings, reset dismissal flag
        if (PermissionHelper.hasStoragePermission(this)) {
            hasUserDismissedPermission = false
        }
    }

    private fun observeTheme() {
        settingsVM.themeMode.observe(this) { mode ->
            val nightMode = when (mode) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }

            if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }

            binding.root.post {
                InsetsHelper.applyAutoBarIcons(this)
            }
        }
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment
        ) as NavHostFragment

        navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        val topLevelDestinations = setOf(
            R.id.homeFragment, R.id.searchFragment, R.id.storageFragment, R.id.settingsFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val showBottomNavigation = destination.id in topLevelDestinations
            showBottomNav(showBottomNavigation)
        }
    }

    private fun handlePrivacyNavigation() {
        if (privacyFlowActive) {
            binding.root.post {
                if (navController.currentDestination?.id != R.id.privacyFragment) {
                    navController.navigate(R.id.privacyFragment)
                }
            }
        } else {
            requestPermissionsIfNeeded()
        }
    }

    fun onPrivacyAccepted() {
        privacyFlowActive = false
        hasUserDismissedPermission = false
        intent.removeExtra(EXTRA_OPEN_PRIVACY)
        requestPermissionsIfNeeded()
    }

    fun requestPermissionsIfNeeded() {
        if (privacyFlowActive || isPermissionDialogShowing || hasUserDismissedPermission) return

        if (PermissionHelper.hasStoragePermission(this)) return

        val showRationale = PermissionHelper.REQUIRED_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        if (showRationale) {
            showDeniedDialog(isPermanentlyDenied = false)
        } else {
            permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
        }
    }

    private fun handlePermissionResult() {
        if (PermissionHelper.hasStoragePermission(this)) {
            hasUserDismissedPermission = false
            return
        }

        val showRationale = PermissionHelper.REQUIRED_PERMISSIONS.any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        showDeniedDialog(isPermanentlyDenied = !showRationale)
    }

    private fun showDeniedDialog(isPermanentlyDenied: Boolean) {
        if (isPermissionDialogShowing) return
        isPermissionDialogShowing = true

        PermissionHelper.showPermissionDeniedDialog(
            activity = this,
            isPermanentlyDenied = isPermanentlyDenied,
            onGrantClicked = {
                isPermissionDialogShowing = false
                if (isPermanentlyDenied) {
                    PermissionHelper.openAppSettings(this)
                } else {
                    permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS)
                }
            },
            onCancelClicked = {
                isPermissionDialogShowing = false
                hasUserDismissedPermission = true // Prevents duplicate triggers
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun showBottomNav(show: Boolean) {
        binding.bottomNav.apply {
            animate().cancel()
            if (show) {
                visibility = View.VISIBLE
                animate().translationY(0f).alpha(1f).setDuration(200).start()
            } else {
                animate().translationY(height.toFloat())
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction { visibility = View.GONE }
                    .start()
            }
        }
    }
}