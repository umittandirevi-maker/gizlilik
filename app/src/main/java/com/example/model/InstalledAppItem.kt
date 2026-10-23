package com.example.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Representation of an application installed on the user's device.
 */
data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false,
    val category: String = "Communication",
    val iconBitmap: ImageBitmap? = null
)
