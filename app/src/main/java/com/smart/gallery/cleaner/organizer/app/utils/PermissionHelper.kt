package com.smart.gallery.cleaner.organizer.app.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smart.gallery.cleaner.organizer.app.R

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

    val STARTUP_PERMISSIONS: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
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
        runCatching {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
                context.startActivity(this)
            }
        }
    }

    fun showPermissionDeniedDialog(
        activity: AppCompatActivity,
        isPermanentlyDenied: Boolean = false,
        onGrantClicked: () -> Unit,
        onCancelClicked: (() -> Unit)? = null
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val messageRes = if (isPermanentlyDenied) R.string.perm_denied_msg else R.string.perm_rationale_msg
        val positiveBtnRes = if (isPermanentlyDenied) R.string.perm_open_settings else R.string.perm_grant

        return MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.perm_title)
            .setMessage(messageRes)
            .setCancelable(false)
            .setPositiveButton(positiveBtnRes) { dialog, _ ->
                dialog.dismiss()
                onGrantClicked()
            }
            .setNegativeButton(R.string.perm_not_now) { dialog, _ ->
                dialog.dismiss()
                onCancelClicked?.invoke()
            }
            .show()
    }
}
