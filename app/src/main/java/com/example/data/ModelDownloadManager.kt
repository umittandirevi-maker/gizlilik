package com.example.data

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ai.AiClassificationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(
        val progressPercent: Int,
        val downloadedMb: Float,
        val totalMb: Float = 521f,
        val speedText: String = "Downloading On-Device Qwen 0.5B"
    ) : DownloadStatus
    data object PausedWaitingWifi : DownloadStatus
    data class Completed(val filePath: String) : DownloadStatus
    data class Error(val message: String) : DownloadStatus
}

object ModelDownloadManager {
    private const val TAG = "ModelDownloadManager"
    private const val PREFS_NAME = "model_download_prefs"
    private const val KEY_DOWNLOAD_ID = "active_download_id"

    // Verified 521MB Qwen2.5-0.5B-Instruct Task URL from Hugging Face
    const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"

    const val MODEL_FILENAME = "qwen_05b.task"
    const val LEGACY_TASK_FILENAME = "qwen_05b_instruct.task"
    const val LEGACY_LITERTLM_FILENAME = "qwen_05b.litertlm"

    // The verified Qwen2.5-0.5B .task file is ~521MB. Partial downloads (<480MB) will corrupt FlatBuffer descriptors
    // and crash native XNNPack with SIGSEGV/SEGV_ACCERR if loaded prematurely.
    const val MIN_VERIFIED_MODEL_SIZE_BYTES = 480_000_000L

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null
    private var directDownloadJob: Job? = null
    private var prefs: SharedPreferences? = null

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    private val _isModelVerified = MutableStateFlow(false)
    val isModelVerified: StateFlow<Boolean> = _isModelVerified.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    fun getActiveDownloadId(context: Context): Long {
        return getPrefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
    }

    fun setActiveDownloadId(context: Context, downloadId: Long) {
        getPrefs(context).edit().putLong(KEY_DOWNLOAD_ID, downloadId).apply()
    }

    fun clearActiveDownloadId(context: Context) {
        getPrefs(context).edit().remove(KEY_DOWNLOAD_ID).apply()
    }

    fun setDownloadError(message: String) {
        _downloadStatus.value = DownloadStatus.Error(message)
    }

    /**
     * Strictly verifies that the model file exists, has completed downloading to at least 480MB,
     * and does not contain an HTML error page (from HTTP 302/404 redirects).
     */
    fun isModelFileFullyVerified(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        val length = file.length()
        if (length < MIN_VERIFIED_MODEL_SIZE_BYTES) {
            return false
        }

        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(16)
                val read = input.read(header)
                if (read < 16) return false
                val headerString = String(header)
                // If it starts with HTML or JSON error, it's not a model archive
                !headerString.startsWith("<!DOC", ignoreCase = true) &&
                        !headerString.startsWith("<html", ignoreCase = true) &&
                        !headerString.startsWith("{\"", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun hasStoragePermission(context: Context): Boolean {
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return writeGranted && readGranted
    }

    fun getModelFile(context: Context): File {
        val candidates = listOf(
            File(context.filesDir, "models/$MODEL_FILENAME"),
            File(context.filesDir, "models/$LEGACY_TASK_FILENAME"),
            File(context.filesDir, "models/$LEGACY_LITERTLM_FILENAME")
        )
        return candidates.firstOrNull { isModelFileFullyVerified(it) }
            ?: File(context.filesDir, "models/$MODEL_FILENAME")
    }

    /**
     * Inspects the system DownloadManager queue to find any active or pending download
     * matching the model file or target URL. Prevents duplicate enqueuing on screen rotation
     * or Activity recreation.
     */
    fun findActiveDownload(context: Context): Long? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null

        // 1. Check stored download ID
        val savedId = getActiveDownloadId(context)
        if (savedId != -1L) {
            try {
                val query = DownloadManager.Query().setFilterById(savedId)
                downloadManager.query(query)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusCol != -1) cursor.getInt(statusCol) else -1
                        if (status == DownloadManager.STATUS_RUNNING ||
                            status == DownloadManager.STATUS_PAUSED ||
                            status == DownloadManager.STATUS_PENDING
                        ) {
                            return savedId
                        } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val migrated = migrateDownloadedFileToInternal(context)
                            if (migrated != null && isModelFileFullyVerified(migrated)) {
                                onModelVerifiedInSandbox(context, migrated.absolutePath)
                                clearActiveDownloadId(context)
                                return null
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking saved download ID $savedId: ${e.message}")
            }
        }

        // 2. Scan system queue for matching filename or URL
        try {
            val broadQuery = DownloadManager.Query()
            downloadManager.query(broadQuery)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)

                while (cursor.moveToNext()) {
                    val id = if (idCol != -1) cursor.getLong(idCol) else -1L
                    val status = if (statusCol != -1) cursor.getInt(statusCol) else -1
                    val uri = if (uriCol != -1) cursor.getString(uriCol) ?: "" else ""
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: "" else ""

                    val isMatching = uri.contains(MODEL_FILENAME) ||
                            uri.contains("Qwen2.5-0.5B-Instruct") ||
                            title.contains("Qwen 0.5B") ||
                            title.contains(MODEL_FILENAME)

                    if (isMatching && id != -1L) {
                        if (status == DownloadManager.STATUS_RUNNING ||
                            status == DownloadManager.STATUS_PAUSED ||
                            status == DownloadManager.STATUS_PENDING
                        ) {
                            setActiveDownloadId(context, id)
                            return id
                        } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val migrated = migrateDownloadedFileToInternal(context)
                            if (migrated != null && isModelFileFullyVerified(migrated)) {
                                onModelVerifiedInSandbox(context, migrated.absolutePath)
                                clearActiveDownloadId(context)
                                return null
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Broad system queue scan failed: ${e.message}")
        }

        return null
    }

    fun checkExistingModel(context: Context): Boolean {
        val internalFile = getModelFile(context)
        if (isModelFileFullyVerified(internalFile)) {
            _isModelVerified.value = true
            _downloadStatus.value = DownloadStatus.Completed(internalFile.absolutePath)
            AiClassificationEngine.tryLoadLocalModel(context, internalFile.absolutePath)
            return true
        }

        // Try migrating from public or app-scoped downloads if already downloaded and verified
        val migrated = migrateDownloadedFileToInternal(context)
        if (migrated != null && isModelFileFullyVerified(migrated)) {
            _isModelVerified.value = true
            _downloadStatus.value = DownloadStatus.Completed(migrated.absolutePath)
            AiClassificationEngine.tryLoadLocalModel(context, migrated.absolutePath)
            return true
        }

        // Check if download is already tracked in system DownloadManager
        val activeId = findActiveDownload(context)
        if (activeId != null) {
            Log.i(TAG, "checkExistingModel: Found active download id=$activeId. Re-binding cursor monitoring.")
            startCursorMonitoring(context, activeId)
            return false
        }

        return false
    }

    /**
     * Idempotent download starter:
     * - Checks if model is already verified on disk
     * - Checks if direct sandbox download is already active
     * - Checks if system DownloadManager already has a running or pending download
     * Only enqueues a new DownloadManager request if no active task exists.
     */
    fun startDownload(context: Context, forceInternalSandbox: Boolean = false) {
        if (checkExistingModel(context)) {
            Log.i(TAG, "Model already verified on disk.")
            return
        }

        // Idempotency: Do not start if direct download is actively in progress
        if (directDownloadJob?.isActive == true) {
            Log.i(TAG, "Direct download actively running, ignoring duplicate start request.")
            return
        }

        // Idempotency: Do not start if DownloadManager task is already running or pending
        val activeId = findActiveDownload(context)
        if (activeId != null) {
            Log.i(TAG, "Existing download task $activeId in progress. Re-binding cursor monitoring.")
            startCursorMonitoring(context, activeId)
            return
        }

        // If StateFlow already indicates active downloading, don't restart
        if (_downloadStatus.value is DownloadStatus.Downloading) {
            Log.i(TAG, "DownloadStatus already Downloading, skipping duplicate start.")
            return
        }

        val hasPerms = hasStoragePermission(context)
        if (!hasPerms || forceInternalSandbox) {
            Log.i(TAG, "Storage permission denied/internal mode: starting direct sandbox download")
            startDirectSandboxDownload(context)
            return
        }

        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: throw IllegalStateException("DownloadManager service unavailable")

            // Remove previous stale or failed ID if any
            val previousId = getActiveDownloadId(context)
            if (previousId != -1L) {
                try {
                    downloadManager.remove(previousId)
                } catch (_: Exception) {}
            }

            val request = DownloadManager.Request(Uri.parse(MODEL_URL)).apply {
                addRequestHeader("User-Agent", DESKTOP_USER_AGENT)
                addRequestHeader("Accept", "*/*")
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverRoaming(false)
                setTitle("Smart Notification Filter: Qwen 0.5B AI")
                setDescription("Downloading on-device AI model weights (521MB)")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                try {
                    val publicDest = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        MODEL_FILENAME
                    )
                    if (publicDest.exists() && publicDest.length() < 1024 * 1024) {
                        publicDest.delete()
                    }
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, MODEL_FILENAME)
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot set external public dir: ${e.message}, falling back to internal")
                    startDirectSandboxDownload(context)
                    return
                }
            }

            val downloadId = downloadManager.enqueue(request)
            setActiveDownloadId(context, downloadId)
            Log.i(TAG, "Enqueued DownloadManager task id=$downloadId for $MODEL_URL")

            startCursorMonitoring(context, downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager error: ${e.message}, falling back to direct stream", e)
            startDirectSandboxDownload(context)
        }
    }

    fun retryDownload(context: Context) {
        monitorJob?.cancel()
        directDownloadJob?.cancel()
        val prevId = getActiveDownloadId(context)
        if (prevId != -1L) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(prevId)
            } catch (_: Exception) {}
            clearActiveDownloadId(context)
        }
        _downloadStatus.value = DownloadStatus.Idle
        startDownload(context, forceInternalSandbox = !hasStoragePermission(context))
    }

    private fun startDirectSandboxDownload(context: Context) {
        if (directDownloadJob?.isActive == true) {
            Log.i(TAG, "Direct download job already running, ignoring duplicate call.")
            return
        }

        directDownloadJob = scope.launch {
            val targetFile = File(context.filesDir, "models/$MODEL_FILENAME").apply { parentFile?.mkdirs() }
            val tempFile = File(context.filesDir, "models/${MODEL_FILENAME}.tmp")

            try {
                _downloadStatus.value = DownloadStatus.Downloading(
                    progressPercent = 0,
                    downloadedMb = 0f,
                    totalMb = 521f,
                    speedText = "Connecting..."
                )

                var currentUrl = MODEL_URL
                var connection: HttpURLConnection? = null
                var redirectCount = 0
                val maxRedirects = 10

                while (redirectCount < maxRedirects) {
                    val urlObj = URL(currentUrl)
                    connection = (urlObj.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
                        setRequestProperty("Accept", "*/*")
                        instanceFollowRedirects = false
                        connectTimeout = 15000
                        readTimeout = 20000
                    }

                    val code = connection.responseCode
                    if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                        code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == HttpURLConnection.HTTP_SEE_OTHER ||
                        code == 307 || code == 308
                    ) {
                        val newLocation = connection.getHeaderField("Location") ?: break
                        currentUrl = newLocation
                        redirectCount++
                        connection.disconnect()
                        continue
                    } else if (code == HttpURLConnection.HTTP_OK) {
                        break
                    } else {
                        throw IllegalStateException("HTTP Error $code: ${connection.responseMessage}")
                    }
                }

                val conn = connection ?: throw IllegalStateException("Failed to open connection")
                val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: (546660344L) // approx 521MB
                val totalMb = totalBytes / (1024f * 1024f)

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var downloadedBytes = 0L
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                tempFile.delete()
                                return@launch
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 300) {
                                lastProgressUpdate = now
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                                val dlMb = downloadedBytes / (1024f * 1024f)
                                _downloadStatus.value = DownloadStatus.Downloading(
                                    progressPercent = percent,
                                    downloadedMb = dlMb,
                                    totalMb = totalMb,
                                    speedText = "Direct Sandbox Stream"
                                )
                            }
                        }
                    }
                }

                if (tempFile.exists() && isModelFileFullyVerified(tempFile)) {
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                    onModelVerifiedInSandbox(context, targetFile.absolutePath)
                } else {
                    val currentMb = if (tempFile.exists()) tempFile.length() / (1024 * 1024) else 0
                    tempFile.delete()
                    _downloadStatus.value = DownloadStatus.Error("Download incomplete (${currentMb}MB / 521MB). Please tap Retry to continue.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct sandbox download error: ${e.message}", e)
                _downloadStatus.value = DownloadStatus.Error(e.localizedMessage ?: "Direct download failed")
            }
        }
    }

    fun startCursorMonitoring(context: Context, downloadId: Long) {
        setActiveDownloadId(context, downloadId)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return

        // Provide immediate status update from cursor so UI never shows 0% or Idle on Activity recreation
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    updateStatusFromCursor(context, cursor, downloadId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in immediate cursor check for $downloadId: ${e.message}")
        }

        if (monitorJob?.isActive == true) {
            // Monitoring loop is already running
            return
        }

        monitorJob = scope.launch {
            var isRunning = true

            while (isRunning && isActive) {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)

                    if (cursor != null && cursor.moveToFirst()) {
                        isRunning = updateStatusFromCursor(context, cursor, downloadId)
                        cursor.close()
                    } else {
                        cursor?.close()
                        // If download ID is not found, verify if model was migrated
                        if (checkExistingModel(context)) {
                            isRunning = false
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cursor monitoring loop error: ${e.message}")
                }

                delay(500)
            }
        }
    }

    private fun updateStatusFromCursor(context: Context, cursor: Cursor, downloadId: Long): Boolean {
        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

        val status = if (statusCol != -1) cursor.getInt(statusCol) else -1
        val bytesDownloaded = if (bytesCol != -1) cursor.getLong(bytesCol) else 0L
        val totalBytes = if (totalCol != -1) cursor.getLong(totalCol) else 0L

        when (status) {
            DownloadManager.STATUS_PENDING -> {
                _downloadStatus.value = DownloadStatus.Downloading(
                    progressPercent = 0,
                    downloadedMb = 0f,
                    totalMb = 521f,
                    speedText = "Waiting for download to begin..."
                )
                return true
            }
            DownloadManager.STATUS_RUNNING -> {
                val percent = if (totalBytes > 0) {
                    ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                } else {
                    0
                }
                val dlMb = bytesDownloaded / (1024f * 1024f)
                val totMb = if (totalBytes > 0) totalBytes / (1024f * 1024f) else 521f

                _downloadStatus.value = DownloadStatus.Downloading(
                    progressPercent = percent,
                    downloadedMb = dlMb,
                    totalMb = totMb,
                    speedText = "Android DownloadManager"
                )
                return true
            }
            DownloadManager.STATUS_PAUSED -> {
                _downloadStatus.value = DownloadStatus.PausedWaitingWifi
                return true
            }
            DownloadManager.STATUS_SUCCESSFUL -> {
                val finalFile = migrateDownloadedFileToInternal(context)
                if (finalFile != null && isModelFileFullyVerified(finalFile)) {
                    onModelVerifiedInSandbox(context, finalFile.absolutePath)
                    clearActiveDownloadId(context)
                } else {
                    _downloadStatus.value = DownloadStatus.Error("Model verification failed on disk (file incomplete). Please tap Retry.")
                }
                return false
            }
            DownloadManager.STATUS_FAILED -> {
                val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                val reason = if (reasonCol != -1) cursor.getInt(reasonCol) else -1
                Log.e(TAG, "DownloadManager failed code $reason, falling back to direct sandbox download")
                clearActiveDownloadId(context)
                // Fallback automatically to internal sandbox download!
                startDirectSandboxDownload(context)
                return false
            }
            else -> return true
        }
    }

    fun onModelVerifiedInSandbox(context: Context, path: String) {
        val file = File(path)
        if (!isModelFileFullyVerified(file)) {
            Log.w(TAG, "onModelVerifiedInSandbox rejected incomplete file (${file.length()} bytes)")
            _downloadStatus.value = DownloadStatus.Error("Model file incomplete (${file.length() / (1024 * 1024)}MB / 521MB). Please tap Retry.")
            return
        }
        _isModelVerified.value = true
        _downloadStatus.value = DownloadStatus.Completed(path)
        clearActiveDownloadId(context)
        AiClassificationEngine.tryLoadLocalModel(context, path)
        NotificationRepository.setOnboardingCompleted(true)
        Log.i(TAG, "Model verified in sandbox at $path")
    }

    /**
     * Safely reads the model file bytes from external public downloads folder,
     * streams/copies them directly into the app's secure internal sandbox directory
     * (context.filesDir.absolutePath + "/models/qwen_05b.task"),
     * and instantly deletes the file from the public directory.
     */
    fun migrateDownloadedFileToInternal(context: Context, sourcePublicFile: File? = null): File? {
        val targetSandboxFile = File(context.filesDir, "models/$MODEL_FILENAME").apply { parentFile?.mkdirs() }

        if (isModelFileFullyVerified(targetSandboxFile)) {
            sourcePublicFile?.let { if (it.exists()) it.delete() }
            return targetSandboxFile
        }

        val candidates = mutableListOf<File>()
        if (sourcePublicFile != null) {
            candidates.add(sourcePublicFile)
        }
        candidates.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), MODEL_FILENAME))
        candidates.add(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), MODEL_FILENAME))
        candidates.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LEGACY_TASK_FILENAME))
        candidates.add(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), LEGACY_TASK_FILENAME))
        candidates.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), LEGACY_LITERTLM_FILENAME))
        candidates.add(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), LEGACY_LITERTLM_FILENAME))

        for (source in candidates) {
            if (isModelFileFullyVerified(source)) {
                try {
                    FileInputStream(source).use { input ->
                        FileOutputStream(targetSandboxFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Model safely migrated to internal sandbox: ${targetSandboxFile.absolutePath} (${targetSandboxFile.length()} bytes)")
                    source.delete()
                    Log.i(TAG, "Deleted public model file for storage hygiene.")
                    return targetSandboxFile
                } catch (e: Exception) {
                    Log.e(TAG, "Failed migrating from ${source.absolutePath}: ${e.message}", e)
                }
            }
        }

        return if (isModelFileFullyVerified(targetSandboxFile)) targetSandboxFile else null
    }

    // Alias for receiver
    fun migratePublicDownloadToInternal(context: Context, sourcePublicFile: File? = null): File? {
        return migrateDownloadedFileToInternal(context, sourcePublicFile)
    }
}
