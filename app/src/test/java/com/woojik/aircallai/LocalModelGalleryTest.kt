package com.woojik.aircallai

import com.woojik.aircallai.ai.local.LocalModelRegistry
import com.woojik.aircallai.ai.local.localConversationConfig
import com.google.ai.edge.litertlm.Role
import com.woojik.aircallai.ai.local.downloadPercent
import com.woojik.aircallai.ai.local.formatSizeBytes
import com.woojik.aircallai.ai.provider.ChatMessage
import com.woojik.aircallai.settings.InMemorySettingsStore
import com.woojik.aircallai.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 로컬 모델 갤러리 증분: 카탈로그 무결성, 다운로드 진행률 계산,
 * 프롬프트 직렬화, 설정에서의 모델 선택 저장을 검증한다.
 */
class LocalModelGalleryTest {

    // ----- 카탈로그 무결성 -----

    @Test
    fun legacySelectionRequiresExplicitNativeModelApply() {
        val settings = SettingsRepository(InMemorySettingsStore())
        for (legacyId in listOf("gemma-4-e2b", "gemma-4-e4b")) {
            settings.setLocalModelId(legacyId)
            assertNull(LocalModelRegistry.byId(settings.localModelId()))
        }
    }

    @Test
    fun registryHasModels() {
        assertTrue(LocalModelRegistry.models.isNotEmpty())
    }

    @Test
    fun modelIdsAreUnique() {
        val ids = LocalModelRegistry.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyModelHasValidMetadata() {
        LocalModelRegistry.models.forEach { model ->
            assertTrue("blank id", model.id.isNotBlank())
            assertTrue("blank name", model.displayName.isNotBlank())
            assertTrue("blank file", model.fileName.isNotBlank())
            assertTrue("file not .litertlm: " + model.fileName, model.fileName.endsWith(".litertlm"))
            assertTrue("bad url: " + model.downloadUrl, model.downloadUrl.startsWith("https://"))
            assertTrue("size must be positive", model.sizeBytes > 0)
            assertTrue("minRam must be sane", model.minRamMb >= 1024)
            assertTrue("blank description", model.description.isNotBlank())
            assertTrue(model.sha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(model.downloadUrl.endsWith("/" + model.fileName))
            assertFalse(model.downloadUrl.contains("/main/"))
            assertFalse(model.fileName.contains("web"))
        }
    }

    @Test
    fun byIdResolvesAndUnknownIsNull() {
        val first = LocalModelRegistry.models.first()
        assertEquals(first, LocalModelRegistry.byId(first.id))
        assertNull(LocalModelRegistry.byId("nonexistent"))
        assertNull(LocalModelRegistry.byId(null))
    }

    @Test
    fun byFileNameResolves() {
        val first = LocalModelRegistry.models.first()
        assertEquals(first, LocalModelRegistry.byFileName(first.fileName))
        assertNull(LocalModelRegistry.byFileName("missing.task"))
    }

    // ----- 진행률 -----

    @Test
    fun downloadPercentComputesCorrectly() {
        assertEquals(0, downloadPercent(0, 100))
        assertEquals(50, downloadPercent(50, 100))
        assertEquals(100, downloadPercent(100, 100))
    }

    @Test
    fun downloadPercentClampsOverflow() {
        assertEquals(100, downloadPercent(150, 100))
    }

    @Test
    fun downloadPercentUnknownTotalIsZero() {
        assertEquals(0, downloadPercent(500, null))
        assertEquals(0, downloadPercent(500, 0))
    }

    // ----- 포맷 -----

    @Test
    fun sizeFormatUsesGbAndMb() {
        assertEquals("2.3 GB", formatSizeBytes(2_300_000_000L))
        assertEquals("512 MB", formatSizeBytes(512_000_000L))
    }

    // ----- 프롬프트 직렬화 -----

    @Test
    fun promptSerializesHistoryWithRoles() {
        val history = listOf(
            ChatMessage(ChatMessage.Role.USER, "안녕"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "안녕하세요"),
            ChatMessage(ChatMessage.Role.USER, "날씨 어때?"),
        )
        val config = localConversationConfig(history)
        assertEquals(listOf(Role.USER, Role.MODEL), config.initialMessages.map { it.role })
        assertEquals(listOf("안녕", "안녕하세요"), config.initialMessages.map { it.toString() })
        assertEquals(false, config.extraContext["enable_thinking"])
        val voiceConfig = localConversationConfig(
            listOf(ChatMessage(ChatMessage.Role.SYSTEM, "짧게 말해 주세요")) + history,
        )
        assertTrue(voiceConfig.systemInstruction.toString().contains("짧게 말해 주세요"))
        assertEquals(2, voiceConfig.initialMessages.size)
    }

    // ----- 설정: 로컬 모델 선택 -----

    @Test
    fun localModelIdDefaultsToNull() {
        val settings = SettingsRepository(InMemorySettingsStore())
        assertNull(settings.localModelId())
    }

    @Test
    fun localModelIdRoundTrips() {
        val settings = SettingsRepository(InMemorySettingsStore())
        val model = LocalModelRegistry.models.first()
        settings.setLocalModelId(model.id)
        assertEquals(model.id, settings.localModelId())
    }

    @Test
    fun localModelIdBlankNormalizesToNull() {
        val settings = SettingsRepository(InMemorySettingsStore())
        settings.setLocalModelId("")
        assertNull(settings.localModelId())
        settings.setLocalModelId("   ")
        assertNull(settings.localModelId())
        settings.setLocalModelId(null)
        assertNull(settings.localModelId())
    }

    @Test
    fun selectedModelResolvesThroughSettings() {
        val settings = SettingsRepository(InMemorySettingsStore())
        val model = LocalModelRegistry.models.first()
        settings.setLocalModelId(model.id)
        assertEquals(model, LocalModelRegistry.byId(settings.localModelId()))
        // 적용 해제 후에는 어댑터가 미설치 상태로 돌아간다.
        settings.setLocalModelId(null)
        assertNull(LocalModelRegistry.byId(settings.localModelId()))
    }
}
