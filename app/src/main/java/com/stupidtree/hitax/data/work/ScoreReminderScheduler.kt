package com.stupidtree.hitax.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ScoreReminderScheduler {
    private const val UNIQUE_PERIODIC_NAME = "score_reminder_periodic"
    private const val UNIQUE_IMMEDIATE_NAME = "score_reminder_once"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val periodicRequest = PeriodicWorkRequestBuilder<ScoreReminderWorker>(
            30, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )

        val immediateRequest = OneTimeWorkRequestBuilder<ScoreReminderWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_NAME,
            ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_NAME)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_IMMEDIATE_NAME)
    }
}
