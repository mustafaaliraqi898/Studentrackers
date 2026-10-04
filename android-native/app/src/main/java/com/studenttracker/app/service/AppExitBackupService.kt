package com.studenttracker.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.studenttracker.app.data.AppDatabaseHelper

/**
 * Lightweight sticky lifecycle Service whose sole responsibility is to detect when the user
 * swipes the app away from the Android Recents (Overview) screen or taps "Close All" (إغلاق الكل).
 *
 * How it works:
 * 1. Started with stopWithTask="false" in AndroidManifest.xml so Android invokes onTaskRemoved()
 *    when the user removes the task from Recents (even if MainActivity was already in onStop()).
 * 2. Executes createAutoBackupOnAppExit() synchronously inside onTaskRemoved() so the SQLite
 *    WAL checkpoint + VACUUM INTO snapshot finishes before stopSelf() completes.
 * 3. Also enqueues an expedited OneTimeWorkRequest via DailyBackupWorker as a secondary OS-managed
 *    guarantee in case the OEM vendor kills the process aggressively during "Close All".
 */
class AppExitBackupService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val appContext = applicationContext
        // 1. Enqueue expedited backup worker as an OS-backed fallback if the process is killed mid-flight
        runCatching {
            DailyBackupWorker.enqueueImmediateExitBackup(appContext)
        }

        // 2. Perform synchronous local SQLite backup immediately before the process terminates
        runCatching {
            val dbHelper = AppDatabaseHelper.getInstance(appContext)
            ExcelAndBackupService.createAutoBackupOnAppExit(appContext, dbHelper)
        }

        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        fun ensureRunning(context: Context) {
            runCatching {
                val intent = Intent(context.applicationContext, AppExitBackupService::class.java)
                context.applicationContext.startService(intent)
            }
        }
    }
}
