package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.PriorityCritical
import com.example.ui.theme.PriorityUrgent

@Composable
fun SettingsScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val config = state.categoryConfig

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PLACED AT THE VERY TOP: Category Notification & Siren Muting
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("category_muting_settings_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FocusAI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Category Muting & Siren Rules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. VERY URGENT (CRITICAL)
                    CategorySectionHeader(
                        title = "🚨 Very Urgent (Critical)",
                        subtitle = "Severe emergencies, accidents, distress messages",
                        badgeText = "CRITICAL",
                        badgeColor = PriorityCritical
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DualToggleRow(
                        label = "Emergency Siren",
                        description = "Play loud siren audio override for critical alarms",
                        isChecked = config.criticalSiren,
                        onCheckedChange = { viewModel.setCriticalSiren(it) },
                        testTag = "switch_critical_siren"
                    )
                    DualToggleRow(
                        label = "Emergency Notification",
                        description = if (config.criticalSiren) "Siren is active; turning off will also silence siren" else "Display notification in Android status bar",
                        isChecked = config.criticalNotify,
                        onCheckedChange = { viewModel.setCriticalNotify(it) },
                        testTag = "switch_critical_notify"
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. URGENT
                    CategorySectionHeader(
                        title = "⚡ Urgent",
                        subtitle = "Time-sensitive alerts, direct chats needing quick action",
                        badgeText = "URGENT",
                        badgeColor = PriorityUrgent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DualToggleRow(
                        label = "Urgent Siren",
                        description = "Play siren audio override for urgent notifications",
                        isChecked = config.urgentSiren,
                        onCheckedChange = { viewModel.setUrgentSiren(it) },
                        testTag = "switch_urgent_siren"
                    )
                    DualToggleRow(
                        label = "Urgent Notification",
                        description = if (config.urgentSiren) "Siren is active; turning off will also silence siren" else "Display notification in Android status bar",
                        isChecked = config.urgentNotify,
                        onCheckedChange = { viewModel.setUrgentNotify(it) },
                        testTag = "switch_urgent_notify"
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. NORMAL (ROUTINE)
                    CategorySectionHeader(
                        title = "💬 Normal (Routine)",
                        subtitle = "Casual conversation, group chats (Muted by default when filter is active)",
                        badgeText = "ROUTINE",
                        badgeColor = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DualToggleRow(
                        label = "Normal Notifications",
                        description = "Allow notifications through when filter is engaged",
                        isChecked = config.routineNotify,
                        onCheckedChange = { viewModel.setRoutineNotify(it) },
                        testTag = "switch_routine_notify"
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. UNIMPORTANT
                    CategorySectionHeader(
                        title = "🔕 Unimportant",
                        subtitle = "Marketing ads, promotions, games, coupons",
                        badgeText = "SPAM / NOISE",
                        badgeColor = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DualToggleRow(
                        label = "Unimportant Notifications",
                        description = "Allow spam and promo notifications (Filtered out by default)",
                        isChecked = config.unimportantNotify,
                        onCheckedChange = { viewModel.setUnimportantNotify(it) },
                        testTag = "switch_unimportant_notify"
                    )
                }
            }
        }

        // Master Siren Override Information Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("master_siren_info_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (config.masterSirenEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = null,
                        tint = if (config.masterSirenEnabled) EmeraldActive else PriorityCritical,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (config.masterSirenEnabled) "FocusAI Master Siren: ENABLED" else "FocusAI Master Siren: DISABLED",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap the siren icon in the top header bar at any time to toggle master siren audio on/off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Engine Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("engine_info_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FocusAI Engine Architecture",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "100% Offline Algorithmic Sentence Analyzer with Porter Stemmer and Levenshtein typo tolerance. Zero cloud latency, zero external data leaks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CategorySectionHeader(
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = badgeColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun DualToggleRow(
    label: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
