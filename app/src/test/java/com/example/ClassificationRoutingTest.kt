package com.example

import com.example.ai.LinguisticSentenceAnalyzer
import com.example.model.PriorityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassificationRoutingTest {

    @Test
    fun testPriorityLevelParsingEnglishAndLegacy() {
        // English tokens
        assertEquals(PriorityLevel.UNIMPORTANT, PriorityLevel.fromModelToken("UNIMPORTANT"))
        assertEquals(PriorityLevel.ROUTINE, PriorityLevel.fromModelToken("ROUTINE"))
        assertEquals(PriorityLevel.URGENT, PriorityLevel.fromModelToken("URGENT"))
        assertEquals(PriorityLevel.CRITICAL, PriorityLevel.fromModelToken("CRITICAL"))

        // Legacy compatibility tokens
        assertEquals(PriorityLevel.UNIMPORTANT, PriorityLevel.fromModelToken("ONEMSİZ"))
        assertEquals(PriorityLevel.ROUTINE, PriorityLevel.fromModelToken("RUTİN"))
        assertEquals(PriorityLevel.URGENT, PriorityLevel.fromModelToken("ACİL"))
        assertEquals(PriorityLevel.CRITICAL, PriorityLevel.fromModelToken("COK_ACIL"))
    }

    @Test
    fun testPunctuationCleaning() {
        assertEquals(PriorityLevel.CRITICAL, PriorityLevel.fromModelToken("**CRITICAL**"))
        assertEquals(PriorityLevel.UNIMPORTANT, PriorityLevel.fromModelToken("\"UNIMPORTANT\"."))
        assertEquals(PriorityLevel.URGENT, PriorityLevel.fromModelToken("Output: URGENT!"))
    }

    @Test
    fun testPorterStemmerAndCollapsing() {
        // Repeated character collapse
        assertEquals("call", LinguisticSentenceAnalyzer.collapseRepeatedChars("calllll"))
        assertEquals("hlep", LinguisticSentenceAnalyzer.collapseRepeatedChars("hleeeep"))
        assertEquals("so", LinguisticSentenceAnalyzer.collapseRepeatedChars("sooooo"))

        // Porter Stemmer inflections collapse
        assertEquals("call", LinguisticSentenceAnalyzer.stem("calling"))
        assertEquals("call", LinguisticSentenceAnalyzer.stem("called"))
        assertEquals("call", LinguisticSentenceAnalyzer.stem("calls"))
        assertEquals("hurri", LinguisticSentenceAnalyzer.stem("hurrying"))
        assertEquals("hurri", LinguisticSentenceAnalyzer.stem("hurried"))
    }

    @Test
    fun testPhoneticAndTypoMatching() {
        // Soundex matching for typos
        assertTrue(LinguisticSentenceAnalyzer.isPhoneticOrLevenshteinMatch("hlep", "help"))
        assertTrue(LinguisticSentenceAnalyzer.isPhoneticOrLevenshteinMatch("hury", "hurry"))
        assertTrue(LinguisticSentenceAnalyzer.isPhoneticOrLevenshteinMatch("emercency", "emergency"))
        assertTrue(LinguisticSentenceAnalyzer.isPhoneticOrLevenshteinMatch("asapb", "asap"))
        assertTrue(LinguisticSentenceAnalyzer.isPhoneticOrLevenshteinMatch("ambulanc", "ambulance"))
    }

    @Test
    fun testInvertedSyntaxProximityClause() {
        // When crisis words occur near relief qualifiers ("no worries", "just left"),
        // the 5-word proximity window MUST actively neutralize to ROUTINE!
        val result = LinguisticSentenceAnalyzer.analyze(
            appType = "MESSENGER",
            channelType = "DIRECT_1_ON_1",
            title = "Mom",
            body = "No worries at all, we just left the hospital now, everything is fine!"
        )
        assertTrue("Expected relief clause to be triggered", result.reliefNeutralized)
        assertEquals(PriorityLevel.ROUTINE, result.priority)
    }

    @Test
    fun testCriticalEmergencyDirectSentence() {
        // Critical emergency compound sentence
        val result = LinguisticSentenceAnalyzer.analyze(
            appType = "MESSENGER",
            channelType = "DIRECT_1_ON_1",
            title = "Sarah",
            body = "Accident occurred, need backup immediately! Call ambulance!"
        )
        assertEquals(PriorityLevel.CRITICAL, result.priority)
        assertTrue(result.syntacticScore >= 70)
        assertFalse(result.reliefNeutralized)
    }

    @Test
    fun testTypoPanicBurstSentence() {
        // Frantic typos in panic message
        val result = LinguisticSentenceAnalyzer.analyze(
            appType = "MESSENGER",
            channelType = "DIRECT_1_ON_1",
            title = "David",
            body = "hlep me quick, somone breakin in right noww!!"
        )
        assertEquals(PriorityLevel.CRITICAL, result.priority)
        assertTrue(result.syntacticScore >= 70)
    }

    @Test
    fun testMarketingSpamSuppression() {
        // Commercial coupon hooks must be suppressed to UNIMPORTANT
        val result = LinguisticSentenceAnalyzer.analyze(
            appType = "UTILITY_OR_GAME",
            channelType = "DIRECT_1_ON_1",
            title = "Super Clash",
            body = "Hurry up, grab your 50% discount coupon and free gems now!"
        )
        assertEquals(PriorityLevel.UNIMPORTANT, result.priority)
    }

    @Test
    fun testGroupBroadcastSuppression() {
        // Public mass broadcast groups are always suppressed to ROUTINE
        val result = LinguisticSentenceAnalyzer.analyze(
            appType = "MESSENGER",
            channelType = "GROUP_CHAT_BROADCAST",
            title = "Work Team",
            body = "Urgent meeting scheduled for 8 PM tonight, everyone please attend."
        )
        assertEquals(PriorityLevel.ROUTINE, result.priority)
    }

    @Test
    fun testSilenceSirenReceiverActionConstant() {
        assertEquals("com.example.action.SILENCE_SIREN", com.example.service.SilenceSirenReceiver.ACTION_SILENCE_SIREN)
        assertEquals(8888, com.example.service.SilenceSirenReceiver.EMERGENCY_ALERT_NOTIFICATION_ID)
    }

    @Test
    fun testCategoryMuteDependencyEnforcement() {
        val repo = com.example.data.NotificationRepository

        // Critical dependency check
        repo.setCriticalNotify(true)
        repo.setCriticalSiren(true)
        assertTrue(repo.categoryConfig.value.criticalSiren)
        assertTrue(repo.categoryConfig.value.criticalNotify)

        // Turning off notification must also turn off siren
        repo.setCriticalNotify(false)
        assertFalse(repo.categoryConfig.value.criticalNotify)
        assertFalse(repo.categoryConfig.value.criticalSiren)

        // Turning on siren forces notification on
        repo.setCriticalSiren(true)
        assertTrue(repo.categoryConfig.value.criticalSiren)
        assertTrue(repo.categoryConfig.value.criticalNotify)

        // Urgent dependency check
        repo.setUrgentNotify(true)
        repo.setUrgentSiren(true)
        assertTrue(repo.categoryConfig.value.urgentSiren)
        assertTrue(repo.categoryConfig.value.urgentNotify)

        repo.setUrgentNotify(false)
        assertFalse(repo.categoryConfig.value.urgentNotify)
        assertFalse(repo.categoryConfig.value.urgentSiren)
    }

    @Test
    fun testMasterSirenToggle() {
        val repo = com.example.data.NotificationRepository
        val initial = repo.categoryConfig.value.masterSirenEnabled
        val toggled = repo.toggleMasterSiren()
        assertEquals(!initial, toggled)
        assertEquals(!initial, repo.categoryConfig.value.masterSirenEnabled)
        // Revert
        repo.toggleMasterSiren()
    }

    @Test
    fun testWhitelistStartsEmptyForUncheckedCheckboxes() {
        // As requested by user: all checkboxes must start unchecked
        val repo = com.example.data.NotificationRepository
        val testPkg = "com.test.arbitraryapp"
        assertFalse(repo.isPackageWhitelisted(testPkg))

        repo.toggleWhitelistPackage(testPkg)
        assertTrue(repo.isPackageWhitelisted(testPkg))

        repo.toggleWhitelistPackage(testPkg)
        assertFalse(repo.isPackageWhitelisted(testPkg))
    }

    @Test
    fun testInstalledAppItemModel() {
        val app = com.example.model.InstalledAppItem(
            appName = "WhatsApp",
            packageName = "com.whatsapp",
            category = "Messaging"
        )
        assertEquals("WhatsApp", app.appName)
        assertEquals("com.whatsapp", app.packageName)
        assertEquals("Messaging", app.category)
        assertFalse(app.isSystemApp)
    }
}
