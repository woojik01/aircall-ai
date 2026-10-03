package com.woojik.aircallai

import com.woojik.aircallai.tools.ToolCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun parsesStructuredJsonCall() {
        val call = ToolCallParser.parseFirst(
            """TOOL_CALL: {"tool":"github","action":"read_repository","arguments":{"owner":"woojik01","repo":"aircall-ai"}}""",
        )
        assertNotNull(call)
        assertEquals("github", call!!.toolName)
        assertEquals("read_repository", call.action)
        assertEquals("woojik01", call.arguments["owner"])
        assertEquals("aircall-ai", call.arguments["repo"])
    }

    @Test
    fun structuredJsonPreservesSpacesNewlinesAndQuotes() {
        val call = ToolCallParser.parseFirst(
            """작업을 실행합니다.
TOOL_CALL: {"tool":"gmail","action":"send_email","arguments":{"to":"a@example.com","subject":"테스트 메일","body":"첫 줄\\n둘째 줄 \\"인용\\" {내용}"}}""",
        )
        assertNotNull(call)
        assertEquals("테스트 메일", call!!.arguments["subject"])
        assertEquals("첫 줄\n둘째 줄 \"인용\" {내용}", call.arguments["body"])
    }

    @Test
    fun returnsNullForMalformedStructuredJson() {
        assertNull(ToolCallParser.parseFirst("""TOOL_CALL: {"tool":"github","action":"""))
    }

    @Test
    fun fallsBackToLegacyFormat() {
        val call = ToolCallParser.parseFirst(
            "TOOL: github.create_issue owner=o repo=r title=\"버그: 음성 인식 안 됨\"",
        )
        assertNotNull(call)
        assertEquals("github", call!!.toolName)
        assertEquals("create_issue", call.action)
        assertEquals("버그: 음성 인식 안 됨", call.arguments["title"])
    }

    @Test
    fun returnsNullWhenNoMarker() {
        assertNull(ToolCallParser.parseFirst("오늘 날씨는 좋습니다."))
    }
}
