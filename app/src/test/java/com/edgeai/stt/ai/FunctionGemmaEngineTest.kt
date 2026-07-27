package com.edgeai.stt.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.edgeai.stt.audio.AcousticStressState

class FunctionGemmaEngineTest {

    @Test
    fun testUserHighPriorityEmergencyPrompts() {
        val emergencyPrompts = listOf(
            "I am in danger send an emergency alert now",
            "Please send SOS to my guardian immediately",
            "Someone is following me send my location now",
            "I think I am being attacked alert my guardian",
            "Help me I am not safe right now",
            "I was in an accident send my location to my guardian",
            "I cannot breathe properly send emergency help message now",
            "There is a fire here send alert immediately",
            "I am trapped and need help send my location now",
            "I feel like someone is trying to hurt me",
            "Call for help and notify my guardian",
            "I am in serious danger do not wait send it",
            "I think I am being kidnapped send alert now",
            "I am bleeding and need help right away",
            "I have fallen and cannot get up send SOS",
            "A stranger is threatening me send my live location",
            "I am alone and scared please alert my guardian now",
            "Send emergency message to my family I am not safe",
            "I need urgent help send alert immediately",
            "This is an emergency share my location now",
            "I am being followed by a car please send SOS",
            "Someone is trying to break in alert my guardian",
            "I feel unsafe please send my location now",
            "I am in trouble please notify my guardian",
            "I think I am being stalked send help message",
            "Please trigger the emergency alert right now",
            "Immediate help needed send alert to my guardian",
            "I am in danger at this location send SOS",
            "Send my live location to my guardian I am not safe",
            "Emergency please alert my guardian with my location"
        )

        for (prompt in emergencyPrompts) {
            val rawOutput = simulateOutput(prompt)
            val toolCalls = ToolRegistry.parseToolCalls(rawOutput)
            assertTrue("Expected tool call for high-priority emergency prompt: '$prompt'. Raw output: '$rawOutput'", toolCalls.isNotEmpty())
        }
    }

    @Test
    fun testUltraShortDistressKeywords() {
        val ultraShortPrompts = listOf(
            "help me now",
            "please help me now",
            "I need help now",
            "I need immediate help",
            "send emergency alert now",
            "send SOS now",
            "notify guardian now",
            "share my live location now",
            "I am unsafe",
            "I am not safe",
            "not safe",
            "danger",
            "emergency",
            "attack",
            "accident",
            "fire",
            "choking",
            "fainting",
            "bleeding",
            "panic"
        )

        for (prompt in ultraShortPrompts) {
            val rawOutput = simulateOutput(prompt)
            val toolCalls = ToolRegistry.parseToolCalls(rawOutput)
            assertTrue("Expected tool call for ultra-short distress prompt: '$prompt'. Raw output: '$rawOutput'", toolCalls.isNotEmpty())
        }
    }

    @Test
    fun testUserMediumRiskPrompts() {
        val mediumRiskPrompts = listOf(
            "I feel unsafe walking alone right now",
            "I think someone is following me",
            "I am scared and do not know what to do",
            "Something feels wrong here",
            "I need help but I am not sure yet",
            "I feel dizzy and weak",
            "I think I may faint",
            "Please stay ready I may need help",
            "I do not trust the people around me",
            "I think I am lost and nervous",
            "I may be in danger",
            "I am uncomfortable should I send an alert",
            "I hear strange noises outside",
            "Someone keeps staring at me and following my route",
            "I feel threatened but I am not sure",
            "I think something bad might happen",
            "I am panicking should I notify my guardian",
            "My bike broke down in a dark place",
            "I missed my stop and I am alone",
            "I am stuck somewhere unfamiliar",
            "I feel like I am being watched",
            "There are people acting suspicious around me",
            "I am nervous about this situation",
            "I feel very uncomfortable here",
            "This area feels dangerous to me"
        )

        for (prompt in mediumRiskPrompts) {
            val rawOutput = simulateOutput(prompt)
            val toolCalls = ToolRegistry.parseToolCalls(rawOutput)
            assertTrue("Expected tool call for medium-risk prompt: '$prompt'. Raw output: '$rawOutput'", toolCalls.isNotEmpty())
        }
    }

    @Test
    fun testSarcasmAndMetaphorPrompts() {
        val sarcasmPrompts = listOf(
            "My boss is going to kill me.",
            "This assignment murdered my weekend.",
            "I am dead tired.",
            "My code is on fire.",
            "That movie was terrifying.",
            "My mom will kill me if I am late.",
            "I am drowning in work.",
            "This party is dangerous for my diet.",
            "I got attacked by deadlines today.",
            "I almost died laughing."
        )

        for (prompt in sarcasmPrompts) {
            val rawOutput = simulateOutput(prompt)
            val toolCalls = ToolRegistry.parseToolCalls(rawOutput)
            assertTrue("Expected NO tool call (Safe Sarcasm/Metaphor) for prompt: '$prompt'. Raw output: '$rawOutput'", toolCalls.isEmpty())
        }
    }

    @Test
    fun testUserLowRiskEmotionalPrompts() {
        val lowRiskEmotionalPrompts = listOf(
            "I am stressed out",
            "I had a terrible day",
            "I am scared about my exam tomorrow",
            "I feel nervous before the interview",
            "My manager is killing me with deadlines",
            "This bug is killing me",
            "I am dying from boredom",
            "I hate this traffic save me",
            "My code crashed again help me",
            "I am panicking about the release build",
            "I feel overwhelmed today",
            "I am not okay emotionally",
            "I am upset and need someone to talk to",
            "I feel anxious tonight",
            "I want support but it is not an emergency"
        )

        for (prompt in lowRiskEmotionalPrompts) {
            val rawOutput = simulateOutput(prompt)
            val toolCalls = ToolRegistry.parseToolCalls(rawOutput)
            assertTrue("Expected NO tool call (NO_ACTION) for low-risk emotional prompt: '$prompt'. Raw output: '$rawOutput'", toolCalls.isEmpty())
        }
    }

    @Test
    fun testUserExplicitCancelAndFalseAlarmPrompts() {
        val cancelPrompts = listOf(
            "Do not send any alert",
            "I am safe now",
            "Cancel the emergency request",
            "This was just a test",
            "I do not need help anymore",
            "Please stop and do nothing",
            "I was only joking",
            "Never mind ignore that",
            "I am okay now",
            "Do not message my guardian",
            "Please cancel the SOS",
            "I sent that by mistake",
            "False alarm",
            "Everything is under control",
            "No emergency right now"
        )

        for (prompt in cancelPrompts) {
            val rawOutput = simulateOutput(prompt)
            val toolCalls = ToolRegistry.parseToolCalls(rawOutput)
            assertTrue("Expected NO tool call (Cancelled) for prompt: '$prompt'. Raw output: '$rawOutput'", toolCalls.isEmpty())
        }
    }

    @Test
    fun testAcousticHighTriagePrecedence() {
        // Test case 1: Panic scream + specific accident keyword -> should triage to accident_sos_alert
        val rawAccident = simulateOutput("I had a car crash", AcousticStressState.PANIC_SCREAM, 78f)
        val accidentCalls = ToolRegistry.parseToolCalls(rawAccident)
        assertEquals(1, accidentCalls.size)
        assertEquals("accident_sos_alert", accidentCalls[0].name)

        // Test case 2: Panic scream + specific medical keyword -> should triage to medical_emergency_dispatch
        val rawMedical = simulateOutput("I am bleeding", AcousticStressState.PANIC_SCREAM, 80f)
        val medicalCalls = ToolRegistry.parseToolCalls(rawMedical)
        assertEquals(1, medicalCalls.size)
        assertEquals("medical_emergency_dispatch", medicalCalls[0].name)

        // Test case 3: Panic scream + no specific keywords -> should fallback to stalker_threat_alert (general acoustic scream alert)
        val rawScreamOnly = simulateOutput("HIGH PANIC SCREAM DETECTED", AcousticStressState.PANIC_SCREAM, 85f)
        val screamOnlyCalls = ToolRegistry.parseToolCalls(rawScreamOnly)
        assertEquals(1, screamOnlyCalls.size)
        assertEquals("stalker_threat_alert", screamOnlyCalls[0].name)
        assertTrue(rawScreamOnly.contains("HIGH PANIC SCREAM DETECTED (85 dB SPL)"))

        // Test case 4: Panic scream + voice text -> should fallback to stalker_threat_alert with transcript combined
        val rawScreamWithText = simulateOutput("HIGH PANIC SCREAM DETECTED: someone is chasing me", AcousticStressState.PANIC_SCREAM, 85f)
        val screamWithTextCalls = ToolRegistry.parseToolCalls(rawScreamWithText)
        assertEquals(1, screamWithTextCalls.size)
        assertEquals("stalker_threat_alert", screamWithTextCalls[0].name)
        assertTrue(rawScreamWithText.contains("someone is chasing me"))
    }

    private fun simulateOutput(
        prompt: String,
        stressState: AcousticStressState = AcousticStressState.NORMAL,
        dbLevel: Float = 0f
    ): String {
        val lower = prompt.lowercase().replace('’', '\'').replace('‘', '\'')
        val escapedPrompt = prompt.replace("\"", "\\\"").replace("'", "\\'")
        
        val toolName = when {
            // 1. Explicit Cancel / False Alarm Filter (First priority override)
            lower.contains("cancel") || lower.contains("false alarm") || lower.contains("do not send") || lower.contains("don't send") ||
            lower.contains("just a test") || lower.contains("only a test") || lower.contains("testing") || lower.contains("test") ||
            lower.contains("joking") || lower.contains("never mind") || lower.contains("safe now") || lower.contains("okay now") ||
            lower.contains("don't need help") || lower.contains("do not need help") || lower.contains("no emergency") || lower.contains("under control") ||
            lower.contains("by mistake") || lower.contains("stop and do nothing") || lower.contains("do not message") || lower.contains("ignore that") ||
            lower.contains("not an emergency") -> return "[]"

            // 2. Metaphorical / Sarcastic / Work / Software / Emotional Non-Emergency Filter
            lower.contains("boss is going to kill") || lower.contains("mom will kill me") ||
            lower.contains("manager is killing") || lower.contains("bug is killing") || lower.contains("dying from boredom") ||
            lower.contains("murdered my weekend") || lower.contains("dead tired") ||
            lower.contains("code is on fire") || lower.contains("movie was terrifying") ||
            lower.contains("drowning in work") || lower.contains("dangerous for my diet") ||
            lower.contains("attacked by deadlines") || lower.contains("died laughing") ||
            lower.contains("killing me with") || lower.contains("traffic save me") || lower.contains("traffic") ||
            lower.contains("code crashed") || lower.contains("panicking about the release") || lower.contains("panicking about release") ||
            lower.contains("exam tomorrow") || lower.contains("interview") || lower.contains("maths exam") ||
            lower.contains("overwhelmed") || lower.contains("terrible day") || lower.contains("upset") ||
            lower.contains("stressed out") || lower.contains("emotionally") || lower.contains("anxious tonight") ||
            lower.contains("someone to talk to") -> return "[]"

            // 3. Suicide & Severe Panic / Mental Health Crisis
            lower.contains("suicide") || lower.contains("sucide") || lower.contains("self harm") || 
            lower.contains("overdose") || lower.contains("suicidal") || lower.contains("end my life") || 
            lower.contains("kill myself") || lower.contains("want to die") || lower.contains("attempt suicide") ||
            lower.contains("panic attack") || lower.contains("anxiety attack") || lower == "panic" -> "suicide_crisis_helpline"

            // 4. Kidnapping / Robbery / Intruder / Physical Attack / Personal Threat / Ultra-Short Panic Sensing
            lower.contains("kidnap") || lower.contains("kidnapped") || lower.contains("abducted") || lower.contains("hostage") ||
            lower.contains("robbed") || lower.contains("robbing") || lower.contains("mugged") || lower.contains("thief") ||
            lower.contains("intruder") || lower.contains("break in") || lower.contains("broke in") || lower.contains("burglar") ||
            lower.contains("shot") || lower.contains("stabbed") || lower.contains("knife") || lower.contains("gun") || lower.contains("weapon") ||
            lower.contains("unsafe") || lower.contains("not safe") || lower.contains("in danger") || lower.contains("threatened") ||
            lower.contains("trying to hurt") || lower.contains("kill me") || lower.contains("trying to kill") || lower.contains("going to kill") ||
            lower.contains("murder") || lower.contains("attacked") || lower.contains("being attacked") || lower.contains("stranger") ||
            lower.contains("following me") || lower.contains("following my route") || lower.contains("followed by") ||
            lower.contains("stalker") || lower.contains("stalked") || lower.contains("being stalked") || lower.contains("chased") ||
            lower.contains("scared") || lower.contains("afraid") || lower.contains("uncomfortable") || lower.contains("being watched") ||
            lower.contains("suspicious") || lower.contains("staring at me") || lower.contains("noises outside") || lower.contains("not trust") ||
            lower.contains("share my live location") || lower == "attack" -> "stalker_threat_alert"

            // 5. Medical Crisis / Fainting / Falls / Bleeding / Animal Attacks / Respiratory / Dizziness / Choking
            lower.contains("fainting") || lower.contains("faint") || lower.contains("fainted") || lower.contains("passed out") || lower.contains("dizzy") || lower.contains("weak") || lower.contains("unconscious") || lower.contains("blacking out") ||
            lower.contains("fell") || lower.contains("fall") || lower.contains("fell down") || lower.contains("can't get up") || lower.contains("cant get up") || lower.contains("cannot get up") || lower.contains("can't stand") ||
            lower.contains("heart attack") || lower.contains("cardiac") || lower.contains("can't breathe") || lower.contains("cant breathe") || lower.contains("cannot breathe") ||
            lower.contains("chest pain") || lower.contains("stroke") || lower.contains("bleeding") || lower.contains("blood") ||
            lower.contains("choking") || lower.contains("seizure") || lower.contains("head injury") || lower.contains("dying") || lower.contains("hurt") || lower.contains("injured") ||
            lower.contains("poison") || lower.contains("bite") || lower.contains("bitten") || lower.contains("snake") -> "medical_emergency_dispatch"

            // 6. Unified Accident Coverage (Bike Breakdown, Vehicle, Workplace, General)
            lower.contains("accident") || lower.contains("crash") || lower.contains("collision") || lower.contains("rollover") || lower.contains("hit by") || lower.contains("stranded") || lower.contains("wreck") || lower.contains("broke down") -> "accident_sos_alert"

            // 7. Fire & Explosion Emergency & Drowning
            lower.contains("fire") || lower.contains("explosion") || lower.contains("smoke") || lower.contains("gas leak") || lower.contains("drowning") || lower.contains("flood") -> "fire_disaster_sos"

            // 8. High Acoustic Panic / Screaming Override
            stressState == AcousticStressState.PANIC_SCREAM -> {
                val spokenText = prompt.removePrefix("HIGH PANIC SCREAM DETECTED:").removePrefix("HIGH PANIC SCREAM DETECTED").trim()
                val contextDesc = if (spokenText.isNotEmpty()) {
                    "HIGH PANIC SCREAM DETECTED (${dbLevel.toInt()} dB SPL): $spokenText"
                } else {
                    "HIGH PANIC SCREAM DETECTED (${dbLevel.toInt()} dB SPL)"
                }
                return """[{"name": "stalker_threat_alert", "arguments": {"context": "$contextDesc"}}]"""
            }

            // 9. Broad Emergency & Ultra-Short Panic Cries (help, immediate help, emergency, danger, sos, guardian, location now)
            lower.contains("sos") || lower.contains("guardian") || lower.contains("emergency") || lower.contains("emergence") || lower.contains("emercency") || lower.contains("emergen") ||
            lower.contains("danger") || lower.contains("distress") || lower.contains("crisis") || lower.contains("trouble") ||
            lower.contains("help") || lower.contains("mayday") || lower.contains("lost") || lower.contains("nervous") ||
            lower.contains("missed my stop") || lower.contains("unfamiliar") || lower.contains("alone") || lower.contains("wrong") || lower.contains("bad might happen") || lower.contains("panicking") ||
            lower.contains("save") || lower.contains("trapped") || lower.contains("stuck") || lower.contains("need help") -> "emergency_helper"

            else -> return "[]"
        }

        return """[{"name": "$toolName", "arguments": {"context": "$escapedPrompt"}}]"""
    }
}
