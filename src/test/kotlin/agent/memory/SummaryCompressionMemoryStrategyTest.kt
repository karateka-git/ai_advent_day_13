package agent.memory

import agent.memory.core.MemoryStateRefreshMode
import agent.memory.model.ConversationSummary
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.SummaryStrategyState
import agent.memory.strategy.summary.ConversationSummarizer
import agent.memory.strategy.summary.SummaryCompressionMemoryStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class SummaryCompressionMemoryStrategyTest {
    @Test
    fun `returns original runtime messages when summary is absent and nothing is covered`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1")
        )

        assertEquals(
            messages,
            strategy.effectiveContext(
                strategy.refreshState(
                    MemoryState(
                        shortTerm = ShortTermMemory(
                            rawMessages = messages,
                            derivedMessages = messages
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `builds context from summary and uncovered tail without deleting history`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
            ChatMessage(role = ChatRole.USER, content = "u2"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
            ChatMessage(role = ChatRole.USER, content = "u3")
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = messages,
                derivedMessages = messages,
                strategyState = SummaryStrategyState(
                    summary = ConversationSummary(
                        content = "Пользователь уже рассказал о прошлой задаче.",
                        coveredMessagesCount = 2
                    ),
                    coveredMessagesCount = 2
                )
            )
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(
            listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = "Краткое резюме предыдущего диалога:\nПользователь уже рассказал о прошлой задаче."
                ),
                ChatMessage(role = ChatRole.USER, content = "u2"),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                ChatMessage(role = ChatRole.USER, content = "u3")
            ),
            strategy.effectiveContext(refreshedState)
        )
        assertEquals(messages, refreshedState.shortTerm.rawMessages)
    }

    @Test
    fun `refreshState updates summary but keeps full message history`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "u1"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
            ChatMessage(role = ChatRole.USER, content = "u2"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
            ChatMessage(role = ChatRole.USER, content = "u3")
        )

        val refreshedState = strategy.refreshState(
            MemoryState(
                shortTerm = ShortTermMemory(
                    rawMessages = messages,
                    derivedMessages = messages,
                    strategyState = SummaryStrategyState()
                )
            )
        )
        val refreshedSummary = (refreshedState.shortTerm.strategyState as? SummaryStrategyState)?.summary
        val refreshedStrategyState = refreshedState.shortTerm.strategyState as? SummaryStrategyState

        assertEquals("Пользователь: u1\nАссистент: a1", refreshedSummary?.content)
        assertEquals(2, refreshedSummary?.coveredMessagesCount)
        assertEquals(2, refreshedStrategyState?.coveredMessagesCount)
        assertEquals(messages, refreshedState.shortTerm.rawMessages)
    }

    @Test
    fun `refreshState rewrites existing summary and advances covered counter`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = listOf(
                    ChatMessage(role = ChatRole.USER, content = "u1"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                    ChatMessage(role = ChatRole.USER, content = "u2"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                    ChatMessage(role = ChatRole.USER, content = "u3"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                    ChatMessage(role = ChatRole.USER, content = "u4")
                ),
                derivedMessages = emptyList(),
                strategyState = SummaryStrategyState(
                    summary = ConversationSummary(
                        content = "Пользователь: u0\nАссистент: a0",
                        coveredMessagesCount = 2
                    ),
                    coveredMessagesCount = 2
                )
            )
        )

        val refreshedState = strategy.refreshState(state)
        val refreshedSummary = (refreshedState.shortTerm.strategyState as? SummaryStrategyState)?.summary
        val refreshedStrategyState = refreshedState.shortTerm.strategyState as? SummaryStrategyState

        assertEquals(
            "Система: Предыдущее резюме: Пользователь: u0\nАссистент: a0\nПользователь: u2\nАссистент: a2",
            refreshedSummary?.content
        )
        assertEquals(4, refreshedSummary?.coveredMessagesCount)
        assertEquals(4, refreshedStrategyState?.coveredMessagesCount)
    }

    @Test
    fun `refreshState activates summary from current window instead of full history`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = RecordingConversationSummarizer()
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = listOf(
                    ChatMessage(role = ChatRole.USER, content = "u1"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                    ChatMessage(role = ChatRole.USER, content = "u2"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                    ChatMessage(role = ChatRole.USER, content = "u3"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                    ChatMessage(role = ChatRole.USER, content = "u4")
                ),
                derivedMessages = emptyList()
            )
        )

        val refreshedState = strategy.refreshState(state)
        val refreshedSummary = (refreshedState.shortTerm.strategyState as? SummaryStrategyState)?.summary
        val refreshedStrategyState = refreshedState.shortTerm.strategyState as? SummaryStrategyState

        assertEquals("Ассистент: a2\nПользователь: u3", refreshedSummary?.content)
        assertEquals(5, refreshedSummary?.coveredMessagesCount)
        assertEquals(5, refreshedStrategyState?.coveredMessagesCount)
        assertEquals(
            listOf(
                ChatMessage(
                    role = ChatRole.SYSTEM,
                    content = "Краткое резюме предыдущего диалога:\nАссистент: a2\nПользователь: u3"
                ),
                ChatMessage(role = ChatRole.ASSISTANT, content = "a3"),
                ChatMessage(role = ChatRole.USER, content = "u4")
            ),
            strategy.effectiveContext(refreshedState)
        )
    }

    @Test
    fun `refreshState does not compress when there are not enough messages outside recent tail`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = FixedSummaryConversationSummarizer("Не должен использоваться")
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = listOf(
                    ChatMessage(role = ChatRole.USER, content = "u1"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                    ChatMessage(role = ChatRole.USER, content = "u2")
                ),
                derivedMessages = listOf(
                    ChatMessage(role = ChatRole.USER, content = "u1"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                    ChatMessage(role = ChatRole.USER, content = "u2")
                ),
                strategyState = SummaryStrategyState()
            )
        )

        val refreshedState = strategy.refreshState(state)

        assertEquals(state, refreshedState)
    }

    @Test
    fun `preview refresh does not invoke summarizer and keeps state unchanged`() {
        val strategy = SummaryCompressionMemoryStrategy(
            recentMessagesCount = 2,
            summaryBatchSize = 2,
            summarizer = object : ConversationSummarizer {
                override fun summarize(messages: List<ChatMessage>): String =
                    error("Preview refresh must not call summarizer.")
            }
        )
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = listOf(
                    ChatMessage(role = ChatRole.USER, content = "u1"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                    ChatMessage(role = ChatRole.USER, content = "u2"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                    ChatMessage(role = ChatRole.USER, content = "u3")
                ),
                derivedMessages = listOf(
                    ChatMessage(role = ChatRole.USER, content = "u1"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a1"),
                    ChatMessage(role = ChatRole.USER, content = "u2"),
                    ChatMessage(role = ChatRole.ASSISTANT, content = "a2"),
                    ChatMessage(role = ChatRole.USER, content = "u3")
                )
            )
        )

        val refreshedState = strategy.refreshState(state, MemoryStateRefreshMode.PREVIEW)

        assertEquals(state, refreshedState)
    }
}

private class RecordingConversationSummarizer : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String =
        messages.joinToString(separator = "\n") { message ->
            "${message.role.displayName}: ${message.content}"
        }
}

private class FixedSummaryConversationSummarizer(
    private val summary: String
) : ConversationSummarizer {
    override fun summarize(messages: List<ChatMessage>): String = summary
}
