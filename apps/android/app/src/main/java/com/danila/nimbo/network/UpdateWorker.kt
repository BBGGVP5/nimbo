package com.danila.nimbo.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("UpdateWorker", "Checking for updates in background...")

        return try {
            val updateInfo = UpdateManager.checkUpdate()

            // checkUpdate() возвращает объект только для реально более нового релиза.
            if (updateInfo != null) {
                Log.d("UpdateWorker", "New version available: ${updateInfo.versionName}. Showing notification.")
                UpdateManager.showUpdateNotification(applicationContext, updateInfo)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Update check failed", e)
            Result.retry()
        }
    }
}
