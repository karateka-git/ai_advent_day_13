package agent.memory.prompt

import agent.memory.model.LongTermMemory
import agent.memory.model.MemoryNote
import agent.memory.model.MemoryOwnerType
import agent.memory.model.UserAccount
import agent.memory.model.WorkingMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import llm.core.model.ChatMessage
import llm.core.model.ChatRole

class LayeredMemoryPromptAssemblerTest {
    private val assembler = LayeredMemoryPromptAssembler()

    @Test
    fun `injects active user profile as instruction block into first system message`() {
        val prompt = assembler.assemble(
            systemPrompt = "Ты помощник.",
            activeUser = UserAccount("anna", "Anna"),
            longTermMemory = LongTermMemory(
                notes = listOf(
                    MemoryNote(
                        id = "",
                        category = "communication_style",
                        content = "Отвечай кратко.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "anna"
                    ),
                    MemoryNote(
                        id = "",
                        category = "persistent_preference",
                        content = "Сначала давай вывод, потом детали.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "anna"
                    ),
                    MemoryNote("architectural_agreement", "Используй Kotlin")
                )
            ),
            workingMemory = WorkingMemory(
                notes = listOf(MemoryNote("goal", "Собрать ТЗ"))
            ),
            shortTermContext = listOf(
                ChatMessage(ChatRole.SYSTEM, "старый системный prompt"),
                ChatMessage(ChatRole.USER, "Привет")
            )
        )

        assertEquals(
            ChatMessage(
                ChatRole.SYSTEM,
                """
                Ты помощник.

                Профиль пользователя (Anna)

                Это обязательные правила ответа для текущего пользователя.
                Автоматически применяй их в каждом ответе, если пользователь явно не попросил иначе.
                Если предыдущие сообщения в этой сессии оформлены иначе, всё равно следуй профилю в новом ответе.

                Приоритет:
                - Текущее сообщение пользователя важнее профиля.
                - Профиль важнее стандартного поведения ассистента.
                - Профиль важнее инерции предыдущих ответов в диалоге.

                Правила ответа
                - Отвечай кратко.

                Постоянные предпочтения
                - Сначала давай вывод, потом детали.

                Long-term memory
                - architectural_agreement: Используй Kotlin

                Working memory
                - goal: Собрать ТЗ
                """.trimIndent()
            ),
            prompt.first()
        )
        assertEquals(ChatMessage(ChatRole.USER, "Привет"), prompt[1])
    }

    @Test
    fun `prepends system message when short-term context has none`() {
        val prompt = assembler.assemble(
            systemPrompt = "Ты помощник.",
            activeUser = UserAccount("default", "Default"),
            longTermMemory = LongTermMemory(),
            workingMemory = WorkingMemory(),
            shortTermContext = listOf(ChatMessage(ChatRole.USER, "Привет"))
        )

        assertEquals(ChatMessage(ChatRole.SYSTEM, "Ты помощник."), prompt.first())
        assertEquals(ChatMessage(ChatRole.USER, "Привет"), prompt[1])
    }

    @Test
    fun `does not include profile notes of another user`() {
        val prompt = assembler.assemble(
            systemPrompt = "Ты помощник.",
            activeUser = UserAccount("default", "Default"),
            longTermMemory = LongTermMemory(
                notes = listOf(
                    MemoryNote(
                        id = "",
                        category = "communication_style",
                        content = "Общайся как со старым другом.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "vlados"
                    ),
                    MemoryNote(
                        id = "",
                        category = "communication_style",
                        content = "Отвечай строго по делу.",
                        ownerType = MemoryOwnerType.USER,
                        ownerId = "default"
                    )
                )
            ),
            workingMemory = WorkingMemory(),
            shortTermContext = listOf(ChatMessage(ChatRole.SYSTEM, "старый системный prompt"))
        )

        assertEquals(
            ChatMessage(
                ChatRole.SYSTEM,
                """
                Ты помощник.

                Профиль пользователя (Default)

                Это обязательные правила ответа для текущего пользователя.
                Автоматически применяй их в каждом ответе, если пользователь явно не попросил иначе.
                Если предыдущие сообщения в этой сессии оформлены иначе, всё равно следуй профилю в новом ответе.

                Приоритет:
                - Текущее сообщение пользователя важнее профиля.
                - Профиль важнее стандартного поведения ассистента.
                - Профиль важнее инерции предыдущих ответов в диалоге.

                Правила ответа
                - Отвечай строго по делу.
                """.trimIndent()
            ),
            prompt.first()
        )
    }
}
