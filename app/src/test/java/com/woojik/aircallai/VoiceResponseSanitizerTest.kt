package com.woojik.aircallai

import com.woojik.aircallai.conversation.VoiceResponseSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VoiceResponseSanitizerTest {

    @Test
    fun removesEmojiPunctuationAndMarkdownSymbols() {
        val result = VoiceResponseSanitizer.sanitize("안녕하세요! 오늘은 정말 좋아요 :) 😊 **반가워요**")
        assertEquals("안녕하세요 오늘은 정말 좋아요 반가워요", result)
    }

    @Test
    fun preservesKoreanEnglishNumbersAndSpaces() {
        val result = VoiceResponseSanitizer.sanitize("AirCall AI 2.0은 좋아요")
        assertEquals("AirCall AI 2 0은 좋아요", result)
        assertFalse(result.any { !it.isLetterOrDigit() && !it.isWhitespace() })
    }

    @Test
    fun blankSanitizedResponseGetsFallback() {
        assertEquals("네 말씀해 주세요", VoiceResponseSanitizer.sanitize("😊!!!"))
    }
}
