package com.smart.gallery.cleaner.organizer.app.utils

import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }
fun View.visibleIf(condition: Boolean) { visibility = if (condition) View.VISIBLE else View.GONE }

fun View.animateFadeIn(duration: Long = 300) {
    alpha = 0f
    visible()
    animate().alpha(1f).setDuration(duration).start()
}

fun View.animateFadeOut(duration: Long = 200, onEnd: (() -> Unit)? = null) {
    animate().alpha(0f).setDuration(duration).withEndAction {
        gone()
        onEnd?.invoke()
    }.start()
}

fun View.setScaleAnimation(context: Context, animResId: Int) {
    startAnimation(AnimationUtils.loadAnimation(context, animResId))
}

fun Fragment.showToast(message: String, long: Boolean = false) {
    Toast.makeText(
        requireContext(),
        message,
        if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    ).show()
}

fun Fragment.showSnackbar(
    message: String,
    actionLabel: String? = null,
    action: (() -> Unit)? = null
) {
    val snack = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
    if (actionLabel != null && action != null) {
        snack.setAction(actionLabel) { action() }
    }
    snack.show()
}

fun Long.toFormattedDate(): String {
    if (this == 0L) return ""
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toRelativeDate(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> toFormattedDate()
    }
}

fun Long.formatDuration(): String {
    val seconds = this / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        else -> "%d:%02d".format(minutes, seconds % 60)
    }
}

fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()
