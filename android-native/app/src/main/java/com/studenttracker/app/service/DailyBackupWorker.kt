package com.studenttracker.app.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
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
            ExcelAndBackupService.createAutoBackupOnAppExit(applicationContext, dbHelper)
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyBackupWorker", "Auto exit backup failed", e)
            ExcelAndBackupService.recordBackupError(applicationContext, "WorkManagerExitBackup", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "StudentTracker_DailyLocalBackupWork"
        private const val EXIT_WORK_NAME = "StudentTracker_ImmediateExitBackupWork"

        /**
         * Enqueues an expedited one-time backup job when the app is swiped away from Recents
         * or closed via "Close All". If the synchronous backup in onTaskRemoved() already
         * completed within the last 10 seconds, createAutoBackupOnAppExit() will safely skip
         * creating a duplicate.
         */
        fun enqueueImmediateExitBackup(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyBackupWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                EXIT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

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
