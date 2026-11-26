package com.smart.gallery.cleaner.organizer.app.activities

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
    private var activePermissionDialog: AlertDialog? = null

    private var shouldShowBottomNavForDestination = true
    private var lastNavBarBottomInset = 0
    private var lastImeBottomInset = 0
    private var lastImeVisible = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        handlePermissionResult()
    }

    companion object {
        const val EXTRA_OPEN_PRIVACY = "open_privacy"
        private const val STATE_PRIVACY_ACTIVE = "state_privacy_active"
        private const val STATE_PERM_DISMISSED = "state_perm_dismissed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        InsetsHelper.enableEdgeToEdge(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            privacyFlowActive = savedInstanceState.getBoolean(STATE_PRIVACY_ACTIVE, false)
            hasUserDismissedPermission = savedInstanceState.getBoolean(STATE_PERM_DISMISSED, false)
        } else {
            privacyFlowActive = intent.getBooleanExtra(EXTRA_OPEN_PRIVACY, false)
        }

        setupWindowInsets()
        observeTheme()
        setupNavigation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PRIVACY_ACTIVE, privacyFlowActive)
        outState.putBoolean(STATE_PERM_DISMISSED, hasUserDismissedPermission)
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasStoragePermission(this)) {
            hasUserDismissedPermission = false
        }
        InsetsHelper.applyAutoBarIcons(this)
    }

    override fun onDestroy() {
        activePermissionDialog?.dismiss()
        activePermissionDialog = null
        super.onDestroy()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { _, insets -> insets }

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { root, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            lastNavBarBottomInset = systemBars.bottom
            lastImeBottomInset = ime.bottom
            lastImeVisible = imeVisible

            // Protect top status bar + left/right cutouts & landscape side navigation bar
            root.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = 0
            )

            binding.bottomNav.updatePadding(bottom = systemBars.bottom)
            applyBottomInsetLayout()
            insets
        }
        InsetsHelper.requestInsetsWhenAttached(binding.mainRoot)
    }

    private fun applyBottomInsetLayout() {
        val showNav = shouldShowBottomNavForDestination && !lastImeVisible
        if (showNav) {
            binding.bottomNav.animate().cancel()
            binding.bottomNav.visibility = View.VISIBLE
            binding.bottomNav.translationY = 0f
            binding.bottomNav.alpha = 1f
            binding.navHostFragment.updatePadding(bottom = 0)
        } else {
            binding.bottomNav.animate().cancel()
            binding.bottomNav.visibility = View.GONE
            val bottomPadding = if (lastImeVisible) lastImeBottomInset else lastNavBarBottomInset
            binding.navHostFragment.updatePadding(bottom = bottomPadding)
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
                if (!isFinishing && !isDestroyed) {
                    InsetsHelper.applyAutoBarIcons(this)
                }
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
            shouldShowBottomNavForDestination = destination.id in topLevelDestinations
            applyBottomInsetLayout()
        }
    }

    private fun handlePrivacyNavigation() {
        if (privacyFlowActive) {
            binding.root.post {
                if (!isFinishing && !isDestroyed && navController.currentDestination?.id != R.id.privacyFragment) {
                    runCatching { navController.navigate(R.id.privacyFragment) }
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
            runCatching { permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS) }
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
        if (isPermissionDialogShowing || isFinishing || isDestroyed) return
        isPermissionDialogShowing = true

        activePermissionDialog?.dismiss()
        activePermissionDialog = PermissionHelper.showPermissionDeniedDialog(
            activity = this,
            isPermanentlyDenied = isPermanentlyDenied,
            onGrantClicked = {
                isPermissionDialogShowing = false
                if (isPermanentlyDenied) {
                    PermissionHelper.openAppSettings(this)
                } else {
                    runCatching { permissionLauncher.launch(PermissionHelper.REQUIRED_PERMISSIONS) }
                }
            },
            onCancelClicked = {
                isPermissionDialogShowing = false
                hasUserDismissedPermission = true
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
