package com.woojik.aircallai

import com.woojik.aircallai.ai.cloud.readChatStream
import com.woojik.aircallai.ai.provider.*
import com.woojik.aircallai.audio.*
import com.woojik.aircallai.conversation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class StreamingResponseTest {
    @Test fun handlesChunksUsageAndRequiresCompletion() = runTest {
        val stream = "data: {\"choices\":[{\"delta\":{\"content\":\"안녕\"}}]}\n\n" +
            "data: {\"choices\":[]}\n\n" + "data: {\"choices\":[{\"delta\":{\"content\":\"하세요.\"}}]}\n\n" + "data: [DONE]\n\n"
        val updates = mutableListOf<String>()
        assertEquals("안녕하세요.", readChatStream(stream.reader().buffered()) { updates += it })
        assertEquals(listOf("안녕", "안녕하세요."), updates)
        try { readChatStream(stream.substringBefore("data: [DONE]").reader().buffered()) {}; fail() }
        catch (e: AIProviderException) { assertEquals(ProviderErrorKind.NETWORK, e.kind) }
    }

    @Test fun firstSentenceSpeaksBeforeGenerationFinishesWithoutDuplicatePlayback() = runTest {
        val gate = CompletableDeferred<Unit>()
        val provider = object : AIProvider by NoopAIProvider() {
            override suspend fun respondStreaming(history: List<ChatMessage>, onText: suspend (String) -> Unit): AIResponse {
                onText("첫 문장.")
                gate.await()
                onText("첫 문장. 다음 문장.")
                return AIResponse(ChatMessage(ChatMessage.Role.ASSISTANT, "첫 문장. 다음 문장."), type, 0)
            }
        }
        val spoken = mutableListOf<String>()
        val recognizer = object : SpeechRecognizerInterface { override suspend fun recognizeOnce() = "안녕" }
        val tts = object : SpeechSynthesizer {
            override suspend fun speak(text: String) { spoken += text }
            override fun stop() {}
        }
        val engine = ConversationEngine(provider)
        val turn = launch { VoiceSession(recognizer, tts, engine).runOneTurn() }
        runCurrent()
        assertEquals(listOf("첫 문장."), spoken)
        assertEquals(1, engine.transcript.value.size)
        gate.complete(Unit); turn.join()
        assertEquals(listOf("첫 문장.", "다음 문장."), spoken)
        assertEquals(2, engine.transcript.value.size)
    }
}
