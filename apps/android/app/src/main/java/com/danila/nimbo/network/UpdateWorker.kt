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
            val updateInfo = UpdateManager.checkUpdate(applicationContext)
            
            // Возвращается новая версия или новый APK-артефакт для текущей версии.
            if (updateInfo != null) {
                Log.d("UpdateWorker", "New version available: ${updateInfo.versionName}. Showing notification.")
                val handled = UpdateManager.showUpdateNotification(applicationContext, updateInfo)
                if (handled) {
                    Log.d("UpdateWorker", "Update notification posted or was already delivered.")
                } else {
                    Log.w("UpdateWorker", "Update notification deferred until Android notifications are enabled.")
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWorker", "Update check failed", e)
            Result.retry()
        }
    }
}

