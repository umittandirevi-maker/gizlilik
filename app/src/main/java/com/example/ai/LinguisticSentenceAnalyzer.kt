package com.example.ai

import com.example.model.PriorityLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Production-Grade Dynamic Linguistic & Phonetic Sentence Structure Analyzer.
 *
 * Implements:
 * 1. Repeated character collapse (e.g. "calllll" -> "call", "hleeeep" -> "hlep")
 * 2. Algorithmic English Porter Stemmer (collapses inflections: "calling", "called", "calls" -> "call")
 * 3. Phonetic Soundex & Levenshtein Sound-Alike Matching (80%+ threshold for typos: "hlep", "emercency", "hury")
 * 4. Structural Proximity Clause (5-word sliding window between crisis tokens and relief qualifiers neutralizing to ROUTINE)
 * 5. Commercial Intent Disambiguation with Stopword Protection
 * 6. 100% Offline, Pure Kotlin, Zero Native C++ / JNI crashes
 */
object LinguisticSentenceAnalyzer {

    data class AnalysisResult(
        val priority: PriorityLevel,
        val syntacticScore: Int,
        val primaryIntent: String,
        val imperativeDetected: Boolean,
        val reliefNeutralized: Boolean,
        val phoneticMatches: List<String>,
        val diagnosticSummary: String,
        val inferenceTimeMs: Long
    )

    private val STOPWORDS = setOf(
        "a", "an", "the", "and", "or", "but", "if", "for", "in", "on", "at", "to", "by", "from",
        "with", "about", "into", "through", "during", "is", "are", "was", "were", "be", "been",
        "being", "have", "has", "had", "do", "does", "did", "of", "it", "its", "we", "you", "he",
        "she", "they", "i", "me", "my", "our", "your", "his", "her", "their", "pm", "am"
    )

    // Base Urgency Semantic Vectors
    private val CRISIS_VECTORS = listOf(
        "help", "emergency", "accident", "hospital", "danger", "ambulance",
        "police", "fire", "trapped", "bleeding", "crash", "injured", "attack",
        "heart", "stroke", "crisis", "dying", "threat", "breakin", "trouble",
        "doctor", "hazard", "robbery", "kidnap"
    )

    private val IMPERATIVE_VECTORS = listOf(
        "call", "answer", "come", "hurry", "rush", "run", "respond",
        "stop", "reach", "wake", "open", "check", "find", "locate",
        "reply", "pickup", "assist", "drive", "bring", "leave"
    )

    private val TIME_IMMEDIACY_VECTORS = listOf(
        "asap", "immediately", "urgent", "now", "quick", "instant",
        "fast", "stat", "minute", "second", "pronto", "critical"
    )

    private val RELIEF_QUALIFIER_PHRASES = listOf(
        "no worries", "all fine", "all good", "everything is fine", "everything is good",
        "false alarm", "solved", "safe now", "just left", "don't worry", "dont worry",
        "do not worry", "not an emergency", "never mind", "under control", "handled",
        "disregard", "we are okay", "i am okay", "i am fine", "we are fine", "it is fine",
        "no problem", "situation resolved", "went well", "nothing serious"
    )

    private val COMMERCIAL_VECTORS = listOf(
        "coupon", "discount", "gems", "coins", "sale", "deal", "promo",
        "free", "cashback", "spin", "reward", "bonus", "subscribe",
        "vip", "points", "loot", "shop", "clan", "upgrade", "offer"
    )

    fun analyze(
        appType: String,
        channelType: String,
        title: String,
        body: String
    ): AnalysisResult {
        val startTime = System.currentTimeMillis()

        // 1. Structural Guard: Utility/Game noise is ALWAYS Unimportant
        if (appType == "UTILITY_OR_GAME") {
            val elapsed = System.currentTimeMillis() - startTime
            return AnalysisResult(
                priority = PriorityLevel.UNIMPORTANT,
                syntacticScore = 0,
                primaryIntent = "Utility / Game Marketing Suppressed",
                imperativeDetected = false,
                reliefNeutralized = false,
                phoneticMatches = emptyList(),
                diagnosticSummary = "[UNIMPORTANT] Game & utility notification dismissed",
                inferenceTimeMs = elapsed
            )
        }

        // 2. Tokenize and normalize text
        val rawCombined = "$title $body".trim()
        val cleaned = rawCombined.lowercase()
        val rawTokens = cleaned.split(Regex("[^a-z0-9']+")).filter { it.isNotBlank() }

        // Collapse repeated characters ("calllll" -> "call", "hleeeep" -> "hlep")
        val collapsedTokens = rawTokens.map { collapseRepeatedChars(it) }

        // Apply Porter Stemmer
        val stemmedTokens = collapsedTokens.map { stem(it) }

        // Check for Commercial Intent in non-stopwords
        val hasCommercialToken = collapsedTokens.any { token ->
            if (token in STOPWORDS) false
            else COMMERCIAL_VECTORS.any { comm -> isPhoneticOrLevenshteinMatch(token, comm) }
        }

        if (hasCommercialToken) {
            val elapsed = System.currentTimeMillis() - startTime
            return AnalysisResult(
                priority = PriorityLevel.UNIMPORTANT,
                syntacticScore = 10,
                primaryIntent = "Commercial Marketing Intent",
                imperativeDetected = false,
                reliefNeutralized = false,
                phoneticMatches = emptyList(),
                diagnosticSummary = "[UNIMPORTANT] Marketing coupon/offer suppressed",
                inferenceTimeMs = elapsed
            )
        }

        // 3. Structural Proximity Clause (Inverted Syntax Neutralization)
        val (isReliefNeutralized, matchedReliefPhrase) = checkProximityReliefClause(cleaned, collapsedTokens)

        // 4. Phonetic & Morphological Urgency Feature Extraction
        val matchedCrisis = mutableListOf<String>()
        val matchedImperatives = mutableListOf<String>()
        val matchedTimeImmediacy = mutableListOf<String>()

        for (i in collapsedTokens.indices) {
            val token = collapsedTokens[i]
            val stemmed = stemmedTokens[i]

            // Check Crisis vectors
            for (crisis in CRISIS_VECTORS) {
                if (stemmed == stem(crisis) || isPhoneticOrLevenshteinMatch(token, crisis)) {
                    if (!matchedCrisis.contains(crisis)) matchedCrisis.add(crisis)
                }
            }

            // Check Imperative vectors
            for (imperative in IMPERATIVE_VECTORS) {
                if (stemmed == stem(imperative) || isPhoneticOrLevenshteinMatch(token, imperative)) {
                    if (!matchedImperatives.contains(imperative)) matchedImperatives.add(imperative)
                }
            }

            // Check Time Immediacy vectors
            for (time in TIME_IMMEDIACY_VECTORS) {
                if (stemmed == stem(time) || isPhoneticOrLevenshteinMatch(token, time)) {
                    if (!matchedTimeImmediacy.contains(time)) matchedTimeImmediacy.add(time)
                }
            }
        }

        // Legacy Turkish keyword compatibility for backwards interoperability
        if (cleaned.contains("acil bak") || cleaned.contains("imdat") || cleaned.contains("neredesin") || cleaned.contains("kaza yaptim")) {
            matchedCrisis.add("emergency")
            matchedImperatives.add("call")
        }

        // 5. Punctuation Cadence & Casing Intensity
        val uppercaseCount = rawCombined.count { it.isUpperCase() }
        val totalLetters = rawCombined.count { it.isLetter() }
        val shoutRatio = if (totalLetters > 0) uppercaseCount.toFloat() / totalLetters else 0f
        val isShouting = shoutRatio > 0.45f && totalLetters > 6
        val hasExclamationBursts = rawCombined.contains("!!") || rawCombined.contains("?!")

        // 6. Syntactic Urgency Scoring Matrix (0 to 100)
        var score = 0
        if (matchedCrisis.isNotEmpty()) score += min(50, matchedCrisis.size * 25)
        if (matchedImperatives.isNotEmpty()) score += min(25, matchedImperatives.size * 15)
        if (matchedTimeImmediacy.isNotEmpty()) score += min(20, matchedTimeImmediacy.size * 10)
        if (isShouting) score += 10
        if (hasExclamationBursts) score += 5

        // Neutralization penalty if structural proximity clause fired
        if (isReliefNeutralized) {
            score = min(score, 25) // Cap firmly at ROUTINE level
        }

        score = score.coerceIn(0, 100)

        // 7. Channel Context Guard
        // Public mass broadcast groups are always suppressed to ROUTINE
        if (channelType == "GROUP_CHAT_BROADCAST") {
            val elapsed = System.currentTimeMillis() - startTime
            return AnalysisResult(
                priority = PriorityLevel.ROUTINE,
                syntacticScore = min(score, 35),
                primaryIntent = "Mass Group Chat Broadcast",
                imperativeDetected = matchedImperatives.isNotEmpty(),
                reliefNeutralized = isReliefNeutralized,
                phoneticMatches = matchedCrisis + matchedImperatives,
                diagnosticSummary = "[ROUTINE] Group chat mass broadcast quieted",
                inferenceTimeMs = elapsed
            )
        }

        // 8. Determine Priority
        val priority = when {
            isReliefNeutralized -> PriorityLevel.ROUTINE
            score >= 70 -> PriorityLevel.CRITICAL
            score >= 40 -> PriorityLevel.URGENT
            else -> PriorityLevel.ROUTINE
        }

        val elapsed = System.currentTimeMillis() - startTime
        val primaryIntent = when {
            isReliefNeutralized -> "Relief Clause ($matchedReliefPhrase neutralized crisis)"
            priority == PriorityLevel.CRITICAL -> "Critical Emergency Distress"
            priority == PriorityLevel.URGENT -> "Time-Sensitive Action Request"
            else -> "Routine Conversational Message"
        }

        val summary = "[${priority.name}] Score: $score/100 • Intent: $primaryIntent" +
                if (matchedCrisis.isNotEmpty()) " • Matches: ${matchedCrisis.take(3).joinToString()}" else ""

        return AnalysisResult(
            priority = priority,
            syntacticScore = score,
            primaryIntent = primaryIntent,
            imperativeDetected = matchedImperatives.isNotEmpty(),
            reliefNeutralized = isReliefNeutralized,
            phoneticMatches = (matchedCrisis + matchedImperatives + matchedTimeImmediacy).distinct(),
            diagnosticSummary = summary,
            inferenceTimeMs = elapsed
        )
    }

    private fun checkProximityReliefClause(
        fullText: String,
        tokens: List<String>
    ): Pair<Boolean, String> {
        for (phrase in RELIEF_QUALIFIER_PHRASES) {
            if (fullText.contains(phrase)) {
                val phraseWords = phrase.split(" ")
                val firstPhraseWord = phraseWords.first()

                val reliefIndex = tokens.indexOfFirst { it == firstPhraseWord }
                if (reliefIndex != -1) {
                    for (i in tokens.indices) {
                        val token = tokens[i]
                        val isCrisis = CRISIS_VECTORS.any { crisis ->
                            stem(token) == stem(crisis) || isPhoneticOrLevenshteinMatch(token, crisis)
                        }

                        if (isCrisis) {
                            val distance = abs(i - reliefIndex)
                            if (distance <= 5) {
                                return true to phrase
                            }
                        }
                    }
                }
            }
        }
        return false to ""
    }

    /**
     * Collapses repeating character sequences of length >= 3 down to 1 or 2.
     * Special case: for 'l', preserves "ll" (so "calllll" -> "call").
     * For other letters, collapses down to single char (so "hleeeep" -> "hlep", "sooooo" -> "so").
     */
    fun collapseRepeatedChars(input: String): String {
        if (input.length < 3) return input
        val sb = java.lang.StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            var count = 1
            while (i + 1 < input.length && input[i + 1] == c) {
                count++
                i++
            }
            if (count >= 3) {
                if (c == 'l') {
                    sb.append("ll")
                } else {
                    sb.append(c)
                }
            } else {
                repeat(count) { sb.append(c) }
            }
            i++
        }
        return sb.toString()
    }

    fun isPhoneticOrLevenshteinMatch(token: String, target: String): Boolean {
        if (token == target) return true
        if (token.length < 3 || target.length < 3) return token == target

        val dist = levenshteinDistance(token, target)

        // Soundex match check with distance bounds
        val soundex1 = soundex(token)
        val soundex2 = soundex(target)
        if (soundex1.isNotEmpty() && soundex1 == soundex2) {
            if (token[0] == target[0] && dist <= 2) {
                return true
            }
        }

        // Levenshtein metric with 80% sound-alike confidence threshold
        val maxLen = max(token.length, target.length)
        val similarity = 1.0 - (dist.toDouble() / maxLen.toDouble())
        return similarity >= 0.80
    }

    fun soundex(s: String): String {
        val clean = s.uppercase().filter { it in 'A'..'Z' }
        if (clean.isEmpty()) return ""

        val first = clean[0]
        val sb = java.lang.StringBuilder().append(first)

        fun mapChar(c: Char): Char = when (c) {
            'B', 'F', 'P', 'V' -> '1'
            'C', 'G', 'J', 'K', 'Q', 'S', 'X', 'Z' -> '2'
            'D', 'T' -> '3'
            'L' -> '4'
            'M', 'N' -> '5'
            'R' -> '6'
            else -> '0'
        }

        var lastCode = mapChar(first)
        for (i in 1 until clean.length) {
            val code = mapChar(clean[i])
            if (code != '0') {
                if (code != lastCode) {
                    sb.append(code)
                    lastCode = code
                }
            } else {
                lastCode = '0'
            }
            if (sb.length == 4) break
        }

        while (sb.length < 4) sb.append('0')
        return sb.toString()
    }

    fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[a.length][b.length]
    }

    fun stem(word: String): String {
        var str = word.lowercase()
        if (str.length <= 2) return str

        if (str.endsWith("sses")) {
            str = str.substring(0, str.length - 2)
        } else if (str.endsWith("ies")) {
            str = str.substring(0, str.length - 2)
        } else if (!str.endsWith("ss") && str.endsWith("s")) {
            str = str.substring(0, str.length - 1)
        }

        if (str.endsWith("eed")) {
            if (measure(str.substring(0, str.length - 3)) > 0) {
                str = str.substring(0, str.length - 1)
            }
        } else if (str.endsWith("ed")) {
            val stemCandidate = str.substring(0, str.length - 2)
            if (containsVowel(stemCandidate)) {
                str = cleanupDouble(stemCandidate)
            }
        } else if (str.endsWith("ing")) {
            val stemCandidate = str.substring(0, str.length - 3)
            if (containsVowel(stemCandidate)) {
                str = cleanupDouble(stemCandidate)
            }
        }

        if (str.endsWith("y")) {
            val stemCandidate = str.substring(0, str.length - 1)
            if (containsVowel(stemCandidate)) {
                str = stemCandidate + "i"
            }
        }

        return str
    }

    private fun containsVowel(s: String): Boolean {
        return s.any { it in "aeiouy" }
    }

    private fun measure(s: String): Int {
        var count = 0
        var i = 0
        val len = s.length
        while (i < len) {
            while (i < len && !isConsonant(s, i)) i++
            while (i < len && isConsonant(s, i)) i++
            if (i < len) count++
        }
        return count
    }

    private fun isConsonant(s: String, i: Int): Boolean {
        val c = s[i]
        if (c in "aeiou") return false
        if (c == 'y') return i == 0 || !isConsonant(s, i - 1)
        return true
    }

    private fun cleanupDouble(s: String): String {
        var res = s
        if (res.endsWith("at") || res.endsWith("bl") || res.endsWith("iz")) {
            res += "e"
        } else if (res.length >= 2 && res[res.length - 1] == res[res.length - 2] &&
            res[res.length - 1] !in "lsz"
        ) {
            res = res.substring(0, res.length - 1)
        }
        return res
    }
}
