package com.woojik.aircallai

import com.woojik.aircallai.conversation.VoiceResponseSanitizer
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTextCleanerTest {

    @Test
    fun keepsQuestionAndExclamationForProsody() {
        val result = VoiceResponseSanitizer.sanitize("안녕하세요! 무슨 일이에요? 😊 **반가워요**")
        assertEquals("안녕하세요! 무슨 일이에요? 반가워요", result)
    }

    @Test
    fun removesEmojiMarkdownAndUrls() {
        val result = VoiceResponseSanitizer.sanitize("여기 https://example.com 링크 #태그 코드 입니다 :)")
        assertEquals("여기 링크 태그 코드 입니다", result)
    }

    @Test
    fun keepsNumbersAndDecimals() {
        val result = VoiceResponseSanitizer.sanitize("AirCall AI 2.0이 좋아요")
        assertEquals("AirCall AI 2.0이 좋아요", result)
    }

    @Test
    fun punctuationOnlyResponseGetsFallback() {
        assertEquals("네 말씀해 주세요", VoiceResponseSanitizer.sanitize("😊???"))
    }
}
