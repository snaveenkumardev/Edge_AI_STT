package com.example.sentriai.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunctionCallParserTest {

    @Test
    fun `parses a well-formed call`() {
        val call = FunctionCallParser.parse(
            "<start_function_call>call:trigger_emergency_alert{" +
                "confidence:0.92," +
                "emergency_type:<escape>Fall<escape>," +
                "trigger_phrase:<escape>I fell and cannot get up<escape>" +
                "}<end_function_call>"
        )

        assertEquals("trigger_emergency_alert", call?.name)
        assertEquals("0.92", call?.arguments?.get("confidence"))
        assertEquals("Fall", call?.arguments?.get("emergency_type"))
        assertEquals("I fell and cannot get up", call?.arguments?.get("trigger_phrase"))
    }

    @Test
    fun `parses when the closing token was cut by the stop-token runtime`() {
        // <end_function_call> is a stop token, so MediaPipe usually truncates before it.
        val call = FunctionCallParser.parse(
            "<start_function_call>call:trigger_emergency_alert{emergency_type:<escape>Help Call<escape>}"
        )

        assertEquals("trigger_emergency_alert", call?.name)
        assertEquals("Help Call", call?.arguments?.get("emergency_type"))
    }

    @Test
    fun `parses when the call prefix is omitted`() {
        val call = FunctionCallParser.parse(
            "<start_function_call>trigger_emergency_alert{confidence:0.5}<end_function_call>"
        )

        assertEquals("trigger_emergency_alert", call?.name)
        assertEquals("0.5", call?.arguments?.get("confidence"))
    }

    @Test
    fun `commas and colons inside a spoken phrase are not separators`() {
        val call = FunctionCallParser.parse(
            "<start_function_call>call:trigger_emergency_alert{" +
                "emergency_type:<escape>Medical Distress<escape>," +
                "trigger_phrase:<escape>help, please: my chest hurts<escape>" +
                "}<end_function_call>"
        )

        assertEquals(2, call?.arguments?.size)
        assertEquals("help, please: my chest hurts", call?.arguments?.get("trigger_phrase"))
    }

    @Test
    fun `braces inside a spoken phrase do not truncate the argument list`() {
        val call = FunctionCallParser.parse(
            "<start_function_call>call:trigger_emergency_alert{" +
                "trigger_phrase:<escape>she said {oh no} loudly<escape>," +
                "confidence:0.7" +
                "}<end_function_call>"
        )

        assertEquals("she said {oh no} loudly", call?.arguments?.get("trigger_phrase"))
        assertEquals("0.7", call?.arguments?.get("confidence"))
    }

    @Test
    fun `prose refusal yields no call`() {
        // The failure mode that motivated the two-stage split: the model answering in English.
        assertNull(
            FunctionCallParser.parse(
                "I cannot analyze speech transcript for safety threats. My current capabilities " +
                    "are limited to assisting with emergency alerts and text reminders."
            )
        )
    }

    @Test
    fun `start token followed by prose yields no call`() {
        assertNull(FunctionCallParser.parse("<start_function_call>\nI cannot do that."))
    }

    @Test
    fun `empty response yields no call`() {
        assertNull(FunctionCallParser.parse(""))
    }

    @Test
    fun `unterminated brace yields no call`() {
        assertNull(FunctionCallParser.parse("<start_function_call>call:trigger_emergency_alert{"))
    }

    @Test
    fun `a differently named call is still parsed so the caller can reject it`() {
        val call = FunctionCallParser.parse(
            "<start_function_call>call:send_text_reminder{body:<escape>hi<escape>}<end_function_call>"
        )

        assertEquals("send_text_reminder", call?.name)
    }
}
