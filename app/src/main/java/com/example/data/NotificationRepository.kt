package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AnalyzedNotification
import com.example.model.CategoryMuteConfig
import com.example.model.PriorityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationRepository {
    private const val PREFS_NAME = "smart_notification_filter_prefs"
    private const val KEY_FILTERING_ACTIVE = "key_filtering_active"
    private const val KEY_ONBOARDING_DONE = "key_onboarding_done"
    private const val KEY_WHITELIST_PACKAGES = "key_whitelist_packages"

    // Category mute config preference keys
    private const val KEY_UNIMPORTANT_NOTIFY = "key_unimportant_notify"
    private const val KEY_ROUTINE_NOTIFY = "key_routine_notify"
    private const val KEY_URGENT_SIREN = "key_urgent_siren"
    private const val KEY_URGENT_NOTIFY = "key_urgent_notify"
    private const val KEY_CRITICAL_SIREN = "key_critical_siren"
    private const val KEY_CRITICAL_NOTIFY = "key_critical_notify"
    private const val KEY_MASTER_SIREN = "key_master_siren"

    private var sharedPreferences: SharedPreferences? = null

    private val _isFilteringActive = MutableStateFlow(false)
    val isFilteringActive: StateFlow<Boolean> = _isFilteringActive.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    // Default: empty whitelist so all application checkboxes start strictly unchecked!
    private val _whitelistPackages = MutableStateFlow<Set<String>>(emptySet())
    val whitelistPackages: StateFlow<Set<String>> = _whitelistPackages.asStateFlow()

    private val _categoryConfig = MutableStateFlow(CategoryMuteConfig())
    val categoryConfig: StateFlow<CategoryMuteConfig> = _categoryConfig.asStateFlow()

    private val _recentNotifications = MutableStateFlow<List<AnalyzedNotification>>(emptyList())
    val recentNotifications: StateFlow<List<AnalyzedNotification>> = _recentNotifications.asStateFlow()

    private val _totalIntercepted = MutableStateFlow(0)
    val totalIntercepted: StateFlow<Int> = _totalIntercepted.asStateFlow()

    private val _totalDismissed = MutableStateFlow(0)
    val totalDismissed: StateFlow<Int> = _totalDismissed.asStateFlow()

    private val _totalUrgent = MutableStateFlow(0)
    val totalUrgent: StateFlow<Int> = _totalUrgent.asStateFlow()

    fun initialize(context: Context) {
        if (sharedPreferences == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPreferences = prefs
            _isFilteringActive.value = prefs.getBoolean(KEY_FILTERING_ACTIVE, false)
            _hasCompletedOnboarding.value = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

            val savedWhitelist = prefs.getStringSet(KEY_WHITELIST_PACKAGES, null)
            _whitelistPackages.value = savedWhitelist ?: emptySet()

            _categoryConfig.value = CategoryMuteConfig(
                unimportantNotify = prefs.getBoolean(KEY_UNIMPORTANT_NOTIFY, false),
                routineNotify = prefs.getBoolean(KEY_ROUTINE_NOTIFY, false),
                urgentSiren = prefs.getBoolean(KEY_URGENT_SIREN, false),
                urgentNotify = prefs.getBoolean(KEY_URGENT_NOTIFY, true),
                criticalSiren = prefs.getBoolean(KEY_CRITICAL_SIREN, true),
                criticalNotify = prefs.getBoolean(KEY_CRITICAL_NOTIFY, true),
                masterSirenEnabled = prefs.getBoolean(KEY_MASTER_SIREN, true)
            )
        }
    }

    fun toggleWhitelistPackage(packageName: String) {
        val current = _whitelistPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _whitelistPackages.value = current
        sharedPreferences?.edit()?.putStringSet(KEY_WHITELIST_PACKAGES, current)?.apply()
    }

    fun isPackageWhitelisted(packageName: String): Boolean {
        return _whitelistPackages.value.contains(packageName)
    }

    fun toggleFilteringActive() {
        val next = !_isFilteringActive.value
        _isFilteringActive.value = next
        sharedPreferences?.edit()?.putBoolean(KEY_FILTERING_ACTIVE, next)?.apply()

        // When Filter Active is enabled, Routine section should not send notifications
        if (next) {
            setRoutineNotify(false)
        }
    }

    fun toggleMasterSiren(): Boolean {
        val current = _categoryConfig.value.masterSirenEnabled
        val next = !current
        val newConfig = _categoryConfig.value.copy(masterSirenEnabled = next)
        _categoryConfig.value = newConfig
        sharedPreferences?.edit()?.putBoolean(KEY_MASTER_SIREN, next)?.apply()
        return next
    }

    fun setUnimportantNotify(enabled: Boolean) {
        val newConfig = _categoryConfig.value.copy(unimportantNotify = enabled)
        _categoryConfig.value = newConfig
        sharedPreferences?.edit()?.putBoolean(KEY_UNIMPORTANT_NOTIFY, enabled)?.apply()
    }

    fun setRoutineNotify(enabled: Boolean) {
        val newConfig = _categoryConfig.value.copy(routineNotify = enabled)
        _categoryConfig.value = newConfig
        sharedPreferences?.edit()?.putBoolean(KEY_ROUTINE_NOTIFY, enabled)?.apply()
    }

    /**
     * Urgent Siren toggle.
     * When siren is turned ON, notification must also be ON.
     */
    fun setUrgentSiren(enabled: Boolean) {
        val current = _categoryConfig.value
        val newConfig = if (enabled) {
            current.copy(urgentSiren = true, urgentNotify = true)
        } else {
            current.copy(urgentSiren = false)
        }
        _categoryConfig.value = newConfig
        sharedPreferences?.edit()
            ?.putBoolean(KEY_URGENT_SIREN, newConfig.urgentSiren)
            ?.putBoolean(KEY_URGENT_NOTIFY, newConfig.urgentNotify)
            ?.apply()
    }

    /**
     * Urgent Notification toggle.
     * Constraint: Notification doesn't turn off until siren is turned off!
     * If user turns notification off while siren is on, siren is also turned off.
     */
    fun setUrgentNotify(enabled: Boolean) {
        val current = _categoryConfig.value
        val newConfig = if (!enabled) {
            // Turning off notification requires siren to be turned off first
            current.copy(urgentNotify = false, urgentSiren = false)
        } else {
            current.copy(urgentNotify = true)
        }
        _categoryConfig.value = newConfig
        sharedPreferences?.edit()
            ?.putBoolean(KEY_URGENT_SIREN, newConfig.urgentSiren)
            ?.putBoolean(KEY_URGENT_NOTIFY, newConfig.urgentNotify)
            ?.apply()
    }

    /**
     * Very Urgent (Critical) Siren toggle.
     * When siren is turned ON, notification must also be ON.
     */
    fun setCriticalSiren(enabled: Boolean) {
        val current = _categoryConfig.value
        val newConfig = if (enabled) {
            current.copy(criticalSiren = true, criticalNotify = true)
        } else {
            current.copy(criticalSiren = false)
        }
        _categoryConfig.value = newConfig
        sharedPreferences?.edit()
            ?.putBoolean(KEY_CRITICAL_SIREN, newConfig.criticalSiren)
            ?.putBoolean(KEY_CRITICAL_NOTIFY, newConfig.criticalNotify)
            ?.apply()
    }

    /**
     * Very Urgent (Critical) Notification toggle.
     * Constraint: Notification doesn't turn off until siren is turned off!
     * If user turns notification off while siren is on, siren is also turned off.
     */
    fun setCriticalNotify(enabled: Boolean) {
        val current = _categoryConfig.value
        val newConfig = if (!enabled) {
            // Turning off notification requires siren to be turned off first
            current.copy(criticalNotify = false, criticalSiren = false)
        } else {
            current.copy(criticalNotify = true)
        }
        _categoryConfig.value = newConfig
        sharedPreferences?.edit()
            ?.putBoolean(KEY_CRITICAL_SIREN, newConfig.criticalSiren)
            ?.putBoolean(KEY_CRITICAL_NOTIFY, newConfig.criticalNotify)
            ?.apply()
    }

    fun setOnboardingCompleted(completed: Boolean) {
        _hasCompletedOnboarding.value = completed
        sharedPreferences?.edit()?.putBoolean(KEY_ONBOARDING_DONE, completed)?.apply()
    }

    @Synchronized
    fun addNotification(item: AnalyzedNotification) {
        val currentList = _recentNotifications.value.toMutableList()
        currentList.add(0, item)
        _recentNotifications.value = currentList.take(5)

        _totalIntercepted.value += 1
        if (item.wasDismissed) {
            _totalDismissed.value += 1
        }
        if (item.priority == PriorityLevel.URGENT || item.priority == PriorityLevel.CRITICAL) {
            _totalUrgent.value += 1
        }
    }

    fun clearHistory() {
        _recentNotifications.value = emptyList()
    }
}
