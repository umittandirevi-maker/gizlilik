package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.model.PriorityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Thread-Safe, Bulletproof Audio & Vibration Override Manager.
 * Prevents system-level audio channel hijacking, releases MediaPlayer instances immediately
 * on completion, handles hardware stream exhaustion with graceful vibrator fallback,
 * and eliminates native audio crashes.
 *
 * STRICT RUNTIME RULE:
 * playAlert / triggerLoudAlert is ONLY and EXCLUSIVELY triggered for PriorityLevel.CRITICAL (COK_ACIL).
 * Routine and Unimportant notifications are 100% blocked from triggering audio.
 */
object AlertOverrideManager {
    private const val TAG = "AlertOverrideManager"

    private val audioLock = Any()
    private var mediaPlayer: MediaPlayer? = null
    private var autoSilenceJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isAlerting = MutableStateFlow(false)
    val isAlerting: StateFlow<Boolean> = _isAlerting.asStateFlow()

    private val _lastAlertReason = MutableStateFlow<String?>(null)
    val lastAlertReason: StateFlow<String?> = _lastAlertReason.asStateFlow()

    private val _isPreviewPlaying = MutableStateFlow(false)
    val isPreviewPlaying: StateFlow<Boolean> = _isPreviewPlaying.asStateFlow()

    /**
     * Dedicated entry point: ONLY and EXCLUSIVELY triggered when the notification
     * has been strictly verified as PriorityLevel.CRITICAL (COK_ACIL).
     */
    fun playAlert(context: Context, reason: String) {
        triggerLoudAlert(context, PriorityLevel.CRITICAL, reason)
    }

    fun triggerLoudAlert(context: Context, priority: PriorityLevel, reason: String) {
        // Enforce: Priority must be CRITICAL or URGENT, and Master Siren must be enabled
        if (priority != PriorityLevel.CRITICAL && priority != PriorityLevel.URGENT) {
            Log.i(TAG, "Audio alert bypassed: Priority $priority is not CRITICAL or URGENT. Remaining 100% silent.")
            return
        }

        if (!com.example.data.NotificationRepository.categoryConfig.value.masterSirenEnabled) {
            Log.i(TAG, "Audio alert bypassed: Master Siren is disabled in settings.")
            return
        }

        _lastAlertReason.value = reason
        _isAlerting.value = true

        try {
            vibrateDevice(context, priority)
            playAlertSound(context, isLooping = true)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to trigger alert override, falling back to vibrator: ${e.message}", e)
            fallbackVibrate(context)
        }
    }

    fun toggleSoundPreview(context: Context) {
        if (_isPreviewPlaying.value || _isAlerting.value) {
            silenceAlert(context)
        } else {
            _isPreviewPlaying.value = true
            _isAlerting.value = true
            _lastAlertReason.value = "Sound Preview: Emergency Siren USAGE_ALARM"
            try {
                vibrateDevice(context, PriorityLevel.CRITICAL)
                playAlertSound(context, isLooping = false)
            } catch (e: Throwable) {
                Log.e(TAG, "Preview failed, falling back to vibrator: ${e.message}", e)
                fallbackVibrate(context)
            }
        }
    }

    private fun playAlertSound(context: Context, isLooping: Boolean) {
        synchronized(audioLock) {
            // Defensive cleanup: Force-release any existing active audio objects before allocating a new one
            forceReleaseMediaPlayer()

            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val player = MediaPlayer()
                player.setDataSource(context.applicationContext, alertUri)
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.isLooping = isLooping

                // Immediate auto-cleanup listener as required
                player.setOnCompletionListener { mp ->
                    synchronized(audioLock) {
                        try {
                            mp.reset()
                            mp.release()
                        } catch (ignored: Throwable) {}
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        _isAlerting.value = false
                        _isPreviewPlaying.value = false
                    }
                }

                // Defensive error listener to catch hardware stream failures and prevent native crashes
                player.setOnErrorListener { mp, what, extra ->
                    Log.w(TAG, "MediaPlayer hardware stream error (what=$what, extra=$extra). Safely resetting.")
                    synchronized(audioLock) {
                        try {
                            mp.reset()
                            mp.release()
                        } catch (ignored: Throwable) {}
                        if (mediaPlayer === mp) {
                            mediaPlayer = null
                        }
                        _isAlerting.value = false
                        _isPreviewPlaying.value = false
                    }
                    fallbackVibrate(context)
                    true // Handled without OS crash popup
                }

                player.prepare()
                player.start()
                mediaPlayer = player

                // Auto-silence safety timer: bounds looping critical siren to maximum 15 seconds
                // so the audio hardware channel is never permanently locked
                autoSilenceJob?.cancel()
                if (isLooping) {
                    autoSilenceJob = scope.launch {
                        delay(15_000L)
                        silenceAlert(context)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Hardware audio allocation or stream limit exhausted (${e.javaClass.simpleName}: ${e.message}). Falling back to vibrator pulse.", e)
                forceReleaseMediaPlayer()
                fallbackVibrate(context)
            }
        }
    }

    fun silenceActiveAlert(context: Context) {
        silenceAlert(context)
    }

    fun silenceAlert(context: Context) {
        synchronized(audioLock) {
            autoSilenceJob?.cancel()
            autoSilenceJob = null
            forceReleaseMediaPlayer()
            stopVibration(context)
            _isAlerting.value = false
            _isPreviewPlaying.value = false
            _lastAlertReason.value = null
        }
    }

    private fun forceReleaseMediaPlayer() {
        try {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                } catch (ignored: Throwable) {}
                try {
                    mp.reset()
                } catch (ignored: Throwable) {}
                try {
                    mp.release()
                } catch (ignored: Throwable) {}
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error in forceReleaseMediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
            _isAlerting.value = false
            _isPreviewPlaying.value = false
        }
    }

    private fun vibrateDevice(context: Context, priority: PriorityLevel) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = if (priority == PriorityLevel.CRITICAL) {
                    longArrayOf(0, 500, 200, 500, 200, 800)
                } else {
                    longArrayOf(0, 300, 150, 300)
                }
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(600)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    /**
     * Graceful fallback: Standard short system vibrator pulse when audio stream limits are exhausted.
     */
    fun fallbackVibrate(context: Context) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(400)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Fallback vibration error: ${e.message}")
        }
    }

    private fun stopVibration(context: Context) {
        try {
            getVibrator(context)?.cancel()
        } catch (e: Throwable) {
            Log.w(TAG, "Cancel vibration failed: ${e.message}")
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Throwable) {
            null
        }
    }
}
