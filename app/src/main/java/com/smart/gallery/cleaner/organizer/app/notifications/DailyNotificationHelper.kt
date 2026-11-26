package com.smart.gallery.cleaner.organizer.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smart.gallery.cleaner.organizer.app.R
import com.smart.gallery.cleaner.organizer.app.activities.PrivacyPolicyActivity
import com.smart.gallery.cleaner.organizer.app.data.repository.SettingsRepository
import java.util.Locale
import java.util.concurrent.TimeUnit

object DailyNotificationHelper {

    const val CHANNEL_ID = "gallery_cleaner_daily_channel"
    const val ID_DAILY = 1001
    private const val DAILY_WORK_NAME = "gallery_cleaner_daily_reminder_work"

    private val dailyPairs: List<Pair<Int, Int>> = listOf(
        R.string.notif_daily_title_1 to R.string.notif_daily_msg_1,
        R.string.notif_daily_title_2 to R.string.notif_daily_msg_2,
        R.string.notif_daily_title_3 to R.string.notif_daily_msg_3,
        R.string.notif_daily_title_4 to R.string.notif_daily_msg_4,
        R.string.notif_daily_title_5 to R.string.notif_daily_msg_5,
        R.string.notif_daily_title_6 to R.string.notif_daily_msg_6,
        R.string.notif_daily_title_7 to R.string.notif_daily_msg_7,
        R.string.notif_daily_title_8 to R.string.notif_daily_msg_8,
        R.string.notif_daily_title_9 to R.string.notif_daily_msg_9,
        R.string.notif_daily_title_10 to R.string.notif_daily_msg_10
    )

    @Volatile
    private var largeIconCache: Bitmap? = null

    /**
     * Returns a Context configured with the user's selected app language so background
     * WorkManager notifications ALWAYS render in the language selected by the user.
     */
    fun getLocalizedContext(context: Context): Context {
        val cachedTag = SettingsRepository.getCachedLanguage(context)
        val effectiveTag = when {
            cachedTag.isNotBlank() -> cachedTag
            else -> AppCompatDelegate.getApplicationLocales().toLanguageTags()
                .split(",")
                .firstOrNull()
                ?.trim()
                .orEmpty()
        }
        if (effectiveTag.isBlank()) return context

        val locale = Locale.forLanguageTag(effectiveTag)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getRandomDailyNotification(context: Context): Pair<String, String> {
        val localized = getLocalizedContext(context)
        val (titleRes, msgRes) = dailyPairs.random()
        return localized.getString(titleRes) to localized.getString(msgRes)
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val localized = getLocalizedContext(context)
        val channelName = localized.getString(R.string.notif_channel_name)
        val channelDesc = localized.getString(R.string.notif_channel_desc)

        val channel = NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            description = channelDesc
            enableLights(true)
            lightColor = "#2DD4BF".toColorInt()
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun showDailyNotification(
        context: Context,
        notificationId: Int = ID_DAILY
    ): Boolean {
        if (!canPostNotifications(context)) return false
        createNotificationChannel(context)

        val (title, message) = getRandomDailyNotification(context)

        val intent = Intent(context, PrivacyPolicyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ai_sparkle)
            .setLargeIcon(largeIcon(context))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setColor(ContextCompat.getColor(context, R.color.primary))
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }.isSuccess
    }

    fun scheduleDailyReminder(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                DAILY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    private fun largeIcon(context: Context): Bitmap? =
        largeIconCache ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()?.also { largeIconCache = it }
}
