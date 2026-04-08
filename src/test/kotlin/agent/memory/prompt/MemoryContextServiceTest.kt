package agent.memory.prompt

import agent.memory.core.MemoryStrategy
import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.MemoryState
import agent.memory.model.ShortTermMemory
import agent.memory.model.UserAccount
import agent.memory.model.WorkingMemory
import agent.memory.strategy.MemoryStrategyType
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class MemoryContextServiceTest {
    private val service = DefaultMemoryContextService(
        memoryStrategyProvider = { EchoShortTermStrategy() }
    )

    @Test
    fun `builds effective prompt context through strategy and layered assembler`() {
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                rawMessages = listOf(
                    ChatMessage(ChatRole.SYSTEM, "ignored"),
                    ChatMessage(ChatRole.USER, "Привет")
                ),
                derivedMessages = listOf(
                    ChatMessage(ChatRole.SYSTEM, "ignored"),
                    ChatMessage(ChatRole.USER, "Привет")
                )
            ),
            longTerm = LongTermMemory(
                notes = listOf(
                    MemoryNote(
                        id = "",
                        category = "communication_style",
                        content = "Отвечай кратко",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "anna"
                    ),
                    MemoryNote("architectural_agreement", "Используем Kotlin CLI")
                )
            ),
            working = WorkingMemory(
                notes = listOf(MemoryNote("goal", "Собрать ТЗ"))
            ),
            users = listOf(UserAccount("anna", "Anna")),
            activeUserId = "anna"
        )

        val promptContext = service.effectivePromptContext(state)

        assertEquals(
            listOf(
                ChatMessage(ChatRole.SYSTEM, "ignored"),
                ChatMessage(ChatRole.USER, "Привет")
            ),
            promptContext.messages
        )
        assertEquals(
            """
            Профиль пользователя (Anna)

            Это обязательные правила ответа для текущего пользователя.
            Автоматически применяй их в каждом ответе, если пользователь явно не попросил иначе.
            Если предыдущие сообщения в этой сессии оформлены иначе, всё равно следуй профилю в новом ответе.

            Приоритет:
            - Текущее сообщение пользователя важнее профиля.
            - Профиль важнее стандартного поведения ассистента.
            - Профиль важнее инерции предыдущих ответов в диалоге.

            Правила ответа
            - Отвечай кратко

            Long-term memory
            - architectural_agreement: Используем Kotlin CLI

            Working memory
            - goal: Собрать ТЗ
            """.trimIndent(),
            promptContext.systemPromptContribution
        )
    }

    @Test
    fun `preview prompt context uses the same contribution contract`() {
        val state = MemoryState(
            shortTerm = ShortTermMemory(
                derivedMessages = listOf(ChatMessage(ChatRole.USER, "preview"))
            )
        )

        val promptContext = service.previewPromptContext(state)

        assertEquals(listOf(ChatMessage(ChatRole.USER, "preview")), promptContext.messages)
        assertEquals(null, promptContext.systemPromptContribution)
    }
}

private class EchoShortTermStrategy : MemoryStrategy {
    override val type: MemoryStrategyType = MemoryStrategyType.NO_COMPRESSION

    override fun effectiveContext(state: MemoryState): List<ChatMessage> = state.shortTerm.derivedMessages
}
