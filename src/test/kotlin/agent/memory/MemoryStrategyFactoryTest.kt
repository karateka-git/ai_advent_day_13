package agent.memory

import agent.memory.strategy.MemoryStrategyFactory
import agent.memory.strategy.BranchingMemoryStrategy
import agent.memory.strategy.MemoryStrategyType
import agent.memory.strategy.NoCompressionMemoryStrategy
import agent.memory.strategy.SlidingWindowMemoryStrategy
import agent.memory.strategy.StickyFactsMemoryStrategy
import agent.memory.strategy.SummaryCompressionMemoryStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse

class MemoryStrategyFactoryTest {
    @Test
    fun `availableOptions returns all supported strategies`() {
        assertEquals(
            listOf(
                MemoryStrategyType.NO_COMPRESSION,
                MemoryStrategyType.SUMMARY_COMPRESSION,
                MemoryStrategyType.SLIDING_WINDOW,
                MemoryStrategyType.STICKY_FACTS,
                MemoryStrategyType.BRANCHING
            ),
            MemoryStrategyFactory.availableOptions().map { it.type }
        )
    }

    @Test
    fun `create returns no compression strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.NO_COMPRESSION,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<NoCompressionMemoryStrategy>(strategy)
    }

    @Test
    fun `create returns summary compression strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.SUMMARY_COMPRESSION,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<SummaryCompressionMemoryStrategy>(strategy)
    }

    @Test
    fun `create returns sliding window strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.SLIDING_WINDOW,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<SlidingWindowMemoryStrategy>(strategy)
    }

    @Test
    fun `create returns sticky facts strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.STICKY_FACTS,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<StickyFactsMemoryStrategy>(strategy)
    }

    @Test
    fun `create returns branching strategy`() {
        val strategy = MemoryStrategyFactory.create(
            strategyType = MemoryStrategyType.BRANCHING,
            languageModel = FactoryTestLanguageModel()
        )

        assertIs<BranchingMemoryStrategy>(strategy)
    }

    @Test
    fun `availableOptions exposes strategy-specific prompt descriptions only where needed`() {
        val optionsByType = MemoryStrategyFactory.availableOptions().associateBy { it.type }

        assertNull(optionsByType.getValue(MemoryStrategyType.NO_COMPRESSION).specificPromptDescription)
        assertNull(optionsByType.getValue(MemoryStrategyType.SLIDING_WINDOW).specificPromptDescription)
        assertEquals(
            "Provider prompt-токены включают не только основной запрос, но и внутренние вызовы на обновление summary.",
            optionsByType.getValue(MemoryStrategyType.SUMMARY_COMPRESSION).specificPromptDescription
        )
        assertEquals(
            "Provider prompt-токены включают не только основной запрос, но и внутренние вызовы на извлечение и обновление facts.",
            optionsByType.getValue(MemoryStrategyType.STICKY_FACTS).specificPromptDescription
        )
    }

    @Test
    fun `availableOptions exposes additional commands only for branching strategy`() {
        val optionsByType = MemoryStrategyFactory.availableOptions().associateBy { it.type }

        assertEquals(
            emptyList(),
            optionsByType.getValue(MemoryStrategyType.NO_COMPRESSION).additionalCommands
        )
        assertEquals(
            emptyList(),
            optionsByType.getValue(MemoryStrategyType.SUMMARY_COMPRESSION).additionalCommands
        )
        assertEquals(
            emptyList(),
            optionsByType.getValue(MemoryStrategyType.SLIDING_WINDOW).additionalCommands
        )
        assertEquals(
            emptyList(),
            optionsByType.getValue(MemoryStrategyType.STICKY_FACTS).additionalCommands
        )

        val branchingCommands = optionsByType.getValue(MemoryStrategyType.BRANCHING).additionalCommands
        assertEquals(
            listOf(
                "checkpoint [name]",
                "branches",
                "branch create <name>",
                "branch use <name>"
            ),
            branchingCommands.map { it.command }
        )
    }
}

private class FactoryTestLanguageModel : LanguageModel {
    override val info = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = "fake-model"
    )

    override val tokenCounter = null

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}
