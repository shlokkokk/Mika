package com.wangzan101.mika.core.calls

import com.wangzan101.mika.core.action.ActionRisk
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 计划里预授权的号码:只有完全相同的号码跳过确认,别的号码(含尾号相同的)照常问。 */
class PreauthorizedCallTest {
    private val port = object : PhoneCallPort {
        override suspend fun state() = JsonObject(emptyMap())
        override suspend fun dial(number: String, simId: String?, task: PhoneCallTask?, ownerConversationId: String?) = JsonObject(emptyMap())
        override suspend fun answer(callId: String) = JsonObject(emptyMap())
        override suspend fun hangup(callId: String) = JsonObject(emptyMap())
        override suspend fun takeover() = JsonObject(emptyMap())
    }

    private fun dialRisk(preauthorized: String?, number: String) =
        PhoneCallsCapability(port, "thread", preauthorized).tools().single { it.def.name == "phone_call_dial" }
            .approvalRisk(JsonObject(mapOf("number" to JsonPrimitive(number))))!!

    @Test fun exactPreauthorizedNumberDialsWithoutAsking() {
        val risk = dialRisk("+12025550123", "+1 (202) 555-0123")
        assertNull(risk.ask)
        assertEquals(ActionRisk.External, risk.level)
    }

    @Test fun numbersSharingOnlyTheLastDigitsStillAsk() {
        assertNotNull(dialRisk("+12025550123", "+442025550123").ask)
        assertNotNull(dialRisk("+12025550123", "12025550123").ask)
        assertNotNull(dialRisk("+12025550123", "+12025550124").ask)
    }

    @Test fun withoutPreauthorizationEveryDialAsks() {
        assertNotNull(dialRisk(null, "+12025550123").ask)
    }
}
