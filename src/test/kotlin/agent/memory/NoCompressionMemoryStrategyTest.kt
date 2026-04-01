package agent.memory

import agent.memory.model.MemoryState
import agent.memory.strategy.NoCompressionMemoryStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class NoCompressionMemoryStrategyTest {
    private val strategy = NoCompressionMemoryStrategy()

    @Test
    fun `returns all messages unchanged`() {
        val messages = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "system"),
            ChatMessage(role = ChatRole.USER, content = "user"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "assistant")
        )

        assertEquals(messages, strategy.effectiveContext(MemoryState(messages = messages)))
    }
}

