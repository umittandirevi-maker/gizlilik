package com.example.model

enum class PriorityLevel(val label: String, val badgeText: String) {
    UNIMPORTANT("UNIMPORTANT", "UNIMPORTANT"),
    ROUTINE("ROUTINE", "ROUTINE"),
    URGENT("URGENT", "URGENT"),
    CRITICAL("CRITICAL", "CRITICAL");

    // Backward compatibility alias
    val turkishLabel: String get() = label

    companion object {
        fun fromModelToken(token: String): PriorityLevel {
            var sanitized = token.trim()
            if (sanitized.contains("Output ONLY one word", ignoreCase = true)) {
                sanitized = sanitized.substringAfterLast("Output ONLY one word")
            }
            if (sanitized.contains("Output:", ignoreCase = true)) {
                sanitized = sanitized.substringAfterLast("Output:")
            }
            if (sanitized.contains("Answer:", ignoreCase = true)) {
                sanitized = sanitized.substringAfterLast("Answer:")
            }
            if (sanitized.contains(":")) {
                sanitized = sanitized.substringAfterLast(":")
            }

            val normalized = sanitized
                .replace('İ', 'I')
                .replace('ı', 'i')
                .replace('Ö', 'O')
                .replace('ö', 'o')
                .replace('Ç', 'C')
                .replace('ç', 'c')
                .replace('Ş', 'S')
                .replace('ş', 's')
                .replace('Ğ', 'G')
                .replace('ğ', 'g')
                .replace('Ü', 'U')
                .replace('ü', 'u')
                .replace(Regex("[^a-zA-Z0-9_]"), " ")
                .trim()
                .uppercase()

            val words = normalized.split("\\s+".toRegex()).filter { it.isNotBlank() }

            val targetWord = words.lastOrNull { word ->
                word == "CRITICAL" || word == "COK_ACIL" ||
                word == "URGENT" || word == "ACIL" ||
                word == "UNIMPORTANT" || word == "ONEMSIZ" ||
                word == "ROUTINE" || word == "RUTIN"
            } ?: words.firstOrNull() ?: ""

            return when {
                targetWord == "CRITICAL" || targetWord == "COK_ACIL" -> CRITICAL
                targetWord == "URGENT" || targetWord == "ACIL" -> URGENT
                targetWord == "UNIMPORTANT" || targetWord == "ONEMSIZ" -> UNIMPORTANT
                targetWord == "ROUTINE" || targetWord == "RUTIN" -> ROUTINE
                else -> ROUTINE
            }
        }

        fun fromString(value: String): PriorityLevel = fromModelToken(value)
    }
}

data class AnalyzedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val priority: PriorityLevel,
    val rawAiOutput: String,
    val wasDismissed: Boolean,
    val soundAlertTriggered: Boolean,
    val inferenceTimeMs: Long,
    val syntacticScore: Int = 0,
    val diagnosticSummary: String = ""
)
