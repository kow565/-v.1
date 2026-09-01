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

    fun schedule(context: Context) {
        val minutes = AppPrefs.intervalMinutes(context).coerceAtLeast(15L)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val req = PeriodicWorkRequestBuilder<MonitorWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun runNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<MonitorWorker>().setConstraints(constraints).build())
    }
}
