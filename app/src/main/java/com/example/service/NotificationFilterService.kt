package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.SmartFilterApp
import com.example.ai.AiClassificationEngine
import com.example.audio.AlertOverrideManager
import com.example.data.NotificationRepository
import com.example.model.AnalyzedNotification
import com.example.model.PriorityLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class NotificationFilterService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "NotificationFilterSvc"
        const val EXTRA_IS_GROUP = "com.example.extra.IS_GROUP"
        const val EXTRA_APP_TYPE = "com.example.extra.APP_TYPE"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return

        if (packageName == this.packageName) {
            return // Ignore our own internal app notifications
        }

        if (!NotificationRepository.isFilteringActive.value) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            val pm = applicationContext.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }

        val isGroup = if (extras.containsKey(EXTRA_IS_GROUP)) {
            extras.getBoolean(EXTRA_IS_GROUP)
        } else {
            extras.containsKey("android.isGroupConversation") && extras.getBoolean("android.isGroupConversation")
        }

        val appType = if (extras.containsKey(EXTRA_APP_TYPE)) {
            extras.getString(EXTRA_APP_TYPE) ?: "UTILITY_OR_GAME"
        } else if (sbn.packageName.contains("whatsapp", ignoreCase = true) ||
            sbn.packageName.contains("telegram", ignoreCase = true) ||
            sbn.packageName.contains("messaging", ignoreCase = true) ||
            sbn.packageName.contains("mms", ignoreCase = true)
        ) {
            "MESSENGER"
        } else {
            "UTILITY_OR_GAME"
        }

        val channelType = if (isGroup) "GROUP_CHAT_BROADCAST" else "DIRECT_1_ON_1"

        val formattedPrompt = """
        App Type: $appType
        Channel Type: $channelType
        Sender/Source: $title
        Message Content: $text
        """.trimIndent()

        serviceScope.launch {
            var aiResponse: String? = null
            var currentPriority: PriorityLevel = PriorityLevel.ROUTINE
            var wasDismissed = false
            var soundAlertTriggered = false
            var syntacticScore = 0
            var diagnosticSummary = ""

            try {
                val classification = try {
                    withTimeoutOrNull(900L) {
                        AiClassificationEngine.classifyNotification(
                            context = applicationContext,
                            formattedPrompt = formattedPrompt,
                            appType = appType,
                            channelType = channelType,
                            title = title,
                            body = text
                        )
                    } ?: run {
                        Log.w(TAG, "Classification hit 900ms boundary; executing linguistic fast-path")
                        AiClassificationEngine.classifyNotification(
                            context = applicationContext,
                            formattedPrompt = formattedPrompt,
                            appType = appType,
                            channelType = channelType,
                            title = title,
                            body = text
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Classification shield intercepted: ${t.message}. Falling back directly to linguistic parser.", t)
                    AiClassificationEngine.classifyNotification(
                        context = applicationContext,
                        formattedPrompt = formattedPrompt,
                        appType = appType,
                        channelType = channelType,
                        title = title,
                        body = text
                    )
                }

                aiResponse = classification.rawOutput
                currentPriority = classification.priority
                syntacticScore = classification.syntacticScore
                diagnosticSummary = classification.diagnosticSummary

                val isWhitelisted = NotificationRepository.isPackageWhitelisted(packageName)
                val config = NotificationRepository.categoryConfig.value

                // Strict Evaluation of Verified Priority Category based on CategoryMuteConfig
                when (currentPriority) {
                    PriorityLevel.UNIMPORTANT -> {
                        if (isWhitelisted || config.unimportantNotify) {
                            wasDismissed = false
                            soundAlertTriggered = false
                            Log.i(TAG, "UNIMPORTANT notification allowed by config/whitelist from $packageName")
                        } else {
                            wasDismissed = true
                            soundAlertTriggered = false
                            try {
                                cancelNotification(sbn.key)
                                Log.i(TAG, "Silently canceled UNIMPORTANT notification key=${sbn.key} from $packageName")
                            } catch (t: Throwable) {
                                Log.w(TAG, "cancelNotification error: ${t.message}")
                            }
                        }
                    }
                    PriorityLevel.ROUTINE -> {
                        if (isWhitelisted || config.routineNotify) {
                            wasDismissed = false
                            soundAlertTriggered = false
                            Log.i(TAG, "ROUTINE notification allowed by config/whitelist from $packageName")
                        } else {
                            // Filter Active: Routine section muted by default
                            wasDismissed = true
                            soundAlertTriggered = false
                            try {
                                cancelNotification(sbn.key)
                                Log.i(TAG, "Silently canceled ROUTINE notification key=${sbn.key} from $packageName")
                            } catch (t: Throwable) {
                                Log.w(TAG, "cancelNotification error: ${t.message}")
                            }
                        }
                    }
                    PriorityLevel.URGENT -> {
                        if (!config.urgentNotify && !isWhitelisted) {
                            wasDismissed = true
                            soundAlertTriggered = false
                            try {
                                cancelNotification(sbn.key)
                                Log.i(TAG, "Silently canceled URGENT notification key=${sbn.key} from $packageName")
                            } catch (t: Throwable) {
                                Log.w(TAG, "cancelNotification error: ${t.message}")
                            }
                        } else {
                            wasDismissed = false
                            if (config.urgentSiren && config.masterSirenEnabled) {
                                soundAlertTriggered = true
                                try {
                                    AlertOverrideManager.triggerLoudAlert(
                                        context = applicationContext,
                                        priority = PriorityLevel.URGENT,
                                        reason = "URGENT: $appName - $title"
                                    )
                                    postCriticalAlertNotification(appName, title, text)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Alert trigger caught: ${t.message}")
                                }
                            } else {
                                soundAlertTriggered = false
                            }
                        }
                    }
                    PriorityLevel.CRITICAL -> {
                        if (!config.criticalNotify && !isWhitelisted) {
                            wasDismissed = true
                            soundAlertTriggered = false
                            try {
                                cancelNotification(sbn.key)
                                Log.i(TAG, "Silently canceled CRITICAL notification key=${sbn.key} from $packageName")
                            } catch (t: Throwable) {
                                Log.w(TAG, "cancelNotification error: ${t.message}")
                            }
                        } else {
                            wasDismissed = false
                            if (config.criticalSiren && config.masterSirenEnabled) {
                                soundAlertTriggered = true
                                try {
                                    AlertOverrideManager.playAlert(
                                        context = applicationContext,
                                        reason = "CRITICAL: $appName - $title"
                                    )
                                    postCriticalAlertNotification(appName, title, text)
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Alert trigger caught: ${t.message}")
                                }
                            } else {
                                soundAlertTriggered = false
                            }
                        }
                    }
                }

                val analyzed = AnalyzedNotification(
                    id = UUID.randomUUID().toString(),
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    body = text,
                    timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    priority = currentPriority,
                    rawAiOutput = aiResponse ?: "ROUTINE",
                    wasDismissed = wasDismissed,
                    soundAlertTriggered = soundAlertTriggered,
                    inferenceTimeMs = classification.inferenceTimeMs,
                    syntacticScore = syntacticScore,
                    diagnosticSummary = diagnosticSummary
                )

                withContext(Dispatchers.Main) {
                    NotificationRepository.addNotification(analyzed)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error evaluating incoming notification: ${t.message}", t)
            } finally {
                // Aggressively flush and reset variables to prevent state leaks
                aiResponse = null
                currentPriority = PriorityLevel.ROUTINE
                wasDismissed = false
                soundAlertTriggered = false
            }
        }
    }

    private fun postCriticalAlertNotification(appName: String, title: String, text: String) {
        try {
            val silenceIntent = Intent(applicationContext, SilenceSirenReceiver::class.java).apply {
                action = SilenceSirenReceiver.ACTION_SILENCE_SIREN
                putExtra(SilenceSirenReceiver.EXTRA_NOTIFICATION_ID, SilenceSirenReceiver.EMERGENCY_ALERT_NOTIFICATION_ID)
            }
            val silencePendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                SilenceSirenReceiver.EMERGENCY_ALERT_NOTIFICATION_ID,
                silenceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val silenceAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_lock_silent_mode_off,
                "🚫 Silence Siren",
                silencePendingIntent
            ).build()

            val notification = NotificationCompat.Builder(applicationContext, SmartFilterApp.CHANNEL_ALERTS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 CRITICAL: $appName")
                .setContentText(if (text.isNotBlank()) "$title: $text" else title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (text.isNotBlank()) "$title: $text" else title))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .addAction(silenceAction)
                .build()

            val notificationManager = NotificationManagerCompat.from(applicationContext)
            notificationManager.notify(SilenceSirenReceiver.EMERGENCY_ALERT_NOTIFICATION_ID, notification)
            Log.i(TAG, "Dispatched critical alert notification with '🚫 Silence Siren' action button")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post critical alert notification with silence action: ${e.message}")
        }
    }
}
