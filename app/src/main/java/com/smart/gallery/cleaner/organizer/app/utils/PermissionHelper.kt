package com.smart.gallery.cleaner.organizer.app.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PermissionHelper {
    val REQUIRED_PERMISSIONS: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )

        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    fun hasStoragePermission(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun openAppSettings(context: Context) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
            context.startActivity(this)
        }
    }

    fun showPermissionDeniedDialog(
        activity: AppCompatActivity,
        isPermanentlyDenied: Boolean = false,
        onGrantClicked: () -> Unit,
        onCancelClicked: (() -> Unit)? = null
    ) {
        val message = if (isPermanentlyDenied) {
            "Permission to access photos and videos has been turned off. Please enable Photos and Videos access in App Settings to scan your device."
        } else {
            "Gallery Cleaner needs access to your photos and videos to scan for duplicates, large files, and junk media. Without this, the app cannot scan."
        }

        val positiveBtnText = if (isPermanentlyDenied) "Open Settings" else "Grant Access"

        MaterialAlertDialogBuilder(activity)
            .setTitle("Photos & Videos Permission Required")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(positiveBtnText) { dialog, _ ->
                dialog.dismiss()
                onGrantClicked()
            }
            .setNegativeButton("Not Now") { dialog, _ ->
                dialog.dismiss()
                onCancelClicked?.invoke()
            }
            .show()
    }
}