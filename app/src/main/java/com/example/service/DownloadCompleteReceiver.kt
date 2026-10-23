package com.example.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.ModelDownloadManager
import com.example.data.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background Safety & Process Resilience:
 * BroadcastReceiver triggered by Android OS when DownloadManager finishes a download.
 * Uses goAsync() to copy and verify the 480MB+ model file completely independent of the UI state.
 * Even if the app is fully swiped away or closed when the download hits 100%, the OS-level receiver
 * securely moves the model into the internal sandbox and updates onboarding status.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {
    private val tag = "DownloadCompleteReceiver"

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        Log.i(tag, "Received ACTION_DOWNLOAD_COMPLETE for download ID: $downloadId")

        // Use goAsync() to allow up to 30 seconds of background IO execution
        // without blocking the main thread or being killed prematurely by Android OS.
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                if (downloadManager == null) {
                    Log.e(tag, "DownloadManager service unavailable in broadcast receiver")
                    return@launch
                }

                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)

                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        Log.i(tag, "DownloadManager reports STATUS_SUCCESSFUL. Starting internal sandbox migration in background...")
                        val migratedFile = ModelDownloadManager.migrateDownloadedFileToInternal(appContext)

                        if (migratedFile != null && ModelDownloadManager.isModelFileFullyVerified(migratedFile)) {
                            Log.i(tag, "Model verified in sandbox at ${migratedFile.absolutePath} (${migratedFile.length()} bytes)")
                            NotificationRepository.initialize(appContext)
                            NotificationRepository.setOnboardingCompleted(true)
                            ModelDownloadManager.onModelVerifiedInSandbox(appContext, migratedFile.absolutePath)
                        } else {
                            Log.w(tag, "Model migration could not locate fully verified downloaded file (expected >= 480MB)")
                            ModelDownloadManager.setDownloadError("Model verification failed on disk (file incomplete). Please tap Retry.")
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIndex != -1) cursor.getInt(reasonIndex) else -1
                        Log.e(tag, "Download failed with reason code: $reason. Triggering sandbox fallback.")
                        ModelDownloadManager.clearActiveDownloadId(appContext)
                        ModelDownloadManager.startDownload(appContext, forceInternalSandbox = true)
                    }
                    cursor.close()
                } else {
                    cursor?.close()
                }
            } catch (e: Throwable) {
                Log.e(tag, "Error handling download complete receiver in background: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
