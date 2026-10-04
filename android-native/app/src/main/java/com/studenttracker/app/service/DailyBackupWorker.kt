package com.studenttracker.app.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.studenttracker.app.data.AppDatabaseHelper
import java.util.concurrent.TimeUnit

class DailyBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val dbHelper = AppDatabaseHelper.getInstance(applicationContext)
            ExcelAndBackupService.ensureDailyAutoBackup(applicationContext, dbHelper)
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyBackupWorker", "Auto daily backup failed", e)
            ExcelAndBackupService.recordBackupError(applicationContext, "WorkManagerAutoBackup", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "StudentTracker_DailyLocalBackupWork"

        fun scheduleDailyBackup(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
