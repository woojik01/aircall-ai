package com.woojik.aircallai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolIntentTest {

    @Test
    fun gmailRequestDetected() {
        val tools = ToolIntent.toolsRequestedIn("README 내용을 woojik1220@gmail.com에 보내줘")
        assertTrue(tools.contains("gmail"))
        assertTrue(tools.contains("github"))
    }

    @Test
    fun plainChatNotDetected() {
        val tools = ToolIntent.toolsRequestedIn("오늘 날씨 어때?")
        assertTrue(tools.isEmpty())
    }

    @Test
    fun completionClaimDetected() {
        assertTrue(ToolIntent.claimsCompletion("메일을 성공적으로 보냈습니다!"))
        assertTrue(ToolIntent.claimsCompletion("이슈를 만들었어요"))
        assertFalse(ToolIntent.claimsCompletion("메일을 보내려면 Gmail 연결이 필요합니다"))
    }

    @Test
    fun notesAndCalendarDetected() {
        assertTrue(ToolIntent.toolsRequestedIn("이 내용 메모해줘").contains("notes"))
        assertTrue(ToolIntent.toolsRequestedIn("내일 3시에 미팅 일정 잡아줘").contains("calendar"))
    }
}
