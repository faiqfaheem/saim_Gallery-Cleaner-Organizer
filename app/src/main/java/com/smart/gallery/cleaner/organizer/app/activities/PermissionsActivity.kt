package com.smart.gallery.cleaner.organizer.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.databinding.ActivityPermissionsBinding
import com.smart.gallery.cleaner.organizer.app.notifications.DailyNotificationHelper
import com.smart.gallery.cleaner.organizer.app.utils.InsetsHelper
import com.smart.gallery.cleaner.organizer.app.utils.PermissionHelper

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private var isPermanentlyDenied = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (PermissionHelper.hasStoragePermission(this)) {
            openMainActivity()
        } else {
            val showRationale = PermissionHelper.REQUIRED_PERMISSIONS.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
            }
            isPermanentlyDenied = !showRationale
            if (isPermanentlyDenied) {
                binding.tvPermSubtitle.text = getString(R.string.perm_denied_msg)
                binding.btnGrantPermission.text = getString(R.string.perm_open_settings)
                Snackbar.make(binding.root, R.string.perm_denied_title, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PermissionHelper.hasStoragePermission(this)) {
            openMainActivity()
            return
        }

        InsetsHelper.enableEdgeToEdge(this)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        InsetsHelper.applySystemBars(binding.rootPermissions)

        binding.btnGrantPermission.setOnClickListener {
            if (isPermanentlyDenied) {
                PermissionHelper.openAppSettings(this)
            } else {
                runCatching { permissionLauncher.launch(PermissionHelper.STARTUP_PERMISSIONS) }
            }
        }

        binding.btnSkipPermission.setOnClickListener {
            openMainActivity()
        }
    }

    override fun onResume() {
        super.onResume()
        InsetsHelper.applyAutoBarIcons(this)
        if (PermissionHelper.hasStoragePermission(this)) {
            openMainActivity()
        }
    }

    private fun openMainActivity() {
        DailyNotificationHelper.createNotificationChannel(this)
        DailyNotificationHelper.scheduleDailyReminder(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}
