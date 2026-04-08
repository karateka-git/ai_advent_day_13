package agent.memory

import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.slidingwindow.SlidingWindowMemoryStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class SlidingWindowMemoryStrategyTest {
    @Test
    fun `returns recent dialog tail`() {
        val strategy = SlidingWindowMemoryStrategy(recentMessagesCount = 2)
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
            ChatMessage(role = ChatRole.USER, content = "u2"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a2")
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = messages,
                derivedMessages = messages
            )
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2")
            ),
            strategy.effectiveContext(refreshedState)
        )
    }

    @Test
    fun `ignores stored summary and uses full history tail`() {
        val strategy = SlidingWindowMemoryStrategy(recentMessagesCount = 2)
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
            ChatMessage(role = ChatRole.USER, content = "u2")
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = messages,
                derivedMessages = messages,
                strategyState = SummaryStrategyState(
                    summary = ConversationSummary(
                        content = "Сжатый фрагмент",
                        coveredMessagesCount = 2
                    ),
                    coveredMessagesCount = 2
                )
            )
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(
            listOf(
                ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                ChatMessage(role = ChatRole.USER, content = "u2")
            ),
            strategy.effectiveContext(refreshedState)
        )
    }
}
