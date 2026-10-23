package com.example.model

/**
 * Configuration model for granular category notification and siren muting.
 *
 * Rules:
 * - Unimportant: Notification mute toggle (defaults to false / muted).
 * - Routine / Normal: Notification mute toggle (defaults to false / muted when filter is active).
 * - Urgent: Siren toggle & Notification toggle. Notification cannot be turned off while siren is on.
 * - Very Urgent (Critical): Siren toggle & Notification toggle. Notification cannot be turned off while siren is on.
 * - Master Siren: Global sound override toggle from the top bar.
 */
data class CategoryMuteConfig(
    val unimportantNotify: Boolean = false,
    val routineNotify: Boolean = false,
    val urgentSiren: Boolean = false,
    val urgentNotify: Boolean = true,
    val criticalSiren: Boolean = true,
    val criticalNotify: Boolean = true,
    val masterSirenEnabled: Boolean = true
)
