package agent.memory

import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.strategy.nocompression.NoCompressionMemoryStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class NoCompressionMemoryStrategyTest {
    private val strategy = NoCompressionMemoryStrategy()

    @Test
    fun `returns all runtime messages unchanged`() {
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "user"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "assistant")
        )

        assertEquals(
            messages,
            strategy.effectiveContext(
                MemoryState(
                    shortTerm = ShortTermMemory(
                        rawMessages = messages,
                        derivedMessages = messages
                    )
                )
            )
        )
    }
}
