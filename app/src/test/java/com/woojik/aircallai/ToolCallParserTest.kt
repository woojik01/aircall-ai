package com.woojik.aircallai

import com.woojik.aircallai.tools.ToolCallParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** PRD-06 Tool-AI 연결: TOOL 지시어 파싱 검증. */
class ToolCallParserTest {

    @Test
    fun parsesSimpleCall() {
        val call = ToolCallParser.parseFirst(
            "TOOL: github.read_repository owner=woojik01 repo=aircall-ai",
        )
        assertNotNull(call)
        assertEquals("github", call!!.toolName)
        assertEquals("read_repository", call.action)
        assertEquals("woojik01", call.arguments["owner"])
        assertEquals("aircall-ai", call.arguments["repo"])
    }

    @Test
    fun parsesQuotedValueWithSpaces() {
        val call = ToolCallParser.parseFirst(
            "TOOL: github.create_issue owner=o repo=r title=\"버그: 음성 인식 안 됨\"",
        )
        assertNotNull(call)
        assertEquals("버그: 음성 인식 안 됨", call!!.arguments["title"])
    }

    @Test
    fun findsMarkerInMultilineResponse() {
        val call = ToolCallParser.parseFirst(
            "조회해 드리겠습니다.\nTOOL: github.read_repository owner=a repo=b\n끝.",
        )
        assertNotNull(call)
        assertEquals("a", call!!.arguments["owner"])
    }

    @Test
    fun returnsNullWhenNoMarker() {
        assertNull(ToolCallParser.parseFirst("오늘 날씨는 좋습니다."))
    }

    @Test
    fun returnsNullForMalformedCall() {
        assertNull(ToolCallParser.parseFirst("TOOL: "))
        assertNull(ToolCallParser.parseFirst("TOOL: 잘못된형식"))
    }

    @Test fun rejectsTruncatedAmbiguousAndMultipleCalls() {
        listOf(
            "TOOL: notes.add_note text=\"닫히지 않은 값",
            "TOOL: notes.add_note text=하나 text=둘",
            "TOOL: notes.add_note text=공백 있는 값",
            "TOOL: notes.add_note text=\"닫힌 값\"나머지",
            "TOOL: notes.add_note text=하나\nTOOL: notes.add_note text=둘",
        ).forEach { assertNull(it, ToolCallParser.parseFirst(it)) }
    }

    @Test fun parsesEscapedQuotesAndTabs() {
        val call = ToolCallParser.parseFirst("TOOL: notes.add_note\ttext=\"그가 \\\"안녕\\\"이라고 했다\"")
        assertEquals("그가 \"안녕\"이라고 했다", call!!.arguments["text"])
    }
}
