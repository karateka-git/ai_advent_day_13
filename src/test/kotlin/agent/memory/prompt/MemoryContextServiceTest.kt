package agent.memory.prompt

import agent.memory.core.MemoryStrategy
import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.WorkingMemory
import agent.memory.strategy.MemoryStrategyType
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.LanguageModel
import llm.core.model.ChatMessage
import llm.core.model.ChatRole
import llm.core.model.LanguageModelInfo
import llm.core.model.LanguageModelResponse
import llm.core.tokenizer.TokenCounter

class MemoryContextServiceTest {
    private val service = DefaultMemoryContextService(
        memoryStrategyProvider = { EchoShortTermStrategy() }
    )

    @Test
    fun `builds effective conversation through strategy and layered assembler`() {
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                messages = listOf(
                    ChatMessage(ChatRole.SYSTEM, "ignored"),
                    ChatMessage(ChatRole.USER, "Привет")
                )
            ),
            working = WorkingMemory(
                notes = listOf(MemoryNote("goal", "Собрать ТЗ"))
            ),
            longTerm = LongTermMemory(
                notes = listOf(MemoryNote("communication_style", "Отвечай кратко"))
            )
        )

        val conversation = service.effectiveConversation(
            systemPrompt = "Ты помощник.",
            state = state
        )

        assertEquals(
            ChatMessage(
                ChatRole.SYSTEM,
                "Ты помощник.\n\nLong-term memory\n- communication_style: Отвечай кратко\n\nWorking memory\n- goal: Собрать ТЗ"
            ),
            conversation.first()
        )
        assertEquals(ChatMessage(ChatRole.USER, "Привет"), conversation[1])
    }

    @Test
    fun `counts prompt tokens through assembled context`() {
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                messages = listOf(
                    ChatMessage(ChatRole.SYSTEM, "ignored"),
                    ChatMessage(ChatRole.USER, "Привет")
                )
            )
        )
        val languageModel = FakeLanguageModel(CharacterTokenCounter())

        val tokens = service.countPromptTokens(
            languageModel = languageModel,
            systemPrompt = "Ты помощник.",
            state = state
        )
        val expectedConversation = service.effectiveConversation("Ты помощник.", state)

        assertEquals(
            expectedConversation.sumOf { "${it.role.apiValue}\n${it.content}".length },
            tokens
        )
    }
}

private class EchoShortTermStrategy : MemoryStrategy {
    override val type: MemoryStrategyType = MemoryStrategyType.NO_COMPRESSION

    override fun effectiveContext(state: MemoryState): List<ChatMessage> = state.shortTerm.messages
}

private class FakeLanguageModel(
    override val tokenCounter: TokenCounter?
) : LanguageModel {
    override val info = LanguageModelInfo(
        name = "FakeLanguageModel",
        model = "fake-model"
    )

    override fun complete(messages: List<ChatMessage>): LanguageModelResponse =
        error("Не должен вызываться в этом тесте.")
}

private class CharacterTokenCounter : TokenCounter {
    override fun countText(text: String): Int = text.length
}
