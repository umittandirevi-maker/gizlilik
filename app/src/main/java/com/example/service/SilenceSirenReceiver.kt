package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.audio.AlertOverrideManager

/**
 * BroadcastReceiver to intercept "🚫 Silence Siren" action button clicks
 * directly from the system notification bar without opening the app.
 */
class SilenceSirenReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SILENCE_SIREN = "com.example.action.SILENCE_SIREN"
        const val EXTRA_NOTIFICATION_ID = "com.example.extra.NOTIFICATION_ID"
        const val EMERGENCY_ALERT_NOTIFICATION_ID = 8888
        private const val TAG = "SilenceSirenReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.i(TAG, "onReceive triggered with action: $action")

        if (action == ACTION_SILENCE_SIREN) {
            // Instantly silence the active siren audio loop and stop vibration
            AlertOverrideManager.silenceActiveAlert(context)

            // Dismiss the alert notification from status bar
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, EMERGENCY_ALERT_NOTIFICATION_ID)
            try {
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(notifId)
                Log.i(TAG, "Successfully silenced siren and dismissed notification id: $notifId")
            } catch (e: Exception) {
                Log.w(TAG, "Error canceling notification $notifId: ${e.message}")
            }
        }
    }
}
