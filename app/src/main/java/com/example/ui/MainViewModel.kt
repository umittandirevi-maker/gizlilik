package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AlertOverrideManager
import com.example.data.NotificationRepository
import com.example.model.AnalyzedNotification
import com.example.model.CategoryMuteConfig
import com.example.model.InstalledAppItem
import com.example.model.NavigationTab
import com.example.service.SilenceSirenReceiver
import com.example.ui.util.AppIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedTab: NavigationTab = NavigationTab.HOME,
    val isNotificationPermissionGranted: Boolean = false,
    val isFilteringActive: Boolean = false,
    val engineDescription: String = "FocusAI Offline Notification Shield",
    val isAlertingActive: Boolean = false,
    val lastAlertReason: String? = null,
    val isPreviewPlaying: Boolean = false,
    val recentNotifications: List<AnalyzedNotification> = emptyList(),
    val totalIntercepted: Int = 0,
    val totalDismissed: Int = 0,
    val totalUrgent: Int = 0,
    val whitelistPackages: Set<String> = emptySet(),
    val categoryConfig: CategoryMuteConfig = CategoryMuteConfig(),
    val installedApps: List<InstalledAppItem> = emptyList(),
    val searchQuery: String = "",
    val isLoadingApps: Boolean = false
)

class MainViewModel(
    @Suppress("UNUSED_PARAMETER") private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(NavigationTab.HOME)
    val selectedTab: StateFlow<NavigationTab> = _selectedTab.asStateFlow()

    private val _isNotificationPermissionGranted = MutableStateFlow(false)
    val isNotificationPermissionGranted: StateFlow<Boolean> = _isNotificationPermissionGranted.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        combine(
            _selectedTab,
            _isNotificationPermissionGranted,
            NotificationRepository.isFilteringActive,
            AlertOverrideManager.isAlerting,
            AlertOverrideManager.lastAlertReason
        ) { tab, isNotifGranted, isActive, isAlerting, alertReason ->
            Five(tab, isNotifGranted, isActive, isAlerting, alertReason)
        },
        combine(
            AlertOverrideManager.isPreviewPlaying,
            NotificationRepository.recentNotifications,
            NotificationRepository.totalIntercepted,
            NotificationRepository.totalDismissed,
            NotificationRepository.totalUrgent
        ) { isPreview, recents, intercepted, dismissed, urgent ->
            Five(isPreview, recents, intercepted, dismissed, urgent)
        },
        combine(
            NotificationRepository.whitelistPackages,
            NotificationRepository.categoryConfig,
            _installedApps,
            _searchQuery,
            _isLoadingApps
        ) { whitelist, catConfig, apps, query, loading ->
            Five(whitelist, catConfig, apps, query, loading)
        }
    ) { group1, group2, group3 ->
        MainUiState(
            selectedTab = group1.a,
            isNotificationPermissionGranted = group1.b,
            isFilteringActive = group1.c,
            engineDescription = "FocusAI Offline Notification Shield",
            isAlertingActive = group1.d,
            lastAlertReason = group1.e,
            isPreviewPlaying = group2.a,
            recentNotifications = group2.b.take(5),
            totalIntercepted = group2.c,
            totalDismissed = group2.d,
            totalUrgent = group2.e,
            whitelistPackages = group3.a,
            categoryConfig = group3.b,
            installedApps = group3.c,
            searchQuery = group3.d,
            isLoadingApps = group3.e
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun selectTab(tab: NavigationTab) {
        _selectedTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun checkNotificationPermission(context: Context) {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val listenerGranted = if (!flat.isNullOrEmpty()) {
            val names = flat.split(":".toRegex()).toTypedArray()
            names.any { name ->
                val cn = ComponentName.unflattenFromString(name)
                cn != null && cn.packageName == packageName
            }
        } else {
            false
        }
        _isNotificationPermissionGranted.value = listenerGranted
    }

    fun checkInitialStatus(context: Context) {
        NotificationRepository.initialize(context)
        checkNotificationPermission(context)
        loadInstalledApps(context)
    }

    fun loadInstalledApps(context: Context) {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                val pm = context.packageManager
                val detectedApps = mutableListOf<InstalledAppItem>()
                val seenPackages = mutableSetOf<String>()

                // 1. Query installed applications on device
                val allApps = try {
                    pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                } catch (e: Exception) {
                    emptyList()
                }

                for (appInfo in allApps) {
                    val pkg = appInfo.packageName
                    if (pkg == context.packageName) continue
                    if (seenPackages.contains(pkg)) continue

                    val isDownloaded = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    // Hide all system apps except dialer and messages
                    if (!isDownloaded && !isEssentialPhoneOrSms(pkg)) {
                        continue
                    }

                    // Strict requirement: Remove any application without a valid app icon
                    val iconBitmap = try {
                        val drawable = pm.getApplicationIcon(appInfo)
                        AppIconHelper.drawableToImageBitmap(drawable)
                    } catch (e: Exception) {
                        null
                    }

                    if (iconBitmap == null) {
                        continue
                    }

                    val label = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkg.substringAfterLast('.')
                    }

                    if (label.isBlank()) continue

                    val category = categorizeApp(pkg, isDownloaded)

                    detectedApps.add(
                        InstalledAppItem(
                            appName = label,
                            packageName = pkg,
                            isSystemApp = !isDownloaded,
                            category = category,
                            iconBitmap = iconBitmap
                        )
                    )
                    seenPackages.add(pkg)
                }

                // Sort: Essential communication first, then alphabetical
                _installedApps.value = detectedApps.sortedWith(
                    compareByDescending<InstalledAppItem> {
                        it.category == "Calls & Phone" || it.category == "Messaging" || it.category == "Social Media"
                    }.thenBy { it.appName.lowercase() }
                )
            } catch (e: Exception) {
                _installedApps.value = emptyList()
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    private fun isEssentialPhoneOrSms(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("dialer") ||
                lower.contains("telecom") ||
                (lower.contains("phone") && !lower.contains("service") && !lower.contains("config") && !lower.contains("overlay")) ||
                lower.contains("messaging") ||
                lower.contains("sms") ||
                lower.contains("mms") ||
                lower == "com.google.android.dialer" ||
                lower == "com.google.android.apps.messaging" ||
                lower == "com.android.dialer" ||
                lower == "com.android.mms" ||
                lower == "com.android.messaging" ||
                lower == "com.samsung.android.messaging" ||
                lower == "com.samsung.android.dialer"
    }

    private fun categorizeApp(pkg: String, isDownloaded: Boolean): String {
        val lower = pkg.lowercase()
        return when {
            lower.contains("whatsapp") || lower.contains("telegram") || lower.contains("signal") ||
                    lower.contains("messaging") || lower.contains("viber") || lower.contains("wechat") ||
                    lower.contains("line") || lower.contains("sms") || lower.contains("mms") -> "Messaging"
            lower.contains("instagram") || lower.contains("facebook") || lower.contains("twitter") ||
                    lower.contains("tiktok") || lower.contains("reddit") || lower.contains("snapchat") ||
                    lower.contains("linkedin") -> "Social Media"
            lower.contains("dialer") || lower.contains("phone") || lower.contains("call") || lower.contains("telecom") -> "Calls & Phone"
            lower.contains("mail") || lower.contains("outlook") || lower.contains("gmail") -> "Email"
            lower.contains("discord") || lower.contains("slack") || lower.contains("teams") ||
                    lower.contains("meet") || lower.contains("zoom") -> "Work & Team Chat"
            isDownloaded -> "Downloaded App"
            else -> "System Communication"
        }
    }

    fun toggleFiltering() {
        NotificationRepository.toggleFilteringActive()
    }

    fun toggleMasterSiren(): Boolean {
        val newState = NotificationRepository.toggleMasterSiren()
        viewModelScope.launch {
            _snackbarMessage.emit(if (newState) "Siren Sound Enabled" else "Siren Sound Disabled")
        }
        return newState
    }

    fun setUnimportantNotify(enabled: Boolean) {
        NotificationRepository.setUnimportantNotify(enabled)
    }

    fun setRoutineNotify(enabled: Boolean) {
        NotificationRepository.setRoutineNotify(enabled)
    }

    fun setUrgentSiren(enabled: Boolean) {
        NotificationRepository.setUrgentSiren(enabled)
    }

    fun setUrgentNotify(enabled: Boolean) {
        NotificationRepository.setUrgentNotify(enabled)
    }

    fun setCriticalSiren(enabled: Boolean) {
        NotificationRepository.setCriticalSiren(enabled)
    }

    fun setCriticalNotify(enabled: Boolean) {
        NotificationRepository.setCriticalNotify(enabled)
    }

    fun toggleWhitelistPackage(packageName: String) {
        NotificationRepository.toggleWhitelistPackage(packageName)
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun silenceActiveAlert(context: Context) {
        AlertOverrideManager.silenceActiveAlert(context)
        try {
            NotificationManagerCompat.from(context).cancel(SilenceSirenReceiver.EMERGENCY_ALERT_NOTIFICATION_ID)
        } catch (ignored: Exception) {}
    }

    fun clearNotificationHistory() {
        NotificationRepository.clearHistory()
    }

    private data class Five<A, B, C, D, E>(
        val a: A, val b: B, val c: C, val d: D, val e: E
    )
}
