package com.edgeai.stt.ai

import org.json.JSONArray

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: String = "{}"
)

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any> = emptyMap()
)

sealed class ToolInvocationResult {
    object NoTool : ToolInvocationResult()
    data class Emergency(
        val toolName: String,
        val message: String,
        val location: String,
        val contextSummary: String,
        val protocolAction: String
    ) : ToolInvocationResult()
    data class GeneralTool(val name: String, val message: String) : ToolInvocationResult()
}

object ToolRegistry {
    val SYSTEM_PROMPT = """
        You are an on-device emergency triage assistant. Analyze user spoken text and select the exact tool required.
        
        Rules:
        1. If user reports "someone is trying to kill me", "someone is following me", "being attacked", "I am unsafe", "I got robbed", "intruder", "kidnapped", "abducted", "hostage" -> invoke 'stalker_threat_alert'.
        2. If user reports "I attempt a suicide", "end my life", "suicidal", "want to die", "panic attack", "panic", "severe anxiety" -> invoke 'suicide_crisis_helpline'.
        3. If user reports "I had a heart attack", "I can't breathe", "chest pain", "fainting", "fainted", "passed out", "heavy bleeding", "snake bite" -> invoke 'medical_emergency_dispatch'.
        4. If user reports ANY type of accident ("I had an accident", "car crash", "stuck in fire accident", "workplace accident", "chemical accident", "fall accident", "lost", "stranded") -> invoke 'accident_sos_alert' with arguments {"accident_type": "vehicle|fire|workplace|chemical|general", "context": "..."}.
        5. If user reports "stuck in fire", "building fire", "explosion", "drowning", "trapped" -> invoke 'fire_disaster_sos'.
        6. If general emergency, injury, distress, panic, lost, or danger -> invoke 'emergency_helper'.
        7. If prompt is metaphorical, sarcastic, hyperbole, or work idiom ("boss is going to kill me", "died laughing", "drowning in work", "code on fire") -> output empty list [].
        8. If no danger is detected -> output empty list [].
        9. Output MUST strictly be a JSON array: [{"name": "tool_name", "arguments": {"context": "..."}}]
        
        Examples (Emergency):
        - User: "Someone is following me and I think I'm getting kidnapped" -> [{"name": "stalker_threat_alert", "arguments": {"context": "Someone is following me and I think I'm getting kidnapped"}}]
        - User: "I feel dizzy and I'm fainting" -> [{"name": "medical_emergency_dispatch", "arguments": {"context": "I feel dizzy and I'm fainting"}}]
        - User: "I'm lost in the woods and nervous" -> [{"name": "emergency_helper", "arguments": {"context": "I'm lost in the woods and nervous"}}]
        
        Examples (Sarcasm / Metaphors - DO NOT CALL TOOL):
        - User: "My boss is going to kill me" -> []
        - User: "This assignment murdered my weekend" -> []
        - User: "I am dead tired" -> []
        - User: "My code is on fire" -> []
        - User: "That movie was terrifying" -> []
        - User: "My mom will kill me if I am late" -> []
        - User: "I am drowning in work" -> []
        - User: "This party is dangerous for my diet" -> []
        - User: "I got attacked by deadlines today" -> []
        - User: "I almost died laughing" -> []
    """.trimIndent()

    val DEFINITIONS = listOf(
        ToolDefinition(
            name = "suicide_crisis_helpline",
            description = "CRITICAL SUICIDE, PANIC & MENTAL HEALTH CRISIS. Use when user expresses intent of suicide, self-harm, severe panic attack, anxiety crisis, or overdose. Connects 988 Lifeline and dispatches emergency aid.",
            parameters = """{"type": "object", "properties": {"context": {"type": "string"}}}"""
        ),
        ToolDefinition(
            name = "stalker_threat_alert",
            description = "CRITICAL THREAT, KIDNAPPING & ATTACK. Use when user is being attacked, followed, stalked, kidnapped, abducted, held hostage, robbed, threatened with weapons, unsafe, or in immediate personal danger. Triggers silent alarm, live location tracking, and emergency alert.",
            parameters = """{"type": "object", "properties": {"context": {"type": "string"}}}"""
        ),
        ToolDefinition(
            name = "medical_emergency_dispatch",
            description = "MEDICAL EMERGENCY, FAINTING & SEVERE INJURY. Use when user reports heart attack, inability to breathe, chest pain, stroke, fainting, fainted, passed out, severe bleeding, falls, animal/snake bites, or medical crisis. Dispatches 911/EMS with location.",
            parameters = """{"type": "object", "properties": {"context": {"type": "string"}}}"""
        ),
        ToolDefinition(
            name = "accident_sos_alert",
            description = "ALL ACCIDENTS & LOST/STRANDED (Vehicle, Fire, Workplace, Chemical, General). Handles any accident or lost/stranded report (e.g. 'I had an accident', 'fire accident', 'car crash', 'lost'). Accepts 'accident_type' ('vehicle', 'fire', 'workplace', 'chemical', 'general') and 'context'.",
            parameters = """{"type": "object", "properties": {"accident_type": {"type": "string"}, "context": {"type": "string"}}}"""
        ),
        ToolDefinition(
            name = "fire_disaster_sos",
            description = "FIRE, EXPLOSION & DISASTER EMERGENCY. Use when user reports fire, building fire, stuck in fire, explosion, drowning, or smoke emergency.",
            parameters = """{"type": "object", "properties": {"context": {"type": "string"}}}"""
        ),
        ToolDefinition(
            name = "emergency_helper",
            description = "GENERAL EMERGENCY, LOST & PANIC DISPATCH. Use for unclassified danger, injuries, distress, being lost, nervous panic, or general emergency dispatch.",
            parameters = """{"type": "object", "properties": {"context": {"type": "string"}}}"""
        )
    )

    fun parseToolCalls(rawResponse: String): List<ToolCall> {
        if (rawResponse.contains("<start_function_call>") || rawResponse.contains("call:")) {
            val calls = mutableListOf<ToolCall>()
            val pattern = "(?:<start_function_call>)?call:([a-zA-Z0-9_]+)\\{([^}]+)\\}(?:<end_function_call>)?"
            val regex = pattern.toRegex()
            val matchResults = regex.findAll(rawResponse)
            for (match in matchResults) {
                val toolName = match.groupValues[1]
                val argsContent = match.groupValues[2]
                
                val argPattern = "([a-zA-Z0-9_]+):(?:<escape>(.*?)<escape>|([a-zA-Z0-9_\\-]+))"
                val argRegex = argPattern.toRegex()
                val argsMap = mutableMapOf<String, Any>()
                for (argMatch in argRegex.findAll(argsContent)) {
                    val paramName = argMatch.groupValues[1]
                    val paramVal = if (argMatch.groupValues[2].isNotEmpty()) {
                        argMatch.groupValues[2]
                    } else {
                        argMatch.groupValues[3]
                    }
                    argsMap[paramName] = paramVal
                }
                calls.add(ToolCall(name = toolName, arguments = argsMap))
            }
            if (calls.isNotEmpty()) {
                return calls
            }
        }

        val cleaned = rawResponse.trim()
            .replace("^```(?:json)?\\s*".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("```$".toRegex(), "")
            .trim()

        if (cleaned.isEmpty() || cleaned == "[]") return emptyList()

        val list = mutableListOf<ToolCall>()
        try {
            val jsonArray = JSONArray(cleaned)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name")
                val argsMap = mutableMapOf<String, Any>()
                if (obj.has("arguments")) {
                    val argsObj = obj.optJSONObject("arguments")
                    argsObj?.keys()?.forEach { key ->
                        argsMap[key] = argsObj.get(key)
                    }
                }
                if (name.isNotEmpty()) {
                    list.add(ToolCall(name, argsMap))
                }
            }
        } catch (e: Throwable) {
            // Regex fallback parser for JVM unit tests where org.json.JSONArray is unmocked
            val nameRegex = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val match = nameRegex.find(cleaned)
            if (match != null) {
                val toolName = match.groupValues[1]
                list.add(ToolCall(toolName, mapOf("context" to cleaned)))
            }
        }
        return list
    }

    fun invokeTool(
        toolCall: ToolCall,
        userTranscript: String,
        currentLocation: String
    ): ToolInvocationResult {
        val contextStr = toolCall.arguments["context"]?.toString() ?: userTranscript
        val accidentType = toolCall.arguments["accident_type"]?.toString()?.lowercase() ?: "general"

        return when (toolCall.name) {
            "suicide_crisis_helpline" -> ToolInvocationResult.Emergency(
                toolName = "suicide_crisis_helpline",
                message = "💙 988 SUICIDE & CRISIS LIFELINE ACTIVATED: Connecting to 988 Suicide & Crisis Lifeline and dispatching emergency response team with GPS location.",
                location = currentLocation,
                contextSummary = contextStr,
                protocolAction = "SUICIDE & MENTAL HEALTH CRISIS DISPATCH"
            )
            "stalker_threat_alert" -> ToolInvocationResult.Emergency(
                toolName = "stalker_threat_alert",
                message = "🕵️ PERSONAL THREAT, KIDNAPPING & ATTACK PROTOCOL: Screen dimmed, continuous live GPS stream active, silent alert sent to trusted contacts & emergency response.",
                location = currentLocation,
                contextSummary = contextStr,
                protocolAction = "SILENT ALARM & LIVE TRACKING"
            )
            "medical_emergency_dispatch" -> ToolInvocationResult.Emergency(
                toolName = "medical_emergency_dispatch",
                message = "🚑 MEDICAL EMERGENCY DISPATCH: 911/EMS dispatched with user GPS location & cardiac/fainting/trauma alert.",
                location = currentLocation,
                contextSummary = contextStr,
                protocolAction = "PRIORITY EMS DISPATCH"
            )
            "accident_sos_alert" -> {
                val (actionHeader, msg) = when (accidentType) {
                    "fire" -> Pair(
                        "🔥 FIRE ACCIDENT RESCUE DISPATCH",
                        "🔥 FIRE ACCIDENT SOS ACTIVATED: Fire rescue services & burn unit dispatched to GPS location."
                    )
                    "vehicle" -> Pair(
                        "🚗 VEHICLE COLLISION SOS DISPATCH",
                        "🚗 VEHICLE COLLISION SOS ACTIVATED: Traffic crash response team & highway safety services dispatched."
                    )
                    "chemical" -> Pair(
                        "⚠️ HAZMAT & CHEMICAL ACCIDENT DISPATCH",
                        "⚠️ HAZMAT SOS ACTIVATED: Chemical hazard rescue & bio-containment team dispatched."
                    )
                    "workplace" -> Pair(
                        "🏗️ WORKPLACE ACCIDENT DISPATCH",
                        "🏗️ WORKPLACE ACCIDENT DISPATCH: Occupational safety & emergency medical services dispatched."
                    )
                    else -> Pair(
                        "🚨 ACCIDENT EMERGENCY DISPATCH",
                        "🚨 ACCIDENT SOS ACTIVATED: Emergency first responders & rescue services dispatched to GPS location."
                    )
                }
                ToolInvocationResult.Emergency(
                    toolName = "accident_sos_alert",
                    message = msg,
                    location = currentLocation,
                    contextSummary = contextStr,
                    protocolAction = actionHeader
                )
            }
            "fire_disaster_sos" -> ToolInvocationResult.Emergency(
                toolName = "fire_disaster_sos",
                message = "🔥 FIRE & RESCUE DISPATCH: Fire department & emergency rescue units dispatched with user GPS location.",
                location = currentLocation,
                contextSummary = contextStr,
                protocolAction = "FIRE & RESCUE DISPATCH"
            )
            "emergency_helper" -> ToolInvocationResult.Emergency(
                toolName = "emergency_helper",
                message = "🚨 GENERAL EMERGENCY DISPATCH: Local authorities notified with user location.",
                location = currentLocation,
                contextSummary = contextStr,
                protocolAction = "GENERAL EMERGENCY DISPATCH"
            )
            else -> ToolInvocationResult.GeneralTool(
                name = toolCall.name,
                message = "Invoked tool: ${toolCall.name}"
            )
        }
    }

    fun getGemmaDeclarations(): String {
        return """
<start_function_declaration>declaration:suicide_crisis_helpline{description:<escape>CRITICAL SUICIDE, PANIC & MENTAL HEALTH CRISIS. Use when user expresses intent of suicide, self-harm, severe panic attack, anxiety crisis, or overdose. Connects 988 Lifeline and dispatches emergency aid.<escape>,parameters:{properties:{context:{description:<escape>Context description<escape>,type:<escape>STRING<escape>}},required:[<escape>context<escape>],type:<escape>OBJECT<escape>}}<end_function_declaration>
<start_function_declaration>declaration:stalker_threat_alert{description:<escape>CRITICAL THREAT, KIDNAPPING & ATTACK. Use when user is being attacked, followed, stalked, kidnapped, abducted, held hostage, robbed, threatened with weapons, unsafe, or in immediate personal danger. Triggers silent alarm, live location tracking, and emergency alert.<escape>,parameters:{properties:{context:{description:<escape>Context description<escape>,type:<escape>STRING<escape>}},required:[<escape>context<escape>],type:<escape>OBJECT<escape>}}<end_function_declaration>
<start_function_declaration>declaration:medical_emergency_dispatch{description:<escape>MEDICAL EMERGENCY, FAINTING & SEVERE INJURY. Use when user reports heart attack, inability to breathe, chest pain, stroke, fainting, fainted, passed out, severe bleeding, falls, animal/snake bites, or medical crisis. Dispatches 911/EMS with location.<escape>,parameters:{properties:{context:{description:<escape>Context description<escape>,type:<escape>STRING<escape>}},required:[<escape>context<escape>],type:<escape>OBJECT<escape>}}<end_function_declaration>
<start_function_declaration>declaration:accident_sos_alert{description:<escape>ALL ACCIDENTS & LOST/STRANDED (Vehicle, Fire, Workplace, Chemical, General). Handles any accident or lost/stranded report (e.g. 'I had an accident', 'fire accident', 'car crash', 'lost'). Accepts 'accident_type' ('vehicle', 'fire', 'workplace', 'chemical', 'general') and 'context'.<escape>,parameters:{properties:{accident_type:{description:<escape>Accident category<escape>,type:<escape>STRING<escape>},context:{description:<escape>Context description<escape>,type:<escape>STRING<escape>}},required:[<escape>accident_type<escape>,<escape>context<escape>],type:<escape>OBJECT<escape>}}<end_function_declaration>
<start_function_declaration>declaration:fire_disaster_sos{description:<escape>FIRE, EXPLOSION & DISASTER EMERGENCY. Use when user reports fire, building fire, stuck in fire, explosion, drowning, or smoke emergency.<escape>,parameters:{properties:{context:{description:<escape>Context description<escape>,type:<escape>STRING<escape>}},required:[<escape>context<escape>],type:<escape>OBJECT<escape>}}<end_function_declaration>
<start_function_declaration>declaration:emergency_helper{description:<escape>GENERAL EMERGENCY, LOST & PANIC DISPATCH. Use for unclassified danger, injuries, distress, being lost, nervous panic, or general emergency dispatch.<escape>,parameters:{properties:{context:{description:<escape>Context description<escape>,type:<escape>STRING<escape>}},required:[<escape>context<escape>],type:<escape>OBJECT<escape>}}<end_function_declaration>
        """.trimIndent().trim()
    }
}
