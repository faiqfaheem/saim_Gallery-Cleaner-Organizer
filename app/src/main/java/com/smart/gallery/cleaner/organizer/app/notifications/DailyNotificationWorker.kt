package com.smart.gallery.cleaner.organizer.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        DailyNotificationHelper.showDailyNotification(
            context = applicationContext,
            notificationId = DailyNotificationHelper.ID_DAILY
        )
        return Result.success()
    }
}
