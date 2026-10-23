package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.ai.AiClassificationEngine
import com.example.data.NotificationRepository

class SmartFilterApp : Application() {

    companion object {
        const val CHANNEL_ALERTS = "channel_priority_alerts"
        lateinit var instance: SmartFilterApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationRepository.initialize(this)
        AiClassificationEngine.initialize(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "FocusAI Priority Alerts",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Loud emergency audio alert overrides for critical notifications"
            }

            notificationManager.createNotificationChannel(alertChannel)
        }
    }
}
