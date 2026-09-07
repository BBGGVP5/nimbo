package com.danila.nimbo.network

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danila.nimbo.model.UpdateKind

class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("UpdateWorker", "Checking for updates in background (attempt=$runAttemptCount)...")
        // Видно в логе, если система придержала фон: тогда «уведомление не пришло»
        // объясняется корзиной ожидания, а не ошибкой проверки.
        Log.d("UpdateWorker", com.danila.nimbo.utils.BackgroundHealthChecker.describe(applicationContext))
        
        return try {
            val updateInfo = UpdateManager.checkUpdateInBackground(applicationContext)
            
            // Возвращается новая версия или новый APK-артефакт для текущей версии.
            if (updateInfo != null) {
                val updateType = if (updateInfo.kind == UpdateKind.REPAIR) {
                    "replacement APK"
                } else {
                    "new version"
                }
                Log.d("UpdateWorker", "$updateType available: ${updateInfo.versionName}. Showing notification.")
                val handled = UpdateManager.showUpdateNotification(applicationContext, updateInfo)
                if (handled) {
                    Log.d("UpdateWorker", "Update notification posted or was already delivered.")
                } else {
                    Log.w("UpdateWorker", "Update notification deferred until Android notifications are enabled.")
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Update check failed; WorkManager will retry", e)
            Result.retry()
        }
    }
}

