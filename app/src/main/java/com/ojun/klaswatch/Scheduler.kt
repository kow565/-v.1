package com.ojun.klaswatch

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    private const val UNIQUE = "klas_periodic_monitor"
    private const val KEEP_ALIVE_UNIQUE = "klas_session_keep_alive"
    private const val SCHOLARSHIP_UNIQUE = "scholarship_monitor"

    fun schedule(context: Context) {
        val minutes = AppPrefs.intervalMinutes(context).coerceAtLeast(15L)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val req = PeriodicWorkRequestBuilder<MonitorWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )

        val keepAlive = PeriodicWorkRequestBuilder<KeepAliveWorker>(60, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            KEEP_ALIVE_UNIQUE,
            ExistingPeriodicWorkPolicy.UPDATE,
            keepAlive
        )

        val scholarship = PeriodicWorkRequestBuilder<ScholarshipWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SCHOLARSHIP_UNIQUE,
            ExistingPeriodicWorkPolicy.UPDATE,
            scholarship
        )
    }

    fun runNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(context)
        wm.enqueue(OneTimeWorkRequestBuilder<MonitorWorker>().setConstraints(constraints).build())
        wm.enqueue(OneTimeWorkRequestBuilder<KeepAliveWorker>().setConstraints(constraints).build())
        wm.enqueue(OneTimeWorkRequestBuilder<ScholarshipWorker>().setConstraints(constraints).build())
    }
}
