package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldActive
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800

@Composable
fun CenterActivationSection(
    isActive: Boolean,
    isEnabled: Boolean,
    isAlerting: Boolean = false,
    onSilenceAlert: () -> Unit = {},
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isActive) EmeraldActive else Slate700,
        animationSpec = tween(400),
        label = "borderColor"
    )

    val animatedBackgroundBrush = if (isActive) {
        Brush.radialGradient(
            colors = listOf(
                EmeraldGlow.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.surface
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Slate800,
                MaterialTheme.colorScheme.surface
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("activation_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isActive) "FocusAI ACTIVE" else if (!isEnabled) "PERMISSION REQUIRED" else "FocusAI READY",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) EmeraldActive else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (isActive) "FocusAI is actively guarding your notifications"
                else if (!isEnabled) "Grant notification listener access to engage FocusAI"
                else "Tap below to begin real-time FocusAI filtering",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 180dp circular "ACTIVATE FILTER" button
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(if (isEnabled) pulseScale else 1f)
                    .shadow(
                        elevation = if (isActive) 16.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = if (isActive) EmeraldActive else Color.Transparent
                    )
                    .clip(CircleShape)
                    .background(animatedBackgroundBrush)
                    .border(
                        border = BorderStroke(if (isActive) 4.dp else 2.dp, animatedBorderColor),
                        shape = CircleShape
                    )
                    .clickable(enabled = isEnabled, onClick = onToggle)
                    .testTag("activate_filter_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Activate Filter",
                        modifier = Modifier.size(52.dp),
                        tint = if (isActive) EmeraldActive else if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isActive) "DEACTIVATE\nFocusAI" else "ACTIVATE\nFocusAI",
                        style = MaterialTheme.typography.titleSmall.copy(lineHeight = 16.sp),
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp,
                        color = if (isActive) EmeraldActive else if (isEnabled) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }
            }

            // Dynamic Mute Active Siren Button (Right Underneath 180dp Circular Toggle)
            AnimatedVisibility(
                visible = isAlerting,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onSilenceAlert,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("mute_active_siren_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Mute Active Siren",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🔕 Mute Active Siren",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isActive) "🟢 Intercepting Incoming Stream" else "⚪ Standby Mode",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) EmeraldActive else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
