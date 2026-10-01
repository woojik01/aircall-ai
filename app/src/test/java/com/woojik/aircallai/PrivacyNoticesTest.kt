package com.woojik.aircallai

import com.woojik.aircallai.privacy.PrivacyNotice
import com.woojik.aircallai.privacy.PrivacyNotices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD-08 개인정보: 모드별 데이터 이동 설명 문구의 무결성 검증.
 * 사용자가 오해 없이 이해할 수 있는지 최소 기준(내용 존재/키워드 포함)을 검사한다.
 */
class PrivacyNoticesTest {

    @Test
    fun localModeExplainsNoDataLeaves() {
        val notice: PrivacyNotice = PrivacyNotices.localMode
        assertEquals("Local 모드", notice.title)
        assertTrue(notice.detail.isNotBlank())
        assertTrue(
            "Local 모드는 데이터가 외부로 나가지 않음을 명시해야 한다",
            notice.detail.contains("벗어나지"),
        )
    }

    @Test
    fun cloudModeNamesWhatIsTransmitted() {
        val notice = PrivacyNotices.cloudMode
        assertEquals("Cloud 모드", notice.title)
        assertTrue("HTTPS 언급 필수", notice.detail.contains("HTTPS"))
        assertTrue("전송 대상 명시 필수", notice.detail.contains("대화 텍스트"))
        assertTrue("Key 미전송 명시 필수", notice.detail.contains("보내지 않습니다"))
    }

    @Test
    fun toolsNoticeNamesService() {
        val notice = PrivacyNotices.tools
        assertEquals("Tool 연동", notice.title)
        assertTrue(notice.detail.contains("GitHub"))
        assertTrue("READ 제한 명시", notice.detail.contains("READ"))
    }

    @Test
    fun everyNoticeHasTitleAndDetail() {
        listOf(PrivacyNotices.localMode, PrivacyNotices.cloudMode, PrivacyNotices.tools).forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.detail.length >= 20)
        }
    }
}
